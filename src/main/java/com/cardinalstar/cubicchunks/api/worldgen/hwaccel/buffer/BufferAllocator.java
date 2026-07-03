package com.cardinalstar.cubicchunks.api.worldgen.hwaccel.buffer;

import java.nio.ByteBuffer;

import com.cardinalstar.cubicchunks.api.worldgen.hwaccel.PrimitiveBuffer;
import com.cardinalstar.cubicchunks.util.MathUtil;
import com.gtnewhorizon.gtnhlib.bytebuf.MemoryUtilities;

public interface BufferAllocator {

    default GPUBuffer alloc(BufferDataType dataType, int lenX) {
        return alloc(dataType, lenX, 1, 1);
    }

    default GPUBuffer alloc(BufferDataType dataType, int lenX, int lenY) {
        return alloc(dataType, lenX, lenY, 1);
    }

    default GPUBuffer uniform(PrimitiveBuffer<?> buffer) {
        int byteLen = buffer.getByteLength();
        int wordLen = MathUtil.ceilDiv(byteLen, 4);

        GPUBuffer gpu = alloc(BufferDataType.i32, wordLen);

        ByteBuffer temp = MemoryUtilities.memAlloc(byteLen);

        try {
            buffer.upload(temp);
            temp.flip();

            gpu.upload(temp);
        } finally {
            MemoryUtilities.memFree(temp);
        }

        return gpu;
    }

    GPUBuffer alloc(BufferDataType dataType, int lenX, int lenY, int lenZ);

    void bindSSBO(int index);
    void unbindSSBO(int index);
}
