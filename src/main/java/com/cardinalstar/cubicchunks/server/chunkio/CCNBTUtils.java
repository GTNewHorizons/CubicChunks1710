package com.cardinalstar.cubicchunks.server.chunkio;

import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTSizeTracker;
import net.minecraft.nbt.NBTTagByteArray;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagIntArray;
import net.minecraft.nbt.NBTTagString;
import net.minecraftforge.common.util.Constants.NBT;

import org.apache.commons.lang3.mutable.MutableInt;

import com.cardinalstar.cubicchunks.mixin.early.common.AccessorNBTTagCompound;
import com.cardinalstar.cubicchunks.mixin.early.common.AccessorNBTTagList;

public class CCNBTUtils {

    public static NBTTagCompound loadTag(byte[] data) throws IOException {
        try (var input = new GZIPInputStream(new ByteArrayInputStream(data))) {
            return CompressedStreamTools.func_152456_a(new DataInputStream(input), NBTSizeTracker.field_152451_a);
        }
    }

    public static byte[] saveTag(NBTTagCompound tag) throws IOException {
        try (ByteArrayOutputStream nos = new ByteArrayOutputStream(getTagSizeEstimate(tag))) {
            try (DataOutputStream dos = new DataOutputStream(new BufferedOutputStream(new GZIPOutputStream2(nos)))) {
                CompressedStreamTools.write(tag, dos);
            }

            return nos.toByteArray();
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
            case NBT.TAG_INT -> {
                return 5;
            }
            case NBT.TAG_LONG -> {
                return 9;
            }
            case NBT.TAG_FLOAT -> {
                return 5;
            }
            case NBT.TAG_DOUBLE -> {
                return 9;
            }
            case NBT.TAG_BYTE_ARRAY -> {
                return 5 + ((NBTTagByteArray) tag).func_150292_c().length;
            }
            case NBT.TAG_INT_ARRAY -> {
                return 5 + ((NBTTagIntArray) tag).func_150302_c().length * 4;
            }
            case NBT.TAG_STRING -> {
                return 1 + ((NBTTagString) tag).func_150285_a_().length() * 2;
            }
            case NBT.TAG_LIST -> {
                var list = ((AccessorNBTTagList) tag).getTagList();

                int len = list.size();

                int size = 5;

                for (int i = 0; i < len; i++) {
                    size += getTagSizeEstimate(list.get(i));
                }

                return size;
            }
            case NBT.TAG_COMPOUND -> {
                var map = ((AccessorNBTTagCompound) tag).getTagMap();

                MutableInt size = new MutableInt(5);

                map.forEach((key, value) -> {
                    size.add(key.length() * 2);
                    size.add(getTagSizeEstimate(value));
                });

                return size.intValue();
            }
            default -> {
                return 1;
            }
        }
    }

    private static class GZIPOutputStream2 extends GZIPOutputStream {

        private final byte[] pooled;

        public GZIPOutputStream2(ByteArrayOutputStream nos) throws IOException {
            super(nos);
            pooled = new byte[1];
        }

        @Override
        public void write(int b) throws IOException {
            pooled[0] = (byte)(b & 0xff);
            write(pooled, 0, 1);
        }
    }
}
