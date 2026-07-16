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

import static com.cardinalstar.cubicchunks.util.Coords.blockToCube;
import static net.minecraft.util.MathHelper.clamp_int;

import java.util.HashSet;
import java.util.Set;

import javax.annotation.ParametersAreNonnullByDefault;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.management.PlayerManager;
import net.minecraft.world.WorldProvider;
import net.minecraft.world.WorldServer;
import net.minecraft.world.chunk.Chunk;

import org.jetbrains.annotations.Nullable;
import org.joml.Vector3ic;

import com.cardinalstar.cubicchunks.CubicChunks;
import com.cardinalstar.cubicchunks.api.XYZMap;
import com.cardinalstar.cubicchunks.api.util.Box;
import com.cardinalstar.cubicchunks.mixin.api.ICubicWorldInternal;
import com.cardinalstar.cubicchunks.mixin.api.ICubicWorldInternal.Server;
import com.cardinalstar.cubicchunks.server.CubeProviderServer.EagerCubeLoadRequest;
import com.cardinalstar.cubicchunks.server.chunkio.CubeInitLevel;
import com.cardinalstar.cubicchunks.server.chunkio.CubeLoaderCallback;
import com.cardinalstar.cubicchunks.util.CubePos;
import com.cardinalstar.cubicchunks.util.CubeStatusVisualizer;
import com.cardinalstar.cubicchunks.util.CubeStatusVisualizer.CubeStatus;
import com.cardinalstar.cubicchunks.util.HashSet2D;
import com.cardinalstar.cubicchunks.visibility.CuboidalCubeSelector;
import com.cardinalstar.cubicchunks.visibility.WorldVisibilityChange;
import com.cardinalstar.cubicchunks.world.api.ICubeProviderServer.Requirement;
import com.cardinalstar.cubicchunks.world.cube.Cube;

import lombok.Getter;

/**
 * A cubic chunks implementation of Player Manager.
 * <p>
 * This class manages loading and unloading cubes for players.
 */
@ParametersAreNonnullByDefault
public class CubicPlayerManager extends PlayerManager implements CubeLoaderCallback {

    private final PlayerMap players = new PlayerMap();

    private final CubeProviderServer provider;

    private final XYZMap<EagerCubeLoadRequest> cubeLoadRequests = new XYZMap<>();

    private final XYZMap<Cube> loadedCubes = new XYZMap<>();

    private int horizontalViewDistance;
    private int verticalViewDistance;

    private long lastChunkInhabitedUpdate;

    // these player adds will be processed on the next tick
    // this exists as temporary workaround to player respawn code calling addPlayer() before spawning
    // the player in world as it's spawning player in world that triggers sending cubic chunks world
    // information to client, this causes the server to send columns to the client before the client
    // knows it's a cubic chunks world delaying addPlayer() by one tick fixes it.
    // this should be fixed by hooking into the code in a different place to send the cubic chunks world information
    // (player respawn packet?)
    private final Set<EntityPlayerMP> pendingPlayerAddToCubeMap = new HashSet<>();

    public CubicPlayerManager(WorldServer worldServer) {
        super(worldServer);
        this.setPlayerViewDistance(
            worldServer.func_73046_m()
                .getConfigurationManager()
                .getViewDistance(),
            ((ICubicPlayerList) worldServer.func_73046_m()
                .getConfigurationManager()).getVerticalViewDistance());

        this.provider = ((Server) worldServer).getCubeCache();
        this.provider.registerCallback(this);
    }

