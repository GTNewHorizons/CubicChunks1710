package com.cardinalstar.cubicchunks.api.worldgen.hwaccel.buffer;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;

public interface ConstantBuffer {

    GPUBuffer addConstant(BufferDataType dataType, ByteBuffer data);

    default GPUBuffer addConstant(IntBuffer data) {
        ByteBuffer bytes = ByteBuffer.allocateDirect(data.remaining() * 4)
            .order(ByteOrder.nativeOrder());

        int rem = data.remaining();

        for (int i = 0; i < rem; i++) {
            int value = data.get(data.position() + i);

            bytes.putInt(i * 4, value);
        }

        return addConstant(BufferDataType.i32, bytes);
    }

    default GPUBuffer addConstant(int[] data) {
        ByteBuffer bytes = ByteBuffer.allocateDirect(data.length * 4)
            .order(ByteOrder.nativeOrder());

        int rem = data.length;

        for (int i = 0; i < rem; i++) {
            bytes.putInt(i * 4, data[i]);
        }

        return addConstant(BufferDataType.i32, bytes);
    }

    default GPUBuffer addConstant(FloatBuffer data) {
        ByteBuffer bytes = ByteBuffer.allocateDirect(data.remaining() * 4)
            .order(ByteOrder.nativeOrder());

        int rem = data.remaining();

        for (int i = 0; i < rem; i++) {
            float value = data.get(data.position() + i);

            bytes.putFloat(i * 4, value);
        }

        return addConstant(BufferDataType.f32, bytes);
    }

    default GPUBuffer addConstant(float[] data) {
        ByteBuffer bytes = ByteBuffer.allocateDirect(data.length * 4)
            .order(ByteOrder.nativeOrder());

        int rem = data.length;

        for (int i = 0; i < rem; i++) {
            bytes.putFloat(i * 4, data[i]);
        }

        return addConstant(BufferDataType.f32, bytes);
    }
}
