package com.cardinalstar.cubicchunks.world.convert;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;

import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.world.ChunkCoordIntPair;
import net.minecraftforge.common.util.Constants.NBT;

import com.cardinalstar.cubicchunks.api.world.storage.ICubicStorage;
import com.cardinalstar.cubicchunks.api.world.storage.ICubicStorage.NBTBatch;
import com.cardinalstar.cubicchunks.api.world.storage.ICubicStorage.PosBatch;
import com.cardinalstar.cubicchunks.lighting.LightingManager;
import com.cardinalstar.cubicchunks.network.CCPacketBuffer;
import com.cardinalstar.cubicchunks.server.chunkio.RegionCubeStorage;
import com.cardinalstar.cubicchunks.util.Coords;
import com.cardinalstar.cubicchunks.util.CubePos;
import com.cardinalstar.cubicchunks.world.cube.Cube;
import com.cardinalstar.cubicchunks.world.heightmap.HeightMap3D;

import io.netty.buffer.Unpooled;

/**
 * Converts a cube storage world into vanilla's Anvil format. A column and its cubes from Y 0 to 15 become a chunk;
 * vanilla cannot hold anything above or below, so that is left out (and stays in the backup).
 * <p>
 * Each cube's section is the chunk's section, unchanged, see {@link VanillaToCCConverter}. The cube storage is read
 * with the game's own storage code, which opens region files for writing and may pad a file that was cut off; it does
 * not change what they hold. The chunk keeps the cubes'
 * light when nothing above Y 255 shades it and all its cubes are lit; otherwise vanilla lights it again.
 */
@ParametersAreNonnullByDefault
public final class CCToVanillaConverter extends WorldConverter {

    /** What the game itself keeps in a column's Level. */
    private static final Set<String> COLUMN_KEYS = new HashSet<>(
        Arrays.asList("v", "x", "z", "InhabitedTime", "OpacityIndex", "HeightMap3D", "PrecipitationHeightMap3D"));

    /** Chunk data of a column's root that vanilla keeps only in Level, and that is set here. */
    private static final Set<String> LEVEL_ONLY = new HashSet<>(
        Arrays.asList("HeightMap", "LightPopulated", "Biomes", "Biomes16v2"));

    /** Columns converted per task; a multiple of the storage's 2x2 column groups. */
    private static final int BATCH = 8;

    @Nullable
    private final BlockTable blocks;

    public CCToVanillaConverter() {
        this.blocks = null;
    }

    /** With the given block properties instead of the world's ID map and the running game's blocks. */
    public CCToVanillaConverter(BlockTable blocks) {
        this.blocks = blocks;
    }

    @Override
    String[] sourceFolders() {
        return CUBIC;
    }

    @Override
    List<Path> prepare(Path root) throws IOException {
        List<Path> dimensions = dimensions(root, CUBIC);
        if (dimensions.isEmpty()) throw new IOException("No cube storage (region2d, region3d) in " + root);
        // Region files elsewhere in the world are not this conversion's business (a mod's own data in that form).
        for (Path dimension : dimensions) {
            if (!regionFiles(dimension).isEmpty()) {
                throw new IOException("This world already holds Anvil regions in " + dimension);
            }
        }
        return dimensions;
    }

    @Override
    void convert(Path root, List<Path> dimensions, ConversionInstall install, ConversionReport report,
        ConversionProgress progress, AtomicBoolean cancelSignal) throws IOException {
        BlockTable table = blocks != null ? blocks : BlockTable.forWorld(root);
        Path staging = install.staging();
        // The column count of every dimension is only known after listing it; the bar restarts per dimension.
        for (Path dimension : dimensions) {
            progress.setDimension(
                dimension.equals(root) ? "Overworld"
                    : root.relativize(dimension)
                        .toString());
            convertDimension(
                dimension,
                staging.resolve(root.relativize(dimension))
                    .resolve(ANVIL),
                table,
                report,
                progress,
                cancelSignal);
        }
        progress.setDimension("");

        Path backup = newBackupFolder(root, "cubic");
        for (Path dimension : dimensions) {
            Path relative = root.relativize(dimension);
            for (String folder : CUBIC) {
                if (Files.exists(dimension.resolve(folder))) {
                    install.move(
                        dimension.resolve(folder),
                        backup.resolve(relative)
                            .resolve(folder));
                }
            }
            Path converted = staging.resolve(relative)
                .resolve(ANVIL);
            if (Files.exists(converted)) install.moveFolder(converted, dimension.resolve(ANVIL));
        }
        // Without these the game takes the world for a vanilla one again.
        for (String file : new String[] { MARKER, "data/cubicChunksData.dat" }) {
            if (Files.exists(root.resolve(file))) install.move(root.resolve(file), backup.resolve(file));
        }
        report.dimensions.set(dimensions.size());
        report.backup = backup;
    }

