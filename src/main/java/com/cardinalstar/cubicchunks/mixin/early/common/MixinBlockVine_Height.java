package com.cardinalstar.cubicchunks.mixin.early.common;

import java.util.Random;

import net.minecraft.block.BlockVine;
import net.minecraft.world.World;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

import com.cardinalstar.cubicchunks.world.ICubicWorld;
import com.llamalad7.mixinextras.expression.Definition;
import com.llamalad7.mixinextras.expression.Expression;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;

@Mixin(BlockVine.class)
public class MixinBlockVine_Height {

    @ModifyConstant(method = "updateTick", constant = @Constant(intValue = 255), require = 1)
    private int cc$growthCeiling(int vanillaMaximum, World world, int x, int y, int z, Random random) {
        return ((ICubicWorld) world).getMaxHeight() - 1;
    }

    @Definition(id = "y", local = @Local(argsOnly = true, ordinal = 1, type = int.class))
    @Expression("y > 1")
    @WrapOperation(method = "updateTick", at = @At("MIXINEXTRAS:EXPRESSION"), require = 1)
    private boolean cc$canGrowDown(int y, int vanillaMinimum, Operation<Boolean> original,
        @Local(argsOnly = true) World world) {
        // Like vanilla's y > 1, vines do not grow into the lowest layer of the world.
        return y > ((ICubicWorld) world).getMinHeight() + 1;
    }
}
