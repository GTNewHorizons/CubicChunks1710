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

package com.cardinalstar.cubicchunks.util;

import java.util.Random;

import javax.annotation.ParametersAreNonnullByDefault;

import net.minecraft.entity.Entity;
import net.minecraft.util.MathHelper;

import com.cardinalstar.cubicchunks.api.ICube;

@ParametersAreNonnullByDefault
public class Coords {

    public static final int NO_HEIGHT = Integer.MIN_VALUE + 32;
    /**
     * Each {@link ICube} optionally contain 8x1x8 map of biome IDs, or 2x16x2 block wide biome
     * areas.
     */
    public static final int BIOMES_PER_CUBE = 8 * 1 * 8;
    private static final long XZ_INT = 0xFFFFFFFFL;

    // public static BlockPos midPos(BlockPos p1, BlockPos p2) {
    // //bitshifting each number and then adding the result - this rounds the number down and prevents overflow
    // return new BlockPos((p1.getX() >> 1) + (p2.getX() >> 1) + (p1.getX() & p2.getX() & 1),
    // (p1.getY() >> 1) + (p2.getY() >> 1) + (p1.getY() & p2.getY() & 1),
    // (p1.getZ() >> 1) + (p2.getZ() >> 1) + (p1.getZ() & p2.getZ() & 1));
    // }

    public static int blockToLocal(int val) {
        return val & 0xf;
    }

    public static int blockToCube(int val) {
        return val >> 4;
    }

    public static int blockCeilToCube(int val) {
        return -((-val) >> 4);
    }

    /**
     * @deprecated Use {@link #blockToLocalBiome3d(int)}
     */
    @Deprecated
    public static int blockToBiome(int val) {
        return (val & 14) >> 1;
    }

    public static int blockToLocalBiome3d(int val) {
        return (val & 15) >> 2;
    }

    public static int blockToBiome3d(int val) {
        return val >> 2;
    }

    public static int biome3dToMinBlock(int val) {
        return val << 2;
    }

    public static int biome3dToBlock(int val, int localBlock) {
        return val << 2 | localBlock;
    }

    public static int localToBlock(int cubeVal, int localVal) {
        return cubeToMinBlock(cubeVal) + localVal;
    }

    public static int cubeToMinBlock(int val) {
        return val << 4;
    }

    public static int cubeToMaxBlock(int val) {
        return cubeToMinBlock(val) + 15;
    }

    public static int getCubeXForEntity(Entity entity) {
        return blockToCube(MathHelper.floor_double(entity.posX));
    }

    public static int getCubeZForEntity(Entity entity) {
        return blockToCube(MathHelper.floor_double(entity.posZ));
    }

    public static int getCubeYForEntity(Entity entity) {
        // the entity is in the cube it's inside, not the cube it's standing on
        return blockToCube(MathHelper.floor_double(entity.posY));
    }

    // public static BlockPos getCubeCenter(ICube cube) {
    // return new BlockPos(
    // cubeToMinBlock(cube.getX()) | 8,
    // cubeToMinBlock(cube.getY()) | 8,
    // cubeToMinBlock(cube.getZ()) | 8
    // );
    // }

    public static int blockToCube(double blockPos) {
        return blockToCube(MathHelper.floor_double(blockPos));
    }

    public static int cubeToCenterBlock(int cubeVal) {
        return localToBlock(cubeVal, ICube.SIZE / 2);
    }

    /**
     * Returns the minimum coordinate inside the population area this coordinate is in
     *
     * @param coord the coordinate
     * @return the minimum coordinate for population area
     */
    public static int getMinCubePopulationPos(int coord) {
        return localToBlock(blockToCube(coord - ICube.SIZE / 2), ICube.SIZE / 2);
    }

    /**
     * Return a seed for random number generation, based on initial seed and 3 coordinates.
     *
     * @param seed the world seed
     * @param x    the x coordinate
     * @param y    the y coordinate
     * @param z    the zPosition coordinate
     * @return A seed value based on world seed, x, y and zPosition coordinates
     */
    public static long coordsSeedHash(long seed, int x, int y, int z) {
        long hash = 3;
        hash = 41 * hash + seed;
        hash = 41 * hash + x;
        hash = 41 * hash + y;
        return 41 * hash + z;
    }

