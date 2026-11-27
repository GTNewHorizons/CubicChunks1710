package com.cardinalstar.cubicchunks.world.convert;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;

import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.world.ChunkCoordIntPair;
import net.minecraftforge.common.util.Constants.NBT;

import com.cardinalstar.cubicchunks.CubicChunks;
import com.cardinalstar.cubicchunks.api.world.storage.ICubicStorage;
import com.cardinalstar.cubicchunks.api.world.storage.ICubicStorage.NBTBatch;
import com.cardinalstar.cubicchunks.api.world.storage.StorageFormatFactory;
import com.cardinalstar.cubicchunks.lighting.LightingManager;
import com.cardinalstar.cubicchunks.lighting.phosphor.LightingHooks;
import com.cardinalstar.cubicchunks.server.chunkio.RegionCubeStorage;
import com.cardinalstar.cubicchunks.util.Coords;
import com.cardinalstar.cubicchunks.util.CubePos;
import com.cardinalstar.cubicchunks.world.convert.AnvilRegionReader.UnreadableChunkException;
import com.cardinalstar.cubicchunks.world.cube.Cube;
import com.cardinalstar.cubicchunks.world.heightmap.HeightMap3D;
import com.cardinalstar.cubicchunks.world.heightmap.PrecipitationHeightMap;

/**
 * Converts a vanilla Anvil world into cube storage, as the game itself would have saved it.
 * <p>
 * A chunk becomes a column and 16 cubes:
 * <ul>
 * <li>Each section's NBT is the cube's section, unchanged, whatever block ID format it is in. The game reads a cube's
 * section with the same code as a chunk's.</li>
 * <li>The column gets the height maps the game keeps for it (which blocks block light, and which stop rain), computed
 * from the blocks with the world's own block IDs.</li>
 * <li>The cubes keep vanilla's light and are marked as lit, with the lighting data the game saves. A chunk vanilla had
 * not lit or populated yet is left for the game to light or populate.</li>
 * <li>Tags the converter does not know (mods' data) are carried over, see {@link #column}.</li>
 * </ul>
 * Nothing is written above Y 255 or below Y 0; the game generates those cubes when they are first needed.
 */
@ParametersAreNonnullByDefault
public final class VanillaToCCConverter extends WorldConverter {

    /** What vanilla itself keeps in a chunk's Level and the converter places itself. */
    private static final Set<String> CHUNK_KEYS = new HashSet<>(
        Arrays.asList(
            "V",
            "xPos",
            "zPos",
            "LastUpdate",
            "TerrainPopulated",
            "InhabitedTime",
            "Sections",
            "Entities",
            "TileEntities",
            "TileTicks"));

    /** Chunk data that ChunkAPI's own managers keep in a column's root, and nothing reads from a column's Level. */
    private static final Set<String> COLUMN_ROOT_ONLY = new HashSet<>(Arrays.asList("HeightMap", "LightPopulated"));

    /** Columns converted per task: whole 2x2 column groups and 2x2x2 cube bundles, so each record is written once. */
    private static final int BATCH = 8;

    @Nullable
    private final BlockTable blocks;

    public VanillaToCCConverter() {
        this.blocks = null;
    }

    /** With the given block properties instead of the world's ID map and the running game's blocks. */
    public VanillaToCCConverter(BlockTable blocks) {
        this.blocks = blocks;
    }

    @Override
    String[] sourceFolders() {
        return new String[] { ANVIL };
    }

    @Override
    List<Path> prepare(Path root) throws IOException {
        List<Path> dimensions = anvilDimensions(root);
        if (dimensions.isEmpty()) throw new IOException("No Anvil regions (region/r.X.Z.mca) in " + root);
        // A format marker without any cube storage was left by opening the vanilla world with CubicChunks; it is
        // replaced. Cube storage with content is another matter.
        for (Path dimension : dimensions(root, CUBIC)) {
            throw new IOException("This world already holds cube storage in " + dimension);
        }
        return dimensions;
    }

