package com.cardinalstar.cubicchunks.server.chunkio;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.ChunkCoordIntPair;
import net.minecraft.world.chunk.Chunk;
import net.minecraftforge.common.MinecraftForge;

import org.jetbrains.annotations.Nullable;

import com.cardinalstar.cubicchunks.CubicChunks;
import com.cardinalstar.cubicchunks.CubicChunksConfig;
import com.cardinalstar.cubicchunks.api.event.ColumnEvent;
import com.cardinalstar.cubicchunks.api.event.CubeEvent;
import com.cardinalstar.cubicchunks.api.world.storage.ICubicStorage;
import com.cardinalstar.cubicchunks.api.world.storage.ICubicStorage.PosBatch;
import com.cardinalstar.cubicchunks.async.TaskPool;
import com.cardinalstar.cubicchunks.async.TaskPool.ITaskExecutor;
import com.cardinalstar.cubicchunks.async.TaskPool.ITaskFuture;
import com.cardinalstar.cubicchunks.util.CubePos;
import com.cardinalstar.cubicchunks.util.DataUtils;
import com.cardinalstar.cubicchunks.world.cube.Cube;
import com.github.bsideup.jabel.Desugar;

import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;

public class CubeIO implements ICubeIO {

    // read-ahead data is normally taken within ticks; drop it if the load was cancelled (it is still on disk)
    private static final long PRELOAD_EXPIRY_MILLIS = 30_000;
    // how long a save is remembered to refuse reads that started before it (the old cache kept saves this long)
    private static final long SAVE_FENCE_MILLIS = 120_000;
    // how many entries a single save may check when trimming, to keep saves fast
    private static final int SAVE_TRIM_CHECKS = 100;
    // unloaded cubes whose saved data may wait for the rest of their bundle at once; beyond it the oldest are written
    // on the next tick
    private static final int MAX_WAITING_CUBES = 4096;

    private final ICubicStorage storage;
    private final IPreloadFailureDelegate preloadFailures;
    private final ITaskExecutor<ChunkCoordIntPair, Optional<NBTTagCompound>> columnLoadExecutor;
    private final ITaskExecutor<CubePos, Optional<NBTTagCompound>> cubeLoadExecutor;
    private final ITaskExecutor<Savable, Void> saveExecutor;

    private final IOCache<ChunkCoordIntPair> columnCache;
    private final IOCache<CubePos> cubeCache;

    /** Storage that reports the records it wrote, which the cube cache then keeps instead of packing the data again. */
    @Nullable
    private final RecordingCubeStorage recording;
    /** Whether a cube is loaded; saves of unloaded cubes wait for the other loaded cubes of their bundle. */
    @Nullable
    private final Predicate<CubePos> loaded;
    private final long writeWaitMillis;
    /** Saves of unloaded cubes waiting for the rest of their bundle, by bundle origin, oldest first. Server thread. */
    private final Map<CubePos, Waiting> waiting = new java.util.LinkedHashMap<>();
    /** Waiting bundles with no other cube loaded when their last save arrived, written on the next tick. */
    private final Set<CubePos> complete = new HashSet<>();
    private int waitingCubes;

    private static final class Waiting {

        /** {@link #waitClock()} at the bundle's first waiting save. */
        final long since;
        final Map<CubePos, NBTTagCompound> saves = new java.util.LinkedHashMap<>();

        Waiting(long since) {
            this.since = since;
        }
    }

    interface Savable {
    }

    @Desugar
    private record SaveColumn(ChunkCoordIntPair pos, NBTTagCompound tag) implements Savable {}

    @Desugar
    private record SaveCube(CubePos pos, NBTTagCompound tag) implements Savable {}

    /** Saves that waited for their bundles, queued as one task so that each bundle reaches storage in one batch. */
    @Desugar
    private record SaveCubes(Map<CubePos, NBTTagCompound> saves) implements Savable {}

    public CubeIO(ICubicStorage storage, IPreloadFailureDelegate preloadFailures) {
        this(storage, preloadFailures, null);
    }

