package com.cardinalstar.cubicchunks.world.convert.adapter.biome;

import net.minecraft.nbt.NBTTagCompound;

import com.cardinalstar.cubicchunks.util.DataUtils;

public class EIDBiomeAdapter implements BiomeAdapter {

    @Override
    public void writeBiomeData(int[] biomeIds, NBTTagCompound level) {
        short[] shorts = new short[256];

        for (int i = 0; i < 256; i++) {
            shorts[i] = (short) biomeIds[i];
        }

        level.setByteArray("Biomes16v2", DataUtils.shortToByteArray(shorts));
    }

    @Override
    public void readBiomeData(NBTTagCompound level, int[] biomeIds) {
        short[] shorts = DataUtils.byteToShortArray(level.getByteArray("Biomes16v2"));

        for(int i = 0; i < 256; i++) {
            biomeIds[i] = (short) biomeIds[i];
        }
    }
}
