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

/** Inclusive ellipsoid of cube centers; equal horizontal and vertical radii produce a sphere. */
@ParametersAreNonnullByDefault
public final class EllipsoidalCubeSelector implements CubeSelector {

    public static final EllipsoidalCubeSelector INSTANCE = new EllipsoidalCubeSelector();

    private EllipsoidalCubeSelector() {}

    @Override
    public void forAllVisibleCubes(CubePos center, int horizontalViewDistance, int verticalViewDistance,
        Consumer3D fn) {
        int horizontalSquared = horizontalViewDistance * horizontalViewDistance;
        int verticalSquared = verticalViewDistance * verticalViewDistance;
        for (int dx = -horizontalViewDistance; dx <= horizontalViewDistance; dx++) {
            for (int dz = -horizontalViewDistance; dz <= horizontalViewDistance; dz++) {
                int remaining = horizontalSquared - dx * dx - dz * dz;
                if (remaining < 0) continue;
                // Integer division followed by floor(sqrt) keeps boundary membership exact.
                int extentY = (int) Math.sqrt((long) remaining * verticalSquared / horizontalSquared);
                for (int dy = -extentY; dy <= extentY; dy++) {
                    fn.accept(center.getX() + dx, center.getY() + dy, center.getZ() + dz);
                }
            }
        }
    }

    @Override
    public void forAllVisibleColumns(CubePos center, int horizontalViewDistance, int verticalViewDistance,
        Consumer2D fn) {
        int radiusSquared = horizontalViewDistance * horizontalViewDistance;
        for (int dx = -horizontalViewDistance; dx <= horizontalViewDistance; dx++) {
            int extentZ = (int) Math.sqrt(radiusSquared - dx * dx);
            for (int dz = -extentZ; dz <= extentZ; dz++) {
                fn.accept(center.getX() + dx, center.getZ() + dz);
            }
        }
    }

    @Override
    public WorldVisibilityChange findChanged(CubePos oldPos, CubePos newPos, int oldHorizontalView, int oldVerticalView,
        int newHorizontalView, int newVerticalView) {
        WorldVisibilityChange result = new WorldVisibilityChange();
        forAllVisibleCubes(
            oldPos,
            oldHorizontalView,
            oldVerticalView,
            (x, y, z) -> {
                if (!contains(newPos, newHorizontalView, newVerticalView, x, y, z)) result.cubesToUnload.add(x, y, z);
            });
        forAllVisibleCubes(
            newPos,
            newHorizontalView,
            newVerticalView,
            (x, y, z) -> {
                if (!contains(oldPos, oldHorizontalView, oldVerticalView, x, y, z)) result.cubesToLoad.add(x, y, z);
            });
        forAllVisibleColumns(
            oldPos,
            oldHorizontalView,
            oldVerticalView,
            (x, z) -> { if (!contains(newPos, newHorizontalView, x, z)) result.columnsToUnload.add(x, z); });
        forAllVisibleColumns(
            newPos,
            newHorizontalView,
            newVerticalView,
            (x, z) -> { if (!contains(oldPos, oldHorizontalView, x, z)) result.columnsToLoad.add(x, z); });
        return result;
    }

    @Override
    public boolean contains(CubePos center, int horizontalViewDistance, int verticalViewDistance, int x, int y, int z) {
        long dx = (long) x - center.getX();
        long dy = (long) y - center.getY();
        long dz = (long) z - center.getZ();
        if (Math.abs(dx) > horizontalViewDistance || Math.abs(dy) > verticalViewDistance
            || Math.abs(dz) > horizontalViewDistance) return false;
        long horizontalSquared = (long) horizontalViewDistance * horizontalViewDistance;
        long verticalSquared = (long) verticalViewDistance * verticalViewDistance;
        return (dx * dx + dz * dz) * verticalSquared + dy * dy * horizontalSquared
            <= horizontalSquared * verticalSquared;
    }

    @Override
    public boolean contains(CubePos center, int horizontalViewDistance, int x, int z) {
        long dx = (long) x - center.getX();
        long dz = (long) z - center.getZ();
        if (Math.abs(dx) > horizontalViewDistance || Math.abs(dz) > horizontalViewDistance) return false;
        return dx * dx + dz * dz <= (long) horizontalViewDistance * horizontalViewDistance;
    }
}
