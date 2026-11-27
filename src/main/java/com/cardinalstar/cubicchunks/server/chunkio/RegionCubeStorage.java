/*
 * This file is part of Cubic Chunks Mod, licensed under the MIT License (MIT).
 * Copyright (c) 2015-2021 OpenCubicChunks
 * Copyright (c) 2015-2021 contributors
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package com.cardinalstar.cubicchunks.server.chunkio;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Consumer;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.ChunkCoordIntPair;

import org.jetbrains.annotations.NotNull;

import com.cardinalstar.cubicchunks.CubicChunks;
import com.cardinalstar.cubicchunks.CubicChunksConfig;
import com.cardinalstar.cubicchunks.api.world.storage.ICubicStorage;
import com.cardinalstar.cubicchunks.server.chunkio.region.ShadowPagingRegion;
import com.cardinalstar.cubicchunks.util.CubePos;
import com.cardinalstar.cubicchunks.util.DataUtils;

import cubicchunks.regionlib.impl.EntryLocation2D;
import cubicchunks.regionlib.impl.EntryLocation3D;
import cubicchunks.regionlib.impl.SaveCubeColumns;
import cubicchunks.regionlib.impl.save.SaveSection2D;
import cubicchunks.regionlib.impl.save.SaveSection3D;
import cubicchunks.regionlib.lib.ExtRegion;
import cubicchunks.regionlib.lib.factory.SimpleRegionFactory;
import cubicchunks.regionlib.lib.provider.SharedCachedRegionProvider;
import cubicchunks.regionlib.util.Utils;

/**
 * Implementation of {@link ICubicStorage} for bundle storage: cubes in 2x2x2 bundle records ({@link CubeBundleCodec}),
 * columns in 2x2 group records ({@link ColumnGroupCodec}) and
 * empty cubes as states in their column's record ({@link CubeStates}).
 * <p>
 * Writes are serialized; reads do not wait for them. A cube's full record always wins over its state: a cube that
 * becomes empty has its state stored before its full record is removed, and one that gets a full record keeps its old
 * state until a later write of its column drops it. A read therefore finds every cube in one place or the other.
 */
public class RegionCubeStorage implements ICubicStorage, RecordingCubeStorage {

    /** Columns whose states {@link #stateCache} keeps. */
    private static final int STATE_CACHE_COLUMNS = 16384;

    /** Runs startup-only maintenance before opening storage; gameplay dimension loads never compact. */
    public static ICubicStorage openForWorld(Path worldRoot, Path path) throws IOException {
        StartupRegionCompaction.beforeOpen(worldRoot, path);
        return new RegionCubeStorage(path);
    }

    private static SaveCubeColumns saveForPath(Path path) throws IOException {
        // Bundle records are shared by several header entries, which only ShadowPagingRegion supports.
        {
            Utils.createDirectories(path);

            Path part2d = path.resolve("region2d");
            Utils.createDirectories(part2d);

            Path part3d = path.resolve("region3d");
            Utils.createDirectories(part3d);

            @SuppressWarnings("unchecked")
            SaveSection2D section2d = new SaveSection2D(
                new SharedCachedRegionProvider<>(
                    new SimpleRegionFactory<>(
                        new EntryLocation2D.Provider(),
                        part2d,
                        (keyProv, r) -> ShadowPagingRegion.<EntryLocation2D>builder()
                            .setDirectory(part2d)
                            .setRegionKey(r)
                            .setKeyProvider(keyProv)
                            .setSectorSize(512)
                            .build(),
                        (dir, key) -> Files.exists(part2d.resolve(key.getName())))),
                new SharedCachedRegionProvider<>(
                    new SimpleRegionFactory<>(
                        new EntryLocation2D.Provider(),
                        part2d,
                        (keyProvider,
                            regionKey) -> new ExtRegion<>(part2d, Collections.emptyList(), keyProvider, regionKey),
                        (dir, key) -> Files.exists(part2d.resolve(key.getName() + ".ext")))));
            @SuppressWarnings("unchecked")
            SaveSection3D section3d = new SaveSection3D(
                new SharedCachedRegionProvider<>(
                    new SimpleRegionFactory<>(
                        new EntryLocation3D.Provider(),
                        part3d,
                        (keyProv, r) -> ShadowPagingRegion.<EntryLocation3D>builder()
                            .setDirectory(part3d)
                            .setRegionKey(r)
                            .setKeyProvider(keyProv)
                            .setSectorSize(512)
                            .build(),
                        (dir, key) -> Files.exists(part3d.resolve(key.getName())))),
                new SharedCachedRegionProvider<>(
                    new SimpleRegionFactory<>(
                        new EntryLocation3D.Provider(),
                        part3d,
                        (keyProvider,
                            regionKey) -> new ExtRegion<>(part3d, Collections.emptyList(), keyProvider, regionKey),
                        (dir, key) -> Files.exists(part3d.resolve(key.getName() + ".ext")))));

            return new SaveCubeColumns(section2d, section3d);
        }
    }

    SaveCubeColumns save;
    private final Path cubeDirectory;
    private final StorageMaintenanceLock storageLock;
    /** Bundled writes since opening: cubes saved and records (bundle or per-cube) written for them, for the log. */
    private final java.util.concurrent.atomic.AtomicLong cubesWritten = new java.util.concurrent.atomic.AtomicLong(),
        recordsWritten = new java.util.concurrent.atomic.AtomicLong();

    /** Held by writers for their whole write: records are read, changed and written back. Reads do not take it. */
    private final Object writeLock = new Object();
    /** Shared by every operation, exclusive for {@link #close()}. */
    private final ReentrantReadWriteLock openLock = new ReentrantReadWriteLock();
    private boolean closed;
    /**
     * Stored states of empty cubes by column, guarded by itself. A write publishes a column's states after its record
     * is in place; a lookup caches what it read only if no states were published meanwhile, so it cannot replace newer
     * ones.
     */
    private final java.util.LinkedHashMap<ChunkCoordIntPair, CubeStates.Column> stateCache = new java.util.LinkedHashMap<>(
        16,
        0.75f,
        true);
    /** States published by writes so far; changed under the {@link #stateCache} lock. */
    private long statesPublished;
    /** Incremented when a write starts and when it ends: odd while one is in progress. */
    private final java.util.concurrent.atomic.AtomicLong writes = new java.util.concurrent.atomic.AtomicLong();
    /**
     * Empty cubes written as full records because their column had no record yet to hold their state, by column (cube
     * Y to state). They become states when the column is written. Only used under the write lock.
     */
    private final Map<ChunkCoordIntPair, Map<Integer, Integer>> pending = new HashMap<>();

