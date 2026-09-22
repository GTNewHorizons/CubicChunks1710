package com.cardinalstar.cubicchunks.world.convert;

import java.io.DataInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;

import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.world.ChunkCoordIntPair;
import net.minecraft.world.chunk.storage.RegionFile;

import com.cardinalstar.cubicchunks.api.ICube;
import com.cardinalstar.cubicchunks.api.world.storage.ICubicStorage;
import com.cardinalstar.cubicchunks.api.world.storage.ICubicStorage.NBTBatch;
import com.cardinalstar.cubicchunks.api.world.storage.StorageFormatFactory;
import com.cardinalstar.cubicchunks.server.chunkio.RegionCubeStorage;
import com.cardinalstar.cubicchunks.util.CubePos;
import com.cardinalstar.cubicchunks.world.convert.adapter.CubeData;
import com.cardinalstar.cubicchunks.world.convert.adapter.SectionAdapter;
import com.cardinalstar.cubicchunks.world.convert.adapter.SectionAdapterDiscovery;
import com.cardinalstar.cubicchunks.world.convert.adapter.biome.BiomeAdapter;
import com.cardinalstar.cubicchunks.world.convert.adapter.biome.BiomeAdapterDiscovery;
import com.cardinalstar.cubicchunks.world.cube.Cube;

/**
 * Converts a vanilla Anvil world ({@code region/*.mca}) into CubicChunks format
 * ({@code region2d/}, {@code region3d/}, {@code data/cubicchunks.world_format.dat}).
 *
 * <p>Runs offline — no world or server instance is required.
 * The existing vanilla data is left in place; only CC directories are added.
 */
@ParametersAreNonnullByDefault
public final class VanillaToCCConverter implements IWorldConverter {

    /** Vanilla chunk Level keys that are explicitly handled; all others are preserved as-is. */
    private static final Set<String> VANILLA_CHUNK_LEVEL_KEYS = new HashSet<>(Arrays.asList(
        "V", "xPos", "zPos", "LastUpdate", "TerrainPopulated", "LightPopulated",
        "InhabitedTime", "Biomes", "HeightMap", "Sections", "Entities", "TileEntities", "TileTicks"
    ));

    private final SectionAdapter sectionWriteAdapter;
    private final BiomeAdapter biomeWriteAdapter;

    public VanillaToCCConverter(SectionAdapter sectionWriteAdapter, BiomeAdapter biomeWriteAdapter) {
        this.sectionWriteAdapter = sectionWriteAdapter;
        this.biomeWriteAdapter = biomeWriteAdapter;
    }

    @Override
    public void convert(File dimensionRoot, boolean isOverworld, ConversionProgress progress, AtomicBoolean cancelSignal) throws IOException {
        File regionDir = new File(dimensionRoot, "region");
        File[] mcaFiles = regionDir.listFiles((d, n) -> n.endsWith(".mca"));

        if (mcaFiles == null || mcaFiles.length == 0) return;

        int total = countChunks(mcaFiles);
        progress.update(0, total, "Starting...");

        Path worldPath = dimensionRoot.toPath();
        try (ICubicStorage storage = new RegionCubeStorage(worldPath)) {
            int done = 0;

            for (File mcaFile : mcaFiles) {
                int[] regionCoords = parseRegionCoords(mcaFile.getName());
                if (regionCoords == null) continue;

                int regionX = regionCoords[0];
                int regionZ = regionCoords[1];

                Map<ChunkCoordIntPair, NBTTagCompound> columns = new LinkedHashMap<>();
                Map<CubePos, NBTTagCompound> cubes = new LinkedHashMap<>();

                RegionFile regionFile = new RegionFile(mcaFile);
                try {
                    for (int localX = 0; localX < 32; localX++) {
                        for (int localZ = 0; localZ < 32; localZ++) {
                            if (!regionFile.isChunkSaved(localX, localZ)) continue;

                            int chunkX = regionX * 32 + localX;
                            int chunkZ = regionZ * 32 + localZ;

                            convertChunk(regionFile, localX, localZ, chunkX, chunkZ, columns, cubes);
                            done++;

                            progress.update(done, total, chunkX + "," + chunkZ);
                            if (cancelSignal.get()) {
                                storage.writeBatch(new NBTBatch(columns, cubes));
                                storage.flush();
                                return;
                            }
                        }
                    }
                } finally {
                    regionFile.close();
                }

                storage.writeBatch(new NBTBatch(columns, cubes));
            }

            storage.flush();
        }

        if (isOverworld) {
            writeFormatMarker(dimensionRoot);
        }

        for (var mca : mcaFiles) {
            Files.delete(mca.toPath());
        }

        if (isOverworld) {
            Path worldFormat = dimensionRoot.toPath().resolve("data").resolve("cubicchunks.world_format.dat");

            NBTTagCompound saveData = new NBTTagCompound();
            saveData.setString("format", StorageFormatFactory.DEFAULT.toString());

            NBTTagCompound container = new NBTTagCompound();
            container.setTag("data", saveData);

            try (FileOutputStream fileoutputstream = new FileOutputStream(worldFormat.toFile())) {
                CompressedStreamTools.writeCompressed(container, fileoutputstream);
            }
        }

        if (regionDir.list().length == 0) {
            Files.delete(regionDir.toPath());
        }

        progress.update(progress.getCompleted(), progress.getTotal(), "Done.");
    }

