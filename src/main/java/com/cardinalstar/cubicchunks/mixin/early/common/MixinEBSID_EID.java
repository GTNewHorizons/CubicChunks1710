package com.cardinalstar.cubicchunks.mixin.early.common;

import net.minecraft.world.chunk.storage.ExtendedBlockStorage;

import org.spongepowered.asm.mixin.Dynamic;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

import com.cardinalstar.cubicchunks.mixin.ext.EBSIDAccessor;

@Mixin(ExtendedBlockStorage.class)
public abstract class MixinEBSID_EID implements EBSIDAccessor {

    @Shadow(remap = false)
    @Dynamic
    public abstract int eid$getID(int x, int y, int z);

    @Shadow(remap = false)
    @Dynamic
    public abstract void eid$setID(int x, int y, int z, int id);

    @Shadow
    private int tickRefCount;

    @Shadow
    private int blockRefCount;

    @Override
    public int getBlockID(int x, int y, int z) {
        return eid$getID(x, y, z);
    }

    @Override
    public void setBlockID(int x, int y, int z, int id, boolean tickRandomly) {
        eid$setID(x, y, z, id);

        if (tickRandomly) {
            tickRefCount++;
        }

        if (id != 0) {
            blockRefCount++;
        }
    }
}
