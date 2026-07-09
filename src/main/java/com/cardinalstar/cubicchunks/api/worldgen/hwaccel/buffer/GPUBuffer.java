package com.cardinalstar.cubicchunks.api.worldgen.hwaccel.buffer;

public interface GPUBuffer {

    BufferDataType getDataType();

    int getBufferOffset();

    default int getBufferLength() {
        return getDataType().width() * getLenX() * getLenY() * getLenZ();
    }

    int getLenX();

    int getLenY();

    int getLenZ();
}
