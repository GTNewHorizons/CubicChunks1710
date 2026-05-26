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
package com.cardinalstar.cubicchunks.world.column;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;

import net.minecraft.world.chunk.storage.ExtendedBlockStorage;

import com.cardinalstar.cubicchunks.CubicChunksConfig;
import com.cardinalstar.cubicchunks.util.AddressTools;
import com.cardinalstar.cubicchunks.world.cube.Cube;
import com.google.common.collect.Lists;

/**
 * Stores cubes for columns
 */
@ParametersAreNonnullByDefault
public class CubeMap implements Iterable<Cube> {

    public static final ExtendedBlockStorage[] ZERO_LEN_EBS_ARRAY = new ExtendedBlockStorage[0];

    @Nonnull
    private final List<Cube> cubes = new ArrayList<>();

    private ExtendedBlockStorage[] tickableEBSes = ZERO_LEN_EBS_ARRAY;

    /**
     * Removes the cube at {@code cubeY}
     *
     * @param cubeY cube y position
     *
     * @return the removed cube if it existed, otherwise <code>null</code>
     */
    @Nullable
    public Cube remove(int cubeY) {
        int index = binarySearch(cubeY);
        return index < cubes.size() && cubes.get(index)
            .getY() == cubeY ? cubes.remove(index) : null;
    }

    /**
     * Removes the cube at {@code cubeY}
     *
     * @param cubeY cube y position
     *
     * @return the removed cube if it existed, otherwise <code>null</code>
     */
    @Nullable
    public Cube get(int cubeY) {
        int index = binarySearch(cubeY);
        return index < cubes.size() && cubes.get(index).getY() == cubeY ? cubes.get(index) : null;
    }

    /**
     * Adds a cube
     *
     * @param cube the cube to add
     */
    public void put(Cube cube) {
        int searchIndex = binarySearch(cube.getY());
        if (this.contains(cube.getY(), searchIndex)) {
            throw new IllegalArgumentException("Cube at " + cube.getY() + " already exists!");
        }
        cubes.add(searchIndex, cube);
    }

    /**
     * Iterate over all cubes between {@code startY} and {@code endY} in this storage in order. If
     * {@code startY < endY}, order is bottom to top, otherwise order is top to bottom.
     *
     * @param startY initial cube y position
     * @param endY   last cube y position
     *
     * @return an iterator over the cubes
     */
    public Iterable<Cube> cubes(int startY, int endY) {
        boolean reverse = false;
        if (startY > endY) {
            int i = startY;
            startY = endY;
            endY = i;
            reverse = true;
        }

        int bottom = binarySearch(startY);
        int top = binarySearch(endY + 1); // subList()'s second arg is exclusive so we need to add 1

        if (bottom < cubes.size() && top <= cubes.size()) {
            return reverse ? Lists.reverse(cubes.subList(bottom, top)) : cubes.subList(bottom, top);
        } else {
            return Collections.emptyList();
        }
    }

    /**
     * Check if the target cube is stored here
     *
     * @param cubeY       the y coordinate of the cube
     * @param searchIndex the index to search at (got form {@link #binarySearch(int)})
     *
     * @return <code>true</code> if the cube is contained here, <code>false</code> otherwise
     */
    private boolean contains(int cubeY, int searchIndex) {
        return searchIndex < cubes.size() && cubes.get(searchIndex)
            .getY() == cubeY;
    }

    /**
     * Iterate over all cubes in this storage
     *
     * @return the iterator
     */
    @Override
    public Iterator<Cube> iterator() {
        return cubes.iterator();
    }

    /**
     * Retrieve a collection of all cubes within this storage. The collection is non-modifiable
     *
     * @return the collection
     */
    public Collection<Cube> all() {
        return cubes;
    }

    /**
     * Check if this storage is empty
     *
     * @return <code>true</code> if there are no cubes in this storage, <code>false</code> otherwise
     */
    public boolean isEmpty() {
        return cubes.isEmpty();
    }

    private <T> T getSafe(@Nullable T[] array, int index) {
        if (array == null) return null;

        if (index < 0 || index >= array.length) return null;

        return array[index];
    }

    /**
     * @return An array of EBSs from cubes that need ticking
     */
    public ExtendedBlockStorage[] getTickableStorages() {
        int count = 0;

        boolean needsUpdate = false;

        for (int i = 0, cubesSize = cubes.size(); i < cubesSize; i++) {
            Cube cube = cubes.get(i);

            if (cube.shouldTick()) {
                if (!needsUpdate) {
                    ExtendedBlockStorage existing = getSafe(tickableEBSes, count);

                    if (existing != cube.getStorage()) {
                        needsUpdate = true;
                    }
                }

                count++;
            }
        }

        if (!needsUpdate && count == tickableEBSes.length) return tickableEBSes;

        if (count == 0) {
            tickableEBSes = ZERO_LEN_EBS_ARRAY;
        } else {
            tickableEBSes = new ExtendedBlockStorage[count];

            int count2 = 0;

            for (int i = 0, cubesSize = cubes.size(); i < cubesSize; i++) {
                Cube cube = cubes.get(i);

                if (cube.shouldTick()) {
                    tickableEBSes[count2++] = cube.getStorage();
                }
            }
        }

        return tickableEBSes;
    }

    /**
     * Binary search for the index of the specified cube. If the cube is not present, returns the index at which it
     * should be inserted.
     *
     * @param cubeY cube y position
     *
     * @return the target index
     */
    private int binarySearch(int cubeY) {
        int start = 0;
        int end = cubes.size() - 1;
        int mid;

        while (start <= end) {
            mid = start + end >>> 1;

            int at = cubes.get(mid)
                .getY();
            if (at < cubeY) { // we are below the target;
                start = mid + 1;
            } else if (at > cubeY) {
                end = mid - 1; // we are above the target
            } else {
                return mid;// found target!
            }
        }

        return start; // not found :(
    }

    private int relightCubeIdx = 0;
    private int relightCubeBlockIdx = (int) (Math.random() * 4096);

    public void enqueueRelightChecks() {
        if (cubes.isEmpty()) {
            return;
        }
        int count = CubicChunksConfig.relightChecksPerTickPerColumn;
        for (int i = 0; i < count; i++) {
            if (relightCubeIdx >= cubes.size()) {
                relightCubeIdx = 0;
                relightCubeBlockIdx++;
                if (relightCubeBlockIdx >= 4096) {
                    relightCubeBlockIdx = 0;
                }
            }
            // taking the bits in reverse order means we will still visit every possible position, but jumping around
            // the cube
            // order of coords: 0, 8, 4, 12, 2, 10, 6, 14, 1, 9, 5, 13, 3, 11, 7, 15
            int reversedBits = Integer.reverse(relightCubeBlockIdx) >>> (32 - 12);
            assert reversedBits < 4096 && reversedBits >= 0;

            final Cube cube = cubes.get(relightCubeIdx);
            final int x = AddressTools.getLocalX(relightCubeBlockIdx);
            final int y = AddressTools.getLocalY(relightCubeBlockIdx);
            final int z = AddressTools.getLocalZ(relightCubeBlockIdx);
            final int minX = cube.getCoords()
                .getMinBlockX();
            final int minY = cube.getCoords()
                .getMinBlockY();
            final int minZ = cube.getCoords()
                .getMinBlockZ();
            cube.getWorld()
                .func_147451_t(minX + x, minY + y, minZ + z);

            relightCubeIdx++;
        }
    }
}
