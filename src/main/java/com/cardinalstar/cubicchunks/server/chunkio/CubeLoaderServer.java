package com.cardinalstar.cubicchunks.server.chunkio;

import static net.minecraftforge.common.MinecraftForge.EVENT_BUS;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nonnull;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.ChunkCoordIntPair;
import net.minecraft.world.WorldServer;
import net.minecraft.world.chunk.Chunk;
import net.minecraftforge.common.ForgeChunkManager;
import net.minecraftforge.event.world.ChunkDataEvent;

import com.cardinalstar.cubicchunks.CubicChunks;
import com.cardinalstar.cubicchunks.api.IColumn;
import com.cardinalstar.cubicchunks.api.MetaKey;
import com.cardinalstar.cubicchunks.api.XYZAddressable;
import com.cardinalstar.cubicchunks.api.XYZMap;
import com.cardinalstar.cubicchunks.api.XZMap;
import com.cardinalstar.cubicchunks.api.event.ColumnEvent;
import com.cardinalstar.cubicchunks.api.event.CubeEvent;
import com.cardinalstar.cubicchunks.api.world.storage.ICubicStorage;
import com.cardinalstar.cubicchunks.api.worldgen.GenerationResult;
import com.cardinalstar.cubicchunks.api.worldgen.IWorldGenerator;
import com.cardinalstar.cubicchunks.mixin.api.ICubicWorldInternal;
import com.cardinalstar.cubicchunks.server.CubicPlayerManager;
import com.cardinalstar.cubicchunks.util.Array3D;
import com.cardinalstar.cubicchunks.util.CubePos;
import com.cardinalstar.cubicchunks.util.XZAddressable;
import com.cardinalstar.cubicchunks.world.CubicChunksSavedData;
import com.cardinalstar.cubicchunks.world.api.ICubeProviderServer.Requirement;
import com.cardinalstar.cubicchunks.world.column.EmptyColumn;
import com.cardinalstar.cubicchunks.world.core.IColumnInternal;
import com.cardinalstar.cubicchunks.world.cube.BlankCube;
import com.cardinalstar.cubicchunks.world.cube.BoundaryCube;
import com.cardinalstar.cubicchunks.world.cube.Cube;

import it.unimi.dsi.fastutil.Pair;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import lombok.Setter;

public class CubeLoaderServer implements ICubeLoader {

    private static final MetaKey<CubeInfo> CUBE_INFO = new MetaKey<>() {};

    private final WorldServer world;
    private final ICubeIO cubeIO;
    private final IWorldGenerator generator;
    private final CubeLoaderCallback callback;

    private final XYZMap<CubeInfo> cubes = new XYZMap<>();
    private final XZMap<ColumnInfo> columns = new XZMap<>();

    // GC is server-thread confined. Event callbacks may reenter loading/GC synchronously.
    private boolean gcRunning;
    private int activeLoadCalls;
    private int pauseLoadCalls;
    private final List<Pair<Cube, CubeInitLevel>> pendingCubeGenerates = new ArrayList<>();
    private final List<Cube> pendingCubeLoads = new ArrayList<>();
    private final List<Chunk> pendingColumnLoads = new ArrayList<>();

    private Array3D<Cube> cache;
    @Setter
    private long now;

    private final int maxCube;
    private final int minCube;

    @Nonnull
    private final EmptyColumn emptyColumn;
    @Nonnull
    private final BlankCube emptyCube;

    public CubeLoaderServer(WorldServer world, ICubicStorage storage, IWorldGenerator generator,
        CubeLoaderCallback callback) {
        this.world = world;
        this.cubeIO = new CubeIO(storage, generator instanceof IPreloadFailureDelegate delegate ? delegate : null);
        this.generator = generator;
        this.callback = callback;

        CubicChunksSavedData data = CubicChunksSavedData.get(world);

        this.minCube = data.minHeight >> 4;
        this.maxCube = (data.maxHeight - 1) >> 4;

        this.emptyColumn = new EmptyColumn(world, 0, 0);
        this.emptyCube = new BlankCube(emptyColumn);
    }

    @Override
    public void pauseLoadCalls() {
        pauseLoadCalls++;
    }