    private static final class Encoded {

        final List<ChunkCoordIntPair> positions = new ArrayList<>();
        final List<byte[]> records = new ArrayList<>();
    }

    private void convertDimension(Path dimension, Path output, BlockTable table, ConversionReport report,
        ConversionProgress progress, AtomicBoolean cancelSignal) throws IOException {
        try (ICubicStorage storage = new RegionCubeStorage(dimension)) {
            // By region file, then in batches of BATCH x BATCH columns: region files are written one after another.
            Map<Long, Map<Integer, List<ChunkCoordIntPair>>> regions = new java.util.TreeMap<>();
            int[] total = { 0 };
            storage.forEachColumn(pos -> {
                long region = (long) (pos.chunkXPos >> 5) << 32 | (pos.chunkZPos >> 5) & 0xFFFFFFFFL;
                int batch = (pos.chunkXPos & 31) / BATCH * (32 / BATCH) + (pos.chunkZPos & 31) / BATCH;
                regions.computeIfAbsent(region, key -> new java.util.TreeMap<>())
                    .computeIfAbsent(batch, key -> new ArrayList<>())
                    .add(pos);
                total[0]++;
            });
            progress.update(0, total[0], "Starting...");

            int[] done = { 0 };
            for (Map<Integer, List<ChunkCoordIntPair>> region : regions.values()) {
                List<Callable<Encoded>> tasks = new ArrayList<>();
                for (List<ChunkCoordIntPair> batch : region.values()) {
                    tasks.add(() -> convertBatch(storage, batch, table, report));
                }
                AnvilRegionWriter[] writer = { null };
                try {
                    runOrdered(tasks.iterator(), encoded -> {
                        for (int i = 0; i < encoded.positions.size(); i++) {
                            ChunkCoordIntPair pos = encoded.positions.get(i);
                            if (writer[0] == null) {
                                writer[0] = new AnvilRegionWriter(
                                    AnvilRegionWriter.file(output, pos.chunkXPos, pos.chunkZPos));
                            }
                            writer[0].write(pos.chunkXPos, pos.chunkZPos, encoded.records.get(i));
                            progress.update(++done[0], total[0], pos.chunkXPos + "," + pos.chunkZPos);
                        }
                        report.chunks.addAndGet(encoded.positions.size());
                    }, cancelSignal);
                } finally {
                    if (writer[0] != null) writer[0].close();
                }
            }
        }
    }

    private static Encoded convertBatch(ICubicStorage storage, List<ChunkCoordIntPair> positions, BlockTable table,
        ConversionReport report) throws IOException {
        List<CubePos> cubePositions = new ArrayList<>(positions.size() * (ANVIL_SECTIONS + 2));
        for (ChunkCoordIntPair pos : positions) {
            for (int y = -1; y <= ANVIL_SECTIONS; y++) cubePositions.add(new CubePos(pos.chunkXPos, y, pos.chunkZPos));
        }
        NBTBatch read = storage.readBatch(new PosBatch(positions, cubePositions));

        Encoded encoded = new Encoded();
        for (ChunkCoordIntPair pos : positions) {
            NBTTagCompound column = read.columns.get(pos);
            // listed by a record that holds no data for it any more
            if (column == null) continue;
            NBTTagCompound[] cubes = new NBTTagCompound[ANVIL_SECTIONS + 2];
            for (int y = -1; y <= ANVIL_SECTIONS; y++) {
                cubes[y + 1] = read.cubes.get(new CubePos(pos.chunkXPos, y, pos.chunkZPos));
            }
            encoded.positions.add(pos);
            encoded.records
                .add(AnvilRegionWriter.encode(chunk(pos.chunkXPos, pos.chunkZPos, column, cubes, table, report)));
        }
        return encoded;
    }

