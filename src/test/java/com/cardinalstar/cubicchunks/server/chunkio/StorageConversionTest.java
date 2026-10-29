package com.cardinalstar.cubicchunks.server.chunkio;

import static com.cardinalstar.cubicchunks.server.chunkio.StorageTestData.*;
import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.ChunkCoordIntPair;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.cardinalstar.cubicchunks.CubicChunksConfig;
import com.cardinalstar.cubicchunks.api.world.storage.ICubicStorage.NBTBatch;
import com.cardinalstar.cubicchunks.api.world.storage.ICubicStorage.PosBatch;
import com.cardinalstar.cubicchunks.util.CubePos;

/**
 * Converting a save's per-cube and per-column records to bundle storage at once ({@code convertStorageOnWorldLoad}).
 */
class StorageConversionTest {

    @TempDir
    Path save;
    private CCNBTUtils.TagCompression compression;

    @BeforeAll
    static void locks(@TempDir Path locks) {
        System.setProperty("cubicchunks.storageLockDirectory", locks.toString());
    }

    @BeforeEach
    void setUp() {
        compression = CubicChunksConfig.chunkCompression;
        CubicChunksConfig.chunkCompression = CCNBTUtils.TagCompression.ZSTD;
    }

    @AfterEach
    void tearDown() {
        CubicChunksConfig.chunkCompression = compression;
    }

    private final Map<CubePos, NBTTagCompound> cubes = new HashMap<>();
    private final Map<ChunkCoordIntPair, NBTTagCompound> columns = new HashMap<>();
    private final List<CubePos> empty = new ArrayList<>();

    /** Per-cube and per-column records as an older build writes them, in regions on both sides of 0. */
    private void writeLegacy() throws Exception {
        long seed = 1;
        // (0, 0, 0) and (2, 0, 0) share a region, so they are converted in the same batch
        for (CubePos origin : new CubePos[] { new CubePos(0, 0, 0), new CubePos(2, 0, 0), new CubePos(-4, 2, 6),
            new CubePos(14, -2, -18) }) {
            cubes.putAll(bundle(origin, seed++, 0b10110111));
            CubePos sky = CubeBundleCodec.member(origin, 3);
            cubes.put(sky, StorageTestData.empty(sky));
            empty.add(sky);
        }
        for (ChunkCoordIntPair origin : new ChunkCoordIntPair[] { new ChunkCoordIntPair(0, 0),
            new ChunkCoordIntPair(-4, 6), new ChunkCoordIntPair(30, -34) }) {
            columns.putAll(columnGroup(origin, seed++, 0b1111));
        }
        try (RegionCubeStorage plain = new RegionCubeStorage(save)) {
            plain.writeBatch(new NBTBatch(new HashMap<>(columns), new HashMap<>(cubes)));
        }
    }

    private int regionFiles() throws Exception {
        int count = 0;
        for (String directory : new String[] { "region2d", "region3d" }) {
            try (java.util.stream.Stream<Path> files = Files.list(save.resolve(directory))) {
                count += (int) files.filter(
                    f -> f.getFileName()
                        .toString()
                        .matches("-?\\d+(\\.-?\\d+){1,2}\\.[23]dr"))
                    .count();
            }
        }
        return count;
    }

    /** Every full cube shares its bundle's record, empty cubes left the region files, columns share their group's. */
    private void assertConverted() throws Exception {
        Map<CubePos, Set<Integer>> bundles = new HashMap<>();
        for (CubePos pos : cubes.keySet()) {
            if (empty.contains(pos))
                assertEquals(0, header(save, pos), "empty cube " + pos + " is in its compact table");
            else bundles.computeIfAbsent(CubeBundleCodec.origin(pos), k -> new HashSet<>())
                .add(header(save, pos));
        }
        for (Set<Integer> records : bundles.values()) assertEquals(1, records.size());
        Map<ChunkCoordIntPair, Set<Integer>> groups = new HashMap<>();
        for (ChunkCoordIntPair pos : columns.keySet())
            groups.computeIfAbsent(ColumnGroupCodec.origin(pos), k -> new HashSet<>())
                .add(header(save, pos));
        for (Set<Integer> records : groups.values()) assertEquals(1, records.size());
        try (CompactCubeStorage storage = new CompactCubeStorage(save, true)) {
            NBTBatch read = storage.readBatch(new PosBatch(columns.keySet(), cubes.keySet()));
            assertEquals(cubes, read.cubes);
            assertEquals(columns, read.columns);
        }
    }

    @Test
    void convertsEverythingOnceAndSkipsItAfterwards() throws Exception {
        writeLegacy();
        int regions = regionFiles();
        // a stray copy of a region is not a region of this save
        Files.copy(save.resolve("region3d/0.0.0.3dr"), save.resolve("region3d/0.0.0 - Copy.3dr"));
        StorageConversion.Summary first = StorageConversion.convert(save, (done, total) -> {});
        assertEquals(regions, first.regions, "only the real region files are converted");
        assertEquals(cubes.size(), first.cubes);
        assertEquals(columns.size(), first.columns);
        assertEquals(0, first.failed);
        assertConverted();
        StorageConversion.Summary second = StorageConversion.convert(save, (done, total) -> {});
        assertEquals(0, second.cubes + second.columns, "converted data is skipped");
        assertConverted();
    }

    /**
     * A length beyond the record's sectors makes the read leave the member out; a negative one makes the whole batch
     * read throw, so the conversion retries group by group.
     */
    @ParameterizedTest
    @ValueSource(ints = { 0x7fffffff, 0xffffffff })
    void aDamagedGroupIsLeftAsItIsAndTheRestConverts(int length) throws Exception {
        writeLegacy();
        CubePos damaged = CubeBundleCodec.member(new CubePos(0, 0, 0), 1);
        int record = header(save, damaged);
        damageLength(cubeRegion(save, damaged), record, length);
        Map<CubePos, Integer> before = new HashMap<>();
        for (CubePos pos : cubes.keySet()) if (CubeBundleCodec.origin(pos)
            .equals(new CubePos(0, 0, 0))) before.put(pos, header(save, pos));
        for (int round = 0; round < 2; round++) {
            StorageConversion.Summary result = StorageConversion.convert(save, (done, total) -> {});
            assertEquals(before.size(), result.failed, "round " + round + ": the damaged bundle is reported");
            for (Map.Entry<CubePos, Integer> e : before.entrySet()) assertEquals(
                e.getValue(),
                header(save, e.getKey()),
                "round " + round + ": " + e.getKey() + " is left as it is");
        }
        // the bundle at (2, 0, 0), in the same region and batch, is converted
        cubes.keySet()
            .removeAll(before.keySet());
        empty.removeAll(before.keySet());
        assertConverted();
    }
}
