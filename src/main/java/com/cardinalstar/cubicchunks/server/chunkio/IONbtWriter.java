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
package com.cardinalstar.cubicchunks.server.chunkio;

import static net.minecraftforge.common.MinecraftForge.EVENT_BUS;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.ParametersAreNonnullByDefault;

import net.minecraft.block.Block;
import net.minecraft.entity.Entity;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.NextTickListEntry;
import net.minecraft.world.WorldServer;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;
import net.minecraftforge.event.world.ChunkDataEvent;

import org.apache.logging.log4j.Level;

import com.cardinalstar.cubicchunks.CubicChunks;
import com.cardinalstar.cubicchunks.api.IColumn;
import com.cardinalstar.cubicchunks.api.IHeightMap;
import com.cardinalstar.cubicchunks.api.event.CubeEvent;
import com.cardinalstar.cubicchunks.api.event.CubeEvent.DataLoad;
import com.cardinalstar.cubicchunks.api.event.CubeEvent.DataSave;
import com.cardinalstar.cubicchunks.lighting.ILightingManager;
import com.cardinalstar.cubicchunks.mixin.api.ICubicWorldInternal;
import com.cardinalstar.cubicchunks.network.CCPacketBuffer;
import com.cardinalstar.cubicchunks.util.Coords;
import com.cardinalstar.cubicchunks.util.Mods;
import com.cardinalstar.cubicchunks.world.core.ClientHeightMap;
import com.cardinalstar.cubicchunks.world.core.ServerHeightMap;
import com.cardinalstar.cubicchunks.world.cube.Cube;
import com.falsepattern.chunk.internal.DataRegistryImpl;

import cpw.mods.fml.common.FMLLog;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.PooledByteBufAllocator;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;

@ParametersAreNonnullByDefault
class IONbtWriter {

    static NBTTagCompound write(Chunk column) {
        NBTTagCompound columnNbt = new NBTTagCompound();

        NBTTagCompound level = new NBTTagCompound();
        columnNbt.setTag("Level", level);

        writeBaseColumn(column, level);
        writeOpacityIndex(column, level);

        if (Mods.ChunkAPI.isModLoaded()) {
            DataRegistryImpl.writeChunkToNBT(column, columnNbt);
        } else {
            writeBiomes(column, level);
        }

        EVENT_BUS.post(new ChunkDataEvent.Save(column, columnNbt));

        return columnNbt;
    }

    static NBTTagCompound write(final Cube cube) {
        NBTTagCompound cubeNbt = new NBTTagCompound();
        // Added to preserve compatibility with vanilla NBT chunk format.
        NBTTagCompound level = new NBTTagCompound();
        cubeNbt.setTag("Level", level);
        writeBaseCube(cube, level);

        if (cube.getStorage() != null) {
            NBTTagList sections = new NBTTagList();
            level.setTag("Sections", sections);

            NBTTagCompound section = new NBTTagCompound();
            sections.appendTag(section);

            if (!Mods.ChunkAPI.isModLoaded()) {
                writeBlocks(cube, section);
            } else {
                DataRegistryImpl.writeSubChunkToNBT(cube.getColumn(), cube.getStorage(), section);
            }
        }

        writeEntities(cube, level);
        writeTileEntities(cube, level);
        writeScheduledTicks(cube, level);
        writeLightingInfo(cube, level);
        writeBiomes(cube, level);

        EVENT_BUS.post(new DataSave(cube.getWorld(), cube, cubeNbt));

        return cubeNbt;
    }

    private static void writeBaseColumn(Chunk column, NBTTagCompound nbt) {// coords
        nbt.setInteger("x", column.xPosition);
        nbt.setInteger("z", column.zPosition);

        // column properties
        nbt.setByte("v", (byte) 1);
        nbt.setLong("InhabitedTime", column.inhabitedTime);
    }

    private static void writeBiomes(Chunk column, NBTTagCompound nbt) {// biomes
        nbt.setByteArray("Biomes", column.getBiomeArray());
    }

    private static void writeOpacityIndex(Chunk column, NBTTagCompound nbt) {// light index
        IHeightMap hmap = ((IColumn) column).getOpacityIndex();
        if (hmap instanceof ServerHeightMap) {
            nbt.setByteArray("OpacityIndex", ((ServerHeightMap) hmap).getData());
        } else {
            nbt.setByteArray("OpacityIndexClient", ((ClientHeightMap) hmap).getData());
        }
    }

    private static void writeBaseCube(Cube cube, NBTTagCompound cubeNbt) {
        cubeNbt.setByte("v", (byte) 1);

        // coords
        cubeNbt.setInteger("x", cube.getX());
        cubeNbt.setInteger("y", cube.getY());
        cubeNbt.setInteger("z", cube.getZ());

        // save the worldgen stage and the target stage
        cubeNbt.setShort("population", cube.getPopulationStatus());
        cubeNbt.setBoolean("isSurfaceTracked", cube.isSurfaceTracked());

        cubeNbt.setBoolean("initLightDone", cube.isInitialLightingDone());
    }