    /**
     * Updates all CubeWatchers and ColumnWatchers. Also sends packets to clients.
     */
    // CHECKED: 1.10.2-12.18.1.2092
    @Override
    public void updatePlayerInstances() {
        getWorldServer().theProfiler.startSection("Process light updates");

        ((ICubicWorldInternal) getWorldServer()).getLightingManager()
            .onSendCubes();

        getWorldServer().theProfiler.endStartSection("playerCubeMapUpdatePlayerInstances");

        getWorldServer().theProfiler.startSection("addPendingPlayers");

        for (EntityPlayerMP player : pendingPlayerAddToCubeMap) {
            if (player.addedToChunk) {
                addPlayer(player);
            }
        }

        pendingPlayerAddToCubeMap.removeIf(e -> e.addedToChunk);

        getWorldServer().theProfiler.endStartSection("Immediate nearby cube loading");

        for (var player : players.getPlayerArray()) {
            CubeProviderServer cubeCache = ((Server) getWorldServer()).getCubeCache();

            var pos = player.getManagedCubePos();

            // Force load the cube the player is in along with its 26 neighbours
            for (Vector3ic v : new Box(-1, -1, -1, 1, 1, 1)) {
                cubeCache.getCube(pos.getX() + v.x(), pos.getY() + v.y(), pos.getZ() + v.z(), Requirement.LIGHT);
            }

            player.sync.flush();
        }

        long now = getWorldServer().getTotalWorldTime();

        if (this.lastChunkInhabitedUpdate == 0) {
            this.lastChunkInhabitedUpdate = now;
        }

        long delta = now - this.lastChunkInhabitedUpdate;

        if (delta >= 200) {
            this.lastChunkInhabitedUpdate = now;

            HashSet2D viewedColumns = new HashSet2D();

            for (var player : players.getPlayerArray()) {
                CuboidalCubeSelector.INSTANCE.forAllVisibleColumns(
                    player.getManagedCubePos(),
                    horizontalViewDistance,
                    verticalViewDistance,
                    viewedColumns::add);
            }

            viewedColumns.forEach((x, z) -> {
                Chunk loadedColumn = provider.getLoadedColumn(x, z);

                if (loadedColumn != null) {
                    loadedColumn.inhabitedTime += delta;
                }
            });
        }

        getWorldServer().theProfiler.endStartSection("tickEntries");

        getWorldServer().theProfiler.endStartSection("unload");

        // if there are no players - unload everything
        if (this.players.isEmpty()) {
            WorldProvider worldprovider = this.getWorldServer().provider;

            if (!worldprovider.canRespawnHere()) {
                provider.unloadAllChunks();
            }
        }

        getWorldServer().theProfiler.endSection();// sendCubes
        getWorldServer().theProfiler.endSection();// playerCubeMapUpdatePlayerInstances
    }

    @Override
    public void onCubeLoaded(Cube cube) {
        onCubeGenerated(cube, cube.getInitLevel());
    }

    @Override
    public void onCubeGenerated(Cube cube, CubeInitLevel newLevel) {
        if (newLevel == CubeInitLevel.Lit) {
            var old = loadedCubes.put(cube);

            if (old != null && old != cube) {
                CubicChunks.LOGGER.error("Replaced cube {} with cube {}", old, cube);
            }

            for (var player : players.getPlayerArray()) {
                player.sync.onCubeMarkedDirty(cube.getX(), cube.getY(), cube.getZ());
            }

            var request = cubeLoadRequests.remove(cube);

            if (request != null) {
                request.cancel();
            }
        }

        CubeStatusVisualizer.put(cube.getCoords(), switch (newLevel) {
            case None -> CubeStatus.None;
            case Generated -> CubeStatus.Generated;
            case Populated -> CubeStatus.Populated;
            case Lit -> CubeStatus.Lit;
        });
    }

    @Override
    public void onCubeUnloaded(Cube cube) {
        loadedCubes.remove(cube);

        for (var player : players.getPlayerArray()) {
            player.sync.onCubeMarkedDirty(cube.getX(), cube.getY(), cube.getZ());
        }

        CubeStatusVisualizer.remove(cube.getCoords());
    }

    // CHECKED: 1.10.2-12.18.1.2092
    @Override
    public boolean func_152621_a(int columnX, int columnZ) {
        return isColumnWatched(columnX, columnZ);
    }

    // CHECKED: 1.10.2-12.18.1.2092
    @Override
    public void markBlockForUpdate(int x, int y, int z) {
        Cube cube = provider.getLoadedCube(x >> 4, y >> 4, z >> 4);

        if (cube != null) {
            for (var player : players.getPlayerArray()) {
                player.sync.onBlockMarkedDirty(x, y, z);
            }
        }
    }

    // Note these arguments are in global block coordinates

    public void onSurfaceTracked(Cube cube) {
        for (var player : players.getPlayerArray()) {
            player.sync.onSurfaceTracked(cube);
        }
    }

    public void heightUpdated(int x, int z) {
        Chunk column = provider.getLoadedColumn(x >> 4, z >> 4);

        if (column != null) {
            for (var player : players.getPlayerArray()) {
                player.sync.onColumnHeightMarkedDirty(x, z);
            }
        }
    }

    // CHECKED: 1.10.2-12.18.1.2092
    @Override
    public void addPlayer(EntityPlayerMP player) {
        if (player.worldObj != this.getWorldServer()) {
            CubicChunks.bigWarning(
                "Player world not the same as PlayerCubeMap world! Adding anyway. This is very likely to cause issues! Player "
                    + "world dimension ID: %d, PlayerCubeMap dimension ID: %d",
                player.worldObj.provider.dimensionId,
                getWorldServer().provider.dimensionId);
        } else if (!player.worldObj.playerEntities.contains(player)) {
            CubicChunks.LOGGER.debug(
                "PlayerCubeMap (dimension {}): Adding player to pending to add list",
                getWorldServer().provider.dimensionId);
            pendingPlayerAddToCubeMap.add(player);
            return;
        }

        WatchingPlayer watchingPlayer = new WatchingPlayer(player);
        watchingPlayer.updateManagedPos();

        this.players.addPlayer(watchingPlayer);

        CuboidalCubeSelector.INSTANCE.forAllVisibleCubes(
            watchingPlayer.getManagedCubePos(),
            horizontalViewDistance,
            verticalViewDistance,
            (x, y, z) -> onPlayerStartedViewingCube(watchingPlayer, x, y, z));

        watchingPlayer.sync.flush();
    }

