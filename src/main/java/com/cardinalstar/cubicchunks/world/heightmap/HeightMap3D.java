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

package com.cardinalstar.cubicchunks.world.heightmap;

import javax.annotation.Nonnull;
import javax.annotation.ParametersAreNonnullByDefault;

import com.cardinalstar.cubicchunks.CubicChunks;
import com.cardinalstar.cubicchunks.api.IHeightMap;
import com.cardinalstar.cubicchunks.network.CCPacketBuffer;
import com.cardinalstar.cubicchunks.util.Array2D_16x16;
import com.cardinalstar.cubicchunks.util.Coords;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.PooledByteBufAllocator;

@ParametersAreNonnullByDefault
public class HeightMap3D implements IHeightMap {

    private final Array2D_16x16<YIntervalTree> heightmap = new Array2D_16x16<>(YIntervalTree[]::new);

    @Nonnull
    private final VanillaHeightMap vanilla;

    public HeightMap3D(int[] vanilla) {
        this.vanilla = new VanillaHeightMap(vanilla);

        for (int i = 0; i < 256; i++) {
            this.heightmap.data()[i] = new YIntervalTree();
        }
    }

    @Override
    public void onOpacityChange(int localX, int blockY, int localZ, boolean occluded) {
        if (blockY > CubicChunks.MAX_SUPPORTED_BLOCK_Y || blockY < CubicChunks.MIN_SUPPORTED_BLOCK_Y) {
            return;
        }

        YIntervalTree tree = this.heightmap.get(localX, localZ);

        if (tree.set(blockY, occluded)) {
            int topY = tree.getTopY();
            vanilla.set(localX, localZ, topY == Coords.NO_HEIGHT ? 60 : topY);
        }
    }

    @Override
    public int getTopBlockY(int localX, int localZ) {
        return this.heightmap.get(localX, localZ).getTopY();
    }

    @Override
    public int getTopBlockYBelow(int localX, int blockY, int localZ) {
        return this.heightmap.get(localX, localZ).getBottomAirY(blockY);
    }

    public byte[] getData() {
        ByteBuf buf = PooledByteBufAllocator.DEFAULT.buffer();

        try {
            writeData(new CCPacketBuffer(buf));

            byte[] data = new byte[buf.writerIndex()];
            buf.readBytes(data);
            return data;
        } finally {
            buf.release();
        }
    }

    public void readData(int localX, int localZ, CCPacketBuffer buffer) {
        int i = localZ << 4 | localX;

        this.heightmap.data()[i].readData(buffer);

        int topY = this.heightmap.data()[i].getTopY();
        this.vanilla.data[i] = topY == Coords.NO_HEIGHT ? 60 : topY + 1;
    }

    public void readData(CCPacketBuffer buffer) {
        for (int i = 0; i < 256; i++) {
            this.heightmap.data()[i].readData(buffer);

            int topY = this.heightmap.data()[i].getTopY();
            this.vanilla.data[i] = topY == Coords.NO_HEIGHT ? 60 : topY + 1;
        }
    }

    public void writeData(int localX, int localZ, CCPacketBuffer buffer) {
        int i = localZ << 4 | localX;

        this.heightmap.data()[i].writeData(buffer);
    }

    public void writeData(CCPacketBuffer buffer) {
        for (int i = 0; i < 256; i++) {
            this.heightmap.data()[i].writeData(buffer);
        }
    }
}
