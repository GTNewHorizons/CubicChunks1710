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
package com.cardinalstar.cubicchunks.api;

import java.util.Iterator;

import javax.annotation.Nonnull;
import javax.annotation.ParametersAreNonnullByDefault;

import com.cardinalstar.cubicchunks.util.Coords;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;

/**
 * Hash table implementation for objects in a 3-dimensional cartesian coordinate
 * system.
 *
 * @param <T> class of the objects to be contained in this map
 *
 * @see XYZAddressable
 */
@ParametersAreNonnullByDefault
public class XYZMap<T extends XYZAddressable> implements Iterable<T> {

    Long2ObjectOpenHashMap<T> items = new Long2ObjectOpenHashMap<>();

    public T remove(int x, int y, int z) {
        if (!Coords.isPackable(x, y, z)) return null;

        return items.remove(Coords.key(x, y, z));
    }

    public final T get(int x, int y, int z) {
        if (!Coords.isPackable(x, y, z)) return null;

        return items.get(Coords.key(x, y, z));
    }

    public final T get(XYZAddressable xyz) {
        return get(xyz.getX(), xyz.getY(), xyz.getZ());
    }

    public final T put(T item) {
        if (!Coords.isPackable(item.getX(), item.getY(), item.getZ())) return null;

        return items.put(Coords.key(item.getX(), item.getY(), item.getZ()), item);
    }

    public final <T2 extends XYZAddressable> T remove(T2 key) {
        return remove(key.getX(), key.getY(), key.getZ());
    }

    @Override
    @Nonnull
    public Iterator<T> iterator() {
        return items.values()
            .iterator();
    }

    public int getSize() {
        return items.size();
    }
}