    private static void writeBlocks(Cube cube, NBTTagCompound section) {
        ExtendedBlockStorage ebs = cube.getStorage();
        assert ebs != null;

        section.setByteArray("Blocks", ebs.getBlockLSBArray());

        if (ebs.getBlockMSBArray() != null) {
            section.setByteArray("Add", ebs.getBlockMSBArray().data);
        }
        section.setByteArray("Data", ebs.getMetadataArray().data);

        section.setByteArray("BlockLight", ebs.getBlocklightArray().data);

        if (!cube.getWorld().provider.hasNoSky) {
            section.setByteArray("SkyLight", ebs.getSkylightArray().data);
        }
    }

    private static void writeEntities(Cube cube, NBTTagCompound cubeNbt) {// entities
        cube.hasEntities = false;
        NBTTagList entityTagList = new NBTTagList();
        NBTTagCompound entityCompound;
        for (Entity entity : cube.getEntityContainer()) {
            entityCompound = new NBTTagCompound();

            try {
                if (entity.writeToNBTOptional(entityCompound)) {
                    cube.hasEntities = true;
                    entityTagList.appendTag(entityCompound);

                    int cubeX = Coords.getCubeXForEntity(entity);
                    int cubeY = Coords.getCubeYForEntity(entity);
                    int cubeZ = Coords.getCubeZForEntity(entity);
                    if (cubeX != cube.getX() || cubeY != cube.getY() || cubeZ != cube.getZ()) {
                        CubicChunks.LOGGER.warn(
                            String.format(
                                "Saved entity %s in cube (%d,%d,%d) to cube (%d,%d,%d)! Entity thinks its in (%d,%d,%d)",
                                entity.getClass()
                                    .getName(),
                                cubeX,
                                cubeY,
                                cubeZ,
                                cube.getX(),
                                cube.getY(),
                                cube.getZ(),
                                entity.chunkCoordX,
                                entity.chunkCoordY,
                                entity.chunkCoordZ));
                    }
                }
            } catch (Exception e) {
                FMLLog.log(
                    Level.ERROR,
                    e,
                    "An Entity type %s has thrown an exception trying to write state. It will not persist. Report this to the mod author",
                    entity.getClass()
                        .getName());
            }
        }
        cubeNbt.setTag("Entities", entityTagList);
    }

    private static void writeTileEntities(Cube cube, NBTTagCompound cubeNbt) {// tile entities
        NBTTagList nbtTileEntities = new NBTTagList();
        cubeNbt.setTag("TileEntities", nbtTileEntities);
        for (TileEntity blockEntity : cube.getTileEntityMap()
            .values()) {
            NBTTagCompound nbtTileEntity = new NBTTagCompound();
            blockEntity.writeToNBT(nbtTileEntity);
            nbtTileEntities.appendTag(nbtTileEntity);
        }
    }

    private static void writeScheduledTicks(Cube cube, NBTTagCompound cubeNbt) {// scheduled block ticks
        Iterable<NextTickListEntry> scheduledTicks = getScheduledTicks(cube);
        long time = cube.getWorld()
            .getTotalWorldTime();

        NBTTagList nbtTicks = new NBTTagList();
        cubeNbt.setTag("TileTicks", nbtTicks);
        for (NextTickListEntry scheduledTick : scheduledTicks) {
            NBTTagCompound nbtScheduledTick = new NBTTagCompound();
            nbtScheduledTick.setInteger("i", Block.getIdFromBlock(scheduledTick.func_151351_a()));
            nbtScheduledTick.setInteger("x", scheduledTick.xCoord);
            nbtScheduledTick.setInteger("y", scheduledTick.yCoord);
            nbtScheduledTick.setInteger("z", scheduledTick.zCoord);
            nbtScheduledTick.setInteger("t", (int) (scheduledTick.scheduledTime - time));
            nbtScheduledTick.setInteger("p", scheduledTick.priority);
            nbtTicks.appendTag(nbtScheduledTick);
        }
    }

    private static void writeLightingInfo(Cube cube, NBTTagCompound cubeNbt) {
        ILightingManager lightingManager = ((ICubicWorldInternal) cube.getWorld()).getLightingManager();
        cubeNbt.setString("LightingInfoType", lightingManager.getId());
        NBTTagCompound lightingInfo = new NBTTagCompound();
        cubeNbt.setTag("LightingInfo", lightingInfo);
        lightingManager.writeToNbt(cube, lightingInfo);
    }

    private static void writeBiomes(Cube cube, NBTTagCompound nbt) {
        ByteBuf buffer = PooledByteBufAllocator.DEFAULT.buffer();
        try {
            cube.writeBiomeArray(new CCPacketBuffer(buffer));

            byte[] data = new byte[buffer.writerIndex()];
            buffer.readBytes(data);

            nbt.setByteArray("Biomes", data);
        } finally {
            buffer.release();
        }
    }

    private static List<NextTickListEntry> getScheduledTicks(Cube cube) {
        ArrayList<NextTickListEntry> out = new ArrayList<>();

        // make sure this is a server, otherwise don't save these, writing to client cache
        if (!(cube.getWorld() instanceof WorldServer)) {
            return out;
        }
        WorldServer worldServer = cube.getWorld();

        ObjectOpenHashSet<NextTickListEntry> ticks = ((ICubicWorldInternal.Server) worldServer).getScheduledTicks()
            .getForCube(cube.getCoords());
        if (ticks != null) {
            out.addAll(ticks);
        }

        return out;
    }
}
