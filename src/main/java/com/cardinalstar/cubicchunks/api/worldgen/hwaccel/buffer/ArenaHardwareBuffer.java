package com.cardinalstar.cubicchunks.api.worldgen.hwaccel.buffer;

import static org.lwjgl.opengl.GL15.glBindBuffer;
import static org.lwjgl.opengl.GL15.glDeleteBuffers;
import static org.lwjgl.opengl.GL15.glGenBuffers;
import static org.lwjgl.opengl.GL15.glUnmapBuffer;
import static org.lwjgl.opengl.GL30.GL_MAP_FLUSH_EXPLICIT_BIT;
import static org.lwjgl.opengl.GL30.GL_MAP_INVALIDATE_BUFFER_BIT;
import static org.lwjgl.opengl.GL30.GL_MAP_READ_BIT;
import static org.lwjgl.opengl.GL30.GL_MAP_WRITE_BIT;
import static org.lwjgl.opengl.GL30.glFlushMappedBufferRange;
import static org.lwjgl.opengl.GL30.glMapBufferRange;
import static org.lwjgl.opengl.GL43.GL_SHADER_STORAGE_BUFFER;
import static org.lwjgl.opengl.GL44.GL_DYNAMIC_STORAGE_BIT;
import static org.lwjgl.opengl.GL44.GL_MAP_PERSISTENT_BIT;
import static org.lwjgl.opengl.GL44.glBufferStorage;

import java.io.Closeable;
import java.nio.ByteBuffer;

import com.cardinalstar.cubicchunks.api.worldgen.hwaccel.KernelContext;
import com.cardinalstar.cubicchunks.util.MathUtil;
import com.gtnewhorizon.gtnhlib.bytebuf.MemoryUtilities;
import lombok.Getter;

public class ArenaHardwareBuffer implements Closeable {

    @Getter
    private int ssbo = 0;
    @Getter
    private int byteLen = 0;

    private int nextBufferOffset;

    @Getter
    private boolean open = true;

    @Getter
    private ByteBuffer mapped;

    public void download(ByteBuffer dest, int offset, int len) {
        if (offset + len > byteLen) {
            throw new IllegalArgumentException("Invalid parameters: offset=" + offset + ", len=" + len + ", byteLen=" + byteLen);
        }

        if (len > dest.remaining()) {
            throw new IllegalArgumentException("Invalid parameters: remaining=" + dest.remaining() + ", len=" + len);
        }

        MemoryUtilities.memCopy(MemoryUtilities.memAddress(mapped) + offset, MemoryUtilities.memAddress(dest), len);
    }

    public void upload(ByteBuffer src, int offset, int len) {
        if (offset + len > byteLen) {
            throw new IllegalArgumentException("Invalid parameters: offset=" + offset + ", len=" + len + ", byteLen=" + byteLen);
        }

        if (len > src.remaining()) {
            throw new IllegalArgumentException("Invalid parameters: remaining=" + src.remaining() + ", len=" + len);
        }

        MemoryUtilities.memCopy(MemoryUtilities.memAddress(src), MemoryUtilities.memAddress(mapped) + offset, len);

        glBindBuffer(GL_SHADER_STORAGE_BUFFER, ssbo);
        glFlushMappedBufferRange(GL_SHADER_STORAGE_BUFFER, offset, len);
        glBindBuffer(GL_SHADER_STORAGE_BUFFER, 0);
    }

    public void reset() {
        this.nextBufferOffset = 0;
    }

    public void resize(int byteLen) {
        if (this.ssbo != 0) {
            close();
        }

        this.ssbo = glGenBuffers();

        int storageFlags = GL_DYNAMIC_STORAGE_BIT | GL_MAP_READ_BIT | GL_MAP_WRITE_BIT | GL_MAP_PERSISTENT_BIT;
        int mapFlags = GL_MAP_READ_BIT | GL_MAP_WRITE_BIT | GL_MAP_PERSISTENT_BIT | GL_MAP_FLUSH_EXPLICIT_BIT;

        glBindBuffer(GL_SHADER_STORAGE_BUFFER, ssbo);
        glBufferStorage(GL_SHADER_STORAGE_BUFFER, byteLen, storageFlags);
        this.mapped = glMapBufferRange(GL_SHADER_STORAGE_BUFFER, 0, byteLen, mapFlags, null);
        glBindBuffer(GL_SHADER_STORAGE_BUFFER, 0);

        this.open = true;
        this.byteLen = byteLen;
    }

    @Override
    public void close() {
        glBindBuffer(GL_SHADER_STORAGE_BUFFER, ssbo);
        glUnmapBuffer(GL_SHADER_STORAGE_BUFFER);
        glBindBuffer(GL_SHADER_STORAGE_BUFFER, 0);

        glDeleteBuffers(this.ssbo);

        this.ssbo = 0;
        this.open = false;
        this.mapped = null;
        this.byteLen = 0;
        this.nextBufferOffset = 0;
    }

    public HardwareSubBuffer alloc(BufferDataType dataType, int lenX, int lenY, int lenZ) {
        int byteLen = dataType.width() * lenX * lenY * lenZ;

        if (this.nextBufferOffset + byteLen > this.byteLen) {
            return null;
        }

        int offset = this.nextBufferOffset;
        this.nextBufferOffset = MathUtil.alignTo(this.nextBufferOffset + byteLen, KernelContext.getSSBOAlignment());

        return new HardwareSubBuffer(this, offset, dataType, lenX, lenY, lenZ);
    }
}