    // CHECKED: 1.10.2-12.18.1.2092
    @Override
    public void removePlayer(EntityPlayerMP player) {
        WatchingPlayer watchingPlayer = this.players.removePlayer(player.getEntityId());

        if (watchingPlayer == null) {
            return;
        }

        // Minecraft does something evil there: this method is called *after* changing the player's position
        // so we need to use managedPosition there
        CubePos playerCubePos = watchingPlayer.getManagedCubePos();

        CuboidalCubeSelector.INSTANCE.forAllVisibleCubes(
            playerCubePos,
            horizontalViewDistance,
            verticalViewDistance,
            (x, y, z) -> onPlayerStoppedViewingCube(watchingPlayer, x, y, z));

        watchingPlayer.sync.flush();
    }

    // CHECKED: 1.10.2-12.18.1.2092
    @Override
    public void updatePlayerPertinentChunks(EntityPlayerMP player) {
        // the player moved
        // if the player moved into a new chunk, update which chunks the player needs to know about
        // then update the list of chunks that need to be sent to the client

        // get the player info
        WatchingPlayer watchingPlayer = this.players.get(player.getEntityId());

        if (watchingPlayer == null) {
            // vanilla sometimes does it, this is normal
            return;
        }

        // did the player move into new cube?
        if (!watchingPlayer.cubePosChanged()) {
            return;
        }

        getWorldServer().theProfiler.startSection("updateMovedPlayer");

        CubePos oldPos = watchingPlayer.getManagedCubePos();
        watchingPlayer.updateManagedPos();
        CubePos newPos = watchingPlayer.getManagedCubePos();

        var delta = CuboidalCubeSelector.INSTANCE.findChanged(
            oldPos,
            newPos,
            horizontalViewDistance,
            verticalViewDistance,
            horizontalViewDistance,
            verticalViewDistance);

        applyWorldVisibilityChanges(watchingPlayer, delta);

        getWorldServer().theProfiler.endSection();// updateMovedPlayer

        // With ChunkGc being separate from PlayerCubeMap, there are 2 issues:
        // Problem 0: Sometimes, a chunk can be generated after CubeWatcher's chunk load callback returns with a null
        // but before ChunkGC call. This means that the cube will get unloaded, even when ChunkWatcher is waiting for
        // it.
        // Problem 1: When chunkGc call is not in this method, sometimes, when a player teleports far away and is
        // unlucky, and ChunkGc runs in the same tick the teleport appears to happen after PlayerCubeMap call, but
        // before ChunkGc call. This means that PlayerCubeMap won't yet have a CubeWatcher for the player cubes at all,
        // so even directly checking for CubeWatchers before unload attempt won't work.
        //
        // While normally not an issue as it will be reloaded soon anyway, it breaks a lot of things if that cube
        // contains the player. Which is not unlikely if the player is what caused generating this cube in the first
        // place
        // for problem #0.
        // So we put ChunkGc here so that we can be sure it has consistent data about player location, and that no
        // chunks are
        // loaded while we aren't looking.
        ((ICubicWorldInternal.Server) getWorldServer()).getCubeCache()
            .getCubeLoader()
            .doGC();
    }

    private void onPlayerStartedViewingCube(WatchingPlayer player, int x, int y, int z) {
        Cube cube = provider.getLoadedCube(x, y, z);

        if (cube == null || !cube.isInitializedToLevel(CubeInitLevel.Lit)) {
            var request = cubeLoadRequests.get(x, y, z);

            if (request == null || request.isCompleted()) {
                cubeLoadRequests.put(provider.loadCubeEagerly(x, y, z, Requirement.LIGHT));
            }
        } else {
            player.sync.onCubeMarkedDirty(x, y, z);
        }
    }

    private void onPlayerStoppedViewingCube(WatchingPlayer player, int x, int y, int z) {
        player.sync.onCubeMarkedDirty(x, y, z);

        if (!isCubeWatched(x, y, z)) {
            var request = cubeLoadRequests.remove(x, y, z);

            if (request != null) {
                request.cancel();
            }
        }
    }

    public boolean isColumnWatched(int columnX, int columnZ) {
        for (var player : players.getPlayerArray()) {
            if (player.isWatchingColumn(columnX, columnZ)) {
                return true;
            }
        }

        return false;
    }