    @Override
    public void unpauseLoadCalls() {
        if (pauseLoadCalls <= 0) {
            pauseLoadCalls = 0;
            return;
        }

        if (--pauseLoadCalls == 0) {
            for (Chunk column : pendingColumnLoads) {
                callback.onColumnLoaded(column);
            }

            pendingColumnLoads.clear();

            for (Cube cube : pendingCubeLoads) {
                callback.onCubeLoaded(cube);
            }

            pendingCubeLoads.clear();

            for (var p : pendingCubeGenerates) {
                callback.onCubeGenerated(p.left(), p.right());
            }

            pendingCubeGenerates.clear();
        }
    }

    @Override
    public Chunk getColumn(int x, int z, Requirement effort) {
        ColumnInfo column = getColumnInfo(x, z, effort);

        return column != null ? column.column : null;
    }

    private boolean canUnloadColumn(ColumnInfo info, CubicPlayerManager players) {
        if (pauseLoadCalls > 0 || activeLoadCalls > 0) return false;
        if (columns.get(info.getX(), info.getZ()) != info || info.column == null) return false;
        // Check both indexes: do not destroy resident cubes to make a stale candidate fit.
        if (!info.containedCubes.isEmpty() || ((IColumn) info.column).hasLoadedCubes()) return false;
        if (ForgeChunkManager.getPersistentChunksFor(world)
            .containsKey(info.pos)) return false;
        return !players.func_152621_a(info.getX(), info.getZ());
    }

    private boolean tryUnloadColumn(ColumnInfo info, CubicPlayerManager players) {
        if (!canUnloadColumn(info, players)) return false;
        boolean removed = false;
        try {
            info.column.onChunkUnload();
            // ChunkEvent.Unload handlers may have loaded cubes or acquired tickets/watchers.
            if (!canUnloadColumn(info, players)) return false;
            // Keep the live indexes until serialization succeeds. SaveNBT is another callback boundary.
            cubeIO.saveColumn(info.pos, info.column);
            if (!canUnloadColumn(info, players)) return false;
            columns.remove(info);
            removed = true;
            callback.onColumnUnloaded(info.column);
            return true;
        } finally {
            if (!removed && columns.get(info.getX(), info.getZ()) == info && !info.column.isChunkLoaded) {
                // Balance the unload notification, but do NOT insert the same column into the provider again.
                info.column.isModified = true;
                info.column.onChunkLoad();
            }
        }
    }

    private ColumnInfo getColumnInfo(int x, int z, Requirement effort) {
        activeLoadCalls++;
        try {
            return getColumnInfoInternal(x, z, effort);
        } finally {
            activeLoadCalls--;
        }
    }

    private ColumnInfo getColumnInfoInternal(int x, int z, Requirement effort) {
        ColumnInfo column = columns.get(x, z);

        if (effort == Requirement.GET_CACHED) return column;

        if (column == null) {
            columns.put(column = new ColumnInfo(x, z));
        }

        boolean success;

        try {
            success = column.initialize(effort);
        } catch (Throwable throwable) {
            throw new RuntimeException(String.format("Could not generate column at %d,%d", x, z), throwable);
        }

        if (column.column == null) {
            columns.remove(column);
        }

        return success ? column : null;
    }

    private Cube lastCube;

    @Override
    public Cube getLoadedCube(int x, int y, int z) {
        if (cache != null) {
            Cube cube = cache.get(x, y, z);

            if (cube != null) return cube;
        } else {
            if (lastCube != null && lastCube.getX() == x && lastCube.getY() == y && lastCube.getZ() == z) {
                return lastCube;
            }
        }

        CubeInfo info = cubes.get(x, y, z);

        Cube cube = info == null ? null : info.cube;

        if (cache == null) {
            lastCube = cube;
        } else {
            cache.set(x, y, z, cube);
        }

        return cube;
    }

    @Override
    public boolean cubeExists(int x, int y, int z) {
        if (getLoadedCube(x, y, z) != null) return true;

        return cubeIO.cubeExists(new CubePos(x, y, z));
    }

    @Override
    public Cube getCube(int x, int y, int z, Requirement effort) {
        activeLoadCalls++;
        try {
            return getCubeInternal(x, y, z, effort);
        } finally {
            activeLoadCalls--;
        }
    }