    /** {@code loaded} enables waiting for the rest of a cube bundle before writing (see {@link #tick()}). */
    public CubeIO(ICubicStorage storage, IPreloadFailureDelegate preloadFailures, @Nullable Predicate<CubePos> loaded) {
        this.storage = storage;
        this.preloadFailures = preloadFailures;
        this.recording = storage instanceof RecordingCubeStorage r ? r : null;
        this.loaded = loaded;
        this.writeWaitMillis = recording != null && recording.writesBundles() && loaded != null
            ? Math.max(0, CubicChunksConfig.optimizations.bundleWriteWaitSeconds) * 1000L
            : 0;
        long cubeBudget = (long) Math.max(0, CubicChunksConfig.optimizations.saveCacheMegabytes) << 20;
        this.cubeCache = new IOCache<>(
            cubeBudget,
            PRELOAD_EXPIRY_MILLIS,
            SAVE_FENCE_MILLIS,
            recording != null ? CubeIO::decodeCubes : null);
        this.columnCache = new IOCache<>(cubeBudget / 8, PRELOAD_EXPIRY_MILLIS, SAVE_FENCE_MILLIS);

        columnLoadExecutor = tasks -> {
            try {
                var result = storage
                    .readBatch(new PosBatch(DataUtils.mapToList(tasks, ITaskFuture::getTask), Collections.emptyList()));

                for (var task : tasks) {
                    NBTTagCompound tag = result.columns.get(task.getTask());

                    task.finish(Optional.ofNullable(tag));
                }
            } catch (IOException e) {
                for (var task : tasks) {
                    task.fail(e);
                }
            }
        };

        cubeLoadExecutor = tasks -> {
            try {
                var result = storage
                    .readBatch(new PosBatch(Collections.emptyList(), DataUtils.mapToList(tasks, ITaskFuture::getTask)));

                for (var task : tasks) {
                    NBTTagCompound tag = result.cubes.get(task.getTask());

                    task.finish(Optional.ofNullable(tag));
                }
            } catch (IOException e) {
                for (var task : tasks) {
                    task.fail(e);
                }
            }
        };

        saveExecutor = new ITaskExecutor<>() {

            @Override
            public void execute(List<ITaskFuture<Savable, Void>> tasks) {
                IOException ex = null;
                // records of the cubes that reached storage (null for compact ones), filled by recording storage
                Map<CubePos, ByteBuffer> written = recording != null ? new java.util.HashMap<>() : null;

                try {
                    Map<ChunkCoordIntPair, NBTTagCompound> columns = new Object2ObjectOpenHashMap<>();
                    Map<CubePos, NBTTagCompound> cubes = new Object2ObjectOpenHashMap<>();

                    for (ITaskFuture<Savable, Void> task : tasks) {
                        Savable savable = task.getTask();

                        if (savable instanceof SaveColumn column) {
                            columns.put(column.pos, column.tag);
                        }

                        if (savable instanceof SaveCube cube) {
                            cubes.put(cube.pos, cube.tag);
                        }

                        if (savable instanceof SaveCubes waited) {
                            cubes.putAll(waited.saves);
                        }
                    }

                    ICubicStorage.NBTBatch batch = new ICubicStorage.NBTBatch(columns, cubes);
                    if (recording != null) recording.writeBatch(batch, written);
                    else storage.writeBatch(batch);
                } catch (IOException e) {
                    CubicChunks.LOGGER.error("Could not save columns or cubes", e);
                    ex = e;
                } finally {
                    // also after a failure: the data stays readable from the cache until it is evicted. Settles every
                    // queued save; one replaced by a newer save in this batch is ignored by the cache.
                    long now = System.currentTimeMillis();
                    // one cache record per written record: the cubes of a bundle share theirs
                    Map<ByteBuffer, IOCache.Record> records = new java.util.IdentityHashMap<>();
                    for (ITaskFuture<Savable, Void> task : tasks) {
                        Savable savable = task.getTask();
                        if (savable instanceof SaveColumn column) settle(columnCache, column.pos, column.tag, now);
                        if (savable instanceof SaveCube cube) settleCube(cube.pos, cube.tag, now, written, records);
                        if (savable instanceof SaveCubes waited) {
                            for (Map.Entry<CubePos, NBTTagCompound> save : waited.saves.entrySet()) {
                                settleCube(save.getKey(), save.getValue(), now, written, records);
                            }
                        }
                    }
                }

                for (ITaskFuture<Savable, Void> task : tasks) {
                    if (ex != null) {
                        task.fail(ex);
                    } else {
                        task.finish(null);
                    }
                }
            }

            @Override
            public boolean requiresSerialExecution() {
                // a later save of a position must reach storage after an earlier one
                return true;
            }

            @Override
            public boolean canMerge(List<ITaskFuture<Savable, Void>> tasks, Savable savable) {
                return true;
            }
        };
    }

