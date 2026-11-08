package com.cardinalstar.cubicchunks.server;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import net.minecraft.network.Packet;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.ChunkCoordIntPair;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.world.ChunkWatchEvent;

import com.cardinalstar.cubicchunks.CubicChunks;
import com.cardinalstar.cubicchunks.CubicChunksConfig;
import com.cardinalstar.cubicchunks.api.XYZAddressable;
import com.cardinalstar.cubicchunks.api.event.CubeEvent;
import com.cardinalstar.cubicchunks.network.PacketEncoderColumn;
import com.cardinalstar.cubicchunks.network.PacketEncoderCube;
import com.cardinalstar.cubicchunks.network.PacketEncoderCubeBlockChange;
import com.cardinalstar.cubicchunks.network.PacketEncoderHeightMapUpdate;
import com.cardinalstar.cubicchunks.network.PacketEncoderUnloadColumn;
import com.cardinalstar.cubicchunks.network.PacketEncoderUnloadCube;
import com.cardinalstar.cubicchunks.server.CubicPlayerManager.WatchingPlayer;
import com.cardinalstar.cubicchunks.server.chunkio.CubeInitLevel;
import com.cardinalstar.cubicchunks.util.AddressTools;
import com.cardinalstar.cubicchunks.util.BooleanArray2D;
import com.cardinalstar.cubicchunks.util.CubePos;
import com.cardinalstar.cubicchunks.util.HashMap2D;
import com.cardinalstar.cubicchunks.util.HashMap3D;
import com.cardinalstar.cubicchunks.util.HashSet3D;
import com.cardinalstar.cubicchunks.world.cube.Cube;

import it.unimi.dsi.fastutil.shorts.ShortIterator;
import it.unimi.dsi.fastutil.shorts.ShortOpenHashSet;

public class WorldSyncStateMachine {

    private final CubeProviderServer provider;

    private final WatchingPlayer player;

    private static class ColumnData {

        public int syncedCubeCount;
    }

    private final HashMap2D<ColumnData> syncedColumns = new HashMap2D<>();
    private final HashSet3D syncedCubes = new HashSet3D();

    private final HashSet3D dirtyCubes = new HashSet3D();
    private final HashMap3D<ShortOpenHashSet> dirtyBlocks = new HashMap3D<>();
    private final HashMap2D<BooleanArray2D> dirtyHeightCols = new HashMap2D<>();

    public WorldSyncStateMachine(CubeProviderServer provider, WatchingPlayer player) {
        this.provider = provider;
        this.player = player;
    }

    /**
     * Sends and unloads the cubes that changed, block changes and height map updates; the client's cost is spread over
     * the next ticks ({@link #sendNearest}).
     */
    public void flush() {
        // height map changes first: a cube loaded on the server this tick reaches the client after its heights
        for (var e : dirtyHeightCols.fastEntryIterable()) {
            if (!syncedColumns.containsKey(e.getX(), e.getZ())) {
                CubicChunks.LOGGER.trace(
                    "Tried to sync {} height map updates to a column at {} which was not synced",
                    e.getValue()
                        .cardinality(),
                    new ChunkCoordIntPair(e.getX(), e.getZ()));

                continue;
            }

            // also when the column's unload is still waiting: it stays on the client if it comes back into view
            Chunk column = provider.getLoadedColumn(e.getX(), e.getZ());

            if (column != null) {
                PacketEncoderHeightMapUpdate.createPacket(column, e.getValue())
                    .sendToPlayer(player.player);
            }
        }

        List<CubePos> changed = new ArrayList<>();
        // the cubes to send, or null to unload
        List<Cube> changedCubes = new ArrayList<>();
        Iterator<XYZAddressable> cubeIter = dirtyCubes.fastIterator();

        while (cubeIter.hasNext()) {
            var pos = cubeIter.next();

            Cube cube = provider.getLoadedCube(pos.getX(), pos.getY(), pos.getZ());

            boolean isWatched = syncedCubes.contains(pos);

            boolean watchable = cube != null && cube.isInitializedToLevel(CubeInitLevel.Lit)
                && player.isWatchingCube(pos.getX(), pos.getY(), pos.getZ());

            if (isWatched != watchable) {
                changed.add(new CubePos(pos));
                changedCubes.add(watchable ? cube : null);
            }
        }

        dirtyCubes.clear();
        sendNearest(changed, changedCubes);

        for (var e : dirtyBlocks.fastEntryIterable()) {
            if (!syncedCubes.contains(e.getX(), e.getY(), e.getZ())) {
                CubicChunks.LOGGER.trace(
                    "Tried to sync {} block updates to a cube at {} which was not synced",
                    e.getValue()
                        .size(),
                    new CubePos(e.getX(), e.getY(), e.getZ()));

                continue;
            }

            // also when the cube's unload is still waiting: it stays on the client if it comes back into view
            Cube cube = provider.getLoadedCube(e.getX(), e.getY(), e.getZ());

            if (cube != null) {
                PacketEncoderCubeBlockChange.createPacket(cube, e.getValue())
                    .sendToPlayer(player.player);

                ShortIterator blocks = e.getValue()
                    .iterator();

                while (blocks.hasNext()) {
                    short pos = blocks.nextShort();

                    int rx = AddressTools.getLocalX(pos);
                    int ry = AddressTools.getLocalY(pos);
                    int rz = AddressTools.getLocalZ(pos);

                    TileEntity te = cube.getTileEntityUnsafe(rx, (e.getY() << 4) + ry, rz);

                    if (te != null) {
                        Packet desc = te.getDescriptionPacket();

                        if (desc != null) {
                            player.player.playerNetServerHandler.sendPacket(desc);
                        }
                    }
                }
            }
        }

        dirtyBlocks.clear();
        dirtyHeightCols.clear();
    }

