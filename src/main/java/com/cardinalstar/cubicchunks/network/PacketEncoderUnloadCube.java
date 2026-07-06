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

import javax.annotation.ParametersAreNonnullByDefault;

import net.minecraft.world.World;

import com.cardinalstar.cubicchunks.client.CubeProviderClient;
import com.cardinalstar.cubicchunks.util.CubePos;
import com.cardinalstar.cubicchunks.world.ICubicWorld;
import com.github.bsideup.jabel.Desugar;

@ParametersAreNonnullByDefault
public class PacketEncoderUnloadCube extends CCPacketEncoder<PacketEncoderUnloadCube.PacketUnloadCube> {

    @Desugar
    public record PacketUnloadCube(CubePos pos) implements CCPacket {

        @Override
        public byte getPacketID() {
            return CCPacketEntry.UnloadCube.id;
        }
    }

    public PacketEncoderUnloadCube() {}

    public static PacketUnloadCube createPacket(CubePos cubePos) {
        return new PacketUnloadCube(cubePos);
    }

    @Override
    public byte getPacketID() {
        return CCPacketEntry.UnloadCube.id;
    }

    @Override
    public void writePacket(CCPacketBuffer buffer, PacketUnloadCube packet) {
        buffer.writeCubePos(packet.pos);
    }

    @Override
    public PacketUnloadCube readPacket(CCPacketBuffer buffer) {
        return new PacketUnloadCube(buffer.readCubePos());
    }

    @Override
    public void process(World world, PacketUnloadCube packet) {
        ICubicWorld worldClient = (ICubicWorld) world;
        CubeProviderClient cubeCache = (CubeProviderClient) worldClient.getCubeCache();

        // This apparently makes visual chunk holes much more rare/nonexistent
        cubeCache.getCube(packet.pos)
            .markForRenderUpdate();
        cubeCache.unloadCube(packet.pos);
    }
}