    /// Ends a pending cube save, keeping the record storage reported for it, or a packed copy if there is none. Must
    /// not throw, like settle.
    private void settleCube(CubePos pos, NBTTagCompound tag, long now, @Nullable Map<CubePos, ByteBuffer> written,
        Map<ByteBuffer, IOCache.Record> records) {
        if (written == null || !written.containsKey(pos)) {
            settle(cubeCache, pos, tag, now);
            return;
        }
        try {
            ByteBuffer stored = written.get(pos);
            // only a save that stays cached needs the record, which may have to be copied
            IOCache.Record record = stored != null && cubeCache.keepsRecord(pos, tag)
                ? records.computeIfAbsent(stored, IOCache.Record::new)
                : null;
            cubeCache.writtenAs(pos, tag, now, record);
        } catch (Throwable t) {
            CubicChunks.LOGGER.error("Could not cache saved data for {}", pos, t);
            cubeCache.discardPending(pos, tag);
        }
    }

    /// Every cube stored in a record: the present members of a bundle record, or the one cube of a per-cube record.
    private static Map<CubePos, NBTTagCompound> decodeCubes(CubePos pos, ByteBuffer record) throws IOException {
        Map<CubePos, NBTTagCompound> out = new java.util.HashMap<>();
        if (CubeBundleCodec.isBundle(record)) {
            CubeBundleCodec.Stored stored = CubeBundleCodec.read(record);
            CubePos origin = CubeBundleCodec.origin(pos);
            for (int s = 0; s < 8; s++) {
                NBTTagCompound member = stored.member(s);
                if (member != null) out.put(CubeBundleCodec.member(origin, s), member);
            }
        } else {
            out.put(pos, CCNBTUtils.loadTag(record));
        }
        return out;
    }

    /// Ends a pending save. Must not throw: a save left pending would make flush() and close() wait forever.
    private static <K> void settle(IOCache<K> cache, K pos, NBTTagCompound tag, long now) {
        try {
            cache.written(pos, tag, now);
        } catch (Throwable t) {
            CubicChunks.LOGGER.error("Could not cache saved data for {}", pos, t);
            cache.discardPending(pos, tag);
        }
    }

    @Override
    public boolean columnExists(ChunkCoordIntPair pos) {
        Boolean cached = columnCache.exists(pos);
        if (cached != null) return cached;

        try {
            if (storage.columnExists(pos)) return true;
        } catch (IOException e) {
            CubicChunks.LOGGER.error("Could not check if column {} exists", pos, e);
        }

        return false;
    }

    @Override
    public boolean cubeExists(CubePos pos) {
        Boolean cached = cubeCache.exists(pos);
        if (cached != null) return cached;

        try {
            if (storage.cubeExists(pos)) return true;
        } catch (IOException e) {
            CubicChunks.LOGGER.error("Could not check if cube {} exists", pos, e);
        }

        return false;
    }