    /**
     * Sends and unloads cubes, nearest to the player first. Clients apply received cubes on their game thread, and
     * crossing into a new column brings a whole slab of cubes into view and takes another one out, so a tick spends at
     * most what {@link CubicChunksConfig#maxCubesSentPerTick} cubes with blocks cost a client, or a twentieth of
     * everything waiting if that is more, so that a backlog (joining, teleporting) is done within a second. The others
     * stay dirty and are checked again next tick; a cube whose unload waits stays on the client and gets its block
     * changes.
     */
    private void sendNearest(List<CubePos> positions, List<Cube> cubes) {
        long[] costs = new long[positions.size()];
        long waiting = 0;
        for (int i = 0; i < costs.length; i++) {
            costs[i] = cubes.get(i) != null ? sendCost(cubes.get(i)) : unloadCost(positions.get(i));
            waiting += costs[i];
        }

        long budget = Math.max(8L * CubicChunksConfig.maxCubesSentPerTick, (waiting + 19) / 20);

        Integer[] order = new Integer[positions.size()];
        for (int i = 0; i < order.length; i++) {
            order[i] = i;
        }
        if (waiting > budget) {
            CubePos center = player.getManagedCubePos();
            Arrays.sort(order, Comparator.comparingLong(i -> distanceSquared(center, positions.get(i))));
        }

        Set<Chunk> sentColumns = Collections.newSetFromMap(new IdentityHashMap<>());

        for (int i : order) {
            CubePos pos = positions.get(i);

            if (budget <= 0) {
                dirtyCubes.add(pos.getX(), pos.getY(), pos.getZ());
                continue;
            }

            budget -= costs[i];
            if (cubes.get(i) != null) {
                sendCube(cubes.get(i));
                sentColumns.add(
                    cubes.get(i)
                        .getColumn());
            } else {
                unloadCube(pos);
            }
        }

        // entities skipped while the client did not have their cubes, as vanilla does for the chunks it sends
        for (Chunk column : sentColumns) {
            provider.worldObj.getEntityTracker()
                .func_85172_a(player.player, column);
        }
    }

    /** What applying a cube costs a client, in eighths of a cube with blocks. */
    private static long sendCost(Cube cube) {
        ExtendedBlockStorage storage = cube.getStorage();
        return storage != null && !storage.isEmpty() ? 8 : 1;
    }

    /** What unloading a cube costs a client, in eighths of a received cube with blocks: about a quarter with blocks. */
    private long unloadCost(CubePos pos) {
        Cube cube = provider.getLoadedCube(pos.getX(), pos.getY(), pos.getZ());
        return cube != null && cube.getStorage() != null
            && !cube.getStorage()
                .isEmpty() ? 2 : 1;
    }

    private static long distanceSquared(CubePos a, CubePos b) {
        long dx = a.getX() - b.getX(), dy = a.getY() - b.getY(), dz = a.getZ() - b.getZ();
        return dx * dx + dy * dy + dz * dz;
    }

