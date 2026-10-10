package com.cardinalstar.cubicchunks.world.convert.adapter;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;

public interface SectionAdapter {

    void writeSectionData(CubeData data, NBTTagCompound section);
    void readSectionData(NBTTagCompound section, CubeData data);

}
