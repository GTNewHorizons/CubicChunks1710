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
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.NibbleArray;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;

import org.jetbrains.annotations.NotNull;

import com.cardinalstar.cubicchunks.CubicChunks;
import com.cardinalstar.cubicchunks.lighting.ILightingManager;
import com.cardinalstar.cubicchunks.mixin.api.ICubicWorldInternal;
import com.cardinalstar.cubicchunks.util.AddressTools;
import com.cardinalstar.cubicchunks.util.Coords;
import com.cardinalstar.cubicchunks.util.Mods;
import com.cardinalstar.cubicchunks.world.core.ClientHeightMap;
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

        if (in.readableBytes() > 0) {
            ((IColumnInternal) column).loadClientHeightmapData(in);
        }
    }

    static void encodeCube(CCPacketBuffer out, Cube cube) {
        final boolean capi = Mods.ChunkAPI.isModLoaded();

        byte[] buffer = null;

        if (capi) {
            buffer = getBuffer(DataRegistryImpl.maxPacketSizeCubic());
        }

        ExtendedBlockStorage storage = cube.getStorage();

        boolean empty = storage == null || cube.isEmpty();

        byte flags = 0;
        if (empty) flags |= 0b1;

        // 1. emptiness
        out.writeByte(flags);

        if (!empty && !capi) {
            // 2. block IDs and metadata
            out.writeBytes(storage.getBlockLSBArray());
            NibbleArray msb = storage.getBlockMSBArray();

            out.writeBoolean(msb != null);
            if (msb != null) {
                out.writeBytes(msb.data);
            }

            out.writeBytes(storage.getMetadataArray().data);

            // 3. block light
            out.writeBytes(storage.getBlocklightArray().data);

            // 4. sky light
            if (!cube.getWorld().provider.hasNoSky) {
                out.writeBytes(storage.getSkylightArray().data);
            }
        }

        // 5. heightmap and bottom-block-y. Each non-empty cube has a chance to update this data.
        // Trying to keep track of when it changes would be complex, so send all cubes
        if (!empty) {
            ((IColumnInternal) cube.getColumn()).writeHeightmapDataForClient(out);
        }

        // 6. biomes
        cube.writeBiomeArray(out);

        if (!empty && capi) {
            int written = DataRegistryImpl.writeToBufferCubic(cube.getColumn(), storage, buffer);

            out.writeByteArray(buffer, 0, written);
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

    static void decodeCube(CCPacketBuffer in, Cube cube, World world) {
        final boolean capi = Mods.ChunkAPI.isModLoaded();

        int[] oldHeights = new int[Cube.SIZE * Cube.SIZE];

        byte[] buffer;
        if (capi) {
            buffer = getBuffer(DataRegistryImpl.maxPacketSizeCubic());
        } else {
            buffer = new byte[0];
        }

        byte flags = in.readByte();

        boolean empty = (flags & 0b1) != 0;

        ExtendedBlockStorage storage = null;

        if (!empty) {
            storage = new ExtendedBlockStorage(Coords.cubeToMinBlock(cube.getY()), !cube.getWorld().provider.hasNoSky);
        }

        cube.setStorageFromSave(storage);

        if (!empty && !capi) {
            // 2. Block IDs and metadata
            byte[] lsbData = storage.getBlockLSBArray();
            in.readBytes(lsbData);

            boolean hasMsb = in.readBoolean();
            if (hasMsb) {
                if (storage.getBlockMSBArray() == null) {
                    storage.createBlockMSBArray();
                }

                byte[] msbData = storage.getBlockMSBArray().data;
                in.readBytes(msbData);
            }
            byte[] meta = storage.getMetadataArray().data;
            in.readBytes(meta);

            // 3. block light
            in.readBytes(storage.getBlocklightArray().data);

            // 4. sky light
            if (!cube.getWorld().provider.hasNoSky) {
                in.readBytes(storage.getSkylightArray().data);
            }
        }

        if (!empty) {
            // 5. heightmaps and after all that - update ref counts
            ILightingManager lm = ((ICubicWorldInternal) cube.getWorld()).getLightingManager();

            IColumnInternal column = cube.getColumn();
            ClientHeightMap coi = (ClientHeightMap) column.getOpacityIndex();
            for (int dx = 0; dx < Cube.SIZE; dx++) {
                for (int dz = 0; dz < Cube.SIZE; dz++) {
                    oldHeights[AddressTools.getLocalAddress(dx, dz)] = coi.getTopBlockY(dx, dz);
                }
            }

            column.loadClientHeightmapData(in);

            for (int dx = 0; dx < Cube.SIZE; dx++) {
                for (int dz = 0; dz < Cube.SIZE; dz++) {
                    int oldY = oldHeights[AddressTools.getLocalAddress(dx, dz)];
                    int newY = coi.getTopBlockY(dx, dz);
                    if (oldY != newY) {
                        lm.updateLightBetween(cube.getColumn(), dx, oldY, newY, dz);
                    }
                }
            }
        }

        // 6. biomes
        cube.readBiomeArray(in);

        if (!empty && capi) {
            try {
                DataRegistryImpl.readFromBufferCubic(cube.getColumn(), storage, in.readByteArray(buffer));
            } catch (Throwable t) {
                CubicChunks.LOGGER
                    .error("Error decoding ChunkAPI data ({},{},{})", cube.getX(), cube.getY(), cube.getZ(), t);
            }
        }

        if (!empty) {
            storage.removeInvalidBlocks();
        }
    }
}