    @Override
    void convert(Path root, List<Path> dimensions, ConversionInstall install, ConversionReport report,
        ConversionProgress progress, AtomicBoolean cancelSignal) throws IOException {
        BlockTable table = blocks != null ? blocks : BlockTable.forWorld(root);

        int total = 0;
        Map<Path, List<Path>> regions = new LinkedHashMap<>();
        for (Path dimension : dimensions) {
            List<Path> files = regionFiles(dimension);
            regions.put(dimension, files);
            for (Path file : files) {
                checkCancelled(cancelSignal);
                try (AnvilRegionReader reader = new AnvilRegionReader(file)) {
                    total += reader.chunkCount();
                }
            }
        }
        progress.update(0, total, "Starting...");

        Path staging = install.staging();
        List<Path> skipped = new ArrayList<>();
        for (Path dimension : dimensions) {
            progress.setDimension(
                dimension.equals(root) ? "Overworld"
                    : root.relativize(dimension)
                        .toString());
            int before = report.chunks.get();
            convertDimension(
                regions.get(dimension),
                staging.resolve(root.relativize(dimension)),
                table,
                report,
                progress,
                total,
                cancelSignal);
            if (report.chunks.get() == before) {
                // Not one chunk in it: region files of something else (a mod's own data), or all damaged. It stays.
                CubicChunks.LOGGER
                    .warn("No chunk could be read from {}; its region folder is left as it is", dimension);
                // Without its overworld the result would be no world; mod data is never in the world folder's own
                // region.
                if (dimension.equals(root))
                    throw new IOException("No chunk could be read from " + dimension.resolve(ANVIL));
                ConversionInstall.deleteTree(staging.resolve(root.relativize(dimension)));
                skipped.add(dimension);
            }
        }
        if (skipped.size() == dimensions.size()) throw new IOException("No chunk could be read from " + root);
        progress.setDimension("");
        CubicChunks.LOGGER.info(
            "Converted {} chunks; {} unreadable, {} left to the game, {} cubes with blocks of unknown kind",
            report.chunks.get(),
            report.unreadableChunks.get(),
            report.chunksLeftToGame.get(),
            report.cubesLeftToGame.get());

        Path marker = staging.resolve(MARKER);
        writeFormatMarker(marker);

        Path backup = newBackupFolder(root, "anvil");
        for (Path dimension : dimensions) {
            if (skipped.contains(dimension)) continue;
            Path relative = root.relativize(dimension);
            install.move(
                dimension.resolve(ANVIL),
                backup.resolve(relative)
                    .resolve(ANVIL));
            for (String folder : CUBIC) {
                Path converted = staging.resolve(relative)
                    .resolve(folder);
                // an empty folder of that name may exist
                if (Files.exists(converted)) install.moveFolder(converted, dimension.resolve(folder));
            }
        }
        // Last: until then the game does not take the world for cube storage. A marker that is there already was left
        // by opening the vanilla world with CubicChunks and says nothing; it is replaced.
        install.replace(marker, root.resolve(MARKER));
        report.dimensions.set(dimensions.size() - skipped.size());
        report.backup = backup;
    }

    private void convertDimension(List<Path> regionFiles, Path output, BlockTable table, ConversionReport report,
        ConversionProgress progress, int total, AtomicBoolean cancelSignal) throws IOException {
        Files.createDirectories(output);
        try (ICubicStorage storage = new RegionCubeStorage(output)) {
            for (Path file : regionFiles) {
                int[] region = AnvilRegionReader.regionCoords(
                    file.getFileName()
                        .toString());
                try (AnvilRegionReader reader = new AnvilRegionReader(file)) {
                    List<Callable<NBTBatch>> tasks = new ArrayList<>();
                    for (int x = 0; x < 32; x += BATCH) {
                        for (int z = 0; z < 32; z += BATCH) {
                            int minX = x, minZ = z;
                            tasks.add(() -> convertBatch(reader, region[0], region[1], minX, minZ, table, report));
                        }
                    }
                    runOrdered(tasks.iterator(), batch -> {
                        if (batch.columns.isEmpty()) return;
                        storage.writeBatch(batch);
                        int done = report.chunks.addAndGet(batch.columns.size());
                        ChunkCoordIntPair last = null;
                        for (ChunkCoordIntPair pos : batch.columns.keySet()) last = pos;
                        progress
                            .update(done + report.unreadableChunks.get(), total, last.chunkXPos + "," + last.chunkZPos);
                    }, cancelSignal);
                }
            }
            storage.flush();
        }
    }

