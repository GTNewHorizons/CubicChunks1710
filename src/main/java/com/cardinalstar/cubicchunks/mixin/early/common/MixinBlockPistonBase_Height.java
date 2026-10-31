package com.cardinalstar.cubicchunks.mixin.early.common;

import net.minecraft.block.BlockPistonBase;
import net.minecraft.world.World;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import com.cardinalstar.cubicchunks.world.ICubicWorld;
import com.llamalad7.mixinextras.expression.Expression;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;

@Mixin(BlockPistonBase.class)
public class MixinBlockPistonBase_Height {

    // Like vanilla's y <= 0, nothing moves into the lowest layer of the world.
    @Expression("? <= 0")
    @WrapOperation(method = "canExtend", at = @At("MIXINEXTRAS:EXPRESSION"), require = 1)
    private static boolean cc$canExtendBelowFloor(int y, int zero, Operation<Boolean> original,
        @Local(argsOnly = true) World world) {
        return y <= ((ICubicWorld) world).getMinHeight();
    }

    @Expression("? <= 0")
    @WrapOperation(method = "tryExtend", at = @At("MIXINEXTRAS:EXPRESSION"), require = 1)
    private boolean cc$tryExtendBelowFloor(int y, int zero, Operation<Boolean> original,
        @Local(argsOnly = true) World world) {
        return y <= ((ICubicWorld) world).getMinHeight();
    }

    @Redirect(
        method = "canExtend",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/World;getHeight()I"),
        require = 1)
    private static int cc$canExtendCeiling(World world) {
        return ((ICubicWorld) world).getMaxHeight();
    }

    @Redirect(
        method = "tryExtend",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/World;getHeight()I"),
        require = 1)
    private int cc$tryExtendCeiling(World world) {
        return ((ICubicWorld) world).getMaxHeight();
    }
}
