package com.cardinalstar.cubicchunks.api.worldgen.hwaccel.buffer;

import static org.lwjgl.opengl.GL15.GL_STATIC_DRAW;
import static org.lwjgl.opengl.GL15.glBindBuffer;
import static org.lwjgl.opengl.GL15.glBufferData;
import static org.lwjgl.opengl.GL15.glDeleteBuffers;
import static org.lwjgl.opengl.GL15.glGenBuffers;
import static org.lwjgl.opengl.GL30.glBindBufferBase;
import static org.lwjgl.opengl.GL43.GL_SHADER_STORAGE_BUFFER;

import java.io.Closeable;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

import com.cardinalstar.cubicchunks.api.worldgen.hwaccel.KernelContext;
import com.cardinalstar.cubicchunks.util.MathUtil;
import com.github.bsideup.jabel.Desugar;
import com.gtnewhorizon.gtnhlib.bytebuf.MemoryUtilities;
import lombok.Getter;

public class ConstantHardwareBuffer implements Closeable {

    public final int ssbo;
    private final String bufferName;

    @Getter
    private boolean open = true;
    private boolean finished = false;

    private final List<Range> ranges = new ArrayList<>();
    private int len;

    @Desugar
    private record Range(int start, int size, long ptr) { }

    public ConstantHardwareBuffer(String bufferName) {
        this.ssbo = glGenBuffers();
        this.bufferName = bufferName;
    }

    public BufferAccessor append(BufferDataType dataType, ByteBuffer data) {
        if (dataType.width() != 4) throw new IllegalArgumentException("Constant buffer can only store data types whose byte length is exactly 4 bytes");

        int size = data.remaining();

        long ptr = MemoryUtilities.nmemAlloc(data.remaining());
        MemoryUtilities.memByteBuffer(ptr, size).put(data);

        int offset = len;

        ranges.add(new Range(offset, size, ptr));

        len = MathUtil.alignTo(len + size, KernelContext.getSSBOAlignment());

        return index -> {
            String base = String.format("%s[(%s) + %d]", this.bufferName, index, offset / 4);

            return switch (dataType) {
                case i32 -> "(int(" + base + "))";
                case u32 -> "(" + base + ")";
                case f32 -> "(uintBitsToFloat(" + base + "))";
                default -> throw new AssertionError();
            };
        };
    }

    @Override
    public void close() {
        for (var range : ranges) {
            MemoryUtilities.nmemFree(range.ptr);
        }

        ranges.clear();

        this.open = false;
        glDeleteBuffers(this.ssbo);
    }

    public ConstantHardwareBuffer finish() {
        if (finished) throw new IllegalStateException("Cannot finish ConstantHardwareBuffer twice");
        finished = true;

        ByteBuffer data = MemoryUtilities.memAlloc(len);
        long dataPtr = MemoryUtilities.memAddress(data);

        for (var range : ranges) {
            MemoryUtilities.memCopy(range.ptr, dataPtr, range.size);
            MemoryUtilities.nmemFree(range.ptr);
        }

        ranges.clear();

        glBindBuffer(GL_SHADER_STORAGE_BUFFER, this.ssbo);
        glBufferData(GL_SHADER_STORAGE_BUFFER, data, GL_STATIC_DRAW);
        glBindBuffer(GL_SHADER_STORAGE_BUFFER, 0);

        MemoryUtilities.memFree(data);

        return this;
    }

    public void bindSSBO(int index) {
        if (!finished) throw new IllegalStateException("Cannot bind unfinished ConstantHardwareBuffer");

        glBindBufferBase(GL_SHADER_STORAGE_BUFFER, index, this.ssbo);
    }

    public void unbindSSBO(int index) {
        if (!finished) throw new IllegalStateException("Cannot bind unfinished ConstantHardwareBuffer");

        glBindBufferBase(GL_SHADER_STORAGE_BUFFER, index, 0);
    }
}
