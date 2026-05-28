package com.cardinalstar.cubicchunks.util;

import com.cardinalstar.cubicchunks.api.XYZAddressable;

public class MutableXYZ implements XYZAddressable {

    public int x, y, z;

    @Override
    public int getX() {
        return x;
    }

    @Override
    public int getY() {
        return y;
    }

    @Override
    public int getZ() {
        return z;
    }
}