    private Cube getCubeInternal(int x, int y, int z, Requirement effort) {
        if (cache != null) {
            Cube cube = cache.get(x, y, z);

            if (cube != null && cube.getInitLevel()
                .ordinal()
                >= CubeInitLevel.fromRequirement(effort)
                    .ordinal()) {
                if (effort != Requirement.GET_CACHED) {
                    CubeInfo cachedInfo = cubes.get(x, y, z);
                    if (cachedInfo != null && cachedInfo.cube == cube) cachedInfo.lastAccess = now;
                }
                return cube;
            }
        }

        CubeInfo cubeInfo = cubes.get(x, y, z);

        Cube loaded = cubeInfo != null ? cubeInfo.cube : null;

        // Don't need to do anything because the cube is already initialized to the requested level
        if (loaded != null && cubeInfo.isInitedTo(effort)) {
            if (effort != Requirement.GET_CACHED) cubeInfo.lastAccess = now;
            return loaded;
        }

        if (effort == Requirement.GET_CACHED) return null;

        if (cubeInfo == null) {
            cubes.put(cubeInfo = new CubeInfo(x, y, z));
        }

        boolean changed = cubeInfo.updateInitLevel();

        boolean success;

        try {
            success = cubeInfo.initialize(effort);
        } catch (Throwable throwable) {
            throw new RuntimeException(String.format("Could not generate cube at %d,%d,%d", x, y, z), throwable);
        }

        changed |= cubeInfo.updateInitLevel();

        if (cubeInfo.cube == null) {
            cubes.remove(cubeInfo);
        } else {
            if (success && changed) {
                invokeGenerateCallback(cubeInfo.cube, cubeInfo.getInitLevel());
            }
        }

        if (success && cache != null) {
            cache.set(x, y, z, cubeInfo.cube);
        }

        if (success) {
            cubeInfo.lastAccess = now;
        }

        return success ? cubeInfo.cube : null;
    }

    @Override
    public void onCubeGenerated(Cube cube) {
        CubeInfo cubeInfo = cube.getMeta(CUBE_INFO);

        if (cubeInfo == null) return;

        if (cubeInfo.updateInitLevel()) {
            invokeGenerateCallback(cubeInfo.cube, cubeInfo.getInitLevel());
        }
    }

    @Override
    public void cacheCubes(int x, int y, int z, int spanx, int spany, int spanz) {
        cache = new Array3D<>(spanx, spany, spanz, x, y, z, new Cube[spanx * spany * spanz]);
    }

    @Override
    public void uncacheCubes() {
        cache = null;
    }

    public void preloadColumn(ChunkCoordIntPair pos) {
        cubeIO.preloadColumn(pos);
    }

    public void preloadCube(CubePos pos, CubeInitLevel level) {
        cubeIO.preloadCube(pos, level);
    }

    @Override
    public void unloadCube(int x, int y, int z) {
        CubeInfo info = cubes.remove(x, y, z);

        if (info != null) {
            if (lastCube == info.cube) {
                lastCube = null;
            }
            if (cache != null) {
                cache.set(x, y, z, null);
            }
            info.onCubeUnloaded();
        }
    }

    @Override
    public void save(boolean saveAll) {
        boolean processedLighting = false;

        for (CubeInfo cube : cubes) {
            if (cube.cube != null && cube.cube.needsSaving(saveAll)) {
                if (!processedLighting) {
                    // make sure all light updates are processed
                    ((ICubicWorldInternal) world).getLightingManager()
                        .processUpdates();

                    processedLighting = true;
                }

                cubeIO.saveCube(cube.pos, cube.cube);
            }
        }

        for (ColumnInfo column : columns) {
            if (column.column != null && column.column.needsSaving(saveAll)) {
                cubeIO.saveColumn(column.pos, column.column);
            }
        }
    }

    @Override
    public void saveColumn(Chunk column) {
        if (column == null) return;

        ColumnInfo columnInfo = columns.get(column.xPosition, column.zPosition);

        if (columnInfo == null) return;

        if (columnInfo.column != column) {
            CubicChunks.LOGGER.error(
                "Tried to save Chunk in the wrong CubeLoaderServer (tried to save {}, but had {}).",
                column,
                columnInfo.column);
            return;
        }

        cubeIO.saveColumn(columnInfo.pos, column);
    }

