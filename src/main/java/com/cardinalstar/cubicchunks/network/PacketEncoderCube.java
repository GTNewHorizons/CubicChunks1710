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

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

import com.cardinalstar.cubicchunks.CubicChunks;
import com.cardinalstar.cubicchunks.client.CubeProviderClient;
import com.cardinalstar.cubicchunks.modcompat.angelica.AngelicaInterop;
import com.cardinalstar.cubicchunks.network.PacketEncoderCube.PacketCube;
import com.cardinalstar.cubicchunks.util.CubePos;
import com.cardinalstar.cubicchunks.util.CubeStatusVisualizer;
import com.cardinalstar.cubicchunks.util.CubeStatusVisualizer.CubeStatus;
import com.cardinalstar.cubicchunks.world.cube.Cube;
import com.github.bsideup.jabel.Desugar;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

@ParametersAreNonnullByDefault
public class PacketEncoderCube extends CCPacketEncoder<PacketCube> {

    @Desugar
    public record PacketCube(CubePos cubePos, byte[] data, List<NBTTagCompound> tileEntityTags) implements CCPacket {

        @Override
        public byte getPacketID() {
            return CCPacketEntry.Cube.id;
        }
    }

    public PacketEncoderCube() {}

    public static PacketCube createPacket(Cube cube) {
        CubeStatusVisualizer.put(cube.getCoords(), CubeStatus.Synced);

        ByteBuf cubeData = Unpooled.buffer();

        WorldEncoder.encodeCube(new CCPacketBuffer(cubeData), cube);

        byte[] data = new byte[cubeData.writerIndex()];

        cubeData.readBytes(data);

        List<NBTTagCompound> tileEntityTags = new ArrayList<>();

        if (!cube.getTileEntityMap()
            .isEmpty()) {
            for (TileEntity tileEntity : cube.getTileEntityMap()
                .values()) {
                NBTTagCompound tag = new NBTTagCompound();
                tileEntity.writeToNBT(tag);
                tileEntityTags.add(tag);
            }
        }

        return new PacketCube(cube.getCoords(), data, tileEntityTags);
    }

    @Override
    public byte getPacketID() {
        return CCPacketEntry.Cube.id;
    }

    @Override
    public void writePacket(CCPacketBuffer buffer, PacketCube packet) {
        buffer.writeCubePos(packet.cubePos);

        buffer.writeByteArray(packet.data);

        buffer.writeList(packet.tileEntityTags, CCPacketBuffer::writeCompoundTag);
    }

    @Override
    public PacketCube readPacket(CCPacketBuffer buf) {
        CubePos pos = buf.readCubePos();

        byte[] data = buf.readByteArray();

        List<NBTTagCompound> tileEntityTags = buf.readList(CCPacketBuffer::readCompoundTag);

        return new PacketCube(pos, data, tileEntityTags);
    }

    @Override
    public void process(World world, PacketCube packet) {
        CubeProviderClient cubeCache = (CubeProviderClient) world.getChunkProvider();

        CubePos pos = packet.cubePos;

        Cube cube = cubeCache.loadCube(pos); // new cube
        if (cube == null) {
            CubicChunks.LOGGER.error("Out of order cube received! No column for cube at {} exists!", pos);
            return;
        }

        cube.setClientCube();

        WorldEncoder.decodeCube(new CCPacketBuffer(Unpooled.wrappedBuffer(packet.data)), cube, world);

        cube.markForRenderUpdate();

        if (AngelicaInterop.hasDelegate()) {
            AngelicaInterop.getDelegate()
                .onCubeLoaded(cube.getX(), cube.getY(), cube.getZ());
        }

        for (var tag : packet.tileEntityTags) {
            int blockX = tag.getInteger("x");
            int blockY = tag.getInteger("y");
            int blockZ = tag.getInteger("z");
            TileEntity tileEntity = world.getTileEntity(blockX, blockY, blockZ);

            if (tileEntity != null) {
                tileEntity.readFromNBT(tag);
            }
        }
    }
}
