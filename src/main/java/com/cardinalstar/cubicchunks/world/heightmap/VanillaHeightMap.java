package com.cardinalstar.cubicchunks.world.heightmap;

// This class exists only because I don't want to introduce many off-by-one errors when modifying height tracking
// code to store
// height-above-the-top-block instead of height-of-the-top-block (which is done so that the heightmap array can be
// shared with vanilla)
public final class VanillaHeightMap {

    public final int[] data;

    public VanillaHeightMap(int[] heightmap) {
        this.data = heightmap;
    }

    public int get(int x, int z) {
        return data[z << 4 | x] - 1;
    }

    public void set(int x, int z, int value) {
        data[z << 4 | x] = value + 1;
    }
}