    private static NBTBatch convertBatch(AnvilRegionReader reader, int regionX, int regionZ, int minX, int minZ,
        BlockTable table, ConversionReport report) throws IOException {
        Map<ChunkCoordIntPair, NBTTagCompound> columns = new LinkedHashMap<>();
        Map<CubePos, NBTTagCompound> cubes = new LinkedHashMap<>();
        for (int localX = minX; localX < minX + BATCH; localX++) {
            for (int localZ = minZ; localZ < minZ + BATCH; localZ++) {
                if (!reader.isPresent(localX, localZ)) continue;
                int chunkX = regionX * 32 + localX, chunkZ = regionZ * 32 + localZ;
                try {
                    NBTTagCompound chunk = reader.read(localX, localZ);
                    if (chunk == null) continue;
                    convertChunk(chunk, chunkX, chunkZ, table, report, columns, cubes);
                } catch (UnreadableChunkException e) {
                    // as for the game: a chunk that cannot be read does not exist and is generated again
                    report.unreadableChunks.incrementAndGet();
                    CubicChunks.LOGGER.warn("Leaving out chunk {},{}: {}", chunkX, chunkZ, e.getMessage());
                }
            }
        }
        return new NBTBatch(columns, cubes);
    }

    /** Converts one chunk. Tags of {@code chunk} are moved into the result, not copied. */
    static void convertChunk(NBTTagCompound chunk, int chunkX, int chunkZ, BlockTable table, ConversionReport report,
        Map<ChunkCoordIntPair, NBTTagCompound> columns, Map<CubePos, NBTTagCompound> cubes)
        throws UnreadableChunkException {
        if (!chunk.hasKey("Level", NBT.TAG_COMPOUND)) {
            throw new UnreadableChunkException("Chunk " + chunkX + "," + chunkZ + " has no Level", null);
        }
        NBTTagCompound level = chunk.getCompoundTag("Level");
        if (level.getInteger("xPos") != chunkX || level.getInteger("zPos") != chunkZ) {
            // as vanilla: the slot decides where a chunk is
            CubicChunks.LOGGER.warn(
                "Chunk in slot {},{} says it is at {},{}; keeping it at its slot",
                chunkX,
                chunkZ,
                level.getInteger("xPos"),
                level.getInteger("zPos"));
        }

        NBTTagCompound[] sections = new NBTTagCompound[ANVIL_SECTIONS];
        NBTTagList sectionList = level.getTagList("Sections", NBT.TAG_COMPOUND);
        for (int i = 0; i < sectionList.tagCount(); i++) {
            NBTTagCompound section = sectionList.getCompoundTagAt(i);
            int y = section.getByte("Y");
            if (y < 0 || y >= ANVIL_SECTIONS) {
                throw new UnreadableChunkException("Chunk " + chunkX + "," + chunkZ + " has a section at Y " + y, null);
            }
            sections[y] = section;
        }

        // Height maps, as the game builds them when it loads each cube (Cube.updateHeightMap).
        HeightMap3D opacity = new HeightMap3D(new int[Cube.SIZE * Cube.SIZE], 256);
        HeightMap3D rain = new PrecipitationHeightMap();
        boolean known = true;
        boolean[] partial = new boolean[ANVIL_SECTIONS];
        int[] opaqueBits = new int[Cube.SIZE * Cube.SIZE], rainBits = new int[Cube.SIZE * Cube.SIZE];
        for (int y = 0; y < ANVIL_SECTIONS && known; y++) {
            if (sections[y] == null) continue;
            Arrays.fill(opaqueBits, 0);
            Arrays.fill(rainBits, 0);
            int read = SectionBlocks.collect(sections[y], table, opaqueBits, rainBits);
            if (read == SectionBlocks.UNREADABLE) {
                known = false;
                break;
            }
            if (read == SectionBlocks.PARTIAL) {
                // The game adds this cube to the height maps itself when it loads it.
                partial[y] = true;
                report.cubesLeftToGame.incrementAndGet();
            }
            int minY = Coords.cubeToMinBlock(y);
            for (int i = 0; i < opaqueBits.length; i++) {
                if (opaqueBits[i] != 0) opacity.setBits(i & 15, i >> 4, minY, Cube.SIZE, opaqueBits[i]);
                if (rainBits[i] != 0) rain.setBits(i & 15, i >> 4, minY, Cube.SIZE, rainBits[i]);
            }
        }
        if (!known) report.chunksLeftToGame.incrementAndGet();

        boolean populated = level.getBoolean("TerrainPopulated");
        boolean lit = known && populated && level.getBoolean("LightPopulated");

        columns.put(
            new ChunkCoordIntPair(chunkX, chunkZ),
            column(chunk, level, chunkX, chunkZ, known ? opacity : null, known ? rain : null));

        Map<Integer, NBTTagCompound> built = new HashMap<>();
        for (int y = 0; y < ANVIL_SECTIONS; y++) {
            built.put(y, cube(chunkX, y, chunkZ, sections[y], populated, lit, known ? opacity : null, !partial[y]));
        }

        // Entities live in the cube they are in, also above the old build height. Below Y 0 the game generates
        // terrain where vanilla had the void, so what fell out of the world is not carried over.
        NBTTagList entities = level.getTagList("Entities", NBT.TAG_COMPOUND);
        for (int i = 0; i < entities.tagCount(); i++) {
            NBTTagCompound entity = entities.getCompoundTagAt(i);
            NBTTagList pos = entity.getTagList("Pos", NBT.TAG_DOUBLE);
            double y = pos.tagCount() == 3 ? pos.func_150309_d(1) : Double.NaN;
            if (!(y >= 0 && y <= CubicChunks.MAX_SUPPORTED_BLOCK_Y)) {
                report.droppedOutOfRange.incrementAndGet();
                continue;
            }
            int cubeY = Coords.blockToCube((int) Math.floor(y));
            NBTTagCompound target = built.get(cubeY);
            if (target == null) {
                target = cube(chunkX, cubeY, chunkZ, null, populated, lit, known ? opacity : null, true);
                built.put(cubeY, target);
            }
            append(target, "Entities", entity);
        }
        // Tile entities and scheduled ticks belong to blocks, which vanilla only has from Y 0 to 255.
        for (String key : new String[] { "TileEntities", "TileTicks" }) {
            NBTTagList list = level.getTagList(key, NBT.TAG_COMPOUND);
            for (int i = 0; i < list.tagCount(); i++) {
                NBTTagCompound tag = list.getCompoundTagAt(i);
                int cubeY = Coords.blockToCube(tag.getInteger("y"));
                if (!tag.hasKey("y", NBT.TAG_ANY_NUMERIC) || cubeY < 0 || cubeY >= ANVIL_SECTIONS) {
                    report.droppedOutOfRange.incrementAndGet();
                    continue;
                }
                append(built.get(cubeY), key, tag);
            }
        }

        for (Map.Entry<Integer, NBTTagCompound> entry : built.entrySet()) {
            cubes.put(new CubePos(chunkX, entry.getKey(), chunkZ), entry.getValue());
        }
    }

