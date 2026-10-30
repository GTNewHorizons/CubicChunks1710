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
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

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
import it.unimi.dsi.fastutil.Pair;

/**
 * Implementation of {@link ICubicStorage} for the Cubic Chunks' standard Anvil3d storage format.
 */
public class RegionCubeStorage implements ICubicStorage, RecordingCubeStorage {

    /** Runs startup-only maintenance before opening storage; gameplay dimension loads never compact. */
    public static ICubicStorage openForWorld(Path worldRoot, Path path, boolean compactEmpty) throws IOException {
        return openForWorld(worldRoot, path, compactEmpty, false);
    }

    /**
     * As above; {@code bundled} stores non-compact cubes in 2x2x2 bundle records and columns in 2x2 group records
     * (requires compact empty cubes).
     */
    public static ICubicStorage openForWorld(Path worldRoot, Path path, boolean compactEmpty, boolean bundled)
        throws IOException {
        StartupRegionCompaction.beforeOpen(worldRoot, path, bundled);
        return compactEmpty ? new CompactCubeStorage(path, bundled) : new RegionCubeStorage(path);
    }

    private static SaveCubeColumns saveForPath(Path path, boolean bundled) throws IOException {
        // Bundle records are shared by several header entries, which only ShadowPagingRegion supports.
        if (CubicChunksConfig.useShadowPagingIO || bundled) {
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
        } else {
            return SaveCubeColumns.create(path);
        }
    }

    private SaveCubeColumns save;
    private final Path cubeDirectory;
    private final StorageMaintenanceLock storageLock;
    /**
     * Cubes are written as 2x2x2 bundle records ({@link CubeBundleCodec}) and columns as 2x2 group records
     * ({@link ColumnGroupCodec}); per-cube and per-column records are still read.
     */
    private final boolean bundled;
    /** Bundled writes since opening: cubes saved and records (bundle or per-cube) written for them, for the log. */
    private final java.util.concurrent.atomic.AtomicLong cubesWritten = new java.util.concurrent.atomic.AtomicLong(),
        recordsWritten = new java.util.concurrent.atomic.AtomicLong();

    public RegionCubeStorage(Path path) throws IOException {
        this(path, false, false);
    }

    RegionCubeStorage(Path path, boolean compactDelegate) throws IOException {
        this(path, compactDelegate, false);
    }

