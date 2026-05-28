package com.cardinalstar.cubicchunks.mixin.early.common;

import net.minecraft.world.World;
import net.minecraft.world.gen.feature.WorldGenLakes;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.expression.Definition;
import com.llamalad7.mixinextras.expression.Expression;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.ref.LocalIntRef;

@Mixin(WorldGenLakes.class)
public class MixinWorldGenLakes {

    @WrapOperation(
        method = "generate",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/World;isAirBlock(III)Z", ordinal = 0))
    public boolean noopLoop(World instance, int x, int y, int z, Operation<Boolean> original) {
        return false;
    }

    @Unique
    private int cubicchunks$scanDownToSurface(int value, World world, int x, int z) {
        for (int i = 0; i < 16; i++) {
            if (!world.isAirBlock(x, value, z)) {
                return value;
            }

            value--;
        }

        return value;
    }

    @Definition(id = "y", local = @Local(argsOnly = true, type = int.class, ordinal = 1))
    @Expression("y <= 4")
    @WrapOperation(method = "generate", at = @At("MIXINEXTRAS:EXPRESSION"))
    public boolean noopHeightCheck(int y, int four, Operation<Boolean> original, @Local(argsOnly = true) World world,
        @Local(argsOnly = true, ordinal = 0) int x, @Local(argsOnly = true, ordinal = 1) LocalIntRef yRef,
        @Local(argsOnly = true, ordinal = 2) int z) {
        int scannedY = this.cubicchunks$scanDownToSurface(y, world, x, z);
        yRef.set(scannedY);
        return world.isAirBlock(x, scannedY, z);
    }
}
