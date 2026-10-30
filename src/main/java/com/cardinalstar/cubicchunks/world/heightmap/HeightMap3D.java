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

import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;

import com.cardinalstar.cubicchunks.CubicChunks;
import com.cardinalstar.cubicchunks.api.IHeightMap;
import com.cardinalstar.cubicchunks.network.CCPacketBuffer;
import com.cardinalstar.cubicchunks.util.Array2D_16x16;
import com.cardinalstar.cubicchunks.util.Coords;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.buffer.Unpooled;

@ParametersAreNonnullByDefault
public class HeightMap3D implements IHeightMap {

    private final Array2D_16x16<YIntervalTree> heightmap = new Array2D_16x16<>(YIntervalTree[]::new);

    /** Null for height maps that are not mirrored into {@code Chunk.heightMap}. */
    @Nullable
    private VanillaHeightMap vanilla;
    private final int worldActualHeight;

    public HeightMap3D(int[] vanilla, int worldActualHeight) {
        this(worldActualHeight);
        setVanillaHeightMap(vanilla);
    }

    /** Creates a height map that is not mirrored into a vanilla height map. */
    protected HeightMap3D() {
        this(0);
    }

    private HeightMap3D(int worldActualHeight) {
        this.worldActualHeight = worldActualHeight;

        for (int i = 0; i < 256; i++) {
            this.heightmap.data()[i] = new YIntervalTree();
        }
    }

    /** Rebinds the vanilla view after an external chunk data manager replaces {@code Chunk.heightMap}. */
    public void setVanillaHeightMap(int[] vanilla) {
        this.vanilla = new VanillaHeightMap(vanilla);
    }

    @Override
    public void onOpacityChange(int localX, int blockY, int localZ, boolean occluded) {
        if (blockY > CubicChunks.MAX_SUPPORTED_BLOCK_Y || blockY < CubicChunks.MIN_SUPPORTED_BLOCK_Y) {
            return;
        }

        YIntervalTree tree = this.heightmap.get(localX, localZ);

        if (tree.set(blockY, occluded)) {
            updateVanilla(localX, localZ, tree);
        }
    }

    /**
     * Sets {@code length} Y values of one block column at once: Y {@code minY + i} is marked exactly when bit {@code i}
     * of {@code bits} is set. Same result as calling {@link #onOpacityChange} for each of them, as one update.
     * Returns true when the height map changed.
     *
     * @param length 1 to 32
     */
    public boolean setBits(int localX, int localZ, int minY, int length, int bits) {
        YIntervalTree tree = this.heightmap.get(localX, localZ);
        boolean changed;

        if (minY >= CubicChunks.MIN_SUPPORTED_BLOCK_Y
            && (long) minY + length - 1 <= CubicChunks.MAX_SUPPORTED_BLOCK_Y) {
            changed = tree.setBits(minY, length, bits);
        } else {
            changed = false;
            for (int i = 0; i < length; i++) {
                int y = minY + i;
                if (y <= CubicChunks.MAX_SUPPORTED_BLOCK_Y && y >= CubicChunks.MIN_SUPPORTED_BLOCK_Y) {
                    changed |= tree.set(y, (bits >>> i & 1) != 0);
                }
            }
        }

        if (changed) {
            updateVanilla(localX, localZ, tree);
        }
        return changed;
    }

    private void updateVanilla(int localX, int localZ, YIntervalTree tree) {
        if (vanilla == null) {
            return;
        }

        // We don't want to insert >256 heights into the vanilla map because it breaks a ton of mod code.
        // So we scan down from the highest Y below the logical top of the world (255 in OW, 127 in nether) to find the
        // first air->block transition in that column, defaulting to 0 if there are no blocks in that column (which
        // happens in the end).
        int fakeTop = tree.getBottomSolidY(worldActualHeight - 1);

        vanilla.set(localX, localZ, fakeTop == Coords.NO_HEIGHT ? 0 : fakeTop);
    }

    @Override
    public int getTopBlockY(int localX, int localZ) {
        return this.heightmap.get(localX, localZ)
            .getTopY();
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

        YIntervalTree tree = this.heightmap.data()[i];
        tree.readData(buffer);

        updateVanilla(localX, localZ, tree);
    }

    public void readData(CCPacketBuffer buffer) {
        for (int lZ = 0; lZ < 16; lZ++) {
            for (int lX = 0; lX < 16; lX++) {
                readData(lX, lZ, buffer);
            }
        }
    }

    /** Replaces the contents of this height map with a copy of {@code source}. */
    public void copyFrom(HeightMap3D source) {
        readData(new CCPacketBuffer(Unpooled.wrappedBuffer(source.getData())));
    }

    public void writeData(int localX, int localZ, CCPacketBuffer buffer) {
        int i = localZ << 4 | localX;

        this.heightmap.data()[i].writeData(buffer);
    }

    public void writeData(CCPacketBuffer buffer) {
        for (int lZ = 0; lZ < 16; lZ++) {
            for (int lX = 0; lX < 16; lX++) {
                writeData(lX, lZ, buffer);
            }
        }
    }
}
