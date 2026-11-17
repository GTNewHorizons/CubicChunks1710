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
package com.cardinalstar.cubicchunks.network;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;

import net.minecraft.block.Block;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.ChunkPosition;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;

import org.jetbrains.annotations.NotNull;

import com.cardinalstar.cubicchunks.lighting.ILightingManager;
import com.cardinalstar.cubicchunks.mixin.api.ICubicWorldInternal;
import com.cardinalstar.cubicchunks.server.chunkio.CubeClientData;
import com.cardinalstar.cubicchunks.util.AddressTools;
import com.cardinalstar.cubicchunks.util.Coords;
import com.cardinalstar.cubicchunks.util.Mods;
import com.cardinalstar.cubicchunks.world.core.IColumnInternal;
import com.cardinalstar.cubicchunks.world.cube.Cube;
import com.falsepattern.chunk.internal.DataRegistryImpl;

@ParametersAreNonnullByDefault
class WorldEncoder {

    private static final ThreadLocal<byte[]> BUFFER = new ThreadLocal<>();

    static void encodeColumn(CCPacketBuffer out, Chunk column) {
        if (!Mods.ChunkAPI.isModLoaded()) {
            // 1. biomes
            out.writeBytes(column.getBiomeArray());
        } else {
            byte[] buffer = getBuffer(DataRegistryImpl.maxPacketSize());

            int written = DataRegistryImpl.writeToBuffer(column, 0, true, buffer);

            out.writeByteArray(buffer, 0, written);
        }

        ((IColumnInternal) column).writeHeightmapDataForClient(out);
    }

    static void decodeColumn(CCPacketBuffer in, Chunk column) {
        if (!Mods.ChunkAPI.isModLoaded()) {
            // 1. biomes
            in.readBytes(column.getBiomeArray());
        } else {
            byte[] buffer = in.readByteArray(getBuffer(DataRegistryImpl.maxPacketSize()));

            DataRegistryImpl.readFromBuffer(column, 0, true, buffer);
        }

        ILightingManager lm = ((ICubicWorldInternal) column.worldObj).getLightingManager();

        int[] oldHeights = new int[Cube.SIZE * Cube.SIZE];

        IColumnInternal columnInternal = (IColumnInternal) column;

        for (int dx = 0; dx < Cube.SIZE; dx++) {
            for (int dz = 0; dz < Cube.SIZE; dz++) {
                oldHeights[AddressTools.getLocalAddress(dx, dz)] = columnInternal.getTopYWithStaging(dx, dz);
            }
        }

        columnInternal.loadClientHeightmapData(in);

        for (int dx = 0; dx < Cube.SIZE; dx++) {
            for (int dz = 0; dz < Cube.SIZE; dz++) {
                int oldY = oldHeights[AddressTools.getLocalAddress(dx, dz)];
                int newY = columnInternal.getTopYWithStaging(dx, dz);
                if (oldY != newY) {
                    lm.updateLightBetween(column, dx, oldY, newY, dz);
                }
            }
        }
    }

    private static byte @NotNull [] getBuffer(int maxSize) {
        byte[] buffer = BUFFER.get();

        if (buffer == null || buffer.length < maxSize) {
            buffer = new byte[maxSize];
            BUFFER.set(buffer);
        }

        return buffer;
    }

    /**
     * Sets a client cube's blocks and biomes from decoded client data and creates its tile entities at the positions
     * the server sent ({@code tiles}, local addresses).
     */
    static void applyCube(Cube cube, World world, @Nullable NBTTagCompound clientData, short[] tiles) {
        cacheTileEntityBlockData(cube);

        CubeClientData.apply(cube, clientData);

        refreshTileEntities(cube, world);

        ExtendedBlockStorage storage = cube.getStorage();
        if (storage != null) {
            createTileEntities(cube, world, storage, tiles);
        }
    }

    private static void cacheTileEntityBlockData(Cube cube) {
        for (TileEntity tileEntity : cube.cubeTileEntityMap.values()) {
            tileEntity.updateContainingBlockInfo();
            tileEntity.getBlockMetadata();
            tileEntity.getBlockType();
        }
    }

    private static void refreshTileEntities(Cube cube, World world) {
        List<TileEntity> invalidTileEntities = new ArrayList<>();

        for (TileEntity tileEntity : cube.cubeTileEntityMap.values()) {
            Block oldBlock = tileEntity.getBlockType();
            int oldMetadata = tileEntity.blockMetadata;
            Block newBlock = cube.getBlock(tileEntity.xCoord, tileEntity.yCoord, tileEntity.zCoord);
            int newMetadata = cube.getBlockMetadata(tileEntity.xCoord, tileEntity.yCoord, tileEntity.zCoord);

            if ((oldBlock != newBlock || oldMetadata != newMetadata) && tileEntity.shouldRefresh(
                oldBlock,
                newBlock,
                oldMetadata,
                newMetadata,
                world,
                tileEntity.xCoord,
                tileEntity.yCoord,
                tileEntity.zCoord)) {
                invalidTileEntities.add(tileEntity);
            }
            tileEntity.updateContainingBlockInfo();
        }

        for (TileEntity tileEntity : invalidTileEntities) {
            tileEntity.invalidate();
        }
    }

    private static void createTileEntities(Cube cube, World world, ExtendedBlockStorage storage, short[] tiles) {
        int minBlockX = Coords.cubeToMinBlock(cube.getX());
        int minBlockY = Coords.cubeToMinBlock(cube.getY());
        int minBlockZ = Coords.cubeToMinBlock(cube.getZ());

        for (short address : tiles) {
            int x = AddressTools.getLocalX(address);
            int y = AddressTools.getLocalY(address);
            int z = AddressTools.getLocalZ(address);

            Block block = storage.getBlockByExtId(x, y, z);
            int metadata = storage.getExtBlockMetadata(x, y, z);
            if (!block.hasTileEntity(metadata)) {
                continue;
            }

            int worldY = minBlockY + y;
            ChunkPosition pos = new ChunkPosition(x, worldY, z);
            TileEntity existing = cube.cubeTileEntityMap.get(pos);
            if (existing != null && !existing.isInvalid()) {
                continue;
            }

            TileEntity tileEntity = block.createTileEntity(world, metadata);
            if (tileEntity != null) {
                tileEntity.xCoord = minBlockX + x;
                tileEntity.yCoord = worldY;
                tileEntity.zCoord = minBlockZ + z;
                cube.addTileEntity(tileEntity);
            }
        }
    }
}