    @Override
    public NBTTagCompound loadColumn(ChunkCoordIntPair pos) throws LoadFailureException {
        Optional<NBTTagCompound> cached = columnCache.take(pos);
        if (cached != null) return cached.orElse(null);

        try {
            long fence = columnCache.fence();
            NBTTagCompound tag = storage.readColumn(pos);

            // a save that was queued while reading is newer than what was read
            cached = columnCache.take(pos);
            if (cached != null) return cached.orElse(null);
            // ... and if its write already finished and its entry is gone, storage has it now
            if (columnCache.savedSince(pos, fence)) tag = storage.readColumn(pos);

            if (tag == null) columnCache.putAbsent(pos, System.currentTimeMillis());

            return tag;
        } catch (IOException e) {
            CubicChunks.LOGGER.error("Could not read column {}", pos, e);
            throw new LoadFailureException("Could not read column", e);
        }
    }

    @Override
    public NBTTagCompound loadCube(CubePos pos) throws LoadFailureException {
        Optional<NBTTagCompound> cached = cubeCache.take(pos, System.currentTimeMillis());
        if (cached != null) return cached.orElse(null);

        try {
            long fence = cubeCache.fence();
            NBTTagCompound tag = storage.readCube(pos);

            // a save that was queued while reading is newer than what was read
            cached = cubeCache.take(pos);
            if (cached != null) return cached.orElse(null);
            // ... and if its write already finished and its entry is gone, storage has it now
            if (cubeCache.savedSince(pos, fence)) tag = storage.readCube(pos);

            if (tag == null) cubeCache.putAbsent(pos, System.currentTimeMillis());

            return tag;
        } catch (IOException e) {
            CubicChunks.LOGGER.error("Could not read cube {}", pos, e);
            throw new LoadFailureException("Could not read cube", e);
        }
    }

    /// Trims both caches fully. Called periodically, because trimming on save alone never runs when nothing is saved.
    @Override
    public void trimCaches() {
        long now = System.currentTimeMillis();

        columnCache.trim(now, Integer.MAX_VALUE);
        cubeCache.trim(now, Integer.MAX_VALUE);
    }

    @Override
    public void saveColumn(ChunkCoordIntPair pos, Chunk column, boolean unloading) {
        // NOTE: this function blocks the world thread
        // make it as fast as possible by offloading processing to the IO thread
        // except we have to write the NBT in this thread to avoid problems
        // with concurrent access to world data structures

        // add the column to the save queue
        NBTTagCompound tag = IONbtWriter.write(column);

        MinecraftForge.EVENT_BUS.post(new ColumnEvent.SaveNBT(column.worldObj, column, tag));

        column.isModified = false;

        long now = System.currentTimeMillis();

        // cached before queueing, so the write cannot finish before its entry exists
        columnCache.putPending(pos, tag, unloading, now);
        columnCache.trim(now, SAVE_TRIM_CHECKS);

        TaskPool.submit(saveExecutor, new SaveColumn(pos, tag));
    }

    @Override
    public void saveCube(CubePos pos, Cube cube, boolean unloading) {
        cube.markSaved();

        NBTTagCompound tag = IONbtWriter.write(cube);

        MinecraftForge.EVENT_BUS.post(new CubeEvent.SaveNBT(cube.getWorld(), cube, tag));

        long now = System.currentTimeMillis();

        // cached before queueing, so the write cannot finish before its entry exists
        cubeCache.putPending(pos, tag, unloading, now);
        cubeCache.trim(now, SAVE_TRIM_CHECKS);

        CubePos origin = CubeBundleCodec.origin(pos);
        Waiting w = waiting.get(origin);
        if (unloading && writeWaitMillis > 0) {
            // Saves of loaded cubes are written now: their NBT shares block arrays with the live cube. An unloaded
            // cube's data can wait for the other cubes of its bundle, which are then written in one record. It
            // replaces an earlier waiting save of the cube, and the bundle keeps the time its wait began.
            if (w == null) waiting.put(origin, w = new Waiting(waitClock()));
            if (w.saves.put(pos, tag) == null) waitingCubes++;
            // unloading saves a cube, so the last loaded cube of a bundle finds the others unloaded here; one unloaded
            // without a save leaves its bundle waiting for the time limit
            if (!otherCubesLoaded(origin, w)) complete.add(origin);
            return;
        }
        // an earlier save of this cube that is still waiting is replaced by this one, written now
        if (w != null && w.saves.remove(pos) != null) {
            waitingCubes--;
            if (w.saves.isEmpty()) {
                waiting.remove(origin);
                complete.remove(origin);
            }
        }

        TaskPool.submit(saveExecutor, new SaveCube(pos, tag));
    }