    private void convertChunk(RegionFile regionFile, int localX, int localZ,
                               int chunkX, int chunkZ,
                               Map<ChunkCoordIntPair, NBTTagCompound> columns,
                               Map<CubePos, NBTTagCompound> cubes) throws IOException {
        DataInputStream in = regionFile.getChunkDataInputStream(localX, localZ);
        if (in == null) return;

        NBTTagCompound root;
        try {
            root = CompressedStreamTools.read(in);
        } finally {
            in.close();
        }

        NBTTagCompound level = root.getCompoundTag("Level");

        columns.put(new ChunkCoordIntPair(chunkX, chunkZ), buildColumnNbt(level, chunkX, chunkZ));

        boolean[] present = new boolean[16];

        NBTTagList vanillaSections = level.getTagList("Sections", 10);
        for (int i = 0; i < vanillaSections.tagCount(); i++) {
            NBTTagCompound section = vanillaSections.getCompoundTagAt(i);
            int sectionY = section.getByte("Y") & 0xFF;

            present[sectionY] = true;

            cubes.put(
                new CubePos(chunkX, sectionY, chunkZ),
                buildCubeNbt(level, section, chunkX, sectionY, chunkZ));
        }

        for (int sectionY = 0; sectionY < 16; sectionY++) {
            if (present[sectionY]) continue;

            cubes.put(
                new CubePos(chunkX, sectionY, chunkZ),
                buildCubeNbt(level, null, chunkX, sectionY, chunkZ));
        }
    }

    private NBTTagCompound buildColumnNbt(NBTTagCompound vanillaLevel, int chunkX, int chunkZ) {
        NBTTagCompound level = new NBTTagCompound();
        level.setByte("v", (byte) 2);
        level.setInteger("x", chunkX);
        level.setInteger("z", chunkZ);
        level.setLong("InhabitedTime", vanillaLevel.getLong("InhabitedTime"));
        // Don't bother copying the height map: it will be recalculated automatically

        int[] biomes = new int[256];

        BiomeAdapterDiscovery.detect(vanillaLevel).readBiomeData(vanillaLevel, biomes);
        biomeWriteAdapter.writeBiomeData(biomes, level);

        copyUnknownTags(vanillaLevel, level, VANILLA_CHUNK_LEVEL_KEYS);

        NBTTagCompound root = new NBTTagCompound();
        root.setTag("Level", level);
        return root;
    }

    private NBTTagCompound buildCubeNbt(NBTTagCompound vanillaLevel, @Nullable NBTTagCompound vanillaSection,
                                         int chunkX, int sectionY, int chunkZ) {
        NBTTagCompound level = new NBTTagCompound();
        level.setByte("v", (byte) 1);
        level.setInteger("x", chunkX);
        level.setInteger("y", sectionY);
        level.setInteger("z", chunkZ);

        level.setShort("population", Cube.POP_ALL);
        level.setBoolean("isSurfaceTracked", false);
        level.setBoolean("initLightDone", false);

        if (vanillaSection != null) {
            CubeData cubeData = new CubeData();
            SectionAdapterDiscovery.detect(vanillaSection).readSectionData(vanillaSection, cubeData);

            NBTTagCompound sectionNbt = new NBTTagCompound();
            copyUnknownTags(vanillaSection, sectionNbt, SectionAdapterDiscovery.SECTION_MANAGED_KEYS);
            sectionWriteAdapter.writeSectionData(cubeData, sectionNbt);

            NBTTagList sections = new NBTTagList();
            sections.appendTag(sectionNbt);
            level.setTag("Sections", sections);
        }

        level.setTag("Entities", distributeEntities(vanillaLevel, sectionY));
        level.setTag("TileEntities", distributeTileEntities(vanillaLevel, sectionY));
        level.setTag("TileTicks", distributeTileTicks(vanillaLevel, sectionY));

        NBTTagCompound root = new NBTTagCompound();
        root.setTag("Level", level);
        return root;
    }