    RegionCubeStorage(Path path, boolean compactDelegate, boolean bundled) throws IOException {
        this.bundled = bundled;
        if (!compactDelegate && Files.exists(path.resolve(CompactCubeStorage.DIRECTORY))) {
            throw new IOException("This save uses compact empty cubes; open it with CompactCubeStorage");
        }
        this.storageLock = StorageMaintenanceLock.openStorage(path);
        try {
            this.save = saveForPath(path, bundled);
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

    void removeCubes(Collection<CubePos> positions) throws IOException {
        if (bundled) {
            writeBundles(Collections.emptyMap(), positions, null);
            return;
        }
        Map<EntryLocation3D, ByteBuffer> removed = new HashMap<>();
        Map<String, Boolean> regions = new HashMap<>();
        for (CubePos pos : positions) {
            EntryLocation3D location = new EntryLocation3D(pos.getX(), pos.getY(), pos.getZ());
            String region = location.getRegionKey()
                .getName();
            boolean exists = regions.computeIfAbsent(
                region,
                name -> Files.exists(cubeDirectory.resolve(name))
                    || Files.exists(cubeDirectory.resolve(name + ".ext")));
            if (exists && cubeExists(pos)) removed.put(location, null);
        }
        if (!removed.isEmpty()) save.save3d(removed);
    }

    @Override
    public boolean columnExists(ChunkCoordIntPair pos) throws IOException {
        return this.save.getSaveSection2D()
            .hasEntry(new EntryLocation2D(pos.chunkXPos, pos.chunkZPos));
    }

    @Override
    public boolean cubeExists(CubePos pos) throws IOException {
        return this.save.getSaveSection3D()
            .hasEntry(new EntryLocation3D(pos.getX(), pos.getY(), pos.getZ()));
    }

    @Override
    public NBTTagCompound readColumn(ChunkCoordIntPair pos) throws IOException {
        // we use a true here in order to force creation and caching of the new region, thus avoiding an expensive
        // Files.exists() check for every cube/column (which
        // is really expensive on windows)
        Optional<ByteBuffer> data = this.save.load(new EntryLocation2D(pos.chunkXPos, pos.chunkZPos), true);
        if (!data.isPresent()) return null;

        return decodeColumn(pos, data.get(), null);
    }

    /** As {@link #decodeCube}, for a per-column record or a column group record. */
    private NBTTagCompound decodeColumn(ChunkCoordIntPair pos, ByteBuffer record,
        @org.jetbrains.annotations.Nullable Map<ByteBuffer, ColumnGroupCodec.Stored> decoded) throws IOException {
        if (!bundled || !ColumnGroupCodec.isGroup(record)) return CCNBTUtils.loadTag(record);
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
        // see comment in readColumn
        Optional<ByteBuffer> data = this.save.load(new EntryLocation3D(pos.getX(), pos.getY(), pos.getZ()), true);
        if (!data.isPresent()) return null;

        return decodeCube(pos, data.get(), null);
    }

    /**
     * Decodes a cube from its record: a per-cube NBT record, or a bundle record shared with the other cubes of its
     * bundle. {@code decoded} (if given) reuses bundles already decoded for this batch, keyed by record content.
     */
    private NBTTagCompound decodeCube(CubePos pos, ByteBuffer record,
        @org.jetbrains.annotations.Nullable Map<ByteBuffer, CubeBundleCodec.Stored> decoded) throws IOException {
        if (!bundled || !CubeBundleCodec.isBundle(record)) return CCNBTUtils.loadTag(record);
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
        var columns = this.save
            .load2D(DataUtils.mapToList(positions.columns, c -> new EntryLocation2D(c.chunkXPos, c.chunkZPos)), false);
        var cubes = this.save.load3D(
            DataUtils.mapToList(positions.cubes, c -> new EntryLocation3D(c.getX(), c.getY(), c.getZ())),
            false);
        // a record that cannot be read fails the batch like one that cannot be decoded: left out, its column or cube
        // would load as missing and be generated again, and the next save would overwrite the stored record
        failOnReadErrors(columns.errored, cubes.errored);

        Map<ChunkCoordIntPair, NBTTagCompound> columnTags;
        if (!bundled) {
            columnTags = columns.read.entrySet()
                .parallelStream()
                .map(e -> {
                    try {
                        return Pair.of(
                            new ChunkCoordIntPair(
                                e.getKey()
                                    .getEntryX(),
                                e.getKey()
                                    .getEntryZ()),
                            CCNBTUtils.loadTag(e.getValue()));
                    } catch (IOException ex) {
                        throw new UncheckedIOException(ex);
                    }
                })
                .collect(Collectors.toMap(Pair::left, Pair::right));
        } else {
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
        }

        Map<CubePos, NBTTagCompound> cubeTags;
        if (!bundled) {
            cubeTags = cubes.read.entrySet()
                .parallelStream()
                .map(e -> {
                    try {
                        return Pair.of(
                            new CubePos(
                                e.getKey()
                                    .getEntryX(),
                                e.getKey()
                                    .getEntryY(),
                                e.getKey()
                                    .getEntryZ()),
                            CCNBTUtils.loadTag(e.getValue()));
                    } catch (IOException ex) {
                        throw new UncheckedIOException(ex);
                    }
                })
                .collect(Collectors.toMap(Pair::left, Pair::right));
        } else {
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
        if (bundled) {
            writeColumnGroups(Collections.singletonMap(pos, nbt));
            return;
        }
        ByteBuffer compressed = CCNBTUtils.saveTag(nbt, CubicChunksConfig.chunkCompression);

        // write compressed data to disk
        this.save.save2d(new EntryLocation2D(pos.chunkXPos, pos.chunkZPos), compressed);
    }

    @Override
    public void writeCube(CubePos pos, NBTTagCompound nbt) throws IOException {
        if (bundled) {
            writeBundles(Collections.singletonMap(pos, nbt), Collections.emptyList(), null);
            return;
        }
        ByteBuffer compressed = CCNBTUtils.saveTag(nbt, CubicChunksConfig.chunkCompression);

        // write compressed data to disk
        this.save.save3d(new EntryLocation3D(pos.getX(), pos.getY(), pos.getZ()), compressed);
    }

    @Override
    public void writeBatch(NBTBatch batch) throws IOException {
        writeBatch(batch, null);
    }

    @Override
    public boolean writesBundles() {
        return bundled;
    }

    @Override
    public void writeBatch(NBTBatch batch, @org.jetbrains.annotations.Nullable Map<CubePos, ByteBuffer> written)
        throws IOException {
        if (bundled) {
            // a column group that fails does not stop the cubes of the batch from being written
            IOException failure = null;
            try {
                writeColumnGroups(batch.columns);
            } catch (IOException e) {
                failure = e;
            }
            try {
                writeBundles(batch.cubes, Collections.emptyList(), written);
            } catch (IOException e) {
                if (failure == null) failure = e;
                else failure.addSuppressed(e);
            }
            if (failure != null) throw failure;
            return;
        }
        // compress NBT data
        var compressedColumns = this
            .compressNBTForBatchWrite(batch.columns, pos -> new EntryLocation2D(pos.chunkXPos, pos.chunkZPos));
        // write compressed data to disk
        if (!compressedColumns.isEmpty()) {
            this.save.save2d(compressedColumns);
        }
        var compressedCubes = this
            .compressNBTForBatchWrite(batch.cubes, pos -> new EntryLocation3D(pos.getX(), pos.getY(), pos.getZ()));
        if (!compressedCubes.isEmpty()) {
            // views taken before the region write consumes the buffers
            Map<CubePos, ByteBuffer> records = new HashMap<>();
            if (written != null) compressedCubes.forEach(
                (l, record) -> records
                    .put(new CubePos(l.getEntryX(), l.getEntryY(), l.getEntryZ()), record.asReadOnlyBuffer()));
            this.save.save3d(compressedCubes);
            if (written != null) written.putAll(records);
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
            if (cubeExists(CubeBundleCodec.member(origin, s))) present |= 1 << s;
        }
        removeMask &= ~replaceMask & present;
        // nothing stored changes, e.g. a cube that is compact already is saved again
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
     * and written again, and the header entries of its members then point at the one new record.
     */
    private void writeColumnGroups(Map<ChunkCoordIntPair, NBTTagCompound> columns) throws IOException {
        if (columns.isEmpty()) return;
        Map<ChunkCoordIntPair, NBTTagCompound[]> groups = new HashMap<>();
        for (Map.Entry<ChunkCoordIntPair, NBTTagCompound> e : columns.entrySet()) {
            groups.computeIfAbsent(ColumnGroupCodec.origin(e.getKey()), k -> new NBTTagCompound[4])[ColumnGroupCodec
                .slot(e.getKey())] = e.getValue();
        }
        // Each group is prepared on its own: a group that fails does not stop the others from being written.
        Map<EntryLocation2D, ByteBuffer> entries = new java.util.concurrent.ConcurrentHashMap<>();
        List<IOException> failures = java.util.Collections.synchronizedList(new ArrayList<>());
        groups.entrySet()
            .parallelStream()
            .forEach(group -> {
                Map<EntryLocation2D, ByteBuffer> groupEntries = new HashMap<>();
                try {
                    rewriteColumnGroup(group.getKey(), group.getValue(), groupEntries);
                    entries.putAll(groupEntries);
                } catch (IOException | RuntimeException e) {
                    failures.add(new IOException("Could not write column group " + group.getKey(), e));
                }
            });
        if (!entries.isEmpty()) this.save.save2d(new HashMap<>(entries));
        if (!failures.isEmpty()) {
            IOException failure = failures.get(0);
            for (int i = 1; i < failures.size(); i++) failure.addSuppressed(failures.get(i));
            throw failure;
        }
    }

    private void rewriteColumnGroup(ChunkCoordIntPair origin, NBTTagCompound[] replace,
        Map<EntryLocation2D, ByteBuffer> entries) throws IOException {
        int present = 0, untouched = 0;
        for (int s = 0; s < 4; s++) {
            if (columnExists(ColumnGroupCodec.member(origin, s))) present |= 1 << s;
            if (replace[s] == null) untouched |= 1 << s;
        }
        untouched &= present;

        // members whose stored data cannot be read or decoded keep their record and header as they are
        int keep = 0;
        // stored data of the members that stay as they are, by record (several members usually share one)
        Map<ByteBuffer, Integer> records = new HashMap<>();
        if (untouched != 0) {
            List<EntryLocation2D> keys = new ArrayList<>();
            for (int s = 0; s < 4; s++)
                if ((untouched & 1 << s) != 0) keys.add(location(ColumnGroupCodec.member(origin, s)));
            cubicchunks.regionlib.api.region.BatchReadResult<EntryLocation2D> read;
            try {
                read = this.save.load2D(keys, false);
            } catch (RuntimeException e) {
                // a damaged record can fail the whole read without naming its key; keep every member as it is
                CubicChunks.LOGGER
                    .error("Could not read the stored columns of column group {}; keeping them as they are", origin, e);
                keep |= untouched;
                untouched = 0;
                read = null;
            }
            for (int s = 0; read != null && s < 4; s++) {
                if ((untouched & 1 << s) == 0) continue;
                EntryLocation2D key = location(ColumnGroupCodec.member(origin, s));
                IOException error = read.errored.get(key);
                if (error != null) {
                    CubicChunks.LOGGER.error(
                        "Could not read stored column {} of column group {}; keeping it as it is",
                        ColumnGroupCodec.member(origin, s),
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
        boolean rewritten = false, oversized = false;
        if (only != null && ColumnGroupCodec.isGroup(only.getKey())
            && (ColumnGroupCodec.memberMask(only.getKey()) & only.getValue()) == only.getValue()) {
            // usual case: keep the untouched members' encoded bytes, drop everything else of the old record
            try {
                result = ColumnGroupCodec.rewrite(ColumnGroupCodec.read(only.getKey()), replace, untouched);
                rewritten = true;
            } catch (CubeBundleCodec.OversizedBundleException e) {
                rewritten = oversized = true;
            } catch (IOException | RuntimeException e) {
                CubicChunks.LOGGER.error(
                    "Could not read the stored column group at {}; keeping its other columns as they are",
                    origin,
                    e);
                keep |= only.getValue();
            }
        }
        if (!rewritten) {
            // per-column records (conversion) or members split over several records: encode the group again
            NBTTagCompound[] all = replace.clone();
            for (Map.Entry<ByteBuffer, Integer> record : records.entrySet()) {
                if ((record.getValue() & keep) != 0) continue;
                try {
                    ColumnGroupCodec.Stored stored = ColumnGroupCodec.isGroup(record.getKey())
                        ? ColumnGroupCodec.read(record.getKey())
                        : null;
                    NBTTagCompound[] decoded = new NBTTagCompound[4];
                    for (int s = 0; s < 4; s++) {
                        if ((record.getValue() & 1 << s) == 0) continue;
                        decoded[s] = stored != null ? stored.member(s) : CCNBTUtils.loadTag(record.getKey());
                        if (decoded[s] == null) throw new IOException("Column group record lacks member " + s);
                    }
                    for (int s = 0; s < 4; s++) if (decoded[s] != null) all[s] = decoded[s];
                } catch (IOException | RuntimeException e) {
                    CubicChunks.LOGGER.error(
                        "Could not read stored columns {} of column group {}; keeping them as they are",
                        record.getValue(),
                        origin,
                        e);
                    keep |= record.getValue();
                }
            }
            try {
                result = ColumnGroupCodec.encode(all);
            } catch (CubeBundleCodec.OversizedBundleException e) {
                oversized = true;
            }
        }

        if (oversized || result.remaining() > MAX_SHARED_RECORD) {
            // as for bundles: write only the saved columns, one record each, and leave the others where they are
            for (int s = 0; s < 4; s++) {
                if (replace[s] == null) continue;
                entries.put(
                    location(ColumnGroupCodec.member(origin, s)),
                    CCNBTUtils.saveTag(replace[s], CubicChunksConfig.chunkCompression));
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

    private <KI, KO> Map<KO, ByteBuffer> compressNBTForBatchWrite(Map<KI, NBTTagCompound> nbt,
        Function<KI, KO> keyMappingFunction) throws IOException {
        if (nbt.isEmpty()) { // avoid somewhat expensive stream creation if there are no entries
            return Collections.emptyMap();
        }

        try {
            return nbt.entrySet()
                .parallelStream()
                .collect(Collectors.toMap(entry -> keyMappingFunction.apply(entry.getKey()), entry -> {
                    try {
                        return CCNBTUtils.saveTag(entry.getValue(), CubicChunksConfig.chunkCompression);
                    } catch (IOException e) {
                        // wrap exception so that we can throw it from inside the lambda
                        throw new UncheckedIOException(e);
                    }
                }));
        } catch (UncheckedIOException e) {
            // rethrow original exception
            throw e.getCause();
        }
    }

    @Override
    public void forEachColumn(Consumer<ChunkCoordIntPair> callback) throws IOException {
        this.save.getSaveSection2D()
            .forAllKeys(pos -> callback.accept(new ChunkCoordIntPair(pos.getEntryX(), pos.getEntryZ())));
    }

    @Override
    public void forEachCube(Consumer<CubePos> callback) throws IOException {
        this.save.getSaveSection3D()
            .forAllKeys(pos -> callback.accept(new CubePos(pos.getEntryX(), pos.getEntryY(), pos.getEntryZ())));
    }

    @Override
    public void flush() throws IOException {
        this.save.flush();
    }

    @Override
    public void close() throws IOException {
        if (this.save == null) return;
        if (bundled && cubesWritten.get() > 0) {
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