    /**
     * The chunk of a column and its cubes.
     * <p>
     * Where the column's other data goes, the reverse of {@link VanillaToCCConverter}: what the column has in Level
     * besides the game's own tags is the chunk's Level data. What it has in its root is either chunk data that
     * ChunkAPI's managers keep in a chunk's Level, or mods' {@code ChunkDataEvent} data, which a chunk has outside
     * Level. A tag that is in both places came from a chunk's Level. Any other root tag goes to both places, as the
     * two kinds cannot be told apart; vanilla writes both anew when it saves the chunk.
     *
     * @param cubes the cubes from Y -1 to 16 (index: Y + 1), null where a cube does not exist. Those at Y 0 to 15
     *              become the chunk's sections; their tags are moved into the result, not copied.
     */
    static NBTTagCompound chunk(int chunkX, int chunkZ, NBTTagCompound column, NBTTagCompound[] cubes, BlockTable table,
        ConversionReport report) {
        NBTTagCompound columnLevel = column.getCompoundTag("Level");

        NBTTagCompound root = new NBTTagCompound();
        NBTTagCompound level = new NBTTagCompound();
        for (String key : VanillaToCCConverter.keys(columnLevel)) {
            if (!COLUMN_KEYS.contains(key)) level.setTag(key, columnLevel.getTag(key));
        }
        for (String key : VanillaToCCConverter.keys(column)) {
            if (key.equals("Level")) continue;
            NBTBase tag = column.getTag(key);
            if (level.hasKey(key) || LEVEL_ONLY.contains(key)) {
                level.setTag(key, tag);
            } else {
                level.setTag(key, tag);
                root.setTag(key, tag.copy());
            }
        }

        NBTTagList sections = new NBTTagList(), entities = new NBTTagList(), tileEntities = new NBTTagList(),
            tileTicks = new NBTTagList();
        // Vanilla populates and lights a chunk as a whole, the game each cube. Vanilla's populators run for a column
        // when its cube at Y 0 is populated (VanillaWorldGenerator.populate), so that cube decides: populated terrain
        // must not be populated again, and unpopulated terrain must not be kept from it. Light is only kept when all
        // of it is done.
        boolean populated = cubes[1] != null && (cubes[1].getCompoundTag("Level")
            .getShort("population") & Cube.POP_000) != 0;
        boolean lit = true, complete = true;
        NBTTagCompound[] chunkSections = new NBTTagCompound[ANVIL_SECTIONS];
        for (int y = 0; y < ANVIL_SECTIONS; y++) {
            if (cubes[y + 1] == null) {
                complete = false;
                continue;
            }
            NBTTagCompound cube = cubes[y + 1].getCompoundTag("Level");
            // light saved by another lighting engine is discarded by the game when it loads the cube
            lit &= cube.getBoolean("initLightDone") && LightingManager.ID.equals(cube.getString("LightingInfoType"));
            NBTTagList cubeSections = cube.getTagList("Sections", NBT.TAG_COMPOUND);
            if (cubeSections.tagCount() > 0) {
                NBTTagCompound section = cubeSections.getCompoundTagAt(0);
                section.setByte("Y", (byte) y);
                sections.appendTag(section);
                chunkSections[y] = section;
            }
            moveAll(cube.getTagList("Entities", NBT.TAG_COMPOUND), entities);
            moveAll(cube.getTagList("TileEntities", NBT.TAG_COMPOUND), tileEntities);
            moveAll(cube.getTagList("TileTicks", NBT.TAG_COMPOUND), tileTicks);
        }
        if (!complete) report.incompleteChunks.incrementAndGet();

        // The cubes' light is vanilla's light when nothing outside Y 0 to 255 takes part in it: no block above that
        // blocks light, and no light entering from the cubes above and below.
        int[] heights = new int[Cube.SIZE * Cube.SIZE];
        HeightMap3D opacity = new HeightMap3D(heights, 256);
        if (columnLevel.hasKey("HeightMap3D", NBT.TAG_BYTE_ARRAY)) {
            opacity.readData(new CCPacketBuffer(Unpooled.wrappedBuffer(columnLevel.getByteArray("HeightMap3D"))));
            for (int i = 0; i < heights.length && lit; i++) {
                lit = opacity.getTopBlockY(i & 15, i >> 4) < Coords.cubeToMinBlock(ANVIL_SECTIONS);
            }
        } else {
            // a column the converter left to the game (unknown block layout) and the game never loaded: it has no
            // height map yet
            lit = false;
            int[] opaqueBits = new int[heights.length], rainBits = new int[heights.length];
            for (int y = 0; y < ANVIL_SECTIONS; y++) {
                if (chunkSections[y] == null) continue;
                Arrays.fill(opaqueBits, 0);
                if (SectionBlocks.collect(chunkSections[y], table, opaqueBits, rainBits) == SectionBlocks.UNREADABLE)
                    continue;
                for (int i = 0; i < heights.length; i++) {
                    if (opaqueBits[i] != 0) {
                        opacity.setBits(i & 15, i >> 4, Coords.cubeToMinBlock(y), Cube.SIZE, opaqueBits[i]);
                    }
                }
            }
        }
        lit = lit && complete
            && populated
            && !lightCrosses(cubes[0], true)
            && !lightCrosses(cubes[ANVIL_SECTIONS + 1], false);

        // Vanilla's own height map as long as the game has not saved the column since the chunk was converted, else
        // the column's, which stops at Y 255 like vanilla's.
        if (level.getIntArray("HeightMap").length != heights.length) {
            level.setIntArray("HeightMap", heights);
        }

        level.setByte("V", (byte) 1);
        level.setInteger("xPos", chunkX);
        level.setInteger("zPos", chunkZ);
        level.setLong("LastUpdate", 0L);
        level.setLong("InhabitedTime", columnLevel.getLong("InhabitedTime"));
        level.setBoolean("TerrainPopulated", populated);
        // false: vanilla checks the chunk's light again
        level.setBoolean("LightPopulated", lit);
        level.setTag("Sections", sections);
        level.setTag("Entities", entities);
        level.setTag("TileEntities", tileEntities);
        // as vanilla: only when there are any
        if (tileTicks.tagCount() > 0) level.setTag("TileTicks", tileTicks);
        else level.removeTag("TileTicks");
        root.setTag("Level", level);
        return root;
    }

