package com.cardinalstar.cubicchunks.util;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;

import org.jetbrains.annotations.NotNull;

public final class ByteBufferInputStream extends InputStream {
    private final ByteBuffer buffer;

    public ByteBufferInputStream(ByteBuffer buffer) {
        this.buffer = buffer;
    }

    @Override
    public int read() throws IOException {
        if (!buffer.hasRemaining()) {
            return -1;
        }
        return buffer.get() & 0xFF;
    }

    @Override
    public int read(byte @NotNull [] bytes, int off, int len) throws IOException {
        if (!buffer.hasRemaining()) {
            return -1;
        }
        int bytesToRead = Math.min(len, buffer.remaining());
        buffer.get(bytes, off, bytesToRead);
        return bytesToRead;
    }

    @Override
    public int available() {
        return buffer.remaining();
    }
}
