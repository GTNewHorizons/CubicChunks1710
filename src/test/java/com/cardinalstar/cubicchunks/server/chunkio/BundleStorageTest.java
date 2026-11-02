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

/**
 * Bundle storage: cubes in 2x2x2 bundle records, columns in 2x2 group records, empty cubes as states in their columns,
 * and what happens to damaged data.
 */
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

    private static void write(RegionCubeStorage storage, Map<ChunkCoordIntPair, NBTTagCompound> columns,
        Map<CubePos, NBTTagCompound> cubes) throws IOException {
        storage.writeBatch(new NBTBatch(new HashMap<>(columns), new HashMap<>(cubes)));
    }

    private static void assertStored(RegionCubeStorage storage, Map<ChunkCoordIntPair, NBTTagCompound> columns,
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
        try (RegionCubeStorage storage = new RegionCubeStorage(save, true)) {
            write(storage, cols, cubes);
            assertStored(storage, cols, cubes);
            assertFalse(storage.cubeExists(CubeBundleCodec.member(b, 1)), "absent members stay absent");
        }
        assertOneRecord(a, 0xff);
        assertOneRecord(b, 0b10010001);
        assertOneRecord(columns, 0b1111);
        try (RegionCubeStorage storage = new RegionCubeStorage(save, true)) {
            assertStored(storage, cols, cubes);
        }
    }

    @Test
    void perRecordDataIsConvertedWhenItsGroupIsSaved() throws Exception {
        CubePos origin = new CubePos(2, 2, 2);
        ChunkCoordIntPair columns = new ChunkCoordIntPair(4, 4);
        Map<CubePos, NBTTagCompound> cubes = new HashMap<>(bundle(origin, 4, 0xff));
        Map<ChunkCoordIntPair, NBTTagCompound> cols = new HashMap<>(columnGroup(columns, 5, 0b1111));
        try (RegionCubeStorage legacy = new RegionCubeStorage(save)) {
            write(legacy, cols, cubes);
        }
        try (RegionCubeStorage storage = new RegionCubeStorage(save, true)) {
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
    void emptyCubesAreStatesInTheirColumns() throws Exception {
        CubePos origin = new CubePos(6, -4, 2);
        ChunkCoordIntPair group = new ChunkCoordIntPair(6, 2);
        Map<CubePos, NBTTagCompound> cubes = new HashMap<>(bundle(origin, 16, 0b00001111));
        for (int s = 4; s < 8; s++) {
            CubePos pos = CubeBundleCodec.member(origin, s);
            cubes.put(pos, empty(pos));
        }
        Map<ChunkCoordIntPair, NBTTagCompound> cols = columnGroup(group, 17, 0b1111);
        try (RegionCubeStorage storage = new RegionCubeStorage(save, true)) {
            write(storage, cols, cubes);
            assertStored(storage, cols, cubes);
            Set<CubePos> listed = new HashSet<>();
            storage.forEachCube(listed::add);
            assertEquals(cubes.keySet(), listed, "every cube is listed once");
        }
        assertOneRecord(origin, 0b00001111);
        int states = 0;
        for (int s = 0; s < 4; s++) states += stateCount(storedStates(save, ColumnGroupCodec.member(group, s)));
        assertEquals(4, states, "the empty cubes are states in their columns");
        try (RegionCubeStorage storage = new RegionCubeStorage(save, true)) {
            assertStored(storage, cols, cubes);
        }
    }

    private static int stateCount(CubeStates.Column states) {
        int[] count = new int[1];
        states.forEach(y -> count[0]++);
        return count[0];
    }

    @Test
    void cubesMoveBetweenBundlesAndColumnStates() throws Exception {
        CubePos origin = new CubePos(-2, 4, 8), sky = CubeBundleCodec.member(origin, 6);
        ChunkCoordIntPair column = sky.chunkPos();
        Map<CubePos, NBTTagCompound> cubes = new HashMap<>(bundle(origin, 7, 0xff));
        Map<ChunkCoordIntPair, NBTTagCompound> cols = Collections.singletonMap(column, column(column, 71));
        try (RegionCubeStorage storage = new RegionCubeStorage(save, true)) {
            write(storage, cols, cubes);
            cubes.put(sky, empty(sky));
            write(storage, Collections.emptyMap(), Collections.singletonMap(sky, cubes.get(sky)));
            assertStored(storage, cols, cubes);
            assertOneRecord(origin, 0xff & ~(1 << 6));
            assertTrue(storedStates(save, column).get(sky.getY()) >= 0, "a state once it is empty");
            cubes.put(sky, cube(sky, 70));
            write(storage, Collections.emptyMap(), Collections.singletonMap(sky, cubes.get(sky)));
            assertStored(storage, cols, cubes);
            assertOneRecord(origin, 0xff);
            // the full record wins over the old state, which the next write of the column drops
            write(storage, cols, Collections.emptyMap());
            assertTrue(storedStates(save, column).isEmpty(), "the stale state is dropped");
            assertStored(storage, cols, cubes);
        }
    }

    @Test
    void anEmptyCubeWaitsInAFullRecordUntilItsColumnIsWritten() throws Exception {
        CubePos pos = new CubePos(20, 6, -20);
        ChunkCoordIntPair column = pos.chunkPos();
        Map<CubePos, NBTTagCompound> cubes = Collections.singletonMap(pos, empty(pos));
        Map<ChunkCoordIntPair, NBTTagCompound> cols = Collections.singletonMap(column, column(column, 18));
        try (RegionCubeStorage storage = new RegionCubeStorage(save, true)) {
            write(storage, Collections.emptyMap(), cubes);
            assertNotEquals(0, header(save, pos), "no column record to hold its state yet");
            assertStored(storage, Collections.emptyMap(), cubes);
            write(storage, cols, Collections.emptyMap());
            assertEquals(0, header(save, pos), "a state once its column is written");
            assertStored(storage, cols, cubes);
        }
        try (RegionCubeStorage storage = new RegionCubeStorage(save, true)) {
            assertStored(storage, cols, cubes);
        }
    }

    @Test
    void aWorldWithCompactTablesIsRefused() throws Exception {
        Files.createDirectories(save.resolve("region3d-empty"));
        IOException refused = assertThrows(IOException.class, () -> new RegionCubeStorage(save, true));
        assertTrue(
            refused.getMessage()
                .contains("compact tables"));
        assertThrows(IOException.class, () -> new RegionCubeStorage(save));
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
            try (RegionCubeStorage storage = new RegionCubeStorage(save, true)) {
                write(storage, cols, cubes);
            }
            // the damaged members have their own records next to the shared ones
            try (RegionCubeStorage plain = new RegionCubeStorage(save)) {
                plain.writeCube(damagedCube, cube(damagedCube, 90));
                plain.writeColumn(damagedColumn, column(damagedColumn, 91));
            }
            int cubeRecord = header(save, damagedCube), columnRecord = header(save, damagedColumn);
            damageLength(cubeRegion(save, damagedCube), cubeRecord, length);
            damageLength(columnRegion(save, damagedColumn), columnRecord, length);
            cubes.put(savedCube, cube(savedCube, 92));
            cols.put(savedColumn, column(savedColumn, 93));
            try (RegionCubeStorage storage = new RegionCubeStorage(save, true)) {
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

    /** Empty cubes at {@code ys} of a column, and the column itself, written in one batch. */
    private static Map<CubePos, NBTTagCompound> emptyCubes(RegionCubeStorage storage, ChunkCoordIntPair column,
        long seed, int... ys) throws IOException {
        Map<CubePos, NBTTagCompound> cubes = new HashMap<>();
        for (int y : ys) {
            CubePos pos = new CubePos(column.chunkXPos, y, column.chunkZPos);
            cubes.put(pos, empty(pos));
        }
        write(storage, Collections.singletonMap(column, column(column, seed)), cubes);
        return cubes;
    }

    @Test
    void aColumnSavedNextToADamagedOneKeepsItsStates() throws Exception {
        // a length beyond the record's sectors fails the read; a negative one fails the whole group read unchecked
        for (int length : new int[] { 0x7fffffff, 0xffffffff }) {
            ChunkCoordIntPair group = new ChunkCoordIntPair(length > 0 ? 40 : 48, 0);
            ChunkCoordIntPair saved = ColumnGroupCodec.member(group, 0), damaged = ColumnGroupCodec.member(group, 1);
            Map<CubePos, NBTTagCompound> cubes;
            try (RegionCubeStorage storage = new RegionCubeStorage(save, true)) {
                cubes = emptyCubes(storage, saved, 20, 3, 4);
            }
            try (RegionCubeStorage plain = new RegionCubeStorage(save)) {
                plain.writeColumn(damaged, column(damaged, 21));
            }
            damageLength(columnRegion(save, damaged), header(save, damaged), length);
            try (RegionCubeStorage storage = new RegionCubeStorage(save, true)) {
                write(storage, Collections.singletonMap(saved, column(saved, 22)), Collections.emptyMap());
                assertStored(storage, Collections.singletonMap(saved, column(saved, 22)), cubes);
            }
        }
    }

    @Test
    void aColumnWhoseStatesCannotBeReadIsNotOverwrittenWithoutThem() throws Exception {
        // its record reads but its states tag is invalid: the save keeps the tag as it is
        ChunkCoordIntPair column = new ChunkCoordIntPair(60, 0);
        NBTTagCompound invalid = column(column, 30), tag = new NBTTagCompound();
        tag.setInteger("MinY", 0);
        tag.setByteArray("States", new byte[] { 1 });
        invalid.setTag(CubeStates.TAG, tag);
        try (RegionCubeStorage plain = new RegionCubeStorage(save)) {
            plain.writeColumn(column, invalid);
        }
        // an empty cube of that column saved with it cannot become a state: it gets a full record
        CubePos emptyCube = new CubePos(column.chunkXPos, 3, column.chunkZPos);
        try (RegionCubeStorage storage = new RegionCubeStorage(save, true)) {
            write(
                storage,
                Collections.singletonMap(column, column(column, 31)),
                Collections.singletonMap(emptyCube, empty(emptyCube)));
            assertEquals(column(column, 31), storage.readColumn(column));
            assertEquals(empty(emptyCube), storage.readCube(emptyCube));
        }
        assertEquals(
            tag,
            storedColumn(save, column).getTag(CubeStates.TAG),
            "the invalid states are kept as they were");
        assertNotEquals(0, header(save, emptyCube), "a full record instead of a state");

        // its record cannot be read and its states are not cached: the save is refused, the record left as it is
        ChunkCoordIntPair unreadable = new ChunkCoordIntPair(62, 0);
        try (RegionCubeStorage plain = new RegionCubeStorage(save)) {
            plain.writeColumn(unreadable, column(unreadable, 34));
        }
        int record = header(save, unreadable);
        damageLength(columnRegion(save, unreadable), record, 0x7fffffff);
        try (RegionCubeStorage storage = new RegionCubeStorage(save, true)) {
            assertThrows(
                IOException.class,
                () -> write(
                    storage,
                    Collections.singletonMap(unreadable, column(unreadable, 35)),
                    Collections.emptyMap()));
        }
        assertEquals(record, header(save, unreadable), "its record is left as it is");

        // with its states known (cached when written), a saved column is written although its record cannot be read
        ChunkCoordIntPair known = new ChunkCoordIntPair(64, 0);
        try (RegionCubeStorage storage = new RegionCubeStorage(save, true)) {
            Map<CubePos, NBTTagCompound> cubes = emptyCubes(storage, known, 32, 1, 2);
            damageLength(columnRegion(save, known), header(save, known), 0x7fffffff);
            write(storage, Collections.singletonMap(known, column(known, 33)), Collections.emptyMap());
            assertStored(storage, Collections.singletonMap(known, column(known, 33)), cubes);
        }
    }

    @Test
    void invalidStatesOfOneColumnOnlyGiveItsOwnEmptyCubesFullRecords() throws Exception {
        ChunkCoordIntPair group = new ChunkCoordIntPair(70, 0);
        ChunkCoordIntPair invalidColumn = ColumnGroupCodec.member(group, 0), saved = ColumnGroupCodec.member(group, 1);
        NBTTagCompound invalid = column(invalidColumn, 40), tag = new NBTTagCompound();
        tag.setInteger("MinY", 0);
        tag.setByteArray("States", new byte[] { 2, 0 });
        invalid.setTag(CubeStates.TAG, tag);
        try (RegionCubeStorage plain = new RegionCubeStorage(save)) {
            plain.writeColumn(invalidColumn, invalid);
            plain.writeColumn(saved, column(saved, 41));
        }
        CubePos emptyCube = new CubePos(invalidColumn.chunkXPos, 5, invalidColumn.chunkZPos),
            elsewhere = new CubePos(200, 0, 200);
        Map<CubePos, NBTTagCompound> cubes = new HashMap<>();
        cubes.put(emptyCube, empty(emptyCube));
        cubes.put(elsewhere, cube(elsewhere, 42));
        Map<ChunkCoordIntPair, NBTTagCompound> cols = Collections.singletonMap(saved, column(saved, 43));
        try (RegionCubeStorage storage = new RegionCubeStorage(save, true)) {
            write(storage, cols, cubes);
            assertNotEquals(0, header(save, emptyCube), "a full record instead of a state");
            assertStored(storage, cols, cubes);
        }
    }

    @Test
    void aCubeBecomingFullKeepsItsStateWhenItsWriteFails() throws Exception {
        CubePos pos = new CubePos(30, 30, 30), other = new CubePos(40, 30, 30);
        NBTTagCompound emptyCube = empty(pos), otherCube = cube(other, 100);
        try (RegionCubeStorage storage = new RegionCubeStorage(save, true)) {
            write(
                storage,
                Collections.singletonMap(pos.chunkPos(), column(pos.chunkPos(), 102)),
                Collections.singletonMap(pos, emptyCube));
            assertEquals(0, header(save, pos), "stored as a state");
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
        try (RegionCubeStorage storage = new RegionCubeStorage(save, true)) {
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
        try (RegionCubeStorage storage = new RegionCubeStorage(save, true)) {
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
        try (RegionCubeStorage storage = new RegionCubeStorage(save, true)) {
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
        try (RegionCubeStorage storage = new RegionCubeStorage(save, true)) {
            assertStored(storage, cols, cubes);
        }
    }
}
