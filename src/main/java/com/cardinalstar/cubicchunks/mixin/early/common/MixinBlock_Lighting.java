package com.cardinalstar.cubicchunks.mixin.early.common;

import net.minecraft.block.Block;
import net.minecraft.world.IBlockAccess;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import com.cardinalstar.cubicchunks.mixin.api.BlockExt_Lighting;

@Mixin(Block.class)
public class MixinBlock_Lighting implements BlockExt_Lighting {

    @Unique
    private boolean cc$unsafeLightMode = false;

    @Override
    public void cc$setUnsafeLightMode(boolean enable) {
        cc$unsafeLightMode = enable;
    }

    @Redirect(
        method = "getLightValue(Lnet/minecraft/world/IBlockAccess;III)I",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/IBlockAccess;getBlock(III)Lnet/minecraft/block/Block;"))
    public Block cc$noopGetBlock(IBlockAccess instance, int x, int y, int z) {
        return cc$unsafeLightMode ? (Block) (Object) this : instance.getBlock(x, y, z);
    }
}