    @Override
    public void saveCube(Cube cube) {
        if (cube == null || cube instanceof BlankCube) return;

        CubeInfo cubeInfo = cubes.get(cube.getX(), cube.getY(), cube.getZ());

        if (cubeInfo == null) return;

        if (cubeInfo.cube != cube) {
            CubicChunks.LOGGER.error(
                "Tried to save Cube in the wrong CubeLoaderServer (tried to save {}, but had {}).",
                cube,
                cubeInfo.cube);
            return;
        }

        cubeIO.saveCube(cubeInfo.pos, cube);
    }

    private static final int CUBE_GC_EXPIRY = 20 * 5;

    @Override
    public void doGC() {
        if (gcRunning || activeLoadCalls > 0
            || pauseLoadCalls > 0
            || !pendingColumnLoads.isEmpty()
            || !pendingCubeLoads.isEmpty()
            || !pendingCubeGenerates.isEmpty()) return;
        gcRunning = true;
        try {
            collectGarbage();
        } finally {
            gcRunning = false;
        }
    }

    private boolean canUnloadCube(CubeInfo info, CubicPlayerManager players, long expiry) {
        Cube cube = info.cube;
        if (cube == null || info.generating || info.lastAccess > expiry) return false;
        if (cubes.get(info.getX(), info.getY(), info.getZ()) != info) return false;
        if (ForgeChunkManager.getPersistentChunksFor(world)
            .containsKey(
                cube.getColumn()
                    .getChunkCoordIntPair()))
            return false;
        return !players.isCubeWatched(info.getX(), info.getY(), info.getZ()) && cube.getTickets()
            .canUnload();
    }

    private void collectGarbage() {
        var persistentChunks = ForgeChunkManager.getPersistentChunksFor(world);
        CubicPlayerManager playerManager = (CubicPlayerManager) world.getPlayerManager();

        List<CubeInfo> pendingCubeUnloads = new ArrayList<>();

        int startCubes = cubes.getSize();
        int startCols = columns.getSize();

        final long expiry = now - CUBE_GC_EXPIRY;

        for (CubeInfo cubeInfo : cubes) {
            Cube cube = cubeInfo.cube;

            if (cube == null || cubeInfo.generating || cubeInfo.lastAccess > expiry) continue;

            if (persistentChunks.containsKey(
                cube.getColumn()
                    .getChunkCoordIntPair()))
                continue;

            if (playerManager.isCubeWatched(cube.getX(), cube.getY(), cube.getZ())) continue;

            if (cube.getTickets()
                .canUnload()) {
                pendingCubeUnloads.add(cubeInfo);
            }
        }

        int removedCubes = 0;
        for (CubeInfo info : pendingCubeUnloads) {
            // Earlier unload callbacks can replace, access or pin a later candidate.
            if (!canUnloadCube(info, playerManager, expiry)) continue;
            unloadCube(info.getX(), info.getY(), info.getZ());
            removedCubes++;
        }

        List<ColumnInfo> pendingColumnUnloads = new ArrayList<>();

        for (ColumnInfo columnInfo : columns) {
            Chunk column = columnInfo.column;

            if (column == null) continue;

            if (persistentChunks.containsKey(columnInfo.pos)) continue;

            // It has loaded Cubes in it (Cubes are to Columns, as tickets are to Cubes... in a way)
            if (!columnInfo.containedCubes.isEmpty() || ((IColumn) column).hasLoadedCubes()) continue;

            // PlayerChunkMap may contain reference to a column that for a while doesn't yet have any cubes generated
            if (playerManager.func_152621_a(column.xPosition, column.zPosition)) continue;

            pendingColumnUnloads.add(columnInfo);
        }

        int removedColumns = 0;
        for (ColumnInfo column : pendingColumnUnloads) {
            if (tryUnloadColumn(column, playerManager)) removedColumns++;
        }

        CubicChunks.LOGGER.trace(
            "Garbage collected {} columns ({} -> {}) and {} cubes ({} -> {}); deferred {} stale column candidates.",
            removedColumns,
            startCols,
            columns.getSize(),
            removedCubes,
            startCubes,
            cubes.getSize(),
            pendingColumnUnloads.size() - removedColumns);
    }

    @Override
    public void flush() throws IOException {
        cubeIO.flush();
    }

    @Override
    public void close() throws IOException {
        cubeIO.close();
    }