    public RegionCubeStorage(Path path) throws IOException {
        this.storageLock = StorageMaintenanceLock.openStorage(path);
        try {
            this.save = saveForPath(path);
        } catch (IOException | RuntimeException | Error e) {
            try {
                storageLock.close();
            } catch (IOException close) {
                e.addSuppressed(close);
            }
            throw e;
        }
        this.cubeDirectory = path.resolve("region3d");
    }

    /** Takes the shared open lock; the caller unlocks it. */
    private Lock open() throws IOException {
        Lock lock = openLock.readLock();
        lock.lock();
        if (closed) {
            lock.unlock();
            throw new IOException("Cube storage is closed");
        }
        return lock;
    }

    private boolean hasColumnRecord(ChunkCoordIntPair pos) throws IOException {
        return this.save.getSaveSection2D()
            .hasEntry(location(pos));
    }

    private boolean hasCubeRecord(CubePos pos) throws IOException {
        return this.save.getSaveSection3D()
            .hasEntry(location(pos));
    }

    @Override
    public boolean columnExists(ChunkCoordIntPair pos) throws IOException {
        Lock lock = open();
        try {
            return hasColumnRecord(pos);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public boolean cubeExists(CubePos pos) throws IOException {
        Lock lock = open();
        try {
            long before = writes.get();
            if (hasCubeRecord(pos)) return true;
            if (states(pos.chunkPos()).get(pos.getY()) >= 0) return true;
            // a write may have given it a full record and then dropped its old state since the first check
            return writtenSince(before) && hasCubeRecord(pos);
        } finally {
            lock.unlock();
        }
    }

    /** The stored states of a column's empty cubes; none if the column has no record. */
    private CubeStates.Column states(ChunkCoordIntPair column) throws IOException {
        while (true) {
            long version;
            synchronized (stateCache) {
                CubeStates.Column cached = stateCache.get(column);
                if (cached != null) return cached;
                version = statesPublished;
            }
            Optional<ByteBuffer> record = this.save.load(location(column), true);
            CubeStates.Column loaded = record.isPresent()
                ? CubeStates.Column.read(decodeColumn(column, record.get(), null))
                : CubeStates.Column.EMPTY;
            synchronized (stateCache) {
                CubeStates.Column cached = stateCache.get(column);
                if (cached != null) return cached;
                // a write published states meanwhile, which may be newer than what was read
                if (statesPublished != version) continue;
                cacheStates(column, loaded);
                return loaded;
            }
        }
    }

    /** Called with the {@link #stateCache} lock. */
    private void cacheStates(ChunkCoordIntPair column, CubeStates.Column states) {
        stateCache.put(column, states);
        var entries = stateCache.entrySet()
            .iterator();
        while (stateCache.size() > STATE_CACHE_COLUMNS && entries.hasNext()) {
            entries.next();
            entries.remove();
        }
    }

    /** Publishes the states of columns whose records a write has put in place. */
    private void publishStates(Map<ChunkCoordIntPair, CubeStates.Column> written) {
        if (written.isEmpty()) return;
        synchronized (stateCache) {
            written.forEach(this::cacheStates);
            statesPublished++;
        }
    }

    /**
     * Whether a write may have changed stored cubes since {@code before} ({@link #writes} when a read began): a cube
     * can
     * then have got a full record and lost its old state between the read's two lookups.
     */
    private boolean writtenSince(long before) {
        return (before & 1) != 0 || writes.get() != before;
    }

    /** The empty cube stored as a state in its column, or {@code null}. */
    @org.jetbrains.annotations.Nullable
    private NBTTagCompound emptyCube(CubePos pos) throws IOException {
        int state = states(pos.chunkPos()).get(pos.getY());
        return state < 0 ? null : CubeStates.decode(pos, state);
    }

    @Override
    public NBTTagCompound readColumn(ChunkCoordIntPair pos) throws IOException {
        Lock lock = open();
        try {
            // we use a true here in order to force creation and caching of the new region, thus avoiding an expensive
            // Files.exists() check for every cube/column (which
            // is really expensive on windows)
            Optional<ByteBuffer> data = this.save.load(new EntryLocation2D(pos.chunkXPos, pos.chunkZPos), true);
            if (!data.isPresent()) return null;

            NBTTagCompound column = decodeColumn(pos, data.get(), null);
            // the states of its empty cubes belong to this storage, not to the column
            column.removeTag(CubeStates.TAG);
            return column;
        } finally {
            lock.unlock();
        }
    }

    /** As {@link #decodeCube}, for a column group record or a too large group's per-column record. */
    private NBTTagCompound decodeColumn(ChunkCoordIntPair pos, ByteBuffer record,
        @org.jetbrains.annotations.Nullable Map<ByteBuffer, ColumnGroupCodec.Stored> decoded) throws IOException {
        if (!ColumnGroupCodec.isGroup(record)) return CCNBTUtils.loadTag(record);
        int slot = ColumnGroupCodec.slot(pos);
        if ((ColumnGroupCodec.memberMask(record) & 1 << slot) == 0) {
            throw new IOException("Column " + pos + " points at a column group record without it");
        }
        ColumnGroupCodec.Stored stored = decoded == null ? null : decoded.get(record);
        if (stored == null) {
            stored = ColumnGroupCodec.read(record);
            if (decoded != null) decoded.put(record, stored);
        }
        return stored.member(slot);
    }

    @Override
    public NBTTagCompound readCube(CubePos pos) throws IOException {
        Lock lock = open();
        try {
            long before = writes.get();
            // see comment in readColumn
            Optional<ByteBuffer> data = this.save.load(new EntryLocation3D(pos.getX(), pos.getY(), pos.getZ()), true);
            if (data.isPresent()) return decodeCube(pos, data.get(), null);
            NBTTagCompound empty = emptyCube(pos);
            if (empty != null || !writtenSince(before)) return empty;
            // a write may have given it a full record and then dropped its old state since the first read
            data = this.save.load(new EntryLocation3D(pos.getX(), pos.getY(), pos.getZ()), true);
            return data.isPresent() ? decodeCube(pos, data.get(), null) : null;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Decodes a cube from its record: a bundle record shared with the other cubes of its bundle, or the record of its
     * own that a member of a too large bundle keeps. {@code decoded} (if given) reuses bundles already decoded for this
     * batch, keyed by record content.
     */
    private NBTTagCompound decodeCube(CubePos pos, ByteBuffer record,
        @org.jetbrains.annotations.Nullable Map<ByteBuffer, CubeBundleCodec.Stored> decoded) throws IOException {
        if (!CubeBundleCodec.isBundle(record)) return CCNBTUtils.loadTag(record);
        int slot = CubeBundleCodec.slot(pos);
        if ((CubeBundleCodec.memberMask(record) & 1 << slot) == 0) {
            throw new IOException("Cube " + pos + " points at a bundle record without it");
        }
        CubeBundleCodec.Stored stored = decoded == null ? null : decoded.get(record);
        if (stored == null) {
            stored = CubeBundleCodec.read(record);
            if (decoded != null) decoded.put(record, stored);
        }
        return stored.member(slot);
    }

    @Override
    public @NotNull NBTBatch readBatch(PosBatch positions) throws IOException {
        Lock lock = open();
        try {
            long before = writes.get();
            NBTBatch result = read(positions);
            for (NBTTagCompound column : result.columns.values()) column.removeTag(CubeStates.TAG);
            // cubes without a full record may be empty cubes stored in their column
            List<CubePos> missing = new ArrayList<>();
            for (CubePos pos : positions.cubes) {
                if (result.cubes.containsKey(pos)) continue;
                NBTTagCompound empty = emptyCube(pos);
                if (empty != null) result.cubes.put(pos, empty);
                else missing.add(pos);
            }
            // a write may have given one a full record and then dropped its old state since the first read
            if (!missing.isEmpty() && writtenSince(before))
                result.cubes.putAll(read(new PosBatch(Collections.emptyList(), missing)).cubes);
            return result;
        } finally {
            lock.unlock();
        }
    }

    private NBTBatch read(PosBatch positions) throws IOException {
        var columns = this.save
            .load2D(DataUtils.mapToList(positions.columns, c -> new EntryLocation2D(c.chunkXPos, c.chunkZPos)), false);
        var cubes = this.save.load3D(
            DataUtils.mapToList(positions.cubes, c -> new EntryLocation3D(c.getX(), c.getY(), c.getZ())),
            false);
        // a record that cannot be read fails the batch like one that cannot be decoded: left out, its column or cube
        // would load as missing and be generated again, and the next save would overwrite the stored record
        failOnReadErrors(columns.errored, cubes.errored);

        Map<ChunkCoordIntPair, NBTTagCompound> columnTags;
        // decode each column group record once for all its requested members
        Map<ChunkCoordIntPair, List<Map.Entry<EntryLocation2D, ByteBuffer>>> byGroup = new HashMap<>();
        for (Map.Entry<EntryLocation2D, ByteBuffer> e : columns.read.entrySet()) {
            EntryLocation2D l = e.getKey();
            byGroup
                .computeIfAbsent(
                    ColumnGroupCodec.origin(new ChunkCoordIntPair(l.getEntryX(), l.getEntryZ())),
                    k -> new ArrayList<>(4))
                .add(e);
        }
        columnTags = new java.util.concurrent.ConcurrentHashMap<>();
        Map<ChunkCoordIntPair, NBTTagCompound> groupTags = columnTags;
        try {
            byGroup.values()
                .parallelStream()
                .forEach(members -> {
                    Map<ByteBuffer, ColumnGroupCodec.Stored> decoded = new HashMap<>();
                    for (Map.Entry<EntryLocation2D, ByteBuffer> e : members) {
                        EntryLocation2D l = e.getKey();
                        ChunkCoordIntPair pos = new ChunkCoordIntPair(l.getEntryX(), l.getEntryZ());
                        try {
                            groupTags.put(pos, decodeColumn(pos, e.getValue(), decoded));
                        } catch (IOException ex) {
                            throw new UncheckedIOException(ex);
                        }
                    }
                });
        } catch (UncheckedIOException e) {
            throw e.getCause();
        }

        Map<CubePos, NBTTagCompound> cubeTags;
        // decode each bundle record once for all its requested members
        Map<CubePos, List<Map.Entry<EntryLocation3D, ByteBuffer>>> byBundle = new HashMap<>();
        for (Map.Entry<EntryLocation3D, ByteBuffer> e : cubes.read.entrySet()) {
            EntryLocation3D l = e.getKey();
            byBundle
                .computeIfAbsent(
                    CubeBundleCodec.origin(new CubePos(l.getEntryX(), l.getEntryY(), l.getEntryZ())),
                    k -> new ArrayList<>(8))
                .add(e);
        }
        cubeTags = new java.util.concurrent.ConcurrentHashMap<>();
        try {
            byBundle.values()
                .parallelStream()
                .forEach(members -> {
                    Map<ByteBuffer, CubeBundleCodec.Stored> decoded = new HashMap<>();
                    for (Map.Entry<EntryLocation3D, ByteBuffer> e : members) {
                        EntryLocation3D l = e.getKey();
                        CubePos pos = new CubePos(l.getEntryX(), l.getEntryY(), l.getEntryZ());
                        try {
                            cubeTags.put(pos, decodeCube(pos, e.getValue(), decoded));
                        } catch (IOException ex) {
                            throw new UncheckedIOException(ex);
                        }
                    }
                });
        } catch (UncheckedIOException e) {
            throw e.getCause();
        }

        return new NBTBatch(columnTags, cubeTags);
    }

    private static void failOnReadErrors(Map<?, IOException> columns, Map<?, IOException> cubes) throws IOException {
        IOException failure = null;
        for (Map<?, IOException> errors : Arrays.asList(columns, cubes)) {
            for (IOException e : errors.values()) {
                if (failure == null) failure = e;
                else failure.addSuppressed(e);
            }
        }
        if (failure != null) throw failure;
    }

    @Override
    public void writeColumn(ChunkCoordIntPair pos, NBTTagCompound nbt) throws IOException {
        writeBatch(new NBTBatch(Collections.singletonMap(pos, nbt), Collections.emptyMap()), null);
    }

    @Override
    public void writeCube(CubePos pos, NBTTagCompound nbt) throws IOException {
        writeBatch(new NBTBatch(Collections.emptyMap(), Collections.singletonMap(pos, nbt)), null);
    }

    @Override
    public void writeBatch(NBTBatch batch) throws IOException {
        writeBatch(batch, null);
    }

    @Override
    public void writeBatch(NBTBatch batch, @org.jetbrains.annotations.Nullable Map<CubePos, ByteBuffer> written)
        throws IOException {
        Lock lock = open();
        try {
            synchronized (writeLock) {
                writes.incrementAndGet();
                try {
                    writeBundled(batch, written);
                } finally {
                    writes.incrementAndGet();
                }
            }
        } finally {
            lock.unlock();
        }
    }

    /**
     * Writes columns as column group records and cubes as bundle records, and empty cubes as states in their column's
     * record ({@code null} in {@code written}). Column groups are written first: a cube that becomes empty has its
     * state stored before its full record is removed. A column group that fails does not stop the cubes of the batch
     * from being written; the empty cubes whose state it should have held get full records instead.
     */
    private void writeBundled(NBTBatch batch, @org.jetbrains.annotations.Nullable Map<CubePos, ByteBuffer> written)
        throws IOException {
        Map<CubePos, NBTTagCompound> full = new HashMap<>();
        // states to store, by column (cube Y to state)
        Map<ChunkCoordIntPair, Map<Integer, Integer>> changes = new HashMap<>();
        // empty cubes saved again as they are stored
        List<CubePos> unchanged = new ArrayList<>();
        for (Map.Entry<CubePos, NBTTagCompound> e : batch.cubes.entrySet()) {
            CubePos pos = e.getKey();
            ChunkCoordIntPair column = pos.chunkPos();
            // this save replaces a state still waiting for its column
            Map<Integer, Integer> waiting = pending.get(column);
            if (waiting != null && waiting.remove(pos.getY()) != null && waiting.isEmpty()) pending.remove(column);

            int state = CubeStates.encode(pos, e.getValue());
            if (state < 0) full.put(pos, e.getValue());
            else if (!batch.columns.containsKey(column) && storedAs(pos, state)) unchanged.add(pos);
            else if (batch.columns.containsKey(column) || hasColumnRecord(column))
                changes.computeIfAbsent(column, k -> new HashMap<>())
                    .put(pos.getY(), state);
            else {
                // no column record to hold the state yet: a full record now, a state once the column is written
                full.put(pos, e.getValue());
                pending.computeIfAbsent(column, k -> new HashMap<>())
                    .put(pos.getY(), state);
            }
        }
        // states that waited for the columns written now; their cubes' full records are removed below
        for (ChunkCoordIntPair column : batch.columns.keySet()) {
            Map<Integer, Integer> waiting = pending.remove(column);
            if (waiting != null) waiting.forEach(
                (y, state) -> changes.computeIfAbsent(column, k -> new HashMap<>())
                    .putIfAbsent(y, state));
        }

        IOException failure = null;
        Map<ChunkCoordIntPair, Set<Integer>> stored = new HashMap<>();
        try {
            writeColumnGroups(batch.columns, changes, stored);
        } catch (IOException e) {
            failure = e;
        }
        List<CubePos> removed = new ArrayList<>();
        for (Map.Entry<ChunkCoordIntPair, Map<Integer, Integer>> e : changes.entrySet()) {
            ChunkCoordIntPair column = e.getKey();
            Set<Integer> ys = stored.getOrDefault(column, Collections.emptySet());
            for (int y : e.getValue()
                .keySet()) {
                CubePos pos = new CubePos(column.chunkXPos, y, column.chunkZPos);
                if (ys.contains(y)) removed.add(pos);
                // a state that could not be stored: the cube is written in full, or keeps the full record it has
                else if (batch.cubes.containsKey(pos)) full.put(pos, batch.cubes.get(pos));
            }
        }
        try {
            writeBundles(full, removed, written);
            if (written != null) {
                for (CubePos pos : removed) if (batch.cubes.containsKey(pos)) written.put(pos, null);
                for (CubePos pos : unchanged) written.put(pos, null);
            }
        } catch (IOException e) {
            if (failure == null) failure = e;
            else failure.addSuppressed(e);
        }
        if (failure != null) throw failure;
    }

    /**
     * Whether an empty cube is stored as this state already. A column whose states cannot be read counts as not: the
     * cube's state is then not stored there either, and it gets a full record.
     */
    private boolean storedAs(CubePos pos, int state) {
        try {
            return states(pos.chunkPos()).get(pos.getY()) == state && !hasCubeRecord(pos);
        } catch (IOException | RuntimeException e) {
            return false;
        }
    }

    /** Largest record a sector region stores itself; larger ones go to RegionLib's per-key external files. */
    static final int MAX_SHARED_RECORD = 255 * 512 - Integer.BYTES;

    private static EntryLocation3D location(CubePos pos) {
        return new EntryLocation3D(pos.getX(), pos.getY(), pos.getZ());
    }

    /**
     * Writes and removes cubes as bundle records. Each affected bundle is read, changed and written again: members
     * not written here keep their stored data (from a bundle record or a per-cube record, which is converted), and
     * the header entries of all members then point at the one new record.
     */
    private void writeBundles(Map<CubePos, NBTTagCompound> cubes, Collection<CubePos> removed,
        @org.jetbrains.annotations.Nullable Map<CubePos, ByteBuffer> written) throws IOException {
        Map<CubePos, NBTTagCompound[]> replacements = new HashMap<>();
        Map<CubePos, int[]> removals = new HashMap<>();
        for (Map.Entry<CubePos, NBTTagCompound> e : cubes.entrySet()) {
            replacements.computeIfAbsent(CubeBundleCodec.origin(e.getKey()), k -> new NBTTagCompound[8])[CubeBundleCodec
                .slot(e.getKey())] = e.getValue();
        }
        for (CubePos pos : removed) {
            removals.computeIfAbsent(CubeBundleCodec.origin(pos), k -> new int[1])[0] |= 1 << CubeBundleCodec.slot(pos);
        }
        java.util.Set<CubePos> origins = new java.util.HashSet<>(replacements.keySet());
        origins.addAll(removals.keySet());

        // Each bundle is prepared on its own: a bundle that fails does not stop the others from being written.
        Map<EntryLocation3D, ByteBuffer> entries = new java.util.concurrent.ConcurrentHashMap<>();
        List<EntryLocation3D> deleted = java.util.Collections.synchronizedList(new ArrayList<>());
        List<IOException> failures = java.util.Collections.synchronizedList(new ArrayList<>());
        Map<CubePos, ByteBuffer> records = new java.util.concurrent.ConcurrentHashMap<>();
        origins.parallelStream()
            .forEach(origin -> {
                Map<EntryLocation3D, ByteBuffer> bundleEntries = new HashMap<>();
                List<EntryLocation3D> bundleDeleted = new ArrayList<>();
                Map<CubePos, ByteBuffer> bundleRecords = new HashMap<>();
                try {
                    NBTTagCompound[] replace = replacements.getOrDefault(origin, new NBTTagCompound[8]);
                    int[] remove = removals.get(origin);
                    rewriteBundle(
                        origin,
                        replace,
                        remove == null ? 0 : remove[0],
                        bundleEntries,
                        bundleDeleted,
                        bundleRecords);
                    entries.putAll(bundleEntries);
                    deleted.addAll(bundleDeleted);
                    records.putAll(bundleRecords);
                } catch (IOException | RuntimeException e) {
                    failures.add(new IOException("Could not write cube bundle " + origin, e));
                }
            });
        if (!entries.isEmpty() || !deleted.isEmpty()) {
            Map<EntryLocation3D, ByteBuffer> writes = new HashMap<>(entries);
            for (EntryLocation3D l : deleted) writes.put(l, null);
            this.save.save3d(writes);
        }
        if (written != null) written.putAll(records);
        cubesWritten.addAndGet(records.size());
        java.util.Set<ByteBuffer> distinct = Collections.newSetFromMap(new java.util.IdentityHashMap<>());
        distinct.addAll(records.values());
        recordsWritten.addAndGet(distinct.size());
        if (!failures.isEmpty()) {
            IOException failure = failures.get(0);
            for (int i = 1; i < failures.size(); i++) failure.addSuppressed(failures.get(i));
            throw failure;
        }
    }

    private void rewriteBundle(CubePos origin, NBTTagCompound[] replace, int removeMask,
        Map<EntryLocation3D, ByteBuffer> entries, List<EntryLocation3D> deleted, Map<CubePos, ByteBuffer> reported)
        throws IOException {
        int replaceMask = 0, present = 0;
        for (int s = 0; s < 8; s++) {
            if (replace[s] != null) replaceMask |= 1 << s;
            if (hasCubeRecord(CubeBundleCodec.member(origin, s))) present |= 1 << s;
        }
        removeMask &= ~replaceMask & present;
        // nothing stored changes, e.g. an empty cube stored as a state already is saved again
        if (replaceMask == 0 && removeMask == 0) return;
        int untouched = present & ~replaceMask & ~removeMask;

        // members whose stored data cannot be read or decoded keep their record and header as they are
        int keep = 0;
        // stored data of the members that stay as they are, by record (several members usually share one)
        Map<ByteBuffer, Integer> records = new HashMap<>();
        if (untouched != 0) {
            List<EntryLocation3D> keys = new ArrayList<>();
            for (int s = 0; s < 8; s++)
                if ((untouched & 1 << s) != 0) keys.add(location(CubeBundleCodec.member(origin, s)));
            cubicchunks.regionlib.api.region.BatchReadResult<EntryLocation3D> read;
            try {
                read = this.save.load3D(keys, false);
            } catch (RuntimeException e) {
                // a damaged record can fail the whole read without naming its key; keep every member as it is
                CubicChunks.LOGGER
                    .error("Could not read the stored cubes of bundle {}; keeping them as they are", origin, e);
                keep |= untouched;
                untouched = 0;
                read = null;
            }
            for (int s = 0; read != null && s < 8; s++) {
                if ((untouched & 1 << s) == 0) continue;
                EntryLocation3D key = location(CubeBundleCodec.member(origin, s));
                IOException error = read.errored.get(key);
                if (error != null) {
                    CubicChunks.LOGGER.error(
                        "Could not read stored cube {} of bundle {}; keeping it as it is",
                        CubeBundleCodec.member(origin, s),
                        origin,
                        error);
                    keep |= 1 << s;
                    untouched &= ~(1 << s);
                    continue;
                }
                ByteBuffer record = read.read.get(key);
                if (record == null) {
                    untouched &= ~(1 << s); // gone meanwhile
                    continue;
                }
                records.merge(record, 1 << s, (a, b) -> a | b);
            }
        }

        ByteBuffer result = null;
        Map.Entry<ByteBuffer, Integer> only = records.size() == 1 ? records.entrySet()
            .iterator()
            .next() : null;
        boolean appended = false, oversized = false;
        if (only != null && CubeBundleCodec.isBundle(only.getKey())
            && (CubeBundleCodec.memberMask(only.getKey()) & only.getValue()) == only.getValue()) {
            // usual case: keep the untouched members' encoded bytes, drop everything else of the old record
            try {
                CubeBundleCodec.Stored stored = CubeBundleCodec.read(only.getKey());
                result = CubeBundleCodec.rewrite(stored, replace, stored.mask() & ~untouched);
                appended = true;
            } catch (CubeBundleCodec.OversizedBundleException e) {
                appended = oversized = true;
            } catch (IOException | RuntimeException e) {
                // also NBT parse failures of a member, which Minecraft reports as runtime exceptions
                CubicChunks.LOGGER
                    .error("Could not read the stored bundle at {}; keeping its other cubes as they are", origin, e);
                keep |= only.getValue();
            }
        }
        if (!appended) {
            // per-cube records (conversion) or members split over several records: encode the bundle again
            NBTTagCompound[] all = replace.clone();
            for (Map.Entry<ByteBuffer, Integer> record : records.entrySet()) {
                if ((record.getValue() & keep) != 0) continue;
                try {
                    CubeBundleCodec.Stored stored = CubeBundleCodec.isBundle(record.getKey())
                        ? CubeBundleCodec.read(record.getKey())
                        : null;
                    NBTTagCompound[] decoded = new NBTTagCompound[8];
                    for (int s = 0; s < 8; s++) {
                        if ((record.getValue() & 1 << s) == 0) continue;
                        decoded[s] = stored != null ? stored.member(s) : CCNBTUtils.loadTag(record.getKey());
                        if (decoded[s] == null) throw new IOException("Bundle record lacks member " + s);
                    }
                    for (int s = 0; s < 8; s++) if (decoded[s] != null) all[s] = decoded[s];
                } catch (IOException | RuntimeException e) {
                    CubicChunks.LOGGER.error(
                        "Could not read stored cubes {} of bundle {}; keeping them as they are",
                        record.getValue(),
                        origin,
                        e);
                    keep |= record.getValue();
                }
            }
            boolean any = false;
            for (NBTTagCompound member : all) any |= member != null;
            try {
                result = any ? CubeBundleCodec.encode(all) : null;
            } catch (CubeBundleCodec.OversizedBundleException e) {
                oversized = true;
            }
        }

        if (oversized || result != null && result.remaining() > MAX_SHARED_RECORD) {
            // Too large for one region record. Shared external files would be written after the members' headers are
            // removed, so a crash in between could lose untouched members. Write only the saved cubes, one record each,
            // and leave the others where they are.
            for (int s = 0; s < 8; s++) {
                EntryLocation3D key = location(CubeBundleCodec.member(origin, s));
                if (replace[s] != null) {
                    ByteBuffer record = CCNBTUtils.saveTag(replace[s], CubicChunksConfig.chunkCompression);
                    reported.put(CubeBundleCodec.member(origin, s), record.asReadOnlyBuffer());
                    entries.put(key, record);
                } else if ((removeMask & 1 << s) != 0) deleted.add(key);
            }
            return;
        }

        int written = result == null ? 0 : CubeBundleCodec.memberMask(result);
        // a view taken before the region write consumes the buffer
        ByteBuffer view = result == null ? null : result.asReadOnlyBuffer();
        for (int s = 0; s < 8; s++) {
            if (replace[s] != null && (written & 1 << s) != 0) reported.put(CubeBundleCodec.member(origin, s), view);
        }
        for (int s = 0; s < 8; s++) {
            EntryLocation3D key = location(CubeBundleCodec.member(origin, s));
            if ((keep & 1 << s) != 0) continue;
            // the same buffer instance makes the region store one shared record
            if ((written & 1 << s) != 0) entries.put(key, result);
            else if ((present & 1 << s) != 0) deleted.add(key);
        }
    }

    private static EntryLocation2D location(ChunkCoordIntPair pos) {
        return new EntryLocation2D(pos.chunkXPos, pos.chunkZPos);
    }

    /**
     * Writes columns as 2x2 group records, like {@link #writeBundles} for cubes: each affected group is read, changed
     * and written again, and the header entries of its members then point at the one new record. A column written here
     * keeps the states of its empty cubes; {@code changes} sets states (cube Y to state) of columns with a record.
     * {@code stored} receives, per column, the cube Ys whose new state reached storage.
     */
    private void writeColumnGroups(Map<ChunkCoordIntPair, NBTTagCompound> columns,
        Map<ChunkCoordIntPair, Map<Integer, Integer>> changes, Map<ChunkCoordIntPair, Set<Integer>> stored)
        throws IOException {
        if (columns.isEmpty() && changes.isEmpty()) return;
        Map<ChunkCoordIntPair, NBTTagCompound[]> groups = new HashMap<>();
        Map<ChunkCoordIntPair, Map<Integer, Integer>[]> groupChanges = new HashMap<>();
        for (Map.Entry<ChunkCoordIntPair, NBTTagCompound> e : columns.entrySet()) {
            groups.computeIfAbsent(ColumnGroupCodec.origin(e.getKey()), k -> new NBTTagCompound[4])[ColumnGroupCodec
                .slot(e.getKey())] = e.getValue();
        }
        for (Map.Entry<ChunkCoordIntPair, Map<Integer, Integer>> e : changes.entrySet()) {
            ChunkCoordIntPair origin = ColumnGroupCodec.origin(e.getKey());
            groups.computeIfAbsent(origin, k -> new NBTTagCompound[4]);
            @SuppressWarnings("unchecked")
            Map<Integer, Integer>[] slots = groupChanges.computeIfAbsent(origin, k -> new Map[4]);
            slots[ColumnGroupCodec.slot(e.getKey())] = e.getValue();
        }
        // Each group is prepared on its own: a group that fails does not stop the others from being written.
        Map<EntryLocation2D, ByteBuffer> entries = new java.util.concurrent.ConcurrentHashMap<>();
        Map<ChunkCoordIntPair, CubeStates.Column> states = new java.util.concurrent.ConcurrentHashMap<>();
        Map<ChunkCoordIntPair, Set<Integer>> accepted = new java.util.concurrent.ConcurrentHashMap<>();
        List<IOException> failures = java.util.Collections.synchronizedList(new ArrayList<>());
        groups.entrySet()
            .parallelStream()
            .forEach(group -> {
                Map<EntryLocation2D, ByteBuffer> groupEntries = new HashMap<>();
                Map<ChunkCoordIntPair, CubeStates.Column> groupStates = new HashMap<>();
                Map<ChunkCoordIntPair, Set<Integer>> groupAccepted = new HashMap<>();
                List<IOException> groupFailures = new ArrayList<>();
                try {
                    @SuppressWarnings("unchecked")
                    Map<Integer, Integer>[] slots = groupChanges.getOrDefault(group.getKey(), new Map[4]);
                    rewriteColumnGroup(
                        group.getKey(),
                        group.getValue(),
                        slots,
                        groupEntries,
                        groupStates,
                        groupAccepted,
                        groupFailures);
                    failures.addAll(groupFailures);
                    entries.putAll(groupEntries);
                    states.putAll(groupStates);
                    accepted.putAll(groupAccepted);
                } catch (IOException | RuntimeException e) {
                    failures.add(new IOException("Could not write column group " + group.getKey(), e));
                }
            });
        if (!entries.isEmpty()) {
            try {
                this.save.save2d(new HashMap<>(entries));
            } catch (IOException | RuntimeException e) {
                // some records may be in place: forget the cached states of these columns rather than keep old ones
                synchronized (stateCache) {
                    for (ChunkCoordIntPair column : states.keySet()) stateCache.remove(column);
                    statesPublished++;
                }
                throw e;
            }
        }
        publishStates(states);
        stored.putAll(accepted);
        if (!failures.isEmpty()) {
            IOException failure = failures.get(0);
            for (int i = 1; i < failures.size(); i++) failure.addSuppressed(failures.get(i));
            throw failure;
        }
    }

    /**
     * Prepares the new record of one column group. A member is written when it is replaced or its states change; it
     * keeps the states of its empty cubes, minus those whose cube now has a full record (which wins anyway). The states
     * written are reported in {@code states}, and per column the cube Ys of {@code changes} they include in
     * {@code accepted}.
     * <p>
     * A member whose stored states cannot be read is not written, so they are never lost: a saved column is still
     * written if its states are cached, else it is left as it is and reported in {@code failures}; a member with only
     * state changes is left as it is, and the cubes of those changes get full records instead (writeBundled).
     */
    private void rewriteColumnGroup(ChunkCoordIntPair origin, NBTTagCompound[] replace, Map<Integer, Integer>[] changes,
        Map<EntryLocation2D, ByteBuffer> entries, Map<ChunkCoordIntPair, CubeStates.Column> states,
        Map<ChunkCoordIntPair, Set<Integer>> accepted, List<IOException> failures) throws IOException {
        int present = 0, touched = 0;
        for (int s = 0; s < 4; s++) {
            if (hasColumnRecord(ColumnGroupCodec.member(origin, s))) present |= 1 << s;
            if (replace[s] != null || changes[s] != null) touched |= 1 << s;
        }

        // the stored record of each present member; members whose record cannot be read keep it as it is
        ByteBuffer[] records = new ByteBuffer[4];
        IOException[] errors = new IOException[4];
        int keep = 0;
        if (present != 0) {
            List<EntryLocation2D> keys = new ArrayList<>();
            for (int s = 0; s < 4; s++)
                if ((present & 1 << s) != 0) keys.add(location(ColumnGroupCodec.member(origin, s)));
            cubicchunks.regionlib.api.region.BatchReadResult<EntryLocation2D> read;
            try {
                read = this.save.load2D(keys, false);
            } catch (RuntimeException e) {
                // a damaged record can fail the whole read without naming its key: read each member on its own
                read = null;
            }
            for (int s = 0; s < 4; s++) {
                if ((present & 1 << s) == 0) continue;
                EntryLocation2D key = location(ColumnGroupCodec.member(origin, s));
                IOException error;
                ByteBuffer record;
                try {
                    var one = read != null ? read : this.save.load2D(Collections.singletonList(key), false);
                    error = one.errored.get(key);
                    record = one.read.get(key);
                } catch (RuntimeException e) {
                    error = new IOException(e);
                    record = null;
                }
                if (error != null) {
                    CubicChunks.LOGGER.error(
                        "Could not read stored column {} of column group {}; keeping it as it is",
                        ColumnGroupCodec.member(origin, s),
                        origin,
                        error);
                    errors[s] = error;
                    keep |= 1 << s;
                } else if (record == null) present &= ~(1 << s); // gone meanwhile
                else records[s] = record;
            }
        }

        // decoded group records, by record (several members usually share one)
        Map<ByteBuffer, ColumnGroupCodec.Stored> decoded = new HashMap<>();
        NBTTagCompound[] write = new NBTTagCompound[4];
        for (int s = 0; s < 4; s++) {
            if ((touched & 1 << s) == 0) continue;
            ChunkCoordIntPair column = ColumnGroupCodec.member(origin, s);
            NBTTagCompound stored = null;
            CubeStates.Column old = null;
            IOException error = errors[s];
            if ((present & 1 << s) == 0) old = CubeStates.Column.EMPTY;
            else if (error == null) {
                try {
                    stored = member(records[s], s, decoded);
                } catch (IOException | RuntimeException e) {
                    error = e instanceof IOException io ? io : new IOException(e);
                }
            }
            if (stored != null) {
                try {
                    old = CubeStates.Column.read(stored);
                } catch (IOException e) {
                    error = e;
                }
            }
            if (old == null && stored != null && replace[s] != null) {
                synchronized (stateCache) {
                    old = stateCache.get(column);
                }
                if (old == null) {
                    // its record reads, but its states do not: the saved column keeps them exactly as they are and
                    // takes no state changes, whose cubes get full records instead
                    CubicChunks.LOGGER
                        .error("The stored states of column {} are invalid; keeping them as they are", column, error);
                    NBTTagCompound base = new NBTTagCompound();
                    for (Object key : replace[s].func_150296_c())
                        base.setTag((String) key, replace[s].getTag((String) key));
                    base.setTag(CubeStates.TAG, stored.getTag(CubeStates.TAG));
                    write[s] = base;
                    keep &= ~(1 << s);
                    continue;
                }
            }
            if (old == null && replace[s] != null) {
                // the states this storage last stored or read for it are those of its stored record
                synchronized (stateCache) {
                    old = stateCache.get(column);
                }
            }
            if (old == null) {
                keep |= 1 << s;
                if (replace[s] == null) CubicChunks.LOGGER.error(
                    "Could not read the stored states of column {}; its empty cubes saved now get full records",
                    column,
                    error);
                if (replace[s] != null) failures.add(
                    new IOException(
                        "Could not read the stored states of column " + column + "; its save is not written",
                        error));
                continue;
            }
            NBTTagCompound base;
            if (replace[s] != null) {
                // a copy of the top level only: the caller's NBT is not changed
                base = new NBTTagCompound();
                for (Object key : replace[s].func_150296_c())
                    base.setTag((String) key, replace[s].getTag((String) key));
            } else if (stored != null) base = stored;
            else continue; // states for a column without a record wait as pending (writeBundled)
            Map<Integer, Integer> change = new HashMap<>();
            Map<Integer, Integer> set = changes[s] != null ? changes[s] : Collections.emptyMap();
            // a cube with a full record wins over its state; drop the stale state
            for (int y : stateYs(old)) {
                if (!set.containsKey(y) && hasCubeRecord(new CubePos(column.chunkXPos, y, column.chunkZPos)))
                    change.put(y, -1);
            }
            change.putAll(set);
            Set<Integer> rejected = new java.util.HashSet<>();
            CubeStates.Column updated = old.with(change, rejected::add);
            updated.write(base);
            write[s] = base;
            // written, also when its stored record could not be read (its states are known from the cache)
            keep &= ~(1 << s);
            states.put(column, updated);
            Set<Integer> ys = new java.util.HashSet<>(set.keySet());
            ys.removeAll(rejected);
            accepted.put(column, ys);
        }
        boolean any = false;
        for (NBTTagCompound member : write) any |= member != null;
        if (!any) return;

        // the other present members stay in the new record with their stored data
        int untouched = present & ~keep & ~touched;
        ByteBuffer result = null;
        boolean oversized = false;
        try {
            java.util.Set<ByteBuffer> shared = new java.util.HashSet<>();
            for (int s = 0; s < 4; s++) if ((untouched & 1 << s) != 0) shared.add(records[s]);
            ByteBuffer only = shared.size() == 1 ? shared.iterator()
                .next() : null;
            ColumnGroupCodec.Stored stored = null;
            if (only != null && ColumnGroupCodec.isGroup(only)
                && (ColumnGroupCodec.memberMask(only) & untouched) == untouched) {
                try {
                    stored = decoded.get(only);
                    if (stored == null) decoded.put(only, stored = ColumnGroupCodec.read(only));
                } catch (IOException | RuntimeException e) {
                    stored = null;
                }
            }
            if (stored != null || untouched == 0) {
                // usual case: keep the untouched members' encoded bytes
                result = stored != null ? ColumnGroupCodec.rewrite(stored, write, untouched)
                    : ColumnGroupCodec.encode(write);
            } else {
                // per-column records (conversion) or members split over several records: encode the group again
                NBTTagCompound[] all = write.clone();
                for (int s = 0; s < 4; s++) {
                    if ((untouched & 1 << s) == 0) continue;
                    try {
                        all[s] = member(records[s], s, decoded);
                    } catch (IOException | RuntimeException e) {
                        CubicChunks.LOGGER.error(
                            "Could not read stored column {} of column group {}; keeping it as it is",
                            ColumnGroupCodec.member(origin, s),
                            origin,
                            e);
                        keep |= 1 << s;
                    }
                }
                result = ColumnGroupCodec.encode(all);
            }
        } catch (CubeBundleCodec.OversizedBundleException e) {
            oversized = true;
        }

        if (oversized || result.remaining() > MAX_SHARED_RECORD) {
            // as for bundles: write only the written columns, one record each, and leave the others where they are
            for (int s = 0; s < 4; s++) {
                if (write[s] == null) continue;
                entries.put(
                    location(ColumnGroupCodec.member(origin, s)),
                    CCNBTUtils.saveTag(write[s], CubicChunksConfig.chunkCompression));
            }
            return;
        }

        int written = ColumnGroupCodec.memberMask(result);
        for (int s = 0; s < 4; s++) {
            // the same buffer instance makes the region store one shared record
            if ((keep & 1 << s) == 0 && (written & 1 << s) != 0)
                entries.put(location(ColumnGroupCodec.member(origin, s)), result);
        }
    }

    /** The stored NBT of member {@code slot} of a column record, a column group record or a per-column record. */
    private static NBTTagCompound member(ByteBuffer record, int slot, Map<ByteBuffer, ColumnGroupCodec.Stored> decoded)
        throws IOException {
        if (!ColumnGroupCodec.isGroup(record)) return CCNBTUtils.loadTag(record.duplicate());
        ColumnGroupCodec.Stored stored = decoded.get(record);
        if (stored == null) decoded.put(record, stored = ColumnGroupCodec.read(record));
        NBTTagCompound member = stored.member(slot);
        if (member == null) throw new IOException("Column group record lacks member " + slot);
        return member;
    }

    private static List<Integer> stateYs(CubeStates.Column states) {
        List<Integer> ys = new ArrayList<>();
        states.forEach(ys::add);
        return ys;
    }

    // RegionLib's fallback (.ext) region provider lists every slot of a region file that has no .ext file, so only
    // keys with an entry are reported

    @Override
    public void forEachColumn(Consumer<ChunkCoordIntPair> callback) throws IOException {
        Lock lock = open();
        try {
            // a stable view: no record changes while listing
            synchronized (writeLock) {
                listColumns(callback);
            }
        } finally {
            lock.unlock();
        }
    }

    private void listColumns(Consumer<ChunkCoordIntPair> callback) throws IOException {
        SaveSection2D section = this.save.getSaveSection2D();
        section.forAllKeys(
            pos -> {
                if (section.hasEntry(pos)) callback.accept(new ChunkCoordIntPair(pos.getEntryX(), pos.getEntryZ()));
            });
    }

    @Override
    public void forEachCube(Consumer<CubePos> callback) throws IOException {
        Lock lock = open();
        try {
            // a stable view: no record changes while listing
            synchronized (writeLock) {
                SaveSection3D section = this.save.getSaveSection3D();
                section.forAllKeys(
                    pos -> {
                        if (section.hasEntry(pos))
                            callback.accept(new CubePos(pos.getEntryX(), pos.getEntryY(), pos.getEntryZ()));
                    });
                // and the empty cubes stored as states, unless a full record wins over a stale state
                List<ChunkCoordIntPair> columns = new ArrayList<>();
                listColumns(columns::add);
                for (ChunkCoordIntPair column : columns) {
                    List<Integer> ys = stateYs(states(column));
                    for (int y : ys) {
                        CubePos pos = new CubePos(column.chunkXPos, y, column.chunkZPos);
                        if (!hasCubeRecord(pos)) callback.accept(pos);
                    }
                }
            }
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void flush() throws IOException {
        Lock lock = open();
        try {
            synchronized (writeLock) {
                this.save.flush();
            }
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void close() throws IOException {
        Lock lock = openLock.writeLock();
        lock.lock();
        try {
            if (closed) return;
            closeRegions();
            synchronized (stateCache) {
                stateCache.clear();
            }
            closed = true;
        } finally {
            lock.unlock();
        }
    }

    private void closeRegions() throws IOException {
        if (this.save == null) return;
        if (cubesWritten.get() > 0) {
            CubicChunks.LOGGER.info(
                "Cube storage {}: {} cube saves written as {} records",
                cubeDirectory,
                cubesWritten.get(),
                recordsWritten.get());
        }
        // A failed close may leave live region handles. Do not permit maintenance until they are closed.
        this.save.close();
        this.save = null;
        this.storageLock.close();
    }
}
