package com.cardinalstar.cubicchunks.mixin.ext;

public interface EBSIDAccessor {

    int getBlockID(int x, int y, int z);
    void setBlockID(int x, int y, int z, int id, boolean tickRandomly);

}
