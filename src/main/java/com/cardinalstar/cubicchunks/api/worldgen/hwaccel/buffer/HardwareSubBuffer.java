package com.cardinalstar.cubicchunks.api.worldgen.hwaccel.buffer;

import static org.lwjgl.opengl.GL30.glBindBufferBase;
import static org.lwjgl.opengl.GL30.glBindBufferRange;
import static org.lwjgl.opengl.GL43.GL_SHADER_STORAGE_BUFFER;

import java.nio.ByteBuffer;

import lombok.Getter;

public class HardwareSubBuffer implements GPUBuffer {

    private final ArenaHardwareBuffer arena;
    public final int ssbo;

    public final int ssboOffset;

    @Getter
    public final BufferDataType dataType;
    @Getter
    public final int lenX, lenY, lenZ;

    public HardwareSubBuffer(ArenaHardwareBuffer arena, int ssboOffset, BufferDataType dataType, int lenX, int lenY, int lenZ) {
        this.arena = arena;
        this.ssbo = arena.getSsbo();
        this.ssboOffset = ssboOffset;
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
        return ssboOffset;
    }

    @Override
    public void bind(int index) {
        if (!arena.isOpen()) {
            throw new IllegalStateException("Cannot bind HardwareSubBuffer when its arena buffer has been closed");
        }

        glBindBufferRange(GL_SHADER_STORAGE_BUFFER, index, this.ssbo, this.ssboOffset, this.getBufferLength());
    }

    @Override
    public void unbind(int index) {
        glBindBufferBase(GL_SHADER_STORAGE_BUFFER, index, 0);
    }

    @Override
    public void close() {
        // do nothing
    }

    @Override
    public boolean shouldBeCached() {
        return false;
    }

    @Override
    public void download(ByteBuffer dest) {
        this.arena.download(dest, getBufferOffset(), getBufferLength());
    }

    @Override
    public void upload(ByteBuffer data) {
        this.arena.upload(data, getBufferOffset(), getBufferLength());
    }
}
