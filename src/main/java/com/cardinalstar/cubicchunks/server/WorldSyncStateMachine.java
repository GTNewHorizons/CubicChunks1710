package com.cardinalstar.cubicchunks.server;

import java.util.Iterator;

import net.minecraft.network.Packet;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.ChunkCoordIntPair;
import net.minecraft.world.chunk.Chunk;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.world.ChunkWatchEvent;

import com.cardinalstar.cubicchunks.CubicChunks;
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

    public void flush() {
        Iterator<XYZAddressable> cubeIter = dirtyCubes.fastIterator();

        while (cubeIter.hasNext()) {
            var pos = cubeIter.next();

            Cube cube = provider.getLoadedCube(pos.getX(), pos.getY(), pos.getZ());

            boolean isWatched = syncedCubes.contains(pos);

            boolean watchable = cube != null && cube.isInitializedToLevel(CubeInitLevel.Lit)
                && player.isWatchingCube(pos.getX(), pos.getY(), pos.getZ());

            if (isWatched && !watchable) {
                CubePos cubePos = new CubePos(pos);

                PacketEncoderUnloadCube.createPacket(cubePos)
                    .sendToPlayer(player.player);

                MinecraftForge.EVENT_BUS.post(new CubeEvent.UnWatch(provider.worldObj, cubePos, player.player));

                syncedCubes.remove(pos);

                ColumnData columnData = syncedColumns.get(pos.getX(), pos.getZ());

                columnData.syncedCubeCount--;

                if (columnData.syncedCubeCount <= 0) {
                    syncedColumns.remove(pos.getX(), pos.getZ());

                    PacketEncoderUnloadColumn.createPacket(pos.getX(), pos.getZ())
                        .sendToPlayer(player.player);

                    MinecraftForge.EVENT_BUS.post(
                        new ChunkWatchEvent.UnWatch(new ChunkCoordIntPair(pos.getX(), pos.getZ()), player.player));
                }
            } else if (!isWatched && watchable) {
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
                syncedCubes.add(pos);

                PacketEncoderCube.createPacket(cube)
                    .sendToPlayer(player.player);

                cube.cubeTileEntityMap.forEach((tePos, te) -> {
                    Packet desc = te.getDescriptionPacket();

                    if (desc != null) {
                        player.player.playerNetServerHandler.sendPacket(desc);
                    }
                });

                dirtyBlocks.remove(pos.getX(), pos.getY(), pos.getZ());

                MinecraftForge.EVENT_BUS.post(new CubeEvent.Watch(provider.worldObj, cube, player.player));
            }
        }

        for (var e : dirtyBlocks.fastEntryIterable()) {
            if (!syncedCubes.contains(e.getX(), e.getY(), e.getZ())) {
                CubicChunks.LOGGER.trace(
                    "Tried to sync {} block updates to a cube at {} which was not synced",
                    e.getValue()
                        .size(),
                    new CubePos(e.getX(), e.getY(), e.getZ()));

                continue;
            }

            if (player.isWatchingCube(e.getX(), e.getY(), e.getZ())) {
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
        }

        for (var e : dirtyHeightCols.fastEntryIterable()) {
            if (!syncedColumns.containsKey(e.getX(), e.getZ())) {
                CubicChunks.LOGGER.trace(
                    "Tried to sync {} height map updates to a column at {} which was not synced",
                    e.getValue()
                        .cardinality(),
                    new ChunkCoordIntPair(e.getX(), e.getZ()));

                continue;
            }

            if (player.isWatchingColumn(e.getX(), e.getZ())) {
                Chunk column = provider.getLoadedColumn(e.getX(), e.getZ());

                if (column != null) {
                    PacketEncoderHeightMapUpdate.createPacket(e.getValue(), column)
                        .sendToPlayer(player.player);
                }
            }
        }

        dirtyCubes.clear();
        dirtyBlocks.clear();
        dirtyHeightCols.clear();
    }

    public void onCubeMarkedDirty(int x, int y, int z) {
        dirtyCubes.add(x, y, z);
    }

    public void onColumnHeightMarkedDirty(int x, int z) {
        dirtyHeightCols.computeIfAbsent(x >> 4, z >> 4, (x1, z1) -> new BooleanArray2D(16, 16))
            .set(x & 0xF, z & 0xF);
    }

    public void onBlockMarkedDirty(int x, int y, int z) {
        dirtyBlocks.computeIfAbsent(x >> 4, y >> 4, z >> 4, (x1, y1, z1) -> new ShortOpenHashSet())
            .add((short) AddressTools.getLocalAddress(x, y, z));
    }
}
