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

import java.io.IOException;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.Future;

import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.ChunkPosition;
import net.minecraft.world.World;

import com.cardinalstar.cubicchunks.CubicChunks;
import com.cardinalstar.cubicchunks.client.CubeProviderClient;
import com.cardinalstar.cubicchunks.network.PacketEncoderCube.PacketCube;
import com.cardinalstar.cubicchunks.server.chunkio.CubeClientData;
import com.cardinalstar.cubicchunks.util.AddressTools;
import com.cardinalstar.cubicchunks.util.CubePos;
import com.cardinalstar.cubicchunks.util.CubeStatusVisualizer;
import com.cardinalstar.cubicchunks.util.CubeStatusVisualizer.CubeStatus;
import com.cardinalstar.cubicchunks.world.cube.Cube;
import com.github.bsideup.jabel.Desugar;

/**
 * A cube's blocks and biomes ({@link CubeClientData}) and the local addresses of its tile entities on the server, where
 * the client creates its own.
 */
@ParametersAreNonnullByDefault
public class PacketEncoderCube extends CCPacketEncoder<PacketCube> {

    @Desugar
    public record PacketCube(CubePos cubePos, byte[] data, short[] tiles, @Nullable Future<NBTTagCompound> decoded)
        implements CCPacket {

        /** This packet with its client data already being decoded ({@link CubePredecoder}). */
        PacketCube withDecoded(Future<NBTTagCompound> decoding) {
            return new PacketCube(cubePos, data, tiles, decoding);
        }

        @Override
        public byte getPacketID() {
            return CCPacketEntry.Cube.id;
        }
    }

    public PacketEncoderCube() {}

    public static PacketCube createPacket(Cube cube) throws IOException {
        CubeStatusVisualizer.put(cube.getCoords(), CubeStatus.Synced);

        return new PacketCube(cube.getCoords(), CubeClientData.encode(cube), tilePositions(cube), null);
    }

    /** The local addresses of a server cube's tile entities. */
    private static short[] tilePositions(Cube cube) {
        short[] tiles = new short[cube.cubeTileEntityMap.size()];
        int count = 0;
        for (Map.Entry<ChunkPosition, TileEntity> entry : cube.cubeTileEntityMap.entrySet()) {
            if (entry.getValue()
                .isInvalid()) continue;
            ChunkPosition pos = entry.getKey();
            tiles[count++] = (short) AddressTools.getLocalAddress(pos.chunkPosX, pos.chunkPosY, pos.chunkPosZ);
        }
        return count == tiles.length ? tiles : Arrays.copyOf(tiles, count);
    }

    @Override
    public byte getPacketID() {
        return CCPacketEntry.Cube.id;
    }

    @Override
    public void writePacket(CCPacketBuffer buffer, PacketCube packet) {
        buffer.writeCubePos(packet.cubePos);
        buffer.writeByteArray(packet.data);

        buffer.writeVarIntToBuffer(packet.tiles.length);
        for (short tile : packet.tiles) {
            buffer.writeShort(tile);
        }
    }

    @Override
    public PacketCube readPacket(CCPacketBuffer buffer) {
        CubePos pos = buffer.readCubePos();
        byte[] data = buffer.readByteArray();

        // at most one per block
        int count = buffer.readVarIntFromBuffer();
        if (count < 0 || count > Cube.SIZE * Cube.SIZE * Cube.SIZE) {
            throw new IllegalArgumentException("Invalid tile entity count " + count);
        }
        short[] tiles = new short[count];
        for (int i = 0; i < count; i++) {
            tiles[i] = buffer.readShort();
        }

        return new PacketCube(pos, data, tiles, null);
    }

    @Override
    public void process(World world, PacketCube packet) {
        CubeProviderClient cubeCache = (CubeProviderClient) world.getChunkProvider();

        CubePos pos = packet.cubePos;

        NBTTagCompound clientData;
        try {
            clientData = packet.decoded != null ? CubePredecoder.join(packet.decoded) : null;
            if (clientData == null) clientData = CubeClientData.decode(packet.data);
        } catch (IOException | RuntimeException e) {
            CubicChunks.LOGGER.error("Could not decode the cube at {}", pos, e);
            return;
        }

        Cube cube = cubeCache.loadCube(pos); // new cube
        if (cube == null) {
            CubicChunks.LOGGER.error("Out of order cube received! No column for cube at {} exists!", pos);
            return;
        }

        cube.setClientCube();

        WorldEncoder.applyCube(cube, world, clientData, packet.tiles);

        if (!cube.isCubeLoaded()) {
            cube.onCubeLoad();
        }
        cube.markForRenderUpdate();
    }
}
