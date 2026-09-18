package com.cardinalstar.cubicchunks.server.chunkio;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.UUID;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

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
import com.gtnewhorizon.gtnhlib.bytebuf.MemoryUtilities;

public class CCNBTUtils {

    public static final int GZIP_MAGIC_NUMBER = 0x8b1F;
    public static final int LZ4_MAGIC_NUMBER = 0x184D2204;
    public static final UUID NONE_MAGIC_NUMBER_UUID = UUID.fromString("904e51a4-3ee3-478c-92ee-db5a0af9ecc0");

    public static NBTTagCompound loadTag(ByteBuffer data) throws IOException {
        data.order(ByteOrder.LITTLE_ENDIAN);

        if ((data.getShort(0) & 0xFFFF) == GZIP_MAGIC_NUMBER) {
            try (var input = new GZIPInputStream(new ByteBufferInputStream(data))) {
                try (DataInputStream dis = new DataInputStream(new BufferedInputStream(input))) {
                    return CompressedStreamTools.func_152456_a(dis, NBTSizeTracker.field_152451_a);
                }
            }
        }

        if (data.getInt(0) == LZ4_MAGIC_NUMBER) {
            int decompLen = data.getInt(4);

            ByteBuffer decompressed = MemoryUtilities.memAlloc(decompLen);

            try {
                LZ4Factory.fastestInstance()
                    .fastDecompressor()
                    .decompress(data, 8, decompressed, 0, decompLen);

                decompressed.limit(decompLen);

                try (DataInputStream dos = new DataInputStream(new ByteBufferInputStream(decompressed))) {
                    return CompressedStreamTools.func_152456_a(dos, NBTSizeTracker.field_152451_a);
                }
            } finally {
                MemoryUtilities.memFree(decompressed);
            }
        }

        if (data.getLong(0) == NONE_MAGIC_NUMBER_UUID.getLeastSignificantBits()
            && data.getLong(8) == NONE_MAGIC_NUMBER_UUID.getMostSignificantBits()) {
            data.position(16);

            try (DataInputStream dos = new DataInputStream(new ByteBufferInputStream(data))) {
                return CompressedStreamTools.func_152456_a(dos, NBTSizeTracker.field_152451_a);
            }
        }

        throw new RuntimeException("Unknown NBT tag byte format; your world is likely corrupted");
    }

    public enum TagCompression {
        GZIP,
        LZ4,
        NONE,
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

                ByteBuffer compressed = ByteBuffer.allocate(8 + compressor.maxCompressedLength(data.capacity()))
                    .order(ByteOrder.LITTLE_ENDIAN);

                int compLen = compressor.compress(data, 0, data.capacity(), compressed, 8, compressed.capacity() - 8);

                compressed.putInt(0, LZ4_MAGIC_NUMBER);
                compressed.putInt(4, data.capacity());
                compressed.limit(8 + compLen);

                return compressed;
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