    private void invokeLoadCallback(Chunk column) {
        if (pauseLoadCalls > 0) {
            pendingColumnLoads.add(column);
        } else {
            callback.onColumnLoaded(column);
        }
    }

    private void invokeLoadCallback(Cube cube) {
        if (pauseLoadCalls > 0) {
            pendingCubeLoads.add(cube);
        } else {
            callback.onCubeLoaded(cube);
        }
    }

    private void invokeGenerateCallback(Cube cube, CubeInitLevel level) {
        if (pauseLoadCalls > 0) {
            pendingCubeGenerates.add(Pair.of(cube, level));
        } else {
            callback.onCubeGenerated(cube, level);
        }
    }

    private void handleSideEffects(GenerationResult<?> result, boolean doColumns, boolean doCubes) {
        if (doColumns) {
            for (Chunk column : result.columnSideEffects) {
                ColumnInfo info = columns.get(column.xPosition, column.zPosition);

                if (info != null && info.column != null) {
                    CubicChunks.LOGGER
                        .warn("Worldgen side-effect replaced column at {},{}!", column.xPosition, column.zPosition);
                    continue;
                }

                if (info == null) {
                    info = new ColumnInfo(column.xPosition, column.zPosition);
                    columns.put(info);
                }

                info.source = ObjectSource.GeneratedSideEffect;
                info.column = column;

                info.onColumnLoaded();
            }
        }

        if (doCubes) {
            for (Cube cube : result.cubeSideEffects) {
                CubeInfo info = cubes.get(cube.getX(), cube.getY(), cube.getZ());

                if (info != null && info.cube != null) {
                    CubicChunks.LOGGER
                        .warn("Worldgen side-effect replaced cube at {},{},{}!", cube.getX(), cube.getY(), cube.getZ());
                    continue;
                }

                if (info == null) {
                    info = new CubeInfo(cube.getX(), cube.getY(), cube.getZ());
                    cubes.put(info);
                }

                info.source = ObjectSource.GeneratedSideEffect;
                info.cube = cube;
                cube.setMeta(CUBE_INFO, info);

                info.ensureColumn(Requirement.GET_CACHED);

                info.onCubeLoaded();
            }
        }
    }

    private enum ObjectSource {
        None,
        Disk,
        Generated,
        GeneratedSideEffect,
        Boundary
    }

    private class ColumnInfo implements XZAddressable {

        public final ChunkCoordIntPair pos;

        public NBTTagCompound tag;
        public Chunk column;
        public ObjectSource source;

        public final ObjectOpenHashSet<CubeInfo> containedCubes = new ObjectOpenHashSet<>();

        public ColumnInfo(int x, int z) {
            this.pos = new ChunkCoordIntPair(x, z);
        }

        @Override
        public int getX() {
            return pos.chunkXPos;
        }

        @Override
        public int getZ() {
            return pos.chunkZPos;
        }

        public boolean initialize(Requirement effort) {
            if (column != null) return true;
            if (effort == Requirement.GET_CACHED) return false;

            if (loadNBT()) {
                if (!loadColumn()) return false;
            }

            if (column != null) return true;
            if (effort == Requirement.LOAD) return false;

            GenerationResult<Chunk> result = generator.provideColumn(world, pos.chunkXPos, pos.chunkZPos);

            if (result == null) return false;

            this.column = result.object;
            source = ObjectSource.Generated;

            onColumnLoaded();

            cubeIO.saveColumn(pos, column);

            handleSideEffects(result, true, true);

            return true;
        }

        private boolean loadNBT() {
            if (tag != null) return true;

            tag = cubeIO.loadColumn(pos);

            if (tag == null) return false;

            source = ObjectSource.Disk;

            EVENT_BUS.post(new ColumnEvent.LoadNBT(world, pos, tag));

            return true;
        }

        private boolean loadColumn() {
            this.column = IONbtReader.readColumn(world, getX(), getZ(), tag);

            if (column == null) return false;

            EVENT_BUS.post(new ChunkDataEvent.Load(column, tag));

            onColumnLoaded();

            this.tag = null;

            return true;
        }