    /**
     * Queues as one task the waiting saves of complete bundles, of bundles that waited {@link #writeWaitMillis} and,
     * beyond {@link #MAX_WAITING_CUBES}, of the oldest others. With {@code all}, every waiting save.
     */
    private void writeWaiting(boolean all) {
        Map<CubePos, NBTTagCompound> ready = new java.util.LinkedHashMap<>();
        for (CubePos origin : complete) {
            Waiting w = waiting.remove(origin);
            if (w == null) continue;
            waitingCubes -= w.saves.size();
            ready.putAll(w.saves);
        }
        complete.clear();
        long now = waitClock();
        // oldest first: a bundle's entry is added with its first waiting save and never moved
        var bundles = waiting.values()
            .iterator();
        while (bundles.hasNext()) {
            Waiting w = bundles.next();
            if (!all && waitingCubes <= MAX_WAITING_CUBES && now - w.since < writeWaitMillis) break;
            bundles.remove();
            waitingCubes -= w.saves.size();
            ready.putAll(w.saves);
        }
        if (!ready.isEmpty()) TaskPool.submit(saveExecutor, new SaveCubes(ready));
    }

    /** Milliseconds for the write wait, unaffected by wall clock changes. */
    private static long waitClock() {
        return System.nanoTime() / 1_000_000;
    }

    private boolean otherCubesLoaded(CubePos origin, Waiting w) {
        for (int s = 0; s < 8; s++) {
            CubePos member = CubeBundleCodec.member(origin, s);
            if (!w.saves.containsKey(member) && loaded.test(member)) return true;
        }
        return false;
    }

    @Override
    public void tick() {
        if (!waiting.isEmpty()) writeWaiting(false);
    }

    /// Waits until every queued save has been handed to storage and its write has finished.
    private void awaitWrites() {
        writeWaiting(true);
        TaskPool.flush();

        while (columnCache.pendingCount() > 0 || cubeCache.pendingCount() > 0) {
            Thread.yield();
        }
    }

    // only used by "/save-all flush" command
    @Override
    public void flush() throws IOException {
        awaitWrites();

        this.storage.flush();
    }

    @Override
    public void close() throws IOException {
        awaitWrites();

        this.storage.close();
    }

    @Override
    public void preloadColumn(ChunkCoordIntPair pos) {
        if (columnCache.contains(pos)) return;
        long fence = columnCache.fence();

        TaskPool.submit(columnLoadExecutor, pos, tag -> {
            // something newer (e.g. a save) was cached or saved meanwhile
            if (!columnCache.putPreloaded(pos, tag.orElse(null), fence, System.currentTimeMillis())) return;

            if (!tag.isPresent()) {
                if (preloadFailures != null) preloadFailures.onColumnPreloadFailed(pos);
            }
        });
    }

    @Override
    public void preloadCube(CubePos pos, CubeInitLevel wanted) {
        if (cubeCache.contains(pos)) return;
        long fence = cubeCache.fence();

        TaskPool.submit(cubeLoadExecutor, pos, tag -> {
            // read before handing the tag over: once cached, the server thread may take and change it
            CubeInitLevel actual = !tag.isPresent() ? CubeInitLevel.None : IONbtReader.getCubeInitLevel(tag.get());

            // something newer (e.g. a save) was cached or saved meanwhile
            if (!cubeCache.putPreloaded(pos, tag.orElse(null), fence, System.currentTimeMillis())) return;

            if (actual.ordinal() < wanted.ordinal()) {
                if (preloadFailures != null) {
                    preloadFailures.onCubePreloadFailed(pos, CubeInitLevel.None, wanted);
                }
            }
        });
    }
}
