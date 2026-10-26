package com.cardinalstar.cubicchunks.server.chunkio;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.ChunkCoordIntPair;
import net.minecraft.world.chunk.Chunk;
import net.minecraftforge.common.MinecraftForge;

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

    private final ICubicStorage storage;
    private final IPreloadFailureDelegate preloadFailures;
    private final ITaskExecutor<ChunkCoordIntPair, Optional<NBTTagCompound>> columnLoadExecutor;
    private final ITaskExecutor<CubePos, Optional<NBTTagCompound>> cubeLoadExecutor;
    private final ITaskExecutor<Savable, Void> saveExecutor;

    private final IOCache<ChunkCoordIntPair> columnCache;
    private final IOCache<CubePos> cubeCache;

    interface Savable {
    }

    @Desugar
    private record SaveColumn(ChunkCoordIntPair pos, NBTTagCompound tag) implements Savable {}

    @Desugar
    private record SaveCube(CubePos pos, NBTTagCompound tag) implements Savable {}

    public CubeIO(ICubicStorage storage, IPreloadFailureDelegate preloadFailures) {
        this.storage = storage;
        this.preloadFailures = preloadFailures;
        long cubeBudget = (long) Math.max(0, CubicChunksConfig.optimizations.saveCacheMegabytes) << 20;
        this.cubeCache = new IOCache<>(cubeBudget, PRELOAD_EXPIRY_MILLIS, SAVE_FENCE_MILLIS);
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
                    }

                    storage.writeBatch(new ICubicStorage.NBTBatch(columns, cubes));
                } catch (IOException e) {
                    CubicChunks.LOGGER.error("Could not save columns or cubes", e);
                    ex = e;
                } finally {
                    // also after a failure: the data stays readable from the cache until it is evicted. Settles every
                    // queued save; one replaced by a newer save in this batch is ignored by the cache.
                    long now = System.currentTimeMillis();
                    for (ITaskFuture<Savable, Void> task : tasks) {
                        Savable savable = task.getTask();
                        if (savable instanceof SaveColumn column) settle(columnCache, column.pos, column.tag, now);
                        if (savable instanceof SaveCube cube) settle(cubeCache, cube.pos, cube.tag, now);
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
        Optional<NBTTagCompound> cached = cubeCache.take(pos);
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

        TaskPool.submit(saveExecutor, new SaveCube(pos, tag));
    }

    /// Waits until every queued save has been handed to storage and its write has finished.
    private void awaitWrites() {
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