    public boolean isColumnWatched(@Nullable Chunk column) {
        if (column == null) return false;

        return isColumnWatched(column.xPosition, column.zPosition);
    }

    public boolean isCubeWatched(int cubeX, int cubeY, int cubeZ) {
        for (var player : players.getPlayerArray()) {
            if (player.isWatchingCube(cubeX, cubeY, cubeZ)) {
                return true;
            }
        }

        return false;
    }

    public boolean isCubeWatched(@Nullable Cube cube) {
        if (cube == null) return false;

        return isCubeWatched(cube.getX(), cube.getY(), cube.getZ());
    }

    // CHECKED: 1.10.2-12.18.1.2092
    @Override
    public boolean isPlayerWatchingChunk(EntityPlayerMP player, int columnX, int columnZ) {
        var wrapper = players.get(player.getEntityId());

        return wrapper != null && wrapper.isWatchingColumn(columnX, columnZ);
    }

    public boolean isPlayerWatchingCube(EntityPlayerMP player, int cubeX, int cubeY, int cubeZ) {
        var wrapper = players.get(player.getEntityId());

        return wrapper != null && wrapper.isWatchingCube(cubeX, cubeY, cubeZ);
    }

    // CHECKED: 1.10.2-12.18.1.2092
    @Override
    @Deprecated
    public final void func_152622_a(int newHorizontalViewDistance) {
        this.setPlayerViewDistance(newHorizontalViewDistance, verticalViewDistance);
    }

    public final void setPlayerViewDistance(int newHorizontalViewDistance, int newVerticalViewDistance) {
        // this method is called by vanilla before these fields are initialized.
        // and it doesn't really need to be called because in this case
        // it reduces to setting the viewRadius field
        if (this.players == null) {
            return;
        }

        newHorizontalViewDistance = clamp_int(newHorizontalViewDistance, 3, 32);
        newVerticalViewDistance = clamp_int(newVerticalViewDistance, 3, 32);

        if (newHorizontalViewDistance == this.horizontalViewDistance
            && newVerticalViewDistance == this.verticalViewDistance) {
            return;
        }

        int oldHorizontalViewDistance = this.horizontalViewDistance;
        int oldVerticalViewDistance = this.verticalViewDistance;

        for (WatchingPlayer player : this.players.getPlayerArray()) {
            CubePos playerPos = player.getManagedCubePos();

            var delta = CuboidalCubeSelector.INSTANCE.findChanged(
                playerPos,
                playerPos,
                oldHorizontalViewDistance,
                oldVerticalViewDistance,
                newHorizontalViewDistance,
                newVerticalViewDistance);

            applyWorldVisibilityChanges(player, delta);
        }

        this.horizontalViewDistance = newHorizontalViewDistance;
        this.verticalViewDistance = newVerticalViewDistance;
    }

    private void applyWorldVisibilityChanges(WatchingPlayer player, WorldVisibilityChange delta) {
        delta.cubesToLoad.forEach((x, y, z) -> onPlayerStartedViewingCube(player, x, y, z));
        delta.cubesToUnload.forEach((x, y, z) -> onPlayerStoppedViewingCube(player, x, y, z));
    }

    public class WatchingPlayer {

        public final EntityPlayerMP player;
        private double managedPosY;

        @Getter
        private CubePos managedCubePos;

        public final WorldSyncStateMachine sync;

        WatchingPlayer(EntityPlayerMP player) {
            this.player = player;

            sync = new WorldSyncStateMachine(provider, this);
        }

        public boolean isWatchingColumn(int x, int z) {
            return CuboidalCubeSelector.INSTANCE.contains(managedCubePos, horizontalViewDistance, x, z);
        }

        public boolean isWatchingCube(int x, int y, int z) {
            return CuboidalCubeSelector.INSTANCE
                .contains(managedCubePos, horizontalViewDistance, verticalViewDistance, x, y, z);
        }

        void updateManagedPos() {
            this.player.managedPosX = player.posX;
            this.managedPosY = player.posY;
            this.player.managedPosZ = player.posZ;

            managedCubePos = new CubePos(getManagedCubePosX(), getManagedCubePosY(), getManagedCubePosZ());
        }

        int getManagedCubePosX() {
            return blockToCube(this.player.managedPosX);
        }

        int getManagedCubePosY() {
            return blockToCube(this.managedPosY);
        }

        int getManagedCubePosZ() {
            return blockToCube(this.player.managedPosZ);
        }

        boolean cubePosChanged() {
            // did the player move far enough to matter?
            return blockToCube(player.posX) != this.getManagedCubePosX()
                || blockToCube(player.posY) != this.getManagedCubePosY()
                || blockToCube(player.posZ) != this.getManagedCubePosZ();
        }
    }
}
