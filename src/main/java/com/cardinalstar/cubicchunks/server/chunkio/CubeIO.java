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
import com.cardinalstar.cubicchunks.api.world.storage.ICubicStorage;
import com.cardinalstar.cubicchunks.api.world.storage.ICubicStorage.PosBatch;
import com.cardinalstar.cubicchunks.async.TaskPool;
import com.cardinalstar.cubicchunks.async.TaskPool.ITaskExecutor;
import com.cardinalstar.cubicchunks.async.TaskPool.ITaskFuture;
import com.cardinalstar.cubicchunks.api.event.CubeEvent;
import com.cardinalstar.cubicchunks.util.CubePos;
import com.cardinalstar.cubicchunks.util.DataUtils;
import com.cardinalstar.cubicchunks.world.cube.Cube;
import com.github.bsideup.jabel.Desugar;

import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

public class CubeIO implements ICubeIO {

    private static final long EXPIRY = Duration.ofSeconds(120)
        .toMillis();

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
                return data.tag == null ? null : (NBTTagCompound) data.tag.copy();
            }
        }

        try {
            NBTTagCompound tag = storage.readColumn(pos);

            if (tag == null) {
                synchronized (columnCache) {
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
                return data.tag == null ? null : (NBTTagCompound) data.tag.copy();
            }
        }

        try {
            NBTTagCompound tag = storage.readCube(pos);

            if (tag == null) {
                synchronized (cubeCache) {
                    cubeCache.put(pos, new SaveData(null, null, System.currentTimeMillis()));
                }
            }

            return tag;
        } catch (IOException e) {
            CubicChunks.LOGGER.error("Could not read cube {}", pos, e);
            throw new LoadFailureException("Could not read cube", e);
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
            columnCache.put(pos, new SaveData(tag, task, now));

            if (columnCache.size() > 5000) {
                int i = 0;

                var iter = columnCache.object2ObjectEntrySet()
                    .fastIterator();

                while (columnCache.size() > 5000 && i++ < 100) {
                    var e = iter.next();

                    SaveData data = e.getValue();

                    if (data.task != null && data.task.isDone()) {
                        data.task = null;
                    }

                    if (data.task == null && data.lastAccess + EXPIRY < now) {
                        iter.remove();
                    }
                }
            }
        }
    }

    public void saveCube(CubePos pos, Cube cube) {
        cube.markSaved();

        NBTTagCompound tag = IONbtWriter.write(cube);

        MinecraftForge.EVENT_BUS.post(new CubeEvent.SaveNBT(cube.getWorld(), cube, tag));

        Future<?> task = TaskPool.submit(saveExecutor, new SaveCube(pos, tag));

        long now = System.currentTimeMillis();

        synchronized (cubeCache) {
            cubeCache.put(pos, new SaveData(tag, task, now));

            if (cubeCache.size() > 30000) {
                int i = 0;

                var iter = cubeCache.object2ObjectEntrySet()
                    .fastIterator();

                while (cubeCache.size() > 30000 && i++ < 100) {
                    var e = iter.next();

                    SaveData data = e.getValue();

                    if (data.task != null && data.task.isDone()) {
                        data.task = null;
                    }

                    if (data.task == null && data.lastAccess + EXPIRY < now) {
                        iter.remove();
                    }
                }
            }
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
        TaskPool.submit(columnLoadExecutor, pos, tag -> {
            if (!tag.isPresent()) {
                if (preloadFailures != null) preloadFailures.onColumnPreloadFailed(pos);
            } else {
                synchronized (columnCache) {
                    columnCache.put(pos, new SaveData(tag.orElse(null), null, System.currentTimeMillis()));
                }
            }
        });
    }

    @Override
    public void preloadCube(CubePos pos, CubeInitLevel wanted) {
        TaskPool.submit(cubeLoadExecutor, pos, tag -> {
            CubeInitLevel actual = !tag.isPresent() ? CubeInitLevel.None : IONbtReader.getCubeInitLevel(tag.get());

            if (actual.ordinal() < wanted.ordinal()) {
                if (preloadFailures != null) {
                    preloadFailures.onCubePreloadFailed(pos, CubeInitLevel.None, wanted);
                }
            }

            synchronized (cubeCache) {
                cubeCache.put(pos, new SaveData(tag.orElse(null), null, System.currentTimeMillis()));
            }
        });
    }
}