    private static void append(NBTTagCompound cube, String key, NBTTagCompound tag) {
        cube.getCompoundTag("Level")
            .getTagList(key, NBT.TAG_COMPOUND)
            .appendTag(tag);
    }

    /**
     * The column of a chunk, laid out as the game saves columns ({@code IONbtWriter}, version 1).
     * <p>
     * Where the chunk's other data goes: what vanilla has outside Level (mods' {@code ChunkDataEvent} data) stays in
     * the root. What it has inside Level besides its own tags is either chunk data ChunkAPI's managers keep there
     * (biomes, mods' data), which they read from a column's root, or data a mod put into Level in a
     * {@code ChunkDataEvent}, which it looks for in Level again. The two cannot be told apart, so these tags go to
     * both places; the game writes both anew when it saves the column.
     *
     * @param opacity null when the game has to build the height maps
     */
    private static NBTTagCompound column(NBTTagCompound chunk, NBTTagCompound chunkLevel, int chunkX, int chunkZ,
        @Nullable HeightMap3D opacity, @Nullable HeightMap3D rain) {
        NBTTagCompound root = new NBTTagCompound();
        NBTTagCompound level = new NBTTagCompound();

        for (String key : keys(chunk)) {
            if (!key.equals("Level")) root.setTag(key, chunk.getTag(key));
        }
        for (String key : keys(chunkLevel)) {
            if (CHUNK_KEYS.contains(key)) continue;
            NBTBase tag = chunkLevel.getTag(key);
            if (root.hasKey(key)) {
                CubicChunks.LOGGER.warn(
                    "Chunk {},{} has the tag {} both inside and outside Level; keeping the one inside",
                    chunkX,
                    chunkZ,
                    key);
            }
            root.setTag(key, tag);
            if (!COLUMN_ROOT_ONLY.contains(key)) level.setTag(key, tag.copy());
        }

        // ChunkAPI gives the column this array as it is, and the game fills it from the column's height map.
        if (root.getIntArray("HeightMap").length != Cube.SIZE * Cube.SIZE) {
            root.setIntArray("HeightMap", new int[Cube.SIZE * Cube.SIZE]);
        }

        level.setByte("v", (byte) 1);
        level.setInteger("x", chunkX);
        level.setInteger("z", chunkZ);
        level.setLong("InhabitedTime", chunkLevel.getLong("InhabitedTime"));
        if (opacity != null && rain != null) {
            level.setByteArray("HeightMap3D", opacity.getData());
            level.setByteArray("PrecipitationHeightMap3D", rain.getData());
        }
        root.setTag("Level", level);
        return root;
    }

