package com.cardinalstar.cubicchunks.util;

import java.io.OutputStream;
import java.nio.ByteBuffer;

import org.jetbrains.annotations.NotNull;

/**
 * An {@link OutputStream} which accumulates into a heap {@link ByteBuffer} that can be handed out directly, without
 * the extra full copy that {@link java.io.ByteArrayOutputStream#toByteArray()} performs.
 */
public final class ByteBufferOutputStream extends OutputStream {

    private static final int MIN_CAPACITY = 32;

    private ByteBuffer buffer;

    public ByteBufferOutputStream(int initialCapacity) {
        this.buffer = ByteBuffer.allocate(Math.max(initialCapacity, MIN_CAPACITY));
    }

    @Override
    public void write(int b) {
        ensureRemaining(1);
        buffer.put((byte) b);
    }

    @Override
    public void write(byte @NotNull [] bytes, int off, int len) {
        ensureRemaining(len);
        buffer.put(bytes, off, len);
    }

    private void ensureRemaining(int count) {
        if (buffer.remaining() >= count) {
            return;
        }

        int required = buffer.position() + count;
        int capacity = Math.max(required, buffer.capacity() * 2);

        ByteBuffer grown = ByteBuffer.allocate(capacity);
        buffer.flip();
        grown.put(buffer);

        buffer = grown;
    }

    /**
     * Returns the bytes written so far, as a buffer positioned at 0 with its limit set to the number of bytes written.
     * The returned buffer shares its contents with this stream, so it must not be used after writing any more data.
     */
    public ByteBuffer toByteBuffer() {
        ByteBuffer result = buffer.duplicate();
        result.flip();
        return result;
    }
}
