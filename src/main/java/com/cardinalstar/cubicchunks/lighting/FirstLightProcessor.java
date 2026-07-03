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
package com.cardinalstar.cubicchunks.lighting;

import static com.cardinalstar.cubicchunks.util.Coords.blockToCube;
import static com.cardinalstar.cubicchunks.util.Coords.cubeToMaxBlock;
import static com.cardinalstar.cubicchunks.util.Coords.cubeToMinBlock;

import javax.annotation.ParametersAreNonnullByDefault;

import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.world.EnumSkyBlock;
import net.minecraft.world.World;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;

import com.cardinalstar.cubicchunks.api.IColumn;
import com.cardinalstar.cubicchunks.api.ICube;
import com.cardinalstar.cubicchunks.mixin.api.BlockExt_Lighting;
import com.cardinalstar.cubicchunks.mixin.api.ICubicWorldInternal;
import com.cardinalstar.cubicchunks.server.chunkio.ICubeLoader;
import com.cardinalstar.cubicchunks.util.MathUtil;
import com.cardinalstar.cubicchunks.world.core.IColumnInternal;
import com.cardinalstar.cubicchunks.world.cube.Cube;
import it.unimi.dsi.fastutil.ints.IntIntMutablePair;

/**
 * Notes on world.checkLightFor(): Decreasing light value: Light is recalculated starting from 0 ONLY for blocks where
 * rawLightValue is equal to savedLightValue (ie. updating skylight source that is not there anymore). Otherwise
 * existing light values are assumed to be correct. Generates and updates cube initial lighting, and propagates light
 * changes caused by generating cube downwards.
 * <p>
 * Used only when changes are caused by pre-populator terrain generation.
 * <p>
 * THIS SHOULD ONLY EVER BE USED ONCE PER CUBE.
 */
@ParametersAreNonnullByDefault
public class FirstLightProcessor {

    /**
     * Diffuses skylight in the given cube and all cubes affected by this update.
     *
     * @param cube the cube whose skylight is to be initialized
     */
    public void diffuseSkylight(ICube cube) {
        ILightingManager lm = ((ICubicWorldInternal) cube.getWorld()).getLightingManager();

        ICubeLoader loader = ((ICubicWorldInternal.Server) cube.getWorld()).getCubeCache()
            .getCubeLoader();

        World world = cube.getWorld();

        int cX = cube.getX();
        int cY = cube.getY();
        int cZ = cube.getZ();

        int bX = cX << 4;
        int bY = cY << 4;
        int bZ = cZ << 4;

        int bYMax = bY + 15;

        boolean isCubeEmpty = cube.isEmpty();

        loader.cacheCubes(cX, cY, cZ, 1, 2, 1);

        ExtendedBlockStorage storage = cube.getStorage();

        if (!isCubeEmpty) {
            assert storage != null;

            for (int lX = 0; lX < 16; lX++) {
                for (int lY = 0; lY < 16; lY++) {
                    for (int lZ = 0; lZ < 16; lZ++) {
                        Block block = storage.getBlockByExtId(lX, lY, lZ);

                        try {
                            ((BlockExt_Lighting) block).cc$setUnsafeLightMode(true);

                            if (block != Blocks.air && block.getLightValue(world, bX + lX, bY + lY, bZ + lZ) > 0) {
                                lm.checkLightFor(EnumSkyBlock.Block, bX + lX, bY + lY, bZ + lZ);
                            }
                        } finally {
                            ((BlockExt_Lighting) block).cc$setUnsafeLightMode(false);
                        }
                    }
                }
            }
        }

        if (cube.getWorld().provider.hasNoSky) {
            loader.uncacheCubes();

            return;
        }

        // Cache min/max Y, generating them may be expensive
        int[] minBlockYArr = new int[Cube.SIZE * Cube.SIZE];
        int[] maxBlockYArr = new int[Cube.SIZE * Cube.SIZE];

        // the lowest minHeight and the highest maxHeight values
        // used to make the cube iteration the outer loop, so light propagator can do mass light updates
        int minMinHeight = Integer.MAX_VALUE;
        int maxMaxHeight = Integer.MIN_VALUE;

        // Determine the block columns that require updating. If there is nothing to update, store contradicting data so
        // we can skip the column later.

        IntIntMutablePair minMax = new IntIntMutablePair(0, 0);

        IColumnInternal column = cube.getColumn();

        for (int lX = 0; lX < Cube.SIZE; ++lX) {
            for (int lZ = 0; lZ < Cube.SIZE; ++lZ) {
                // This is the top block within this block column, including the current cube.
                int stagingTopBlock = column.getTopYWithStaging(lX, lZ);

                int minInstantFill = Integer.MIN_VALUE;
                int maxInstantFill = Integer.MIN_VALUE;

                boolean isEdge = lX == 0 || lX == 15 || lZ == 0 || lZ == 15;

                if (!isCubeEmpty && !isEdge && bYMax > stagingTopBlock) {
                    int h1 = column.getTopYWithStaging(lX + 1, lZ);
                    int h2 = column.getTopYWithStaging(lX - 1, lZ);
                    int h3 = column.getTopYWithStaging(lX, lZ + 1);
                    int h4 = column.getTopYWithStaging(lX, lZ - 1);

                    int maxNeighbor = MathUtil.max(h1, h2, h3, h4) + 1;

                    minInstantFill = MathUtil.max(stagingTopBlock + 2, maxNeighbor, bY);
                    maxInstantFill = bYMax;
                }

                if (stagingTopBlock < bYMax) {
                    int minY = Math.max(stagingTopBlock, bY);

                    for (int yPos = minY; yPos <= bYMax; yPos++) {
                        if (yPos >= minInstantFill && yPos <= maxInstantFill) {
                            cube.setLightFor(EnumSkyBlock.Sky, bX + lX, yPos, bZ + lZ, 15);
                        } else {
                            if (isEdge) {
                                cube.setLightFor(EnumSkyBlock.Sky, bX + lX, yPos, bZ + lZ, 0);
                            }
                            lm.checkLightFor(EnumSkyBlock.Sky, bX + lX, yPos, bZ + lZ);
                        }
                    }
                }

                // If the current cube is above the highest occluding block in the column, everything is fully lit.
                int cubeY = cube.getY();

                // If the given cube lies underneath the occluding block, then the update must start at the occluding block.
                if (cubeY < blockToCube(stagingTopBlock)) {
                    // This is the top block within this block column, excluding the staging height map (which means the
                    // blocks in this cube are ignored)
                    int opacityTopBlock = column.getOpacityIndex().getTopBlockY(lX, lZ);

                    minMax.left(opacityTopBlock);
                    minMax.right(stagingTopBlock);

                    minBlockYArr[(lZ << 4) | lX] = opacityTopBlock;
                    maxBlockYArr[(lZ << 4) | lX] = stagingTopBlock;

                    minMinHeight = Math.min(opacityTopBlock, minMinHeight);
                    maxMaxHeight = Math.max(stagingTopBlock, maxMaxHeight);
                }
            }
        }

        // Iterate over all affected cubes.
        Iterable<? extends Cube> cubes = column
            .getLoadedCubes(blockToCube(minMinHeight), blockToCube(maxMaxHeight));

        for (Cube affectedCube : cubes) {
            int bY_Affected = affectedCube.getY() << 4;
            int bYMax_Affected = bY_Affected + 15;

            for (int lX = 0; lX < 16; lX++) {
                for (int lZ = 0; lZ < 16; lZ++) {
                    int minBlockY = minBlockYArr[(lZ << 4) | lX];
                    int maxBlockY = maxBlockYArr[(lZ << 4) | lX];

                    //  is below the existing top of the block
                    if (minBlockY > maxBlockY) {
                        continue;
                    }

                    // if not in this cube, skip
                    if (!MathUtil.rangesIntersect(minBlockY, maxBlockY, bY_Affected, bYMax_Affected)) {
                        continue;
                    }

                    if (affectedCube != cube && !affectedCube.isInitialLightingDone()) {
                        continue;
                    }

                    // Update the block column in this cube.
                    diffuseSkylightInBlockColumn(
                        lm,
                        affectedCube,
                        lX + bX,
                        lZ + bZ,
                        minBlockY,
                        maxBlockY);
                }
            }
        }

        loader.uncacheCubes();
    }

