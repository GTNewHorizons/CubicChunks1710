package com.cardinalstar.cubicchunks.server.chunkio;

import static com.cardinalstar.cubicchunks.server.chunkio.StorageTestData.*;
import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.ChunkCoordIntPair;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.cardinalstar.cubicchunks.CubicChunksConfig;
import com.cardinalstar.cubicchunks.api.world.storage.ICubicStorage.NBTBatch;
import com.cardinalstar.cubicchunks.api.world.storage.ICubicStorage.PosBatch;
import com.cardinalstar.cubicchunks.util.CubePos;

/** Bundle storage: cubes in 2x2x2 bundle records, columns in 2x2 group records, and what happens to damaged data. */
class BundleStorageTest {

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

    private static void write(CompactCubeStorage storage, Map<ChunkCoordIntPair, NBTTagCompound> columns,
        Map<CubePos, NBTTagCompound> cubes) throws IOException {
        storage.writeBatch(new NBTBatch(new HashMap<>(columns), new HashMap<>(cubes)));
    }

    private static void assertStored(CompactCubeStorage storage, Map<ChunkCoordIntPair, NBTTagCompound> columns,
        Map<CubePos, NBTTagCompound> cubes) throws IOException {
        for (Map.Entry<CubePos, NBTTagCompound> e : cubes.entrySet()) {
            assertTrue(storage.cubeExists(e.getKey()), "exists " + e.getKey());
            assertEquals(e.getValue(), storage.readCube(e.getKey()), "read " + e.getKey());
        }
        for (Map.Entry<ChunkCoordIntPair, NBTTagCompound> e : columns.entrySet())
            assertEquals(e.getValue(), storage.readColumn(e.getKey()), "read " + e.getKey());
        NBTBatch batch = storage.readBatch(new PosBatch(columns.keySet(), cubes.keySet()));
        assertEquals(cubes, batch.cubes);
        assertEquals(columns, batch.columns);
    }

    /** The members in {@code mask} share one record; the others have no entry. */
    private void assertOneRecord(CubePos origin, int mask) throws Exception {
        Set<Integer> records = new HashSet<>();
        for (int s = 0; s < 8; s++) {
            int packed = header(save, CubeBundleCodec.member(origin, s));
            if ((mask & 1 << s) == 0) assertEquals(0, packed, "member " + s + " has no record");
            else records.add(packed);
        }
        assertEquals(1, records.size());
        assertFalse(records.contains(0));
    }

    private void assertOneRecord(ChunkCoordIntPair origin, int mask) throws Exception {
        Set<Integer> records = new HashSet<>();
        for (int s = 0; s < 4; s++)
            if ((mask & 1 << s) != 0) records.add(header(save, ColumnGroupCodec.member(origin, s)));
        assertEquals(1, records.size());
        assertFalse(records.contains(0));
    }

    @Test
    void cubesAndColumnsShareGroupRecordsAndReadOneByOne() throws Exception {
        CubePos a = new CubePos(4, -2, 6), b = new CubePos(-8, 30, 2);
        ChunkCoordIntPair columns = new ChunkCoordIntPair(-2, 6);
        Map<CubePos, NBTTagCompound> cubes = new HashMap<>(bundle(a, 1, 0xff));
        cubes.putAll(bundle(b, 2, 0b10010001));
        Map<ChunkCoordIntPair, NBTTagCompound> cols = columnGroup(columns, 3, 0b1111);
        try (CompactCubeStorage storage = new CompactCubeStorage(save, true)) {
            write(storage, cols, cubes);
            assertStored(storage, cols, cubes);
            assertFalse(storage.cubeExists(CubeBundleCodec.member(b, 1)), "absent members stay absent");
        }
        assertOneRecord(a, 0xff);
        assertOneRecord(b, 0b10010001);
        assertOneRecord(columns, 0b1111);
        try (CompactCubeStorage storage = new CompactCubeStorage(save, true)) {
            assertStored(storage, cols, cubes);
        }
    }

    @Test
    void perRecordDataIsConvertedWhenItsGroupIsSaved() throws Exception {
        CubePos origin = new CubePos(2, 2, 2);
        ChunkCoordIntPair columns = new ChunkCoordIntPair(4, 4);
        Map<CubePos, NBTTagCompound> cubes = new HashMap<>(bundle(origin, 4, 0xff));
        Map<ChunkCoordIntPair, NBTTagCompound> cols = new HashMap<>(columnGroup(columns, 5, 0b1111));
        try (CompactCubeStorage legacy = new CompactCubeStorage(save)) {
            write(legacy, cols, cubes);
        }
        try (CompactCubeStorage storage = new CompactCubeStorage(save, true)) {
            assertStored(storage, cols, cubes); // per-cube and per-column records are read as before
            CubePos cube = CubeBundleCodec.member(origin, 5);
            ChunkCoordIntPair column = ColumnGroupCodec.member(columns, 2);
            cubes.put(cube, StorageTestData.cube(cube, 50));
            cols.put(column, StorageTestData.column(column, 51));
            write(
                storage,
                Collections.singletonMap(column, cols.get(column)),
                Collections.singletonMap(cube, cubes.get(cube)));
            assertStored(storage, cols, cubes);
        }
        assertOneRecord(origin, 0xff);
        assertOneRecord(columns, 0b1111);
    }

