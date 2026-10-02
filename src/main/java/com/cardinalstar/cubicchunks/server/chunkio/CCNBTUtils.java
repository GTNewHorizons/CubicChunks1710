package com.cardinalstar.cubicchunks.server.chunkio;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.UUID;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import net.jpountz.lz4.LZ4Exception;
import net.jpountz.lz4.LZ4Factory;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTSizeTracker;
import net.minecraft.nbt.NBTTagByteArray;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagIntArray;
import net.minecraft.nbt.NBTTagString;
import net.minecraftforge.common.util.Constants.NBT;

import com.cardinalstar.cubicchunks.mixin.early.common.AccessorNBTTagCompound;
import com.cardinalstar.cubicchunks.mixin.early.common.AccessorNBTTagList;
import com.cardinalstar.cubicchunks.util.ByteBufferInputStream;
import com.cardinalstar.cubicchunks.util.ByteBufferOutputStream;

public class CCNBTUtils {

    public static final int GZIP_MAGIC_NUMBER = 0x8b1F;
    public static final int LZ4_MAGIC_NUMBER = 0x184D2204;
    public static final UUID NONE_MAGIC_NUMBER_UUID = UUID.fromString("904e51a4-3ee3-478c-92ee-db5a0af9ecc0");

    public static NBTTagCompound loadTag(byte[] data) throws IOException {
        return loadTag(ByteBuffer.wrap(data));
    }

    public static NBTTagCompound loadTag(ByteBuffer data) throws IOException {
        data = data.slice()
            .order(ByteOrder.LITTLE_ENDIAN);
        if (data.remaining() < 2) throw new IOException("Truncated NBT record");

        if ((data.getShort(0) & 0xFFFF) == GZIP_MAGIC_NUMBER) {
            try (var input = new GZIPInputStream(new ByteBufferInputStream(data))) {
                try (DataInputStream dis = new DataInputStream(new BufferedInputStream(input))) {
                    return CompressedStreamTools.func_152456_a(dis, NBTSizeTracker.field_152451_a);
                }
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

    public enum TagCompression {
        GZIP,
        LZ4,
        NONE,
        ZSTD,
    }

    public static ByteBuffer saveTag(NBTTagCompound tag, TagCompression compression) throws IOException {
        switch (compression) {
            case GZIP -> {
                ByteBufferOutputStream nos = new ByteBufferOutputStream(getTagSizeEstimate(tag));

                try (
                    DataOutputStream dos = new DataOutputStream(new BufferedOutputStream(new GZIPOutputStream2(nos)))) {
                    CompressedStreamTools.write(tag, dos);
                }

                return nos.toByteBuffer()
                    .order(ByteOrder.LITTLE_ENDIAN);
            }
            case LZ4 -> {
                ByteBufferOutputStream nos = new ByteBufferOutputStream(getTagSizeEstimate(tag));

                try (DataOutputStream dos = new DataOutputStream(nos)) {
                    CompressedStreamTools.write(tag, dos);
                }

                ByteBuffer data = nos.toByteBuffer();

                var compressor = LZ4Factory.fastestInstance()
                    .fastCompressor();

                int length = data.remaining();
                ByteBuffer compressed = ByteBuffer.allocate(8 + compressor.maxCompressedLength(length))
                    .order(ByteOrder.LITTLE_ENDIAN);

                int compLen = compressor.compress(data, 0, length, compressed, 8, compressed.capacity() - 8);

                compressed.putInt(0, LZ4_MAGIC_NUMBER);
                compressed.putInt(4, length);
                compressed.limit(8 + compLen);

                return compressed;
            }
            case ZSTD -> {
                ByteBufferOutputStream nos = new ByteBufferOutputStream(getTagSizeEstimate(tag));
                try (DataOutputStream dos = new DataOutputStream(nos)) {
                    CompressedStreamTools.write(tag, dos);
                }
                ByteBuffer data = nos.toByteBuffer();
                if (data.remaining() > ZstdCodec.MAX_RECORD_BYTES) return saveTag(tag, TagCompression.GZIP);
                return ZstdCodec.compress(data);
            }
            case NONE -> {
                ByteBufferOutputStream nos = new ByteBufferOutputStream(getTagSizeEstimate(tag));

                try (DataOutputStream dos = new DataOutputStream(nos)) {
                    dos.writeLong(Long.reverseBytes(NONE_MAGIC_NUMBER_UUID.getLeastSignificantBits()));
                    dos.writeLong(Long.reverseBytes(NONE_MAGIC_NUMBER_UUID.getMostSignificantBits()));

                    CompressedStreamTools.write(tag, dos);
                }

                return nos.toByteBuffer();
            }
            default -> {
                throw new AssertionError("Illegal compression level: " + compression);
            }
        }
    }

    private static int getTagSizeEstimate(NBTBase tag) {
        // Standalone storage tools run without Minecraft's mixin accessors.
        if (tag instanceof NBTTagCompound && !(tag instanceof AccessorNBTTagCompound)) return 512;
        switch (tag.getId()) {
            case NBT.TAG_BYTE -> {
                return 2;
            }
            case NBT.TAG_SHORT -> {
                return 3;
            }
            case NBT.TAG_INT, NBT.TAG_FLOAT -> {
                return 5;
            }
            case NBT.TAG_LONG, NBT.TAG_DOUBLE -> {
                return 9;
            }
            case NBT.TAG_BYTE_ARRAY -> {
                return 5 + ((NBTTagByteArray) tag).func_150292_c().length;
            }
            case NBT.TAG_INT_ARRAY -> {
                return 5 + ((NBTTagIntArray) tag).func_150302_c().length * 4;
            }
            case NBT.TAG_STRING -> {
                return 1 + ((NBTTagString) tag).func_150285_a_()
                    .length() * 2;
            }
            case NBT.TAG_LIST -> {
                var list = ((AccessorNBTTagList) tag).getTagList();

                int len = list.size();

                int size = 5;

                // noinspection ForLoopReplaceableByForEach
                for (int i = 0; i < len; i++) {
                    size += getTagSizeEstimate(list.get(i));
                }

                return size;
            }
            case NBT.TAG_COMPOUND -> {
                var map = ((AccessorNBTTagCompound) tag).getTagMap();

                int size = 5;

                for (var e : map.entrySet()) {
                    size += e.getKey()
                        .length() * 2;
                    size += getTagSizeEstimate(e.getValue());
                }

                return size;
            }
            default -> {
                return 1;
            }
        }
    }

    private static class GZIPOutputStream2 extends GZIPOutputStream {

        private final byte[] pooled;

        public GZIPOutputStream2(OutputStream nos) throws IOException {
            super(nos);
            pooled = new byte[1];
        }

        @Override
        public void write(int b) throws IOException {
            pooled[0] = (byte) (b & 0xff);
            write(pooled, 0, 1);
        }
    }
}