    /**
     * The player leaves this world, so nothing new is sent. Every synced cube and column gets its UnWatch. Unload
     * packets go out only if the client keeps this world: on a dimension change it has already been told about the new
     * one, where they would apply to the wrong world.
     */
    public void onPlayerLeft(boolean clientKeepsWorld) {
        List<CubePos> synced = new ArrayList<>();
        Iterator<XYZAddressable> cubes = syncedCubes.fastIterator();
        while (cubes.hasNext()) {
            synced.add(new CubePos(cubes.next()));
        }

        for (CubePos pos : synced) {
            stopWatching(pos, clientKeepsWorld);
        }

        dirtyCubes.clear();
        dirtyBlocks.clear();
        dirtyHeightCols.clear();
    }

    private void unloadCube(CubePos pos) {
        stopWatching(pos, true);
    }

    private void stopWatching(CubePos pos, boolean sendUnload) {
        if (sendUnload) {
            PacketEncoderUnloadCube.createPacket(pos)
                .sendToPlayer(player.player);
        }

        MinecraftForge.EVENT_BUS.post(new CubeEvent.UnWatch(provider.worldObj, pos, player.player));

        syncedCubes.remove(pos.getX(), pos.getY(), pos.getZ());

        ColumnData columnData = syncedColumns.get(pos.getX(), pos.getZ());

        columnData.syncedCubeCount--;

        if (columnData.syncedCubeCount <= 0) {
            syncedColumns.remove(pos.getX(), pos.getZ());

            if (sendUnload) {
                PacketEncoderUnloadColumn.createPacket(pos.getX(), pos.getZ())
                    .sendToPlayer(player.player);
            }

            MinecraftForge.EVENT_BUS
                .post(new ChunkWatchEvent.UnWatch(new ChunkCoordIntPair(pos.getX(), pos.getZ()), player.player));
        }
    }

    private void sendCube(Cube cube) {
        CubePos pos = cube.getCoords();

        ColumnData columnData = syncedColumns.get(pos.getX(), pos.getZ());

        if (columnData == null) {
            columnData = new ColumnData();
            syncedColumns.put(pos.getX(), pos.getZ(), columnData);

            PacketEncoderColumn.createPacket(cube.getColumn())
                .sendToPlayer(player.player);

            MinecraftForge.EVENT_BUS
                .post(new ChunkWatchEvent.Watch(new ChunkCoordIntPair(pos.getX(), pos.getZ()), player.player));
        }

        columnData.syncedCubeCount++;
        syncedCubes.add(pos.getX(), pos.getY(), pos.getZ());

        sendCubeData(cube);

        dirtyBlocks.remove(pos.getX(), pos.getY(), pos.getZ());

        MinecraftForge.EVENT_BUS.post(new CubeEvent.Watch(provider.worldObj, cube, player.player));
    }

    /** Whether the client has been sent this cube, and has not been told to unload it. */
    public boolean isSynced(int x, int y, int z) {
        return syncedCubes.contains(x, y, z);
    }

    public void onCubeMarkedDirty(int x, int y, int z) {
        dirtyCubes.add(x, y, z);
    }

    public void onColumnHeightMarkedDirty(int x, int z) {
        dirtyHeightCols.computeIfAbsent(x >> 4, z >> 4, (x1, z1) -> new BooleanArray2D(16, 16))
            .set2d(x & 0xF, z & 0xF);
    }

    public void onBlockMarkedDirty(int x, int y, int z) {
        dirtyBlocks.computeIfAbsent(x >> 4, y >> 4, z >> 4, (x1, y1, z1) -> new ShortOpenHashSet())
            .add((short) AddressTools.getLocalAddress(x, y, z));
    }

    public void resendCube(Cube cube) {
        CubePos pos = cube.getCoords();

        if (syncedCubes.contains(pos) && player.isWatchingCube(pos.getX(), pos.getY(), pos.getZ())) {
            sendCubeData(cube);
        }
    }

    private void sendCubeData(Cube cube) {
        try {
            PacketEncoderCube.createPacket(cube)
                .sendToPlayer(player.player);
        } catch (IOException e) {
            CubicChunks.LOGGER.error("Could not encode the cube at {} for {}", cube.getCoords(), player.player, e);
            return;
        }

        cube.cubeTileEntityMap.forEach((tePos, te) -> {
            Packet desc = te.getDescriptionPacket();

            if (desc != null) {
                player.player.playerNetServerHandler.sendPacket(desc);
            }
        });
    }
}
