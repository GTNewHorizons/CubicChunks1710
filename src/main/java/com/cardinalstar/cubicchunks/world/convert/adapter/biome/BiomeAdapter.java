package com.cardinalstar.cubicchunks.world.convert.adapter.biome;

import net.minecraft.nbt.NBTTagCompound;

public interface BiomeAdapter {

    void writeBiomeData(int[] biomeIds, NBTTagCompound level);
    void readBiomeData(NBTTagCompound level, int[] biomeIds);

}
