package com.cardinalstar.cubicchunks.mixin.early.common;

import net.minecraft.world.chunk.NibbleArray;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

import com.cardinalstar.cubicchunks.mixin.ext.EBSIDAccessor;

@Mixin(ExtendedBlockStorage.class)
public class MixinEBSID_Vanilla implements EBSIDAccessor {

    @Shadow
    private byte[] blockLSBArray;

    @Shadow
    private NibbleArray blockMSBArray;

    @Shadow
    private int tickRefCount;

    @Shadow
    private int blockRefCount;

    @Override
    public int getBlockID(int x, int y, int z) {
        int id = this.blockLSBArray[y << 8 | z << 4 | x] & 255;

        if (this.blockMSBArray != null) {
            id |= this.blockMSBArray.get(x, y, z) << 8;
        }

        return id;
    }

    @Override
    public void setBlockID(int x, int y, int z, int id, boolean tickRandomly) {
        this.blockLSBArray[y << 8 | z << 4 | x] = (byte) (id & 255);

        if (id > 255) {
            if (this.blockMSBArray == null) {
                this.blockMSBArray = new NibbleArray(this.blockLSBArray.length, 4);
            }

            this.blockMSBArray.set(x, y, z, (id & 3840) >> 8);
        } else if (this.blockMSBArray != null) {
            this.blockMSBArray.set(x, y, z, 0);
        }

        if (tickRandomly) {
            tickRefCount++;
        }

        if (id != 0) {
            blockRefCount++;
        }
    }
}
