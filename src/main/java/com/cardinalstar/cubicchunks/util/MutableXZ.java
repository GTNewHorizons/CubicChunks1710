package com.cardinalstar.cubicchunks.util;

public class MutableXZ implements XZAddressable {

    public int x, z;

    @Override
    public int getX() {
        return x;
    }

    @Override
    public int getZ() {
        return z;
    }
}
