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
package com.cardinalstar.cubicchunks.mixin.api;

import javax.annotation.ParametersAreNonnullByDefault;

import net.minecraft.block.Block;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;

import com.cardinalstar.cubicchunks.api.IColumn;
import com.cardinalstar.cubicchunks.api.ICube;
import com.cardinalstar.cubicchunks.api.ICubicWorldServer;
import com.cardinalstar.cubicchunks.api.IntRange;
import com.cardinalstar.cubicchunks.api.XYZMap;
import com.cardinalstar.cubicchunks.api.XZMap;
import com.cardinalstar.cubicchunks.api.util.NotCubicChunksWorldException;
import com.cardinalstar.cubicchunks.client.CubeProviderClient;
import com.cardinalstar.cubicchunks.lighting.ILightingManager;
import com.cardinalstar.cubicchunks.server.CubeProviderServer;
import com.cardinalstar.cubicchunks.server.SpawnCubes;
import com.cardinalstar.cubicchunks.util.world.CubeSplitTicks;
import com.cardinalstar.cubicchunks.world.ICubicWorld;
import com.cardinalstar.cubicchunks.world.cube.Cube;
import com.cardinalstar.cubicchunks.world.cube.ICubeProvider;
import com.cardinalstar.cubicchunks.world.cube.ICubeProviderInternal;
import com.gtnewhorizon.gtnhlib.blockpos.BlockPos;

/**
 * Internal ICubicWorld additions.
 */
@ParametersAreNonnullByDefault
public interface ICubicWorldInternal extends ICubicWorld {

    /**
     * Updates the world
     */
    void tickCubicWorld();

    /**
     * Returns the {@link ICubeProvider} for this world, or throws {@link NotCubicChunksWorldException}
     * if this is not a CubicChunks world.
     */
    @Override
    ICubeProviderInternal getCubeCache();

    /**
     * Returns the {@link ILightingManager} for this world, or throws {@link NotCubicChunksWorldException}
     * if this is not a CubicChunks world.
     *
     * @return lighting manager instance for this world
     */
    ILightingManager getLightingManager();

    @Override
    Cube getCubeFromCubeCoords(int cubeX, int cubeY, int cubeZ);

    @Override
    Cube getCubeFromBlockCoords(int x, int y, int z);

    void fakeWorldHeight(int height);

    void setHeightBounds(int minHeight, int maxHeight);

    default BlockPos getTopSolidOrLiquidBlockVanilla(int x, int y, int z) {
        Chunk chunk = ((World) this).getChunkFromBlockCoords(x, y);

        BlockPos current = new BlockPos(x, chunk.getTopFilledSegment() + 16, z);
        while (current.getY() >= 0) {
            BlockPos next = current.down();
            Block block = chunk.getBlock(next.getX(), next.getY(), next.getZ());

            if (block.getMaterial()
                .blocksMovement() && !block.isLeaves((World) this, next.getX(), next.getY(), next.getZ())
                && !block.isFoliage((World) this, next.getX(), next.getY(), next.getZ())) {
                break;
            }
            current = next;
        }

        return current;
    }

    interface Server extends ICubicWorldInternal, ICubicWorldServer {

        @Override
        CubeProviderServer getCubeCache();

        void removeForcedCube(ICube cube);

        void addForcedCube(ICube cube);

        XYZMap<ICube> getForcedCubes();

        XZMap<IColumn> getForcedColumns();

        CubeSplitTicks getScheduledTicks();

        SpawnCubes getSpawnArea();

        void setSpawnArea(SpawnCubes spawn);

        void initCubicWorldServer();

        // VanillaNetworkHandler getVanillaNetworkHandler();
    }

    interface Client extends ICubicWorldInternal {

        /**
         * Initializes the world to be a CubicChunks world. Must be done before any players are online and before any
         * chunks
         * are loaded. Cannot be used more than once.
         *
         * @param heightRange     world height range
         * @param generationRange expected height range for world generation. Maximum Y should be above 0.
         */
        void initCubicWorldClient(IntRange heightRange, IntRange generationRange);

        CubeProviderClient getCubeCache();
    }
}
