package com.cardinalstar.cubicchunks.util;

public interface Consumer3DWithValue<T> {

    void accept(int posX, int posY, int posZ, T value);
}