    /**
     * A cube as the game saves it ({@code IONbtWriter}), so that cubes without content are stored as states.
     *
     * @param section     the vanilla section, or null for a cube without blocks
     * @param opacity     the column's height map, or null when the game has to build it and light the cube
     * @param inHeightMap false when the height map does not hold all of this cube's blocks
     */
    private static NBTTagCompound cube(int cubeX, int cubeY, int cubeZ, @Nullable NBTTagCompound section,
        boolean populated, boolean lit, @Nullable HeightMap3D opacity, boolean inHeightMap) {
        NBTTagCompound level = new NBTTagCompound();
        level.setByte("v", (byte) 1);
        level.setInteger("x", cubeX);
        level.setInteger("y", cubeY);
        level.setInteger("z", cubeZ);
        level.setShort("population", populated ? Cube.POP_ALL : 0);
        level.setBoolean("initLightDone", lit);
        level.setBoolean("isInHeightMap", opacity != null && inHeightMap);

        if (section != null) {
            section.removeTag("Y");
            NBTTagList sections = new NBTTagList();
            sections.appendTag(section);
            level.setTag("Sections", sections);
        }
        level.setTag("Entities", new NBTTagList());
        level.setTag("TileEntities", new NBTTagList());
        level.setTag("TileTicks", new NBTTagList());

        if (opacity != null) {
            // What LightingManager.writeToNbt saves: the column's heights when the cube was saved, which the game
            // compares with the column's heights when it loads the cube. They are the same, so nothing is relit.
            int below = Coords.cubeToMinBlock(cubeY), above = below + Cube.SIZE + 1;
            int[] heights = new int[Cube.SIZE * Cube.SIZE];
            for (int i = 0; i < heights.length; i++) {
                int top = opacity.getTopBlockY(i & 15, i >> 4);
                heights[i] = top == Coords.NO_HEIGHT ? below : Math.max(below, Math.min(above, top + 1));
            }
            NBTTagCompound lighting = new NBTTagCompound();
            lighting.setIntArray("LastHeightMap", heights);
            lighting.setLong(LightingHooks.neighborLightChecksSkyKey, 0);
            lighting.setLong(LightingHooks.neighborLightChecksBlockKey, 0);
            level.setString("LightingInfoType", LightingManager.ID);
            level.setTag("LightingInfo", lighting);
            // Cube.writeBiomeArray of a cube without 3D biomes
            level.setByteArray("Biomes", new byte[] { 0 });
        }

        NBTTagCompound root = new NBTTagCompound();
        root.setTag("Level", level);
        return root;
    }

    @SuppressWarnings("unchecked")
    static List<String> keys(NBTTagCompound tag) {
        return new ArrayList<>((Set<String>) tag.func_150296_c());
    }

    /** {@code data/cubicchunks.world_format.dat} as the game's MapStorage writes it. */
    private static void writeFormatMarker(Path marker) throws IOException {
        NBTTagCompound data = new NBTTagCompound();
        data.setString("format", StorageFormatFactory.DEFAULT.toString());
        NBTTagCompound root = new NBTTagCompound();
        root.setTag("data", data);
        Files.createDirectories(marker.getParent());
        try (OutputStream out = Files.newOutputStream(marker)) {
            CompressedStreamTools.writeCompressed(root, out);
        }
    }
}
