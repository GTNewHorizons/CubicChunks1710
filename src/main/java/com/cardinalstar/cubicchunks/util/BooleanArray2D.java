package com.cardinalstar.cubicchunks.util;

import java.util.BitSet;
import java.util.Iterator;

import org.jetbrains.annotations.NotNull;
import org.joml.Vector2i;
import org.joml.Vector2ic;

import com.google.common.collect.AbstractIterator;

public class BooleanArray2D extends BitSet implements Iterable<Vector2ic> {

    private final int spanx, spany;

    public BooleanArray2D(int spanx, int spany) {
        this.spanx = spanx;
        this.spany = spany;
    }

    public BooleanArray2D(int spanx, int spany, boolean[] data) {
        this.spanx = spanx;
        this.spany = spany;

        for (int i = 0; i < data.length; i++) {
            if (data[i]) {
                set(i);
            }
        }
    }

    public BooleanArray2D(int spanx, int spany, byte[] data) {
        this.spanx = spanx;
        this.spany = spany;

        this.or(BitSet.valueOf(data));
    }

    // Not named set/clear: those would override BitSet.set(fromIndex, toIndex) and BitSet.clear(fromIndex, toIndex),
    // which BitSet.set(fromIndex, toIndex, value) calls
    public void set2d(int x, int y) {
        set(index(x, y));
    }

    public void clear2d(int x, int y) {
        clear(index(x, y));
    }

    public boolean get2d(int x, int y) {
        return get(index(x, y));
    }

    @Override
    public @NotNull Iterator<Vector2ic> iterator() {
        return new AbstractIterator<>() {

            private boolean init = false;
            private int index;
            private final Vector2i v = new Vector2i();

            @Override
            protected Vector2ic computeNext() {
                if (!init) {
                    init = true;
                    index = BooleanArray2D.this.nextSetBit(0);
                } else {
                    index = BooleanArray2D.this.nextSetBit(index + 1);
                }

                if (index == -1) {
                    this.endOfData();
                    return null;
                }

                v.set(index % spanx, (index / spanx) % spany);

                return v;
            }
        };
    }

    private int index(int x, int y) {
        return x + (y * spanx);
    }

    @Override
    public BooleanArray2D clone() {
        return (BooleanArray2D) super.clone();
    }
}
