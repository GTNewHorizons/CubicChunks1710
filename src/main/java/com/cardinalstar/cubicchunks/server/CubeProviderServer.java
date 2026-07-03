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
package com.cardinalstar.cubicchunks.server;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import javax.annotation.Detainted;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;

import net.minecraft.entity.EnumCreatureType;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.profiler.Profiler;
import net.minecraft.util.IProgressUpdate;
import net.minecraft.world.ChunkCoordIntPair;
import net.minecraft.world.WorldServer;
import net.minecraft.world.biome.BiomeGenBase;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.storage.IChunkLoader;
import net.minecraft.world.gen.ChunkProviderServer;

import com.cardinalstar.cubicchunks.CubicChunks;
import com.cardinalstar.cubicchunks.api.IColumn;
import com.cardinalstar.cubicchunks.api.ICube;
import com.cardinalstar.cubicchunks.api.XYZAddressable;
import com.cardinalstar.cubicchunks.api.worldgen.IWorldGenerator;
import com.cardinalstar.cubicchunks.api.worldgen.hwaccel.AcceleratableWorldGenerator;
import com.cardinalstar.cubicchunks.api.worldgen.hwaccel.ComputePlan;
import com.cardinalstar.cubicchunks.api.worldgen.hwaccel.KernelContext;
import com.cardinalstar.cubicchunks.api.worldgen.hwaccel.KernelScheduler;
import com.cardinalstar.cubicchunks.server.chunkio.CubeInitLevel;
import com.cardinalstar.cubicchunks.server.chunkio.CubeLoaderCallback;
import com.cardinalstar.cubicchunks.server.chunkio.CubeLoaderServer;
import com.cardinalstar.cubicchunks.server.chunkio.ICubeLoader;
import com.cardinalstar.cubicchunks.util.CubePos;
import com.cardinalstar.cubicchunks.util.CubeStatusVisualizer;
import com.cardinalstar.cubicchunks.util.CubeStatusVisualizer.CubeStatus;
import com.cardinalstar.cubicchunks.util.XZAddressable;
import com.cardinalstar.cubicchunks.world.api.ICubeProviderServer;
import com.cardinalstar.cubicchunks.world.column.EmptyColumn;
import com.cardinalstar.cubicchunks.world.cube.BlankCube;
import com.cardinalstar.cubicchunks.world.cube.Cube;
import com.cardinalstar.cubicchunks.world.cube.ICubeProviderInternal;
import com.cardinalstar.cubicchunks.world.savedata.WorldFormatSavedData;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.MultimapBuilder;
import it.unimi.dsi.fastutil.ints.Int2ObjectRBTreeMap;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntComparator;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectLinkedOpenHashSet;
import lombok.Getter;
import lombok.Setter;

/**
 * This is CubicChunks equivalent of ChunkProviderServer, it loads and unloads Cubes and Columns.
 * <p>
 * There are a few necessary changes to the way vanilla methods work:
 * * Because loading a Chunk (Column) doesn't make much sense with CubicChunks,
 * all methods that load Chunks, actually load an empry column with no blocks in it
 * (there may be some entities that are not in any Cube yet).
 * * dropChunk method is not supported. Columns are unloaded automatically when the last cube is unloaded
 */
