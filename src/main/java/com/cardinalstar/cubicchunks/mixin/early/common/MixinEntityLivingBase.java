package com.cardinalstar.cubicchunks.mixin.early.common;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.MathHelper;
import net.minecraft.world.World;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import com.cardinalstar.cubicchunks.world.ICubicWorld;
import com.llamalad7.mixinextras.expression.Definition;
import com.llamalad7.mixinextras.expression.Expression;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

@Mixin(EntityLivingBase.class)
public abstract class MixinEntityLivingBase extends Entity {

    public MixinEntityLivingBase(World worldIn) {
        super(worldIn);
    }

    @Redirect(
        method = "moveEntityWithHeading",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/World;blockExists(III)Z"))
    public boolean fixBlockExists(World instance, int x, int y, int z) {
        int blockY = MathHelper.floor_double(this.posY);
        ICubicWorld cubicWorld = (ICubicWorld) instance;
        return blockY < cubicWorld.getMinHeight() || blockY >= cubicWorld.getMaxHeight()
            || instance.blockExists(x, blockY, z);
    }

    @Definition(id = "posY", field = "Lnet/minecraft/entity/EntityLivingBase;posY:D")
    @Expression("this.posY > 0.0")
    @WrapOperation(method = "moveEntityWithHeading", at = @At("MIXINEXTRAS:EXPRESSION"))
    public boolean noopHeightCheck(double left, double right, Operation<Boolean> original) {
        return true;
    }
}
