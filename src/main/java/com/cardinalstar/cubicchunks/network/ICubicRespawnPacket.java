package com.cardinalstar.cubicchunks.network;

public interface ICubicRespawnPacket {

    int cubicChunks$getMinHeight();

    int cubicChunks$getMaxHeight();

    int cubicChunks$getMinGenerationHeight();

    int cubicChunks$getMaxGenerationHeight();

    void cubicChunks$setHeightInfo(int minHeight, int maxHeight, int minGenerationHeight, int maxGenerationHeight);
}
