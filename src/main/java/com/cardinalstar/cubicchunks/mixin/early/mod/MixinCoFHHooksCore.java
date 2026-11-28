package com.cardinalstar.cubicchunks.mixin.early.mod;

import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.world.World;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

import com.cardinalstar.cubicchunks.world.FallingBlockMove;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;

/// CoFH Core makes [net.minecraft.entity.Entity#moveEntity] use its own copy of
/// [World#getCollidingBoundingBoxes] for entities that cannot be pushed (items, arrows, falling blocks, ...). The copy
/// needs the same changes as the original, see MixinWorld.
@Pseudo
@Mixin(targets = "cofh.asmhooks.HooksCore", remap = false)
public abstract class MixinCoFHHooksCore {

    /// The copy tests whether a column exists at y = 64. A cube is only loaded there by chance, and without it the
    /// entity collides with nothing and falls through the floor.
    @ModifyConstant(method = "getEntityCollisionBoxes", constant = @Constant(intValue = 64), require = 1)
    private static int testColumnAtBoxHeight(int constant, @Local(argsOnly = true) AxisAlignedBB box) {
        return (int) ((box.maxY - box.minY) / 2 + box.minY);
    }

    @WrapOperation(
        method = "getEntityCollisionBoxes",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/World;getBlock(III)Lnet/minecraft/block/Block;",
            remap = true),
        require = 1)
    private static Block fallingBlockCollidesOnlyWithLoadedCubes(World world, int x, int y, int z,
        Operation<Block> original) {
        if (FallingBlockMove.isInProgress() && !world.isRemote && !world.blockExists(x, y, z)) return Blocks.air;
        return original.call(world, x, y, z);
    }
}