    @Test
    void cubesMoveBetweenBundlesAndTheCompactTables() throws Exception {
        CubePos origin = new CubePos(-2, 4, 8), sky = CubeBundleCodec.member(origin, 6);
        Map<CubePos, NBTTagCompound> cubes = new HashMap<>(bundle(origin, 7, 0xff));
        try (CompactCubeStorage storage = new CompactCubeStorage(save, true)) {
            write(storage, Collections.emptyMap(), cubes);
            cubes.put(sky, empty(sky));
            write(storage, Collections.emptyMap(), Collections.singletonMap(sky, cubes.get(sky)));
            assertStored(storage, Collections.emptyMap(), cubes);
            assertOneRecord(origin, 0xff & ~(1 << 6));
            cubes.put(sky, cube(sky, 70));
            write(storage, Collections.emptyMap(), Collections.singletonMap(sky, cubes.get(sky)));
            assertStored(storage, Collections.emptyMap(), cubes);
        }
        assertOneRecord(origin, 0xff);
    }

    @Test
    void aMemberThatCannotBeReadIsNeverDeleted() throws Exception {
        // a length beyond the record's sectors fails the read; a negative one fails it with an unchecked exception
        for (int length : new int[] { 0x7fffffff, 0xffffffff }) {
            CubePos origin = new CubePos(length > 0 ? 0 : 8, 0, 0);
            ChunkCoordIntPair columns = new ChunkCoordIntPair(length > 0 ? 0 : 8, 0);
            CubePos damagedCube = CubeBundleCodec.member(origin, 2), savedCube = CubeBundleCodec.member(origin, 3);
            ChunkCoordIntPair damagedColumn = ColumnGroupCodec.member(columns, 2),
                savedColumn = ColumnGroupCodec.member(columns, 3);
            Map<CubePos, NBTTagCompound> cubes = new HashMap<>(bundle(origin, 8, 0b11));
            Map<ChunkCoordIntPair, NBTTagCompound> cols = new HashMap<>(columnGroup(columns, 9, 0b11));
            try (CompactCubeStorage storage = new CompactCubeStorage(save, true)) {
                write(storage, cols, cubes);
            }
            // the damaged members have their own records next to the shared ones
            try (RegionCubeStorage plain = new RegionCubeStorage(save, true, false)) {
                plain.writeCube(damagedCube, cube(damagedCube, 90));
                plain.writeColumn(damagedColumn, column(damagedColumn, 91));
            }
            int cubeRecord = header(save, damagedCube), columnRecord = header(save, damagedColumn);
            damageLength(cubeRegion(save, damagedCube), cubeRecord, length);
            damageLength(columnRegion(save, damagedColumn), columnRecord, length);
            cubes.put(savedCube, cube(savedCube, 92));
            cols.put(savedColumn, column(savedColumn, 93));
            try (CompactCubeStorage storage = new CompactCubeStorage(save, true)) {
                write(
                    storage,
                    Collections.singletonMap(savedColumn, cols.get(savedColumn)),
                    Collections.singletonMap(savedCube, cubes.get(savedCube)));
                assertStored(storage, cols, cubes);
                assertTrue(storage.cubeExists(damagedCube));
                assertTrue(storage.columnExists(damagedColumn));
            }
            assertEquals(cubeRecord, header(save, damagedCube), "the damaged cube keeps its record");
            assertEquals(columnRecord, header(save, damagedColumn), "the damaged column keeps its record");
        }
    }

    @Test
    void aCubeBecomingFullKeepsItsCompactEntryWhenItsWriteFails() throws Exception {
        CubePos pos = new CubePos(30, 30, 30), other = new CubePos(40, 30, 30);
        NBTTagCompound emptyCube = empty(pos), otherCube = cube(other, 100);
        try (CompactCubeStorage storage = new CompactCubeStorage(save, true)) {
            write(storage, Collections.emptyMap(), Collections.singletonMap(pos, emptyCube));
            // the cube gets blocks, but its NBT cannot be written (a string over the 64 KiB modified UTF-8 limit)
            NBTTagCompound full = cube(pos, 101);
            char[] big = new char[70_000];
            Arrays.fill(big, 'x');
            full.setString("mod:big", new String(big));
            Map<CubePos, NBTTagCompound> batch = new HashMap<>();
            batch.put(pos, full);
            batch.put(other, otherCube);
            assertThrows(IOException.class, () -> write(storage, Collections.emptyMap(), batch));
            assertEquals(emptyCube, storage.readCube(pos), "its last stored state is kept");
            assertEquals(otherCube, storage.readCube(other), "the rest of the batch is written");
        }
    }

