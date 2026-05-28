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
package com.cardinalstar.cubicchunks.visibility;

import javax.annotation.ParametersAreNonnullByDefault;

import com.cardinalstar.cubicchunks.util.Consumer2D;
import com.cardinalstar.cubicchunks.util.Consumer3D;
import com.cardinalstar.cubicchunks.util.CubePos;
import com.cardinalstar.cubicchunks.util.HashSet2D;
import com.cardinalstar.cubicchunks.util.HashSet3D;

@ParametersAreNonnullByDefault
public class CuboidalCubeSelector implements CubeSelector {

    public static final CuboidalCubeSelector INSTANCE = new CuboidalCubeSelector();

    private CuboidalCubeSelector() {}

    @Override
    public void forAllVisibleCubes(CubePos cubePos, int horizontalViewDistance, int verticalViewDistance,
        Consumer3D fn) {
        int cubeX = cubePos.getX();
        int cubeY = cubePos.getY();
        int cubeZ = cubePos.getZ();

        for (int x = cubeX - horizontalViewDistance; x <= cubeX + horizontalViewDistance; x++) {
            for (int y = cubeY - verticalViewDistance; y <= cubeY + verticalViewDistance; y++) {
                for (int z = cubeZ - horizontalViewDistance; z <= cubeZ + horizontalViewDistance; z++) {
                    fn.accept(x, y, z);
                }
            }
        }
    }

    @Override
    public void forAllVisibleColumns(CubePos cubePos, int horizontalViewDistance, int verticalViewDistance,
        Consumer2D fn) {
        int cubeX = cubePos.getX();
        int cubeZ = cubePos.getZ();

        for (int x = cubeX - horizontalViewDistance; x <= cubeX + horizontalViewDistance; x++) {
            for (int z = cubeZ - horizontalViewDistance; z <= cubeZ + horizontalViewDistance; z++) {
                fn.accept(x, z);
            }
        }
    }

    @Override
    public WorldVisibilityChange findChanged(CubePos oldPos, CubePos newPos, int oldHorizontalView, int oldVerticalView,
        int newHorizontalView, int newVerticalView) {
        HashSet3D visCubesOld = new HashSet3D();
        forAllVisibleCubes(oldPos, oldHorizontalView, oldVerticalView, visCubesOld::add);

        HashSet2D visColsOld = new HashSet2D();
        forAllVisibleColumns(oldPos, oldHorizontalView, oldVerticalView, visColsOld::add);

        HashSet3D visCubesNew = new HashSet3D();
        forAllVisibleCubes(newPos, newHorizontalView, newVerticalView, visCubesNew::add);

        HashSet2D visColsNew = new HashSet2D();
        forAllVisibleColumns(newPos, newHorizontalView, newVerticalView, visColsNew::add);

        WorldVisibilityChange result = new WorldVisibilityChange();

        visColsOld.forEach((x, z) -> {
            if (!visColsNew.contains(x, z)) {
                result.columnsToUnload.add(x, z);
            }
        });

        visCubesOld.forEach((x, y, z) -> {
            if (!visCubesNew.contains(x, y, z)) {
                result.cubesToUnload.add(x, y, z);
            }
        });

        visColsNew.forEach((x, z) -> {
            if (!visColsOld.contains(x, z)) {
                result.columnsToLoad.add(x, z);
            }
        });

        visCubesNew.forEach((x, y, z) -> {
            if (!visCubesOld.contains(x, y, z)) {
                result.cubesToLoad.add(x, y, z);
            }
        });

        return result;
    }

    @Override
    public boolean contains(CubePos playerPos, int horizontalViewDistance, int verticalViewDistance, int x, int y,
        int z) {
        return Math.abs(playerPos.getX() - x) <= horizontalViewDistance
            && Math.abs(playerPos.getY() - y) <= verticalViewDistance
            && Math.abs(playerPos.getZ() - z) <= horizontalViewDistance;
    }

    @Override
    public boolean contains(CubePos playerPos, int horizontalViewDistance, int x, int z) {
        return Math.abs(playerPos.getX() - x) <= horizontalViewDistance
            && Math.abs(playerPos.getZ() - z) <= horizontalViewDistance;
    }
}
