package com.cardinalstar.cubicchunks.util;

import org.jetbrains.annotations.UnknownNullability;

import com.github.bsideup.jabel.Desugar;

import it.unimi.dsi.fastutil.ints.Int2ObjectFunction;

@Desugar
public record Array2D_16x16<T> (T[] data) {

    public Array2D_16x16(T[] data) {
        this.data = data;

        if (data.length != 256) throw new IllegalArgumentException("Array must be exactly 256 items long: " + data);
    }

    public Array2D_16x16(Int2ObjectFunction<T[]> ctor) {
        this(ctor.apply(256));
    }

    public void set(final int x, final int y, final T value) {
        if (x < 0 || x >= 16) return;
        if (y < 0 || y >= 16) return;

        data[y << 4 | x] = value;
    }

    @UnknownNullability
    public T get(final int x, final int y) {
        if (x < 0 || x >= 16) return null;
        if (y < 0 || y >= 16) return null;

        return data[y << 4 | x];
    }
}
