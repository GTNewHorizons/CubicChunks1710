package com.cardinalstar.cubicchunks.mixin.early.common;

import java.util.List;

import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagList;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(NBTTagList.class)
public interface AccessorNBTTagList {

    @Accessor("tagList")
    List<NBTBase> getTagList();

}
