package com.cardinalstar.cubicchunks.mixin.early.common;

import java.util.Map;

import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagCompound;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(NBTTagCompound.class)
public interface AccessorNBTTagCompound {

    @Accessor("tagMap")
    Map<String, NBTBase> getTagMap();

}
