package com.cardinalstar.cubicchunks.api.worldgen.hwaccel.buffer;

import static org.lwjgl.opengl.GL11.glGetError;
import static org.lwjgl.opengl.GL15.glBindBuffer;
import static org.lwjgl.opengl.GL15.glUnmapBuffer;
import static org.lwjgl.opengl.GL30.GL_MAP_READ_BIT;
import static org.lwjgl.opengl.GL30.GL_MAP_WRITE_BIT;
import static org.lwjgl.opengl.GL30.glMapBufferRange;
import static org.lwjgl.opengl.GL31.GL_COPY_READ_BUFFER;
import static org.lwjgl.opengl.GL31.GL_COPY_WRITE_BUFFER;
import static org.lwjgl.opengl.GL31.glCopyBufferSubData;
import static org.lwjgl.opengl.GL43.GL_SHADER_STORAGE_BUFFER;

import java.io.Closeable;
import java.nio.ByteBuffer;

import org.lwjgl.util.glu.GLU;

import com.gtnewhorizon.gtnhlib.bytebuf.MemoryUtilities;

public interface GPUBuffer extends Closeable {

    BufferDataType getDataType();

    int getBufferId();
    int getBufferOffset();
    default int getBufferLength() {
        return getDataType().width() * getLenX() * getLenY() * getLenZ();
    }

    int getLenX();
    int getLenY();
    int getLenZ();

    void bind(int index);
    void unbind(int index);

    default void copyTo(GPUBuffer dest) {
        if (this.getBufferLength() != dest.getBufferLength()) {
            throw new UnsupportedOperationException("Both buffers must be the same length when copying");
        }

        glBindBuffer(GL_COPY_READ_BUFFER, this.getBufferId());
        glBindBuffer(GL_COPY_WRITE_BUFFER, dest.getBufferId());

        glCopyBufferSubData(GL_COPY_READ_BUFFER, GL_COPY_WRITE_BUFFER, this.getBufferOffset(), dest.getBufferOffset(), this.getBufferLength());

        glBindBuffer(GL_COPY_READ_BUFFER, 0);
        glBindBuffer(GL_COPY_WRITE_BUFFER, 0);
    }

    default void download(ByteBuffer dest) {
        if (this.getBufferLength() != dest.remaining()) {
            throw new UnsupportedOperationException("Both buffers must be the same length when copying");
        }

        glBindBuffer(GL_SHADER_STORAGE_BUFFER, getBufferId());
        ByteBuffer mapped = glMapBufferRange(GL_SHADER_STORAGE_BUFFER, getBufferOffset(), getBufferLength(), GL_MAP_READ_BIT, null);

        if (mapped == null) {
            int error = glGetError();

            glBindBuffer(GL_SHADER_STORAGE_BUFFER, 0);

            throw new IllegalStateException("Could not map buffer: " + GLU.gluErrorString(error));
        }

        MemoryUtilities.memCopy(mapped, dest);
        dest.position(dest.position() + this.getBufferLength());

        glUnmapBuffer(GL_SHADER_STORAGE_BUFFER);
        glBindBuffer(GL_SHADER_STORAGE_BUFFER, 0);
    }

    default void upload(ByteBuffer data) {
        if (this.getBufferLength() != data.remaining()) {
            throw new UnsupportedOperationException("Both buffers must be the same length when copying");
        }

        glBindBuffer(GL_SHADER_STORAGE_BUFFER, getBufferId());
        ByteBuffer mapped = glMapBufferRange(GL_SHADER_STORAGE_BUFFER, getBufferOffset(), getBufferLength(), GL_MAP_WRITE_BIT, null);

        if (mapped == null) {
            int error = glGetError();

            glBindBuffer(GL_SHADER_STORAGE_BUFFER, 0);

            throw new IllegalStateException("Could not map buffer: " + GLU.gluErrorString(error));
        }

        MemoryUtilities.memCopy(data, mapped);
        data.position(data.position() + this.getBufferLength());

        glUnmapBuffer(GL_SHADER_STORAGE_BUFFER);
        glBindBuffer(GL_SHADER_STORAGE_BUFFER, 0);
    }

    @Override
    void close();

    boolean shouldBeCached();
}
