package com.cardinalstar.cubicchunks.mixin.early.common;

import net.minecraft.world.World;
import net.minecraft.world.biome.BiomeGenBase;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import com.cardinalstar.cubicchunks.mixin.api.ICubicWorldInternal;
import com.llamalad7.mixinextras.expression.Definition;
import com.llamalad7.mixinextras.expression.Expression;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

@Mixin({ BiomeGenBase.class })
public class MixinBiomeGenBase {

    @Definition(id = "nextInt", method = "Ljava/util/Random;nextInt(I)I")
    @Expression("? <= 0 + ?.nextInt(?)")
    @WrapOperation(method = "genBiomeTerrain", at = @At(value = "MIXINEXTRAS:EXPRESSION"))
    private boolean cc$removeBedrock(int left, int right, Operation<Boolean> original, World world) {
        if (((ICubicWorldInternal) world).cc$getRemoveFloorBedrock()) {
            return false;
        } else {
            return original.call(left, right);
        }
    }
}
