package com.cardinalstar.cubicchunks.util.biome3d;

import net.minecraft.world.biome.BiomeGenBase;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import com.cardinalstar.cubicchunks.network.CCPacketBuffer;
import com.cardinalstar.cubicchunks.util.XSTR;
import com.cardinalstar.cubicchunks.util.biome3d.NaiveCompression.NaiveCompressionDataInput;
import com.cardinalstar.cubicchunks.util.biome3d.NaiveCompression.NaiveCompressionDataOutput;

import io.netty.buffer.Unpooled;
import it.unimi.dsi.fastutil.ints.IntArrayList;

public class BiomeTests {

    @Test
    public void compression() {
        XSTR rng = new XSTR(5);

        IntArrayList input = new IntArrayList();
        input.size(1024);

        for (int i = 0; i < input.size(); i++) {
            input.set(i, rng.nextInt());
        }

        IntArrayList output = new IntArrayList();
        output.size(1024);

        CCPacketBuffer buffer = new CCPacketBuffer(Unpooled.buffer());

        NaiveCompression.compress(new NaiveCompressionDataInput() {

            @Override
            public int size() {
                return input.size();
            }

            @Override
            public int get(int index) {
                return input.getInt(index);
            }
        }, buffer);

        NaiveCompression.decompress(buffer, new NaiveCompressionDataOutput() {

            @Override
            public int size() {
                return output.size();
            }

            @Override
            public void set(int index, int value) {
                output.set(index, value);
            }
        });

        for (int i = 0; i < input.size(); i++) {
            Assertions.assertEquals(
                input.getInt(i),
                output.getInt(i),
                "index " + i + " was " + output.getInt(i) + " but " + input.getInt(i) + " was expected");
        }
    }

    @Test
    public void dynamicArray() {
        XSTR rng = new XSTR(5);

        DynamicBiomeArray array = new DynamicBiomeArray();
        BiomeGenBase[] input = new BiomeGenBase[array.size()];

        for (int i = 0; i < array.size(); i++) {
            BiomeGenBase biome = rng.nextBoolean() ? BiomeGenBase.birchForest : BiomeGenBase.coldBeach;
            input[i] = biome;
            array.put(i, biome);
        }

        for (int i = 0; i < array.size(); i++) {
            Assertions.assertEquals(
                input[i],
                array.get(i),
                "array: index " + i
                    + " was "
                    + array.get(i).biomeName
                    + " but "
                    + input[i].biomeName
                    + " was expected");
        }

        DynamicBiomeArray array2 = new DynamicBiomeArray();

        CCPacketBuffer buffer = new CCPacketBuffer(Unpooled.buffer());

        array.write(buffer);
        array2.read(buffer);

        for (int i = 0; i < array.size(); i++) {
            Assertions.assertEquals(
                array.get(i),
                array2.get(i),
                "serialization: index " + i
                    + " was "
                    + array2.get(i).biomeName
                    + " but "
                    + array.get(i).biomeName
                    + " was expected");
        }
    }
}