    @Test
    void tooLargeGroupsKeepTheirMembersInTheirOwnRecords() throws Exception {
        CubePos origin = new CubePos(0, 8, 0);
        Map<CubePos, NBTTagCompound> cubes = new HashMap<>(bundle(origin, 11, 0b11));
        try (CompactCubeStorage storage = new CompactCubeStorage(save, true)) {
            write(storage, Collections.emptyMap(), cubes);
            int shared = header(save, origin);
            // two cubes with 80 KB of incompressible data each do not fit one record together
            Map<CubePos, NBTTagCompound> large = new HashMap<>();
            for (int s : new int[] { 0, 5 }) {
                CubePos pos = CubeBundleCodec.member(origin, s);
                large.put(pos, StorageTestData.large(pos, 120 + s, 80_000));
            }
            write(storage, Collections.emptyMap(), large);
            cubes.putAll(large);
            assertStored(storage, Collections.emptyMap(), cubes);
            assertEquals(shared, header(save, CubeBundleCodec.member(origin, 1)), "the other member stays where it is");
            assertNotEquals(header(save, origin), header(save, CubeBundleCodec.member(origin, 5)));
        }
    }

    @Test
    void tooLargeColumnGroupsKeepTheirColumnsInTheirOwnRecords() throws Exception {
        ChunkCoordIntPair origin = new ChunkCoordIntPair(6, 6);
        Map<ChunkCoordIntPair, NBTTagCompound> cols = new HashMap<>(columnGroup(origin, 15, 0b11));
        try (CompactCubeStorage storage = new CompactCubeStorage(save, true)) {
            write(storage, cols, Collections.emptyMap());
            int shared = header(save, origin);
            Map<ChunkCoordIntPair, NBTTagCompound> large = new HashMap<>();
            for (int s : new int[] { 0, 3 }) {
                ChunkCoordIntPair pos = ColumnGroupCodec.member(origin, s);
                NBTTagCompound column = column(pos, 130 + s);
                byte[] noise = new byte[80_000];
                new java.util.Random(s).nextBytes(noise);
                column.setByteArray("mod:payload", noise);
                large.put(pos, column);
            }
            write(storage, large, Collections.emptyMap());
            cols.putAll(large);
            assertStored(storage, cols, Collections.emptyMap());
            assertEquals(
                shared,
                header(save, ColumnGroupCodec.member(origin, 1)),
                "the other column stays where it is");
            assertNotEquals(header(save, origin), header(save, ColumnGroupCodec.member(origin, 3)));
        }
    }

    @Test
    void compactionKeepsSharedRecordsShared() throws Exception {
        CubePos a = new CubePos(0, 0, 0), b = new CubePos(4, 0, 0);
        ChunkCoordIntPair columns = new ChunkCoordIntPair(0, 0);
        Map<CubePos, NBTTagCompound> cubes = new HashMap<>(bundle(a, 12, 0xff));
        cubes.putAll(bundle(b, 13, 0b1110001));
        Map<ChunkCoordIntPair, NBTTagCompound> cols = new HashMap<>(columnGroup(columns, 14, 0b1111));
        try (CompactCubeStorage storage = new CompactCubeStorage(save, true)) {
            write(storage, cols, cubes);
            // rewriting members leaves free sectors between records
            for (int i = 0; i < 12; i++) {
                CubePos pos = CubeBundleCodec.member(a, i % 8);
                ChunkCoordIntPair column = ColumnGroupCodec.member(columns, i % 4);
                cubes.put(pos, cube(pos, 200 + i));
                cols.put(column, column(column, 300 + i));
                write(
                    storage,
                    Collections.singletonMap(column, cols.get(column)),
                    Collections.singletonMap(pos, cubes.get(pos)));
            }
        }
        Path region = cubeRegion(save, a);
        long before = Files.size(region);
        RegionCompactor.run(
            new String[] { save.toString(), "--apply", "--world-stopped" },
            new java.io.PrintStream(new java.io.ByteArrayOutputStream()));
        assertTrue(Files.size(region) < before, "compacted");
        assertOneRecord(a, 0xff);
        assertOneRecord(b, 0b1110001);
        assertOneRecord(columns, 0b1111);
        try (CompactCubeStorage storage = new CompactCubeStorage(save, true)) {
            assertStored(storage, cols, cubes);
        }
    }
}
