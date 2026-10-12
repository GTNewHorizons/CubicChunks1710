package com.cardinalstar.cubicchunks.server.chunkio;

import java.io.IOException;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Future;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.ChunkCoordIntPair;
import net.minecraft.world.chunk.Chunk;
import net.minecraftforge.common.MinecraftForge;

import org.jetbrains.annotations.Nullable;

import com.cardinalstar.cubicchunks.CubicChunks;
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

import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

public class CubeIO implements ICubeIO {

    /**
     * Cached tags are only needed until they're written (saves) or read (preloads), and to serve reloads shortly
     * after. A cube tag is around 10 KB, so they're dropped quickly.
     */
    private static final long EXPIRY = Duration.ofSeconds(10)
        .toMillis();
    /** written and preloaded tags above this are dropped even if they haven't expired, pending saves are kept */
    private static final int MAX_CACHED_COLUMNS = 2048;
    private static final int MAX_CACHED_CUBES = 8192;
    /** limits how many pending saves one eviction can skip over */
    private static final int MAX_EVICTION_STEPS = 256;

    private final ICubicStorage storage;
    private final IPreloadFailureDelegate preloadFailures;
    private final ITaskExecutor<ChunkCoordIntPair, Optional<NBTTagCompound>> columnLoadExecutor;
    private final ITaskExecutor<CubePos, Optional<NBTTagCompound>> cubeLoadExecutor;
    private final ITaskExecutor<Savable, Void> saveExecutor;

    private final Object2ObjectLinkedOpenHashMap<ChunkCoordIntPair, SaveData> columnCache = new Object2ObjectLinkedOpenHashMap<>();

    private final Object2ObjectLinkedOpenHashMap<CubePos, SaveData> cubeCache = new Object2ObjectLinkedOpenHashMap<>();

    interface Savable {
    }

    @Desugar
    private record SaveColumn(ChunkCoordIntPair pos, NBTTagCompound tag) implements Savable {}

    @Desugar
    private record SaveCube(CubePos pos, NBTTagCompound tag) implements Savable {}

    @NoArgsConstructor
    @AllArgsConstructor
    private static class SaveData {

        @Nullable
        public NBTTagCompound tag;
        @Nullable
        public Future<?> task;
        public long lastAccess;
    }

    public CubeIO(ICubicStorage storage, IPreloadFailureDelegate preloadFailures) {
        this.storage = storage;
        this.preloadFailures = preloadFailures;

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

                IOException ex = null;

                try {
                    storage.writeBatch(new ICubicStorage.NBTBatch(columns, cubes));
                } catch (IOException e) {
                    CubicChunks.LOGGER.error("Could not save columns or cubes", e);
                    ex = e;
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
            public boolean canMerge(List<ITaskFuture<Savable, Void>> tasks, Savable savable) {
                return true;
            }
        };
    }

    @Override
    public boolean columnExists(ChunkCoordIntPair pos) {
        synchronized (columnCache) {
            SaveData data = columnCache.get(pos);

            if (data != null) return data.tag != null;
        }

        try {
            if (storage.columnExists(pos)) return true;
        } catch (IOException e) {
            CubicChunks.LOGGER.error("Could not check if column {} exists", pos, e);
        }

        return false;
    }

    @Override
    public boolean cubeExists(CubePos pos) {
        synchronized (cubeCache) {
            SaveData data = cubeCache.get(pos);

            if (data != null) return data.tag != null;
        }

        try {
            if (storage.cubeExists(pos)) return true;
        } catch (IOException e) {
            CubicChunks.LOGGER.error("Could not check if cube {} exists", pos, e);
        }

        return false;
    }

    @Override
    public NBTTagCompound loadColumn(ChunkCoordIntPair pos) throws LoadFailureException {
        synchronized (columnCache) {
            SaveData data = columnCache.get(pos);

            if (data != null) {
                return consume(columnCache, pos, data);
            }
        }

        try {
            NBTTagCompound tag = storage.readColumn(pos);

            synchronized (columnCache) {
                SaveData cached = columnCache.get(pos);
                if (cached != null) {
                    return cached.tag == null ? null : (NBTTagCompound) cached.tag.copy();
                }

                if (tag == null) {
                    columnCache.put(pos, new SaveData(null, null, System.currentTimeMillis()));
                }
            }

            return tag;
        } catch (IOException e) {
            CubicChunks.LOGGER.error("Could not read column {}", pos, e);
            throw new LoadFailureException("Could not read column", e);
        }
    }

    @Override
    public NBTTagCompound loadCube(CubePos pos) throws LoadFailureException {
        synchronized (cubeCache) {
            SaveData data = cubeCache.get(pos);

            if (data != null) {
                return consume(cubeCache, pos, data);
            }
        }

        try {
            NBTTagCompound tag = storage.readCube(pos);

            synchronized (cubeCache) {
                SaveData cached = cubeCache.get(pos);
                if (cached != null) {
                    return cached.tag == null ? null : (NBTTagCompound) cached.tag.copy();
                }

                if (tag == null) {
                    cubeCache.put(pos, new SaveData(null, null, System.currentTimeMillis()));
                }
            }

            return tag;
        } catch (IOException e) {
            CubicChunks.LOGGER.error("Could not read cube {}", pos, e);
            throw new LoadFailureException("Could not read cube", e);
        }
    }