    private static void copyUnknownTags(NBTTagCompound src, NBTTagCompound dst, Set<String> exclude) {
        for (String key : src.func_150296_c()) {
            if (!exclude.contains(key)) {
                dst.setTag(key, src.getTag(key).copy());
            }
        }
    }

    private NBTTagList distributeEntities(NBTTagCompound level, int targetSectionY) {
        NBTTagList result = new NBTTagList();
        NBTTagList all = level.getTagList("Entities", 10);

        for (int i = 0; i < all.tagCount(); i++) {
            NBTTagCompound entity = all.getCompoundTagAt(i);
            if (!entity.hasKey("Pos")) continue;

            NBTTagList pos = entity.getTagList("Pos", 6);
            if (pos.tagCount() < 3) continue;

            int entitySectionY = (int) Math.floor(pos.func_150309_d(1) / ICube.SIZE);
            if (entitySectionY == targetSectionY) {
                result.appendTag(entity.copy());
            }
        }

        return result;
    }

    private NBTTagList distributeTileEntities(NBTTagCompound level, int targetSectionY) {
        NBTTagList result = new NBTTagList();
        NBTTagList all = level.getTagList("TileEntities", 10);

        for (int i = 0; i < all.tagCount(); i++) {
            NBTTagCompound te = all.getCompoundTagAt(i);
            if (te.getInteger("y") >> 4 == targetSectionY) {
                result.appendTag(te.copy());
            }
        }

        return result;
    }

    private NBTTagList distributeTileTicks(NBTTagCompound level, int targetSectionY) {
        NBTTagList result = new NBTTagList();
        NBTTagList all = level.getTagList("TileTicks", 10);

        for (int i = 0; i < all.tagCount(); i++) {
            NBTTagCompound tick = all.getCompoundTagAt(i);
            if (tick.getInteger("y") >> 4 == targetSectionY) {
                result.appendTag(tick.copy());
            }
        }

        return result;
    }

    /** Counts total saved chunks across all region files for progress tracking. */
    private int countChunks(File[] mcaFiles) {
        int count = 0;
        for (File mcaFile : mcaFiles) {
            if (parseRegionCoords(mcaFile.getName()) == null) continue;
            try {
                RegionFile rf = new RegionFile(mcaFile);
                try {
                    for (int lx = 0; lx < 32; lx++) {
                        for (int lz = 0; lz < 32; lz++) {
                            if (rf.isChunkSaved(lx, lz)) count++;
                        }
                    }
                } finally {
                    rf.close();
                }
            } catch (IOException ignored) {}
        }
        return count;
    }

    /**
     * Parses region coordinates from a filename like {@code r.-1.2.mca}.
     *
     * @return int[]{regionX, regionZ} or {@code null} if the name doesn't match
     */
    private static int[] parseRegionCoords(String name) {
        if (!name.startsWith("r.") || !name.endsWith(".mca")) return null;
        String[] parts = name.substring(2, name.length() - 4).split("\\.");
        if (parts.length != 2) return null;
        try {
            return new int[] { Integer.parseInt(parts[0]), Integer.parseInt(parts[1]) };
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * Writes {@code data/cubicchunks.world_format.dat} so the CC loader recognises
     * this world on next load. Mirrors the format that {@link net.minecraft.world.storage.MapStorage} uses.
     */
    private void writeFormatMarker(File dimensionRoot) throws IOException {
        File dataDir = new File(dimensionRoot, "data");
        if (!dataDir.exists()) dataDir.mkdirs();

        NBTTagCompound data = new NBTTagCompound();
        data.setString("format", "cubicchunks:anvil3d");

        NBTTagCompound root = new NBTTagCompound();
        root.setTag("data", data);

        File markerFile = new File(dataDir, "cubicchunks.world_format.dat");
        try (FileOutputStream fos = new FileOutputStream(markerFile)) {
            CompressedStreamTools.writeCompressed(root, fos);
        }
    }
}
