package com.cardinalstar.cubicchunks.api.worldgen.hwaccel.buffer;

import static org.lwjgl.opengl.GL15.GL_DYNAMIC_DRAW;
import static org.lwjgl.opengl.GL15.glBindBuffer;
import static org.lwjgl.opengl.GL15.glBufferData;
import static org.lwjgl.opengl.GL15.glDeleteBuffers;
import static org.lwjgl.opengl.GL15.glGenBuffers;
import static org.lwjgl.opengl.GL30.glBindBufferBase;
import static org.lwjgl.opengl.GL43.GL_SHADER_STORAGE_BUFFER;

import java.io.Closeable;

import lombok.Getter;
import lombok.Setter;

public class HardwareBuffer implements Closeable, GPUBuffer {

    public final int ssbo;

    @Getter
    public final BufferDataType dataType;
    @Getter
    public final int lenX, lenY, lenZ;

    private boolean open = true;
    @Setter
    private boolean shouldBeCached;

    public HardwareBuffer(int ssbo, BufferDataType dataType, int lenX, int lenY, int lenZ) {
        this.ssbo = ssbo;
        this.dataType = dataType;
        this.lenX = lenX;
        this.lenY = lenY;
        this.lenZ = lenZ;
    }

    @Override
    public int getBufferId() {
        return ssbo;
    }

    @Override
    public int getBufferOffset() {
        return 0;
    }

    @Override
    public void bind(int index) {
        if (!open) {
            throw new IllegalStateException("Cannot bind closed HardwareBuffer");
        }

        glBindBufferBase(GL_SHADER_STORAGE_BUFFER, index, this.ssbo);
    }

    @Override
    public void unbind(int index) {
        glBindBufferBase(GL_SHADER_STORAGE_BUFFER, index, 0);
    }

    public static HardwareBuffer alloc(BufferDataType dataType, int lenX, int lenY, int lenZ) {
        int ssbo = glGenBuffers();

        glBindBuffer(GL_SHADER_STORAGE_BUFFER, ssbo);
        glBufferData(GL_SHADER_STORAGE_BUFFER, (long) dataType.width() * lenX * lenY * lenZ, GL_DYNAMIC_DRAW);
        glBindBuffer(GL_SHADER_STORAGE_BUFFER, 0);

        return new HardwareBuffer(ssbo, dataType, lenX, lenY, lenZ);
    }

    @Override
    public void close() {
        this.open = false;
        glDeleteBuffers(this.ssbo);
    }

    @Override
    public boolean shouldBeCached() {
        return shouldBeCached;
    }
}