    /**
     * Whether light can pass between a cube just outside the chunk and the chunk: any light above 1 in the cube's
     * layer that touches it. Light of 1 does not reach the next block.
     *
     * @param topLayer the cube is below the chunk, else above it
     */
    private static boolean lightCrosses(@Nullable NBTTagCompound cube, boolean topLayer) {
        if (cube == null) return false;
        NBTTagList sections = cube.getCompoundTag("Level")
            .getTagList("Sections", NBT.TAG_COMPOUND);
        if (sections.tagCount() == 0) return false;
        NBTTagCompound section = sections.getCompoundTagAt(0);
        // above the chunk only block light matters: the sky is as bright there as vanilla assumes
        for (String key : topLayer ? new String[] { "BlockLight", "SkyLight" } : new String[] { "BlockLight" }) {
            if (!section.hasKey(key)) {
                if (key.equals("SkyLight")) continue; // a dimension without sky
                return true;
            }
            byte[] light = section.getByteArray(key);
            if (light.length != 2048) return true;
            // 128 bytes are one layer of 256 blocks; index y << 8 | z << 4 | x
            for (int i = topLayer ? 2048 - 128 : 0, end = i + 128; i < end; i++) {
                if ((light[i] & 0xEE) != 0) return true;
            }
        }
        return false;
    }

    private static void moveAll(NBTTagList from, NBTTagList to) {
        for (int i = 0; i < from.tagCount(); i++) to.appendTag(from.getCompoundTagAt(i));
    }
}