    /**
     * The loaded column or cube holds the data from now on, so the entry is removed unless its save is still pending
     * (it must keep answering reads until it's written). Must hold the cache's lock.
     *
     * @return the cached tag, null if the column or cube doesn't exist
     */
    @Nullable
    private static <K> NBTTagCompound consume(Object2ObjectLinkedOpenHashMap<K, SaveData> cache, K pos,
        SaveData data) {
        if (data.task != null && !data.task.isDone()) {
            return data.tag == null ? null : (NBTTagCompound) data.tag.copy();
        }

        // nothing else references the tag anymore
        cache.remove(pos);
        return data.tag;
    }

    /**
     * Removes expired entries, and the oldest ones while the cache is above its max size. Pending saves are kept.
     * Entries are ordered by age, so this stops at the first one that doesn't need removing. Must hold the cache's
     * lock.
     */
    private static <K> void evict(Object2ObjectLinkedOpenHashMap<K, SaveData> cache, int maxSize, long now) {
        var iter = cache.object2ObjectEntrySet()
            .fastIterator();

        for (int i = 0; i < MAX_EVICTION_STEPS && iter.hasNext(); i++) {
            SaveData data = iter.next()
                .getValue();

            if (data.lastAccess + EXPIRY >= now && cache.size() <= maxSize) break;

            if (data.task != null && data.task.isDone()) {
                data.task = null;
            }

            if (data.task == null) {
                iter.remove();
            }
        }
    }

    public void saveColumn(ChunkCoordIntPair pos, Chunk column) {
        // NOTE: this function blocks the world thread
        // make it as fast as possible by offloading processing to the IO thread
        // except we have to write the NBT in this thread to avoid problems
        // with concurrent access to world data structures

        // add the column to the save queue
        NBTTagCompound tag = IONbtWriter.write(column);

        MinecraftForge.EVENT_BUS.post(new ColumnEvent.SaveNBT(column.worldObj, column, tag));

        column.isModified = false;

        Future<?> task = TaskPool.submit(saveExecutor, new SaveColumn(pos, tag));

        long now = System.currentTimeMillis();

        synchronized (columnCache) {
            columnCache.putAndMoveToLast(pos, new SaveData(tag, task, now));

            evict(columnCache, MAX_CACHED_COLUMNS, now);
        }
    }

    public void saveCube(CubePos pos, Cube cube) {
        cube.markSaved();

        NBTTagCompound tag = IONbtWriter.write(cube);

        MinecraftForge.EVENT_BUS.post(new CubeEvent.SaveNBT(cube.getWorld(), cube, tag));

        Future<?> task = TaskPool.submit(saveExecutor, new SaveCube(pos, tag));

        long now = System.currentTimeMillis();

        synchronized (cubeCache) {
            cubeCache.putAndMoveToLast(pos, new SaveData(tag, task, now));

            evict(cubeCache, MAX_CACHED_CUBES, now);
        }
    }

    // only used by "/save-all flush" command
    @Override
    public void flush() throws IOException {
        TaskPool.flush();

        this.storage.flush();
    }

    @Override
    public void close() throws IOException {
        TaskPool.flush();

        while (true) {
            synchronized (columnCache) {
                if (columnCache.isEmpty()) break;

                columnCache.object2ObjectEntrySet()
                    .removeIf(e -> e.getValue().task == null || e.getValue().task.isDone());
            }

            Thread.yield();
        }

        while (true) {
            synchronized (cubeCache) {
                if (cubeCache.isEmpty()) break;

                cubeCache.object2ObjectEntrySet()
                    .removeIf(e -> e.getValue().task == null || e.getValue().task.isDone());
            }

            Thread.yield();
        }

        this.storage.close();
    }

    @Override
    public void preloadColumn(ChunkCoordIntPair pos) {
        synchronized (columnCache) {
            if (columnCache.containsKey(pos)) return;
        }

        TaskPool.submit(columnLoadExecutor, pos, tag -> {
            synchronized (columnCache) {
                if (columnCache.containsKey(pos)) {
                    return;
                }

                long now = System.currentTimeMillis();
                columnCache.put(pos, new SaveData(tag.orElse(null), null, now));

                evict(columnCache, MAX_CACHED_COLUMNS, now);
            }

            if (!tag.isPresent()) {
                if (preloadFailures != null) preloadFailures.onColumnPreloadFailed(pos);
            }
        });
    }

    @Override
    public void preloadCube(CubePos pos, CubeInitLevel wanted) {
        synchronized (cubeCache) {
            if (cubeCache.containsKey(pos)) return;
        }

        TaskPool.submit(cubeLoadExecutor, pos, tag -> {
            synchronized (cubeCache) {
                if (cubeCache.containsKey(pos)) {
                    return;
                }

                long now = System.currentTimeMillis();
                cubeCache.put(pos, new SaveData(tag.orElse(null), null, now));

                evict(cubeCache, MAX_CACHED_CUBES, now);
            }

            CubeInitLevel actual = !tag.isPresent() ? CubeInitLevel.None : IONbtReader.getCubeInitLevel(tag.get());

            if (actual.ordinal() < wanted.ordinal()) {
                if (preloadFailures != null) {
                    preloadFailures.onCubePreloadFailed(pos, CubeInitLevel.None, wanted);
                }
            }
        });
    }
}
