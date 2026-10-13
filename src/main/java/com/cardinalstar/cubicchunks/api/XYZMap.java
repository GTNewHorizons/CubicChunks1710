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

import com.gtnewhorizon.gtnhlib.hash.Fnv1a32;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenCustomHashMap;
import it.unimi.dsi.fastutil.longs.LongHash.Strategy;

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

    Long2ObjectOpenCustomHashMap<T> items = new Long2ObjectOpenCustomHashMap<>(new Strategy() {

        @Override
        public int hashCode(long e) {
            return Fnv1a32.hashStep(Fnv1a32.initialState(), e);
        }

        @Override
        public boolean equals(long a, long b) {
            return a == b;
        }
    });

    public T remove(int x, int y, int z) {
        if (x < -2097152 || x > 2097151) return null;
        if (y < -2097152 || y > 2097151) return null;
        if (z < -2097152 || z > 2097151) return null;

        return items.remove(Coords.key(x, y, z));
    }

    public final T get(int x, int y, int z) {
        if (x < -2097152 || x > 2097151) return null;
        if (y < -2097152 || y > 2097151) return null;
        if (z < -2097152 || z > 2097151) return null;

        return items.get(Coords.key(x, y, z));
    }

    public final T get(XYZAddressable xyz) {
        return get(xyz.getX(), xyz.getY(), xyz.getZ());
    }

    public final T put(T item) {
        if (item.getX() < -2097152 || item.getX() > 2097151) return null;
        if (item.getY() < -2097152 || item.getY() > 2097151) return null;
        if (item.getZ() < -2097152 || item.getZ() > 2097151) return null;

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
