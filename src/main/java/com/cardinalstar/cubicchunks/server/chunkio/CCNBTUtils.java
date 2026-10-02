package com.cardinalstar.cubicchunks.server.chunkio;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.UUID;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import net.jpountz.lz4.LZ4Exception;
import net.jpountz.lz4.LZ4Factory;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTSizeTracker;
import net.minecraft.nbt.NBTTagCompound;

import com.cardinalstar.cubicchunks.util.ByteBufferInputStream;
import com.cardinalstar.cubicchunks.util.ByteBufferOutputStream;

public class CCNBTUtils {

    public static final int GZIP_MAGIC_NUMBER = 0x8b1F;
    public static final int LZ4_MAGIC_NUMBER = 0x184D2204;
    public static final UUID NONE_MAGIC_NUMBER_UUID = UUID.fromString("904e51a4-3ee3-478c-92ee-db5a0af9ecc0");

    public enum TagCompression {
        GZIP,
        LZ4,
        NONE,
        ZSTD,
    }

    public static NBTTagCompound loadTag(byte[] data) throws IOException {
        return loadTag(ByteBuffer.wrap(data));
    }

    public static NBTTagCompound loadTag(ByteBuffer data) throws IOException {
        data = data.slice()
            .order(ByteOrder.LITTLE_ENDIAN);
        if (data.remaining() < 2) throw new IOException("Truncated NBT record");

        if ((data.getShort(0) & 0xffff) == GZIP_MAGIC_NUMBER) {
            try (DataInputStream input = new DataInputStream(
                new BufferedInputStream(new GZIPInputStream(new ByteBufferInputStream(data))))) {
                return CompressedStreamTools.func_152456_a(input, NBTSizeTracker.field_152451_a);
            }
        }
        if (data.remaining() >= 4 && data.getInt(0) == ZstdCodec.MAGIC) {
            byte[] raw = ZstdCodec.decompress(data, ZstdCodec.MAX_RECORD_BYTES);
            try (DataInputStream input = new DataInputStream(new ByteArrayInputStream(raw))) {
                NBTTagCompound tag = CompressedStreamTools.func_152456_a(input, NBTSizeTracker.field_152451_a);
                if (input.read() != -1) throw new IOException("Trailing Zstd NBT data");
                return tag;
            }
        }
        if (data.remaining() >= 8 && data.getInt(0) == LZ4_MAGIC_NUMBER) {
            int length = data.getInt(4);
            if (length < 1) throw new IOException("Invalid LZ4 NBT length");
            ByteBuffer decoded = ByteBuffer.allocate(length);
            try {
                int actual = LZ4Factory.fastestInstance()
                    .safeDecompressor()
                    .decompress(data, 8, data.remaining() - 8, decoded, 0, length);
                if (actual != length) throw new IOException("Truncated LZ4 NBT data");
            } catch (LZ4Exception e) {
                throw new IOException("Invalid LZ4 NBT data", e);
            }
            return readRaw(decoded);
        }
        if (data.remaining() >= 16 && data.getLong(0) == NONE_MAGIC_NUMBER_UUID.getLeastSignificantBits()
            && data.getLong(8) == NONE_MAGIC_NUMBER_UUID.getMostSignificantBits()) {
            data.position(16);
            return readRaw(data);
        }
        // Legacy uncompressed records have no codec header.
        if (data.get(0) == 10) return readRaw(data);
        throw new IOException("Unknown or truncated NBT tag byte format");
    }

    private static NBTTagCompound readRaw(ByteBuffer data) throws IOException {
        try (DataInputStream input = new DataInputStream(new ByteBufferInputStream(data))) {
            return CompressedStreamTools.func_152456_a(input, NBTSizeTracker.field_152451_a);
        }
    }

    public static byte[] saveTag(NBTTagCompound tag, boolean compress) throws IOException {
        ByteBuffer data = saveTag(tag, compress ? TagCompression.GZIP : TagCompression.NONE);
        // Preserve the existing boolean API's unprefixed raw-NBT output.
        if (!compress) data.position(16);
        byte[] bytes = new byte[data.remaining()];
        data.get(bytes);
        return bytes;
    }

    public static ByteBuffer saveTag(NBTTagCompound tag, TagCompression compression) throws IOException {
        ByteBufferOutputStream output = new ByteBufferOutputStream(512);
        if (compression == TagCompression.GZIP) {
            try (DataOutputStream stream = new DataOutputStream(
                new BufferedOutputStream(new GZIPOutputStream(output)))) {
                CompressedStreamTools.write(tag, stream);
            }
            return output.toByteBuffer();
        }
        try (DataOutputStream stream = new DataOutputStream(output)) {
            if (compression == TagCompression.NONE) {
                stream.writeLong(Long.reverseBytes(NONE_MAGIC_NUMBER_UUID.getLeastSignificantBits()));
                stream.writeLong(Long.reverseBytes(NONE_MAGIC_NUMBER_UUID.getMostSignificantBits()));
            }
            CompressedStreamTools.write(tag, stream);
        }
        ByteBuffer data = output.toByteBuffer();
        switch (compression) {
            case LZ4 -> {
                var compressor = LZ4Factory.fastestInstance()
                    .fastCompressor();
                int length = data.remaining();
                ByteBuffer encoded = ByteBuffer.allocate(8 + compressor.maxCompressedLength(length))
                    .order(ByteOrder.LITTLE_ENDIAN);
                int size = compressor.compress(data, 0, length, encoded, 8, encoded.capacity() - 8);
                encoded.putInt(0, LZ4_MAGIC_NUMBER);
                encoded.putInt(4, length);
                encoded.limit(8 + size);
                return encoded;
            }
            case ZSTD -> {
                if (data.remaining() > ZstdCodec.MAX_RECORD_BYTES) return saveTag(tag, TagCompression.GZIP);
                return ZstdCodec.compress(data);
            }
            case NONE -> {
                return data;
            }
            default -> throw new AssertionError("Illegal compression: " + compression);
        }
    }
}
