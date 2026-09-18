package com.cardinalstar.cubicchunks.world.convert.adapter.biome;

import net.minecraft.nbt.NBTTagCompound;

import com.cardinalstar.cubicchunks.CubicChunks;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;

public class VanillaBiomeAdapter implements BiomeAdapter {

    private final IntOpenHashSet truncatedIds = new IntOpenHashSet();

    @Override
    public void writeBiomeData(int[] biomeIds, NBTTagCompound level) {
        byte[] bytes = new byte[256];

        for(int i = 0; i < 256; i++) {
            int biomeId = biomeIds[i];

            if ((biomeId & 0xFF) != biomeId) {
                if (truncatedIds.add(biomeId)) {
                    CubicChunks.LOGGER.warn("Biome ID was truncated because it does not fit into a byte: {}", biomeId);
                }
            }

            bytes[i] = (byte) (biomeId & 0xFF);
        }

        level.setByteArray("Biomes", bytes);
    }

    @Override
    public void readBiomeData(NBTTagCompound level, int[] biomeIds) {
        byte[] bytes = level.getByteArray("Biomes");

        for(int i = 0; i < 256; i++) {
            biomeIds[i] = bytes[i] & 0xFF;
        }
    }
}
