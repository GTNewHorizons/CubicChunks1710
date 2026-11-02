package com.cardinalstar.cubicchunks.server.chunkio;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.concurrent.ArrayBlockingQueue;

import com.github.luben.zstd.Zstd;
import com.github.luben.zstd.ZstdCompressCtx;
import com.github.luben.zstd.ZstdDecompressCtx;
import com.github.luben.zstd.ZstdException;

final class ZstdCodec {

    static final int MAGIC = 0xFD2FB528;
    static final int MAX_RECORD_BYTES = 64 * 1024 * 1024;

    // Bound retained native contexts even when callers use short-lived threads.
    private static final ArrayBlockingQueue<ZstdCompressCtx> COMPRESSORS = new ArrayBlockingQueue<>(8);
    private static final ArrayBlockingQueue<ZstdDecompressCtx> DECOMPRESSORS = new ArrayBlockingQueue<>(8);

    private ZstdCodec() {}

    static ByteBuffer compress(ByteBuffer input) throws IOException {
        if (input.remaining() > MAX_RECORD_BYTES) throw new IOException("Oversized Zstd NBT record");
        ByteBuffer data = readableArray(input);
        byte[] output = new byte[(int) Zstd.compressBound(data.remaining())];
        ZstdCompressCtx context = COMPRESSORS.poll();
        if (context == null) context = new ZstdCompressCtx().setLevel(1)
            .setChecksum(true);
        try {
            int size = context.compressByteArray(
                output,
                0,
                output.length,
                data.array(),
                data.arrayOffset() + data.position(),
                data.remaining());
            return ByteBuffer.wrap(output, 0, size)
                .order(ByteOrder.LITTLE_ENDIAN);
        } catch (ZstdException e) {
            throw new IOException("Could not compress Zstd NBT", e);
        } finally {
            if (!COMPRESSORS.offer(context)) context.close();
        }
    }

    static byte[] decompress(ByteBuffer input, int maxBytes) throws IOException {
        ByteBuffer data = readableArray(input);
        byte[] encoded = data.array();
        int offset = data.arrayOffset() + data.position(), length = data.remaining();
        if (length < 6) throw new IOException("Truncated Zstd NBT frame");
        try {
            long size = Zstd.getFrameContentSize(encoded, offset, length);
            if (size < 1 || size > maxBytes) throw new IOException("Invalid Zstd NBT length: " + size);
            if (Zstd.findFrameCompressedSize(encoded, offset, length) != length) {
                throw new IOException("Trailing Zstd NBT frames or data");
            }
            byte[] output = new byte[(int) size];
            ZstdDecompressCtx context = DECOMPRESSORS.poll();
            if (context == null) context = new ZstdDecompressCtx();
            try {
                int decoded = context.decompressByteArray(output, 0, output.length, encoded, offset, length);
                if (decoded != size) throw new IOException("Incorrect Zstd NBT length");
                return output;
            } finally {
                if (!DECOMPRESSORS.offer(context)) context.close();
            }
        } catch (ZstdException e) {
            throw new IOException("Invalid Zstd NBT frame", e);
        }
    }

    private static ByteBuffer readableArray(ByteBuffer input) {
        if (input.hasArray()) return input;
        byte[] copy = new byte[input.remaining()];
        input.duplicate()
            .get(copy);
        return ByteBuffer.wrap(copy);
    }
}
