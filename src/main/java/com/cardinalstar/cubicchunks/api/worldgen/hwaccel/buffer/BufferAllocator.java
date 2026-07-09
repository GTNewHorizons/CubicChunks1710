package com.cardinalstar.cubicchunks.api.worldgen.hwaccel.buffer;

public interface BufferAllocator {

    default GPUBuffer alloc(BufferDataType dataType, int lenX) {
        return alloc(dataType, lenX, 1, 1);
    }

    default GPUBuffer alloc(BufferDataType dataType, int lenX, int lenY) {
        return alloc(dataType, lenX, lenY, 1);
    }

    default GPUBuffer alloc(BufferLayout layout) {
        return alloc(layout.dataType(), layout.lenX(), layout.lenY(), layout.lenZ());
    }

    GPUBuffer alloc(BufferDataType dataType, int lenX, int lenY, int lenZ);
}