@ParametersAreNonnullByDefault
public class CubeProviderServer extends ChunkProviderServer
    implements ICubeProviderServer, ICubeProviderInternal.Server {

    @Nonnull
    private final EmptyColumn emptyColumn;
    @Nonnull
    private final BlankCube emptyCube;

    @Nonnull
    private final WorldServer worldServer;
    @Nonnull
    private final CubeLoaderServer cubeLoader;

    // @Nonnull private final CubePrimer cubePrimer;
    @Nonnull
    private final IWorldGenerator worldGenerator;
    @Nonnull
    private final Profiler profiler;

    private final Map<ChunkCoordIntPair, EagerCubeLoadContainer> eagerLoads = new Object2ObjectOpenHashMap<>();
    private final List<ChunkCoordIntPair> eagerLoadOrder = new ArrayList<>();

    private static final int MAX_NS_SPENT_LOADING = 40_000_000;

    private int loadedColumns, loadedCubes;
    private long lastTickEnd = 0, loadTimeAccumulator;

    private final ListMultimap<ChunkCoordIntPair, Runnable> pendingAsyncChunkLoads = MultimapBuilder.hashKeys()
        .arrayListValues()
        .build();

    private final ObjectLinkedOpenHashSet<CubeLoaderCallback> callbacks = new ObjectLinkedOpenHashSet<>();

    public CubeProviderServer(WorldServer worldServer, IChunkLoader chunkLoader, IWorldGenerator worldGenerator) {
        super(
            worldServer,
            chunkLoader, // forge uses this in
            worldServer.provider.createChunkGenerator()); // let's create the chunk generator, for now the vanilla one
                                                          // may be enough

        this.worldGenerator = worldGenerator;
        this.worldServer = worldServer;
        this.profiler = worldServer.theProfiler;
        try {
            Path path = worldServer.getSaveHandler()
                .getWorldDirectory()
                .toPath();

            if (worldServer.provider.getSaveFolder() != null) {
                path = path.resolve(worldServer.provider.getSaveFolder());
            }

            WorldFormatSavedData format = WorldFormatSavedData.get(worldServer);

            this.cubeLoader = new CubeLoaderServer(
                worldServer,
                format.getFormat()
                    .provideStorage(worldServer, path),
                worldGenerator,
                new LoadingCallbacks());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        this.emptyColumn = new EmptyColumn(worldServer, 0, 0);
        this.emptyCube = new BlankCube(emptyColumn);
    }

    class LoadingCallbacks implements CubeLoaderCallback {

        @Override
        public void onColumnLoaded(Chunk column) {
            long k = ChunkCoordIntPair.chunkXZ2Int(column.xPosition, column.zPosition);
            CubeProviderServer.this.loadedChunkHashMap.add(k, column);
            CubeProviderServer.this.loadedChunks.add(column);

            column.lastSaveTime = CubeProviderServer.this.worldServer.getTotalWorldTime();

            pendingAsyncChunkLoads.removeAll(new ChunkCoordIntPair(column.xPosition, column.zPosition))
                .forEach(Runnable::run);

            callbacks.forEach(c -> c.onColumnLoaded(column));

            loadedColumns++;
        }

        @Override
        public void onColumnUnloaded(Chunk column) {
            long k = ChunkCoordIntPair.chunkXZ2Int(column.xPosition, column.zPosition);
            CubeProviderServer.this.loadedChunkHashMap.remove(k);
            CubeProviderServer.this.loadedChunks.remove(column);

            callbacks.forEach(c -> c.onColumnUnloaded(column));
        }

        @Override
        public void onCubeLoaded(Cube cube) {
            callbacks.forEach(c -> c.onCubeLoaded(cube));
            loadedCubes++;
        }

        @Override
        public void onCubeGenerated(Cube cube, CubeInitLevel newLevel) {
            callbacks.forEach(c -> c.onCubeGenerated(cube, newLevel));
        }

        @Override
        public void onCubeUnloaded(Cube cube) {
            callbacks.forEach(c -> c.onCubeUnloaded(cube));
        }
    }

    private int getChunkDistanceSquared(ChunkCoordIntPair coord) {
        int min = Integer.MAX_VALUE;

        List<EntityPlayerMP> players = getPlayers();

        for (int i = 0, playersSize = players.size(); i < playersSize; i++) {
            EntityPlayerMP player = players.get(i);
            final int dX = player.chunkCoordX - coord.chunkXPos;
            final int dZ = player.chunkCoordZ - coord.chunkZPos;

            final int dist2 = dX * dX + dZ * dZ;

            if (dist2 < min) min = dist2;
        }

        return min;
    }

    private List<EntityPlayerMP> getPlayers() {
        // worldServer == null when this provider is being constructed because the field isn't set before the sorting
        // lists call this method.
        // noinspection ConstantValue
        if (worldServer == null) return Collections.emptyList();

        // noinspection unchecked
        return (List<EntityPlayerMP>) (List<?>) worldServer.playerEntities;
    }

    @Override
    @Detainted
    public void unloadChunksIfNotNearSpawn(int x, int z) {
        // ignore, ChunkGc unloads cubes
    }

    @Override
    @Detainted
    public void unloadAllChunks() {
        // ignore, ChunkGc unloads cubes
    }

    /**
     * Vanilla method, returns a Chunk (Column) only of it's already loaded.
     */
    @Nullable
    @Override
    public Chunk getLoadedColumn(int columnX, int columnZ) {
        return cubeLoader.getColumn(columnX, columnZ, Requirement.GET_CACHED);
    }

    /**
     * Loads Chunk (Column) if it can be loaded from disk, or returns already loaded one.
     * Doesn't generate new Columns.
     */
    @Nullable
    @Override
    @Deprecated
    public Chunk loadChunk(int columnX, int columnZ) {
        return this.loadChunk(columnX, columnZ, null);
    }

    /**
     * Load chunk asynchronously. Currently, CubicChunks only loads synchronously.
     */
    @Nullable
    @Override
    @Deprecated
    public Chunk loadChunk(int columnX, int columnZ, @Nullable Runnable runnable) {
        if (runnable != null) {
            pendingAsyncChunkLoads.put(new ChunkCoordIntPair(columnX, columnZ), runnable);
        }

        return getColumn(columnX, columnZ, Requirement.LOAD);
    }

    /**
     * If this Column is already loaded - returns it.
     * Loads from disk if possible, otherwise generates new Column.
     */
    @Override
    public Chunk provideColumn(int cubeX, int cubeZ) {
        return getColumn(cubeX, cubeZ, Requirement.GENERATE);
    }

    @Override
    public Chunk provideChunk(int cubeX, int cubeZ) {
        return provideColumn(cubeX, cubeZ);
    }

    @Override
    public boolean saveChunks(boolean ignored, IProgressUpdate progressUpdater) {
        cubeLoader.save(true);

        return true;
    }

    @Override
    public boolean unloadQueuedChunks() {
        tick();

        // NOTE: the return value is completely ignored
        return false;
    }

    public void registerCallback(CubeLoaderCallback callback) {
        this.callbacks.add(callback);
    }

    public void removeCallback(CubeLoaderCallback callback) {
        this.callbacks.remove(callback);
    }

    public void tick() {
        getCubeLoader().setNow(worldObj.getTotalWorldTime());

        profiler.startSection("Eager object sorting");

        // TODO: make this faster
        eagerLoadOrder.sort(
            Comparator.comparingInt(this::getChunkDistanceSquared)
                .reversed());

        profiler.endSection();

        long pre = System.nanoTime();

        if (worldGenerator instanceof AcceleratableWorldGenerator && KernelContext.isEnabled()) {
            doAcceleratedTerrainGeneration();
        }

        doEagerLoading();

        long post = System.nanoTime();

        acc += post - pre;

        if (counter++ == 20) {
            CubicChunks.LOGGER.info(
                "Worldgen {}ms",
                (acc / 1e6) / counter);

            acc = 0;
            counter = 0;
        }
    }

    private long acc;
    private int counter;

    private void doEagerLoading() {
        profiler.startSection("Eager object loading");

        long start = System.nanoTime();

        int processed = 0;
        int startCols = eagerLoadOrder.size();

        while ((System.nanoTime() - start) < MAX_NS_SPENT_LOADING && !eagerLoadOrder.isEmpty()) {
            ChunkCoordIntPair coord = eagerLoadOrder.get(eagerLoadOrder.size() - 1);

            EagerCubeLoadContainer container = eagerLoads.get(coord);

            if (container == null) {
                eagerLoads.remove(coord);
                eagerLoadOrder.remove(eagerLoadOrder.size() - 1);
                continue;
            }

            Iterator<EagerCubeLoadRequest> cubeIter = container.cubes.values()
                .iterator();

            while ((System.nanoTime() - start) < MAX_NS_SPENT_LOADING && cubeIter.hasNext()) {
                EagerCubeLoadRequest request = cubeIter.next();

                cubeIter.remove();
                request.completed = true;
                CubeStatusVisualizer.remove(request.pos, CubeStatus.Enqueued);

                processed++;

                if (request.isCancelled()) continue;

                cubeLoader.pauseLoadCalls();

                Cube cube = cubeLoader
                    .getCube(request.pos.getX(), request.pos.getY(), request.pos.getZ(), request.effort);

                cubeLoader.unpauseLoadCalls();

                CubeInitLevel actual = cube == null ? CubeInitLevel.None : cube.getInitLevel();
                CubeInitLevel wanted = CubeInitLevel.fromRequirement(request.effort);

                if (actual.ordinal() < wanted.ordinal()) {
                    CubicChunks.LOGGER.error(
                        "Could not init cube {},{},{} for eager request (wanted {}, returned {})",
                        request.pos.getX(),
                        request.pos.getY(),
                        request.pos.getZ(),
                        wanted,
                        actual);
                }
            }

            if (container.cubes.isEmpty()) {
                eagerLoads.remove(coord);
                eagerLoadOrder.remove(eagerLoadOrder.size() - 1);
            } else {
                break;
            }
        }

        long end = System.nanoTime();
        long delta = end - start;

        loadTimeAccumulator += delta;

        if ((loadedColumns > 0 || loadedCubes > 0) && (end - lastTickEnd) > 10e9) {
            double colPerSecPrecise = loadedColumns / (double) loadTimeAccumulator;
            double colPerSecReal = loadedColumns / (double) (end - lastTickEnd);

            double cubePerSecPrecise = loadedCubes / (double) loadTimeAccumulator;
            double cubePerSecReal = loadedCubes / (double) (end - lastTickEnd);

            CubicChunks.LOGGER.info(
                "Columns per second: {} (precise: {}). Cubes per second: {} (precise: {}).",
                String.format("%,.2f", colPerSecReal * 1e9),
                String.format("%,.2f", colPerSecPrecise * 1e9),
                String.format("%,.2f", cubePerSecReal * 1e9),
                String.format("%,.2f", cubePerSecPrecise * 1e9));

            loadedColumns = 0;
            loadedCubes = 0;
            lastTickEnd = end;
            loadTimeAccumulator = 0;
        }

        if (delta > MAX_NS_SPENT_LOADING * 2) {
            CubicChunks.LOGGER.warn("Spent {} ms loading the world this tick", delta / 1e6);
        }

        if (processed > 0) {
            CubicChunks.LOGGER.trace(
                "Processed {} eager load requests this tick ({} -> {} columns)",
                processed,
                startCols,
                eagerLoadOrder.size());
        }

        profiler.endSection();
    }

    private void doAcceleratedTerrainGeneration() {
        profiler.startSection("Accelerated terrain generation");

        AcceleratableWorldGenerator generator = (AcceleratableWorldGenerator) this.worldGenerator;
        KernelScheduler scheduler = KernelContext.getScheduler();

        long start = System.nanoTime();
        long gpuEstimatedNs = 0;

        int i = eagerLoadOrder.size() - 1;

        List<ComputePlan> plans = new ArrayList<>();

        while (i >= 0) {
            if ((System.nanoTime() - start) >= MAX_NS_SPENT_LOADING) break;
            if (gpuEstimatedNs >= scheduler.getGpuBudget()) break;

            ChunkCoordIntPair coord = eagerLoadOrder.get(i--);

            EagerCubeLoadContainer container = eagerLoads.get(coord);

            if (container == null) {
                continue;
            }

            IntArrayList toGenerate = new IntArrayList();

            for (var cubeRequest : container.cubes.values()) {
                if (cubeRequest.effort.contains(Requirement.GENERATE)) {
                    Cube cube = cubeLoader.getLoadedCube(cubeRequest.getX(), cubeRequest.getY(), cubeRequest.getZ());

                    if (cube == null || !cube.isInitializedToLevel(CubeInitLevel.Generated)) {
                        toGenerate.add(cubeRequest.getY());
                    }
                }
            }

            Chunk column = cubeLoader.getColumn(coord.chunkXPos, coord.chunkZPos, Requirement.GET_CACHED);

            if (column != null && toGenerate.isEmpty()) continue;

            ComputePlan plan = generator.plan(column, coord.chunkXPos, coord.chunkZPos, toGenerate);

            gpuEstimatedNs += scheduler.estimatePlanCost(plan);
            plans.add(plan);
        }

        if (!plans.isEmpty()) {
            scheduler.submit(plans);
        }

        profiler.endSection();
    }

    @Override
    public String makeString() {
        return String.format("CubeProviderServer{loader=%s}", this.cubeLoader);
    }

    @Override
    public List<BiomeGenBase.SpawnListEntry> getPossibleCreatures(EnumCreatureType type, int x, int y, int z) {
        return worldGenerator.getPossibleCreatures(type, x, y, z);
    }

    // getLoadedChunkCount() in ChunkProviderServer is fine - CHECKED: 1.10.2-12.18.1.2092

    @Override
    public boolean chunkExists(int cubeX, int cubeZ) {
        return cubeLoader.getColumn(cubeX, cubeZ, Requirement.GET_CACHED) != null;
    }

    // ==============================
    // =====CubicChunks methods======
    // ==============================

    @Override
    public Cube getCube(int cubeX, int cubeY, int cubeZ) {
        return getCube(cubeX, cubeY, cubeZ, Requirement.GENERATE);
    }

    @Override
    public Cube getCube(CubePos coords) {
        return getCube(coords.getX(), coords.getY(), coords.getZ());
    }

    @Nullable
    @Override
    public Cube getLoadedCube(int cubeX, int cubeY, int cubeZ) {
        return cubeLoader.getLoadedCube(cubeX, cubeY, cubeZ);
    }

    @Nullable
    @Override
    public Cube getLoadedCube(CubePos coords) {
        return getLoadedCube(coords.getX(), coords.getY(), coords.getZ());
    }

    public static class EagerCubeLoadContainer implements XZAddressable {

        public final ChunkCoordIntPair pos;

        public final Int2ObjectRBTreeMap<EagerCubeLoadRequest> cubes = new Int2ObjectRBTreeMap<>(
            IntComparator.comparingInt(i -> -i));

        public EagerCubeLoadContainer(ChunkCoordIntPair pos) {
            this.pos = pos;
        }

        public void add(EagerCubeLoadRequest request) {
            cubes.put(request.getY(), request);
        }

        @Override
        public int getX() {
            return pos.chunkXPos;
        }

        @Override
        public int getZ() {
            return pos.chunkZPos;
        }
    }

    @SuppressWarnings("unused")
    public static class EagerCubeLoadRequest implements XYZAddressable {

        public final CubePos pos;
        @Setter
        @Getter
        private Requirement effort;
        @Getter
        private boolean completed, cancelled;

        public EagerCubeLoadRequest(CubePos pos, Requirement effort) {
            this.pos = pos;
            this.effort = effort;
        }

        public void cancel() {
            this.cancelled = true;
            CubeStatusVisualizer.remove(pos, CubeStatus.Enqueued);
        }

        @Override
        public final boolean equals(Object o) {
            if (!(o instanceof EagerCubeLoadRequest that)) return false;

            return pos.equals(that.pos);
        }

        @Override
        public int hashCode() {
            return pos.hashCode();
        }

        @Override
        public String toString() {
            return "EagerCubeLoadRequest{" + "pos=" + pos + '}';
        }

        @Override
        public int getX() {
            return pos.getX();
        }

        @Override
        public int getY() {
            return pos.getY();
        }

        @Override
        public int getZ() {
            return pos.getZ();
        }
    }

    public EagerCubeLoadRequest loadCubeEagerly(int x, int y, int z, Requirement effort) {
        CubePos pos = new CubePos(x, y, z);

        ChunkCoordIntPair coord = new ChunkCoordIntPair(x, z);

        EagerCubeLoadContainer container = eagerLoads.get(coord);

        if (container == null) {
            container = new EagerCubeLoadContainer(coord);
            eagerLoads.put(coord, container);
            eagerLoadOrder.add(coord);
        }

        EagerCubeLoadRequest request = new EagerCubeLoadRequest(pos, effort);

        container.add(request);

        cubeLoader.preloadCube(pos, CubeInitLevel.fromRequirement(effort));

        CubeStatusVisualizer.put(pos, CubeStatus.Enqueued);

        return request;
    }

    @Nullable
    @Override
    public Cube getCube(int cubeX, int cubeY, int cubeZ, Requirement effort) {
        Cube cube = cubeLoader.getCube(cubeX, cubeY, cubeZ, effort);
        return cube == null ? emptyCube : cube;
    }

    @Override
    public boolean cubeExists(int cubeX, int cubeY, int cubeZ) {
        return cubeLoader.cubeExists(cubeX, cubeY, cubeZ);
    }

    @Nullable
    @Override
    public Chunk getColumn(int columnX, int columnZ, Requirement effort) {
        return cubeLoader.getColumn(columnX, columnZ, effort);
    }

    public String dumpLoadedCubes() {
        StringBuilder sb = new StringBuilder(10000).append("\n");
        for (Chunk chunk : this.loadedChunks) {
            if (chunk == null) {
                sb.append("column = null\n");
                continue;
            }
            sb.append("Column[")
                .append(chunk.xPosition)
                .append(", ")
                .append(chunk.zPosition)
                .append("] {");
            boolean isFirst = true;
            for (ICube cube : ((IColumn) chunk).getLoadedCubes()) {
                if (!isFirst) {
                    sb.append(", ");
                }
                isFirst = false;
                if (cube == null) {
                    sb.append("cube = null");
                    continue;
                }
                sb.append("Cube[")
                    .append(cube.getY())
                    .append("]");
            }
            sb.append("\n");
        }
        return sb.toString();
    }

    @Override
    @Nonnull
    public ICubeLoader getCubeLoader() {
        return cubeLoader;
    }
}