        public void onColumnLoaded() {
            if (this.column.xPosition != this.getX()) {
                throw new IllegalStateException(
                    "Expected column to be at X=" + getX()
                        + " but it was at X="
                        + this.column.xPosition
                        + " ("
                        + this.column
                        + ", "
                        + world
                        + ")");
            }

            if (this.column.zPosition != this.getZ()) {
                throw new IllegalStateException(
                    "Expected column to be at Z=" + getZ()
                        + " but it was at Z="
                        + this.column.zPosition
                        + " ("
                        + this.column
                        + ", "
                        + world
                        + ")");
            }

            column.lastSaveTime = world.getTotalWorldTime();

            ((IColumnInternal) column).setColumn(true);

            column.onChunkLoad();

            CubeLoaderServer.this.generator.recreateStructures(column);

            invokeLoadCallback(column);
        }

    }

    private class CubeInfo implements XYZAddressable {

        public final CubePos pos;

        public NBTTagCompound tag;
        public Cube cube;

        public ColumnInfo column;

        private ObjectSource source = ObjectSource.None;

        public boolean generating = false;
        public long lastAccess = 0;

        private CubeInitLevel lastKnownLevel = CubeInitLevel.None;

        public CubeInfo(int x, int y, int z) {
            this.pos = new CubePos(x, y, z);
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

        public boolean initialize(Requirement effort) throws IOException {
            if (pos.getY() < minCube || pos.getY() > maxCube) {
                loadBoundaryCube();
                return true;
            }

            if (effort == Requirement.GET_CACHED) {
                return cube != null;
            }

            // If we haven't already loaded the NBT tag from disk, try to load it
            if (tag == null) {
                loadNBT();
            }

            // If we only want to load the NBT, return whether it was successful or not.
            if (effort == Requirement.NBT) {
                return tag != null;
            }

            // If we loaded the NBT from disk successfully and we don't already have a cube loaded, try to load it
            if (tag != null && source == ObjectSource.None) {
                loadCube();

                if (effort == Requirement.LOAD) return cube != null;
            }

            CubeInitLevel requestedInitLevel = CubeInitLevel.fromRequirement(effort);

            // We may have loaded a cube, but it wasn't to the required initialization level
            // Do some more work on it, to whatever level is required
            return generate(requestedInitLevel);
        }

        private void loadBoundaryCube() {
            ensureColumn(Requirement.LOAD);

            if (this.column == null) {
                CubicChunks.LOGGER.error(
                    "Tried to load a cube that did not have a saved column: it will be regenerated ({},{},{})",
                    getX(),
                    getY(),
                    getZ(),
                    new Exception());
                this.cube = null;
                this.tag = null;
                return;
            }

            this.cube = new BoundaryCube(this.column.column, this.getY());
            onCubeLoaded();
        }

        private boolean loadNBT() {
            if (tag != null) return true;

            tag = cubeIO.loadCube(pos);

            if (tag == null) return false;

            EVENT_BUS.post(new CubeEvent.LoadNBT(world, pos, tag));

            return true;
        }

        private void loadCube() throws IOException {
            // Cubes should always have a containing column unless someone's deleting files. Try to load it, or fail if
            // the column is missing.
            ensureColumn(Requirement.LOAD);

            if (this.column == null) {
                CubicChunks.LOGGER.error(
                    "Tried to load a cube that did not have a saved column: it will be regenerated ({},{},{})",
                    getX(),
                    getY(),
                    getZ(),
                    new Exception());
                this.cube = null;
                this.tag = null;
                return;
            }

            this.cube = IONbtReader.readCube(column.column, getX(), getY(), getZ(), tag);
            cube.setMeta(CUBE_INFO, this);

            source = ObjectSource.Disk;

            onCubeLoaded();

            this.tag = null;
        }

        public boolean isInitedTo(Requirement effort) {
            return isInitedTo(CubeInitLevel.fromRequirement(effort));
        }

        public boolean isInitedTo(CubeInitLevel initLevel) {
            return getInitLevel().ordinal() >= initLevel.ordinal();
        }

        public CubeInitLevel getInitLevel() {
            return cube == null ? CubeInitLevel.None : cube.getInitLevel();
        }

        private boolean generate(CubeInitLevel requestedInitLevel) {
            // The cube is already at the required init level, so we don't need to do any more work on it.
            if (isInitedTo(requestedInitLevel)) return true;

            // If this cube hasn't been generated at all (i.e. it was never on the disk), generate it
            if (source == ObjectSource.None) {
                if (generating) {
                    throw new IllegalStateException(
                        "Cannot recursively generate a cube that is already being generated");
                }

                // Try to get any columns that already exist, since the generator may re-generate it if it thinks the
                // column is missing.
                ensureColumn(Requirement.LOAD);

                GenerationResult<Cube> result;
                try {
                    this.generating = true;

                    result = generator
                        .provideCube(column == null ? null : column.column, pos.getX(), pos.getY(), pos.getZ());
                } finally {
                    this.generating = false;
                }

                if (result == null) return false;

                this.cube = result.object;
                this.source = ObjectSource.Generated;
                cube.setMeta(CUBE_INFO, this);

                // Inject the columns, if any were generated.
                handleSideEffects(result, true, false);

                // You can't generate a cube without also generating a column. Fetch it here if it's missing.
                ensureColumn(Requirement.GET_CACHED);

                if (this.column == null) {
                    throw new IllegalStateException(
                        "Generated a cube without generating its column: this is an invalid state");
                }

                // Alert everything that this cube was generated
                onCubeLoaded();

                // Inject the other cubes in this column
                handleSideEffects(result, false, true);
            }

            boolean generated = isInitedTo(CubeInitLevel.Generated);

            // We were only asked to generate it and we did so successfully
            if (requestedInitLevel == CubeInitLevel.Generated) return generated;
            if (!generated) return false;

            // If this cube hasn't been populated at all, populate it. This generates any required cubes recursively.
            generator.populate(cube);

            boolean populated = isInitedTo(CubeInitLevel.Populated);

            if (requestedInitLevel == CubeInitLevel.Populated) return populated;
            if (!populated) return false;

            // Do the initial lighting
            if (!cube.isInitialLightingDone() || !cube.isSurfaceTracked()) {
                ((ICubicWorldInternal) world).getLightingManager()
                    .doFirstLight(cube);
                cube.setInitialLightingDone(true);
            }

            // Put the surface into the column (to update the column heightmap) as needed
            if (!cube.isSurfaceTracked()) {
                cube.trackSurface();
            }

            return cube.getInitLevel() == CubeInitLevel.Lit;
        }

        public void onCubeLoaded() {
            if (this.column == null) {
                throw new IllegalStateException("Loaded a cube without giving it a column reference: this is a bug");
            }

            if (this.cube.getX() != this.getX()) {
                throw new IllegalStateException(
                    "Expected column to be at X=" + getX()
                        + " but it was at X="
                        + this.cube.getX()
                        + " ("
                        + this.column
                        + ", "
                        + world
                        + ")");
            }

            if (this.cube.getY() != this.getY()) {
                throw new IllegalStateException(
                    "Expected column to be at Y=" + getY()
                        + " but it was at Y="
                        + this.cube.getY()
                        + " ("
                        + this.column
                        + ", "
                        + world
                        + ")");
            }

            if (this.cube.getZ() != this.getZ()) {
                throw new IllegalStateException(
                    "Expected column to be at Z=" + getZ()
                        + " but it was at Z="
                        + this.cube.getZ()
                        + " ("
                        + this.column
                        + ", "
                        + world
                        + ")");
            }

            updateInitLevel();

            ((IColumn) column.column).addCube(cube);
            column.containedCubes.add(this);

            cube.onCubeLoad();

            CubeLoaderServer.this.generator.recreateStructures(cube);

            invokeLoadCallback(cube);
        }

        public boolean updateInitLevel() {
            CubeInitLevel prev = lastKnownLevel;
            lastKnownLevel = getInitLevel();
            return prev != lastKnownLevel;
        }

        public void onCubeUnloaded() {
            if (this.isInitedTo(CubeInitLevel.Generated)) {
                cubeIO.saveCube(pos, cube);
            }

            ((IColumn) column.column).removeCube(getY());
            column.containedCubes.remove(this);

            cube.onCubeUnload();
            callback.onCubeUnloaded(cube);

            // Column collection is a separate guarded GC phase. A cube unload callback can load
            // another cube, watch this column or force it; never recursively detach it here.
        }

        private void ensureColumn(Requirement effort) {
            if (column == null) {
                column = getColumnInfo(getX(), getZ(), effort);
            }
        }
    }
}
