package com.cardinalstar.cubicchunks.util;

public interface Consumer2DWithValue<T> {

    void accept(int posX, int posZ, T value);
}
