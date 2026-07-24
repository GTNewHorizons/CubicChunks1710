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

import javax.annotation.ParametersAreNonnullByDefault;

import net.minecraft.world.ChunkCoordIntPair;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.EmptyChunk;

import org.joml.Vector2ic;

import com.cardinalstar.cubicchunks.CubicChunks;
import com.cardinalstar.cubicchunks.client.CubeProviderClient;
import com.cardinalstar.cubicchunks.lighting.ILightingManager;
import com.cardinalstar.cubicchunks.mixin.api.ICubicWorldInternal;
import com.cardinalstar.cubicchunks.mixin.api.ICubicWorldInternal.Client;
import com.cardinalstar.cubicchunks.util.AddressTools;
import com.cardinalstar.cubicchunks.util.BooleanArray2D;
import com.cardinalstar.cubicchunks.world.core.IColumnInternal;
import com.cardinalstar.cubicchunks.world.cube.Cube;
import com.cardinalstar.cubicchunks.world.heightmap.HeightMap3D;
import com.github.bsideup.jabel.Desugar;

import io.netty.buffer.Unpooled;

@ParametersAreNonnullByDefault
public class PacketEncoderHeightMapUpdate extends CCPacketEncoder<PacketEncoderHeightMapUpdate.PacketHeightMapUpdate> {

    @Desugar
    public record PacketHeightMapUpdate(ChunkCoordIntPair chunk, BooleanArray2D updates, List<CCPacketBuffer> data)
        implements CCPacket {

        @Override
        public byte getPacketID() {
            return CCPacketEntry.HeightMapUpdate.id;
        }
    }

    public PacketEncoderHeightMapUpdate() {}

    public static PacketHeightMapUpdate createPacket(Chunk column, BooleanArray2D updates) {
        ChunkCoordIntPair pos = column.getChunkCoordIntPair();

        List<CCPacketBuffer> data = new ArrayList<>();

        HeightMap3D opacityIndex = (HeightMap3D) ((IColumnInternal) column).getOpacityIndex();

        for (Vector2ic v : updates) {
            CCPacketBuffer buffer = new CCPacketBuffer(Unpooled.buffer());

            opacityIndex.writeData(v.x(), v.y(), buffer);

            data.add(buffer);
        }

        return new PacketHeightMapUpdate(pos, updates.clone(), data);
    }

    @Override
    public byte getPacketID() {
        return CCPacketEntry.HeightMapUpdate.id;
    }

    @Override
    public void writePacket(CCPacketBuffer buffer, PacketHeightMapUpdate packet) {
        buffer.writeChunkPos(packet.chunk);

        buffer.writeByteArray(packet.updates.toByteArray());
        buffer.writeList(packet.data, CCPacketBuffer::writeByteBuf);
    }

    @Override
    public PacketHeightMapUpdate readPacket(CCPacketBuffer buffer) {
        ChunkCoordIntPair pos = buffer.readChunkPos();

        BooleanArray2D updates = new BooleanArray2D(16, 16, buffer.readByteArray());
        List<CCPacketBuffer> data = buffer.readList(buf -> new CCPacketBuffer(buf.readByteBuf()));

        return new PacketHeightMapUpdate(pos, updates, data);
    }

    @Override
    public void process(World world, PacketHeightMapUpdate packet) {
        Client worldClient = (Client) world;
        CubeProviderClient cubeCache = worldClient.getCubeCache();

        Chunk column = cubeCache.provideColumn(packet.chunk.chunkXPos, packet.chunk.chunkZPos);
        if (column instanceof EmptyChunk) {
            CubicChunks.LOGGER
                .error("Ignored block update to blank column {},{}", packet.chunk.chunkXPos, packet.chunk.chunkZPos);
            return;
        }

        ILightingManager lm = ((ICubicWorldInternal) column.worldObj).getLightingManager();
        HeightMap3D opacityIndex = (HeightMap3D) ((IColumnInternal) column).getOpacityIndex();

        int[] oldHeights = new int[Cube.SIZE * Cube.SIZE];

        IColumnInternal columnInternal = (IColumnInternal) column;

        for (int dx = 0; dx < Cube.SIZE; dx++) {
            for (int dz = 0; dz < Cube.SIZE; dz++) {
                oldHeights[AddressTools.getLocalAddress(dx, dz)] = columnInternal.getTopYWithStaging(dx, dz);
            }
        }

        int i = 0;

        for (Vector2ic update : packet.updates) {
            opacityIndex.readData(update.x(), update.y(), packet.data.get(i++));
        }

        for (Vector2ic update : packet.updates) {
            int lX = update.x();
            int lZ = update.y();

            int oldY = oldHeights[AddressTools.getLocalAddress(lX, lZ)];
            int newY = columnInternal.getTopYWithStaging(lX, lZ);

            if (oldY != newY) {
                lm.updateLightBetween(column, lX, oldY, newY, lZ);
            }
        }
    }
}
