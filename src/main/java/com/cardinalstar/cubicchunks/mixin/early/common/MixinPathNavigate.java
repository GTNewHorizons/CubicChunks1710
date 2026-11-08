package com.cardinalstar.cubicchunks.mixin.early.common;

import net.minecraft.pathfinding.PathNavigate;
import net.minecraft.util.MathHelper;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.expression.Expression;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;

@Mixin(PathNavigate.class)
public abstract class MixinPathNavigate {

    @Expression("(int) @(?)")
    @ModifyExpressionValue(method = { "getPathableYPos", "tryMoveToXYZ" }, at = @At("MIXINEXTRAS:EXPRESSION"))
    double wrapIntAssignCast(double original) {
        return MathHelper.floor_double(original);
    }
}
