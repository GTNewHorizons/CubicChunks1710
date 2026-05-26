package com.cardinalstar.cubicchunks.server;

import java.util.Iterator;

import net.minecraft.world.ChunkCoordIntPair;
import net.minecraft.world.chunk.Chunk;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.world.ChunkWatchEvent;

import com.cardinalstar.cubicchunks.CubicChunks;
import com.cardinalstar.cubicchunks.api.XYZAddressable;
import com.cardinalstar.cubicchunks.api.event.CubeEvent;
import com.cardinalstar.cubicchunks.network.PacketEncoderColumn;
import com.cardinalstar.cubicchunks.network.PacketEncoderCubeBlockChange;
import com.cardinalstar.cubicchunks.network.PacketEncoderCubes;
import com.cardinalstar.cubicchunks.network.PacketEncoderHeightMapUpdate;
import com.cardinalstar.cubicchunks.network.PacketEncoderUnloadColumn;
import com.cardinalstar.cubicchunks.network.PacketEncoderUnloadCube;
import com.cardinalstar.cubicchunks.server.CubicPlayerManager.WatchingPlayer;
import com.cardinalstar.cubicchunks.server.chunkio.CubeInitLevel;
import com.cardinalstar.cubicchunks.util.AddressTools;
import com.cardinalstar.cubicchunks.util.BlockPosMap;
import com.cardinalstar.cubicchunks.util.BlockPosSet;
import com.cardinalstar.cubicchunks.util.BooleanArray2D;
import com.cardinalstar.cubicchunks.util.ChunkMap;
import com.cardinalstar.cubicchunks.util.CubePos;
import com.cardinalstar.cubicchunks.world.cube.Cube;

import it.unimi.dsi.fastutil.shorts.ShortOpenHashSet;

public class WorldSyncStateMachine {

    private final CubeProviderServer provider;

    private final WatchingPlayer player;

    private static class ColumnData {

        public int syncedCubeCount;
    }

    private final ChunkMap<ColumnData> syncedColumns = new ChunkMap<>();
    private final BlockPosSet syncedCubes = new BlockPosSet();

    private final BlockPosSet dirtyCubes = new BlockPosSet();
    private final BlockPosMap<ShortOpenHashSet> dirtyBlocks = new BlockPosMap<>();
    private final ChunkMap<BooleanArray2D> dirtyHeightCols = new ChunkMap<>();

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

                        MinecraftForge.EVENT_BUS.post(
                            new ChunkWatchEvent.Watch(new ChunkCoordIntPair(pos.getX(), pos.getZ()), player.player));
                }

                columnData.syncedCubeCount++;
                syncedCubes.add(pos);

                PacketEncoderCubes.createPacket(cube)
                    .sendToPlayer(player.player);

                MinecraftForge.EVENT_BUS.post(new CubeEvent.Watch(provider.worldObj, cube, player.player));
            }
        }

        for (var e : dirtyBlocks.fastEntryIterable()) {
            if (!syncedCubes.contains(e.getBlockX(), e.getBlockY(), e.getBlockZ())) {
                CubicChunks.LOGGER.trace(
                    "Tried to sync {} block updates to a cube at {} which was not synced",
                    e.getValue()
                        .size(),
                    new CubePos(e.getBlockX(), e.getBlockY(), e.getBlockZ()));

                continue;
            }

            if (player.isWatchingCube(e.getBlockX(), e.getBlockY(), e.getBlockZ())) {
                Cube cube = provider.getLoadedCube(e.getBlockX(), e.getBlockY(), e.getBlockZ());

                if (cube != null) {
                    PacketEncoderCubeBlockChange.createPacket(cube, e.getValue())
                        .sendToPlayer(player.player);
                }
            }
        }

        for (var e : dirtyHeightCols.fastEntryIterable()) {
            if (!syncedColumns.containsKey(e.getChunkX(), e.getChunkZ())) {
                CubicChunks.LOGGER.trace(
                    "Tried to sync {} height map updates to a column at {} which was not synced",
                    e.getValue()
                        .cardinality(),
                    new ChunkCoordIntPair(e.getChunkX(), e.getChunkZ()));

                continue;
            }

            if (player.isWatchingColumn(e.getChunkX(), e.getChunkZ())) {
                Chunk column = provider.getLoadedColumn(e.getChunkX(), e.getChunkZ());

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