    /**
     * Diffuses skylight inside of the given cube in the block column specified by posX/posZ.
     * The update is limited vertically by minBlockY and maxBlockY.
     *
     * @param cube      the cube inside of which the skylight is to be diffused
     * @param posX      the x position of the block column to be updated
     * @param posZ      the z position of the block column to be updated
     * @param minBlockY the lower bound of the section to be updated
     * @param maxBlockY the upper bound of the section to be updated
     */
    private void diffuseSkylightInBlockColumn(ILightingManager lm, ICube cube, int posX, int posZ,
        int minBlockY, int maxBlockY) {
        int cubeMinBlockY = cubeToMinBlock(cube.getY());
        int cubeMaxBlockY = cubeToMaxBlock(cube.getY());

        int maxBlockYInCube = Math.min(cubeMaxBlockY, maxBlockY);
        int minBlockYInCube = Math.max(cubeMinBlockY, minBlockY);

        ExtendedBlockStorage storage = cube.getStorage();

        if (storage == null) {
            // All air — opacity 0 < 15, always needs skylight update
            for (int blockY = maxBlockYInCube; blockY >= minBlockYInCube; --blockY) {
                lm.checkLightFor(EnumSkyBlock.Sky, posX, blockY, posZ);
            }

            return;
        }

        World world = cube.getWorld();

        int localX = posX & 0xF;
        int localZ = posZ & 0xF;

        for (int blockY = maxBlockYInCube; blockY >= minBlockYInCube; --blockY) {
            // Opaque blocks don't need update. Nothing can emit skylight, and skylight can't get into them nor out of them.
            Block block = storage.getBlockByExtId(localX, blockY & 0xF, localZ);

            if (block == Blocks.air || block.getLightOpacity(world, posX, blockY, posZ) < 15) {
                lm.checkLightFor(EnumSkyBlock.Sky, posX, blockY, posZ);
            }
        }
    }

    /**
     * Determines which vertical section of the specified block column in the given cube requires a lighting update
     * based on the current occlusion in the cube's column.
     *
     * @param cube   the cube inside of which the skylight is to be updated
     * @param localX the local x-coordinate of the block column
     * @param localZ the local z-coordinate of the block column
     */
    private static boolean getMinMaxLightUpdateY(ICube cube, int localX, int localZ, IntIntMutablePair minMax) {

        IColumn column = cube.getColumn();
        int heightMax = ((IColumnInternal) column).getTopYWithStaging(localX, localZ);// ==Y of the top block

        // If the given cube is above the highest occluding block in the column, everything is fully lit.
        int cubeY = cube.getY();
        if (blockToCube(heightMax) < cubeY) {
            return false;
        }

        // If the given cube lies underneath the occluding block, then the update must start at the occluding block.
        int previousMaxHeight = column.getOpacityIndex()
            .getTopBlockY(localX, localZ);

        minMax.left(previousMaxHeight);
        minMax.right(heightMax);

        return true;
    }
}
