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
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;

import javax.annotation.ParametersAreNonnullByDefault;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.network.play.server.S23PacketBlockChange;
import net.minecraft.world.World;

import com.cardinalstar.cubicchunks.CubicChunks;
import com.cardinalstar.cubicchunks.client.CubeProviderClient;
import com.cardinalstar.cubicchunks.mixin.early.common.AccessorS23PacketBlockChange;
import com.cardinalstar.cubicchunks.util.AddressTools;
import com.cardinalstar.cubicchunks.util.CubePos;
import com.cardinalstar.cubicchunks.util.Mods;
import com.cardinalstar.cubicchunks.world.core.IColumnInternal;
import com.cardinalstar.cubicchunks.world.cube.BlankCube;
import com.cardinalstar.cubicchunks.world.cube.Cube;
import com.falsepattern.chunk.internal.DataRegistryImpl;
import com.github.bsideup.jabel.Desugar;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import gnu.trove.iterator.TIntIterator;
import gnu.trove.set.TIntSet;
import gnu.trove.set.hash.TIntHashSet;
import it.unimi.dsi.fastutil.shorts.ShortCollection;

@ParametersAreNonnullByDefault
public class PacketEncoderCubeBlockChange extends CCPacketEncoder<PacketEncoderCubeBlockChange.PacketCubeBlockChange> {

    @Desugar
    public record PacketCubeBlockChange(CubePos cubePos, List<S23PacketBlockChange> updates)
        implements CCPacket {

        @Override
        public byte getPacketID() {
            return CCPacketEntry.CubeBlockChange.id;
        }
    }

    public PacketEncoderCubeBlockChange() {}

    @SuppressWarnings("DataFlowIssue")
    public static PacketCubeBlockChange createPacket(Cube cube, ShortCollection localAddresses) {
        CubePos cubePos = cube.getCoords();

        List<S23PacketBlockChange> updates = new ArrayList<>(localAddresses.size());
        TIntSet xzAddresses = new TIntHashSet();

        var addrIter = localAddresses.iterator();

        while (addrIter.hasNext()) {
            short localAddress = addrIter.nextShort();

            int x = AddressTools.getLocalX(localAddress);
            int y = AddressTools.getLocalY(localAddress);
            int z = AddressTools.getLocalZ(localAddress);

            S23PacketBlockChange change = new S23PacketBlockChange();

            int wX = (cube.getX() << 4) + x;
            int wY = (cube.getY() << 4) + y;
            int wZ = (cube.getZ() << 4) + z;

            ((AccessorS23PacketBlockChange) change).setX(wX);
            ((AccessorS23PacketBlockChange) change).setY(wY);
            ((AccessorS23PacketBlockChange) change).setZ(wZ);

            change.field_148883_d = cube.getBlock(x, y, z);
            change.field_148884_e = cube.getBlockMetadata(x, y, z);

            if (Mods.ChunkAPI.isModLoaded()) {
                DataRegistryImpl.writeBlockToPacket(cube.getColumn(), wX, wY, wZ, change);
            }

            updates.add(change);

            xzAddresses.add(AddressTools.getLocalAddress(x, z));
        }

        int[] heightValues = new int[xzAddresses.size()];

        int i = 0;
        TIntIterator it = xzAddresses.iterator();
        while (it.hasNext()) {
            int v = it.next();
            int x = AddressTools.getLocalX(v);
            int z = AddressTools.getLocalZ(v);
            int height = ((IColumnInternal) cube.getColumn()).getTopYWithStaging(x, z);
            v |= height << 8;
            heightValues[i] = v;
            i++;
        }

        return new PacketCubeBlockChange(cubePos, updates);
    }

    @Override
    public byte getPacketID() {
        return CCPacketEntry.CubeBlockChange.id;
    }

    @Override
    public void writePacket(CCPacketBuffer buffer, PacketCubeBlockChange packet) {
        buffer.writeCubePos(packet.cubePos);

        buffer.writeList(packet.updates, (buffer1, value) -> {
            try {
                value.writePacketData(buffer1);
            } catch (IOException e) {
                throw new UncheckedIOException("Could not save S23PacketBlockChange " + value.serialize(), e);
            }
        });
    }

    @Override
    public PacketCubeBlockChange readPacket(CCPacketBuffer buffer) {
        CubePos pos = buffer.readCubePos();

        List<S23PacketBlockChange> updates = buffer.readList(buffer1 -> {
            S23PacketBlockChange packet = new S23PacketBlockChange();

            try {
                packet.readPacketData(buffer1);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }

            return packet;
        });

        return new PacketCubeBlockChange(pos, updates);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void process(World world, PacketCubeBlockChange packet) {
        WorldClient worldClient = (WorldClient) world;
        CubeProviderClient cubeCache = (CubeProviderClient) worldClient.getChunkProvider();

        // get the cube
        Cube cube = cubeCache.getCube(packet.cubePos);
        if (cube instanceof BlankCube) {
            CubicChunks.LOGGER.error("Ignored block update to blank cube {}", packet.cubePos);
            return;
        }

        for (S23PacketBlockChange update : packet.updates) {
            update.processPacket(Minecraft.getMinecraft().thePlayer.sendQueue);
        }
    }
}
