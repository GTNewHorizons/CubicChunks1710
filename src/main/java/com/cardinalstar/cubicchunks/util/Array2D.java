package com.cardinalstar.cubicchunks.util;

public final class Array2D<T> {

    private final int spanx;
    private final int spany;
    private final int offsetx;
    private final int offsety;
    private final T[] data;

    public Array2D(int spanx, int spany, int offsetx, int offsety, T[] data) {
        this.spanx = spanx;
        this.spany = spany;
        this.offsetx = offsetx;
        this.offsety = offsety;
        this.data = data;
    }

    public void set(final int x, final int y, final T value) {
        final int relx = x - offsetx;
        final int rely = y - offsety;

        if (relx < 0 || relx >= spanx) return;
        if (rely < 0 || rely >= spany) return;

        data[relx + (rely * spanx)] = value;
    }

    public T get(final int x, final int y) {
        final int relx = x - offsetx;
        final int rely = y - offsety;

        if (relx < 0 || relx >= spanx) return null;
        if (rely < 0 || rely >= spany) return null;

        return data[relx + (rely * spanx)];
    }
}