    public static Random coordsSeedRandom(long seed, int x, int y, int z) {
        return new Random(coordsSeedHash(seed, x, y, z));
    }

    public static final String HEIGHT_BITS_PROPERTY = "cubicchunks.heightBits";
    /** The layout before {@link #HEIGHT_BITS_PROPERTY} existed: 21 bits for every axis. */
    public static final int DEFAULT_HEIGHT_BITS = 21;

    /**
     * Bits for Y in a packed coordinate ({@link #key}); X and Z split the rest, and an odd bit left over is unused.
     * The packed layout is also used for block positions by GTNHLib's CoordinatePacker and ChunkAPI's BlockPosUtil,
     * so this bounds the whole world. Mods may store packed positions, so changing it breaks existing worlds.
     */
    public static final int Y_BITS = readHeightBits();
    public static final int XZ_BITS = (64 - Y_BITS) / 2;

    private static final int Z_SHIFT = 0;
    private static final int Y_SHIFT = XZ_BITS;
    private static final int X_SHIFT = XZ_BITS + Y_BITS;

    /** Inclusive range each axis can be packed with. */
    public static final int MIN_PACKED_Y = (int) -(1L << (Y_BITS - 1));
    public static final int MAX_PACKED_Y = (int) ((1L << (Y_BITS - 1)) - 1);
    public static final int MIN_PACKED_XZ = (int) -(1L << (XZ_BITS - 1));
    public static final int MAX_PACKED_XZ = (int) ((1L << (XZ_BITS - 1)) - 1);

    private static int readHeightBits() {
        String value = System.getProperty(HEIGHT_BITS_PROPERTY);
        if (value == null) return DEFAULT_HEIGHT_BITS;

        int bits;
        try {
            bits = Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            throw new IllegalStateException(HEIGHT_BITS_PROPERTY + " must be an integer, got '" + value + "'", e);
        }
        // 12 leaves X/Z 26 bits, enough for vanilla's +-30M; Y can't use more than an int
        if (bits < 12 || bits > 32) {
            throw new IllegalStateException(HEIGHT_BITS_PROPERTY + " must be between 12 and 32, got " + bits);
        }
        return bits;
    }

    public static boolean isPackable(long x, long y, long z) {
        return x >= MIN_PACKED_XZ && x <= MAX_PACKED_XZ
            && y >= MIN_PACKED_Y
            && y <= MAX_PACKED_Y
            && z >= MIN_PACKED_XZ
            && z <= MAX_PACKED_XZ;
    }

    private static long pack(long value, int bits, int shift, long min, long max) {
        if (value < min || value > max) {
            throw new IllegalArgumentException(
                "Can only pack numbers between " + min + ".." + max + " (inclusive): got " + value);
        }
        return (value & ((1L << bits) - 1)) << shift;
    }

    private static int unpack(long key, int bits, int shift) {
        // Move the field to the top of the long, then shift it back down to sign-extend it
        return (int) (key << (64 - bits - shift) >> (64 - bits));
    }

    public static long key(long x, long y, long z) {
        return pack(x, XZ_BITS, X_SHIFT, MIN_PACKED_XZ, MAX_PACKED_XZ)
            | pack(y, Y_BITS, Y_SHIFT, MIN_PACKED_Y, MAX_PACKED_Y)
            | pack(z, XZ_BITS, Z_SHIFT, MIN_PACKED_XZ, MAX_PACKED_XZ);
    }

    public static int x(long key) {
        return unpack(key, XZ_BITS, X_SHIFT);
    }

    public static int y(long key) {
        return unpack(key, Y_BITS, Y_SHIFT);
    }

    public static int z(long key) {
        return unpack(key, XZ_BITS, Z_SHIFT);
    }

    public static long packChunk(int x, int z) {
        return (z & XZ_INT) << 32 | (x & XZ_INT);
    }

    public static int unpackChunkX(long key) {
        return (int) key;
    }

    public static int unpackChunkZ(long key) {
        return (int) (key >> 32);
    }
}
