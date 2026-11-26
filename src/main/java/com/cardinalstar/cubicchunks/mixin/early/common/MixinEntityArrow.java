package com.cardinalstar.cubicchunks.mixin.early.common;

import net.minecraft.block.Block;
import net.minecraft.entity.Entity;
import net.minecraft.entity.projectile.EntityArrow;
import net.minecraft.init.Blocks;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

@Mixin(EntityArrow.class)
public abstract class MixinEntityArrow extends Entity {

    /// xTile
    @Shadow
    private int field_145791_d;
    /// yTile
    @Shadow
    private int field_145792_e;
    /// zTile
    @Shadow
    private int field_145789_f;

    protected MixinEntityArrow(World world) {
        super(world);
    }

    /**
     * Vanilla checks the stuck-in block every tick, even while flying, when the tile coords are still the -1 sentinel.
     * Vanilla rejects y = -1 before touching a chunk, but with cubic chunks it's a real cube, so every flying arrow
     * would load or generate cube (-1, -1, -1). An arrow can only be inside a block in a loaded cube anyway.
     */
    @WrapOperation(
        method = "onUpdate",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/World;getBlock(III)Lnet/minecraft/block/Block;",
            ordinal = 0),
        require = 1)
    private Block cc$getStuckBlockIfLoaded(World world, int x, int y, int z, Operation<Block> original) {
        return world.blockExists(x, y, z) ? original.call(world, x, y, z) : Blocks.air;
    }

    /**
     * Vanilla saves the tile coords as shorts, which wrap for arrows stuck beyond +-32767. Overwrite them with an int
     * tag;
     * vanilla's getShort still reads it, and getInteger still reads old short tags.
     */
    @Inject(method = "writeEntityToNBT", at = @At("TAIL"), require = 1)
    private void cc$writeIntYTile(NBTTagCompound tag, CallbackInfo ci) {
        tag.setInteger("xTile", this.field_145791_d);
        tag.setInteger("yTile", this.field_145792_e);
        tag.setInteger("zTile", this.field_145789_f);
    }

    @Inject(method = "readEntityFromNBT", at = @At("TAIL"), require = 1)
    private void cc$readIntYTile(NBTTagCompound tag, CallbackInfo ci) {
        this.field_145791_d = tag.getInteger("xTile");
        this.field_145792_e = tag.getInteger("yTile");
        this.field_145789_f = tag.getInteger("zTile");
    }
}
