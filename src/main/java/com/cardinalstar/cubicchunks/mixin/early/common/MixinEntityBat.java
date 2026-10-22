package com.cardinalstar.cubicchunks.mixin.early.common;

import net.minecraft.entity.passive.EntityAmbientCreature;
import net.minecraft.entity.passive.EntityBat;
import net.minecraft.util.MathHelper;
import net.minecraft.world.World;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

import com.cardinalstar.cubicchunks.world.ICubicWorld;
import com.llamalad7.mixinextras.expression.Definition;
import com.llamalad7.mixinextras.expression.Expression;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

@Mixin(EntityBat.class)
public abstract class MixinEntityBat extends EntityAmbientCreature {

    protected MixinEntityBat(World world) {
        super(world);
    }

    @Definition(id = "targetY", field = "Lnet/minecraft/util/ChunkCoordinates;posY:I")
    @Expression("?.targetY < 1")
    @WrapOperation(method = "updateAITasks", at = @At("MIXINEXTRAS:EXPRESSION"), require = 1)
    private boolean cc$invalidFlightHeight(int y, int vanillaMinimum, Operation<Boolean> original) {
        ICubicWorld world = (ICubicWorld) this.worldObj;
        return y < world.getMinHeight() || y >= world.getMaxHeight();
    }

    @ModifyArg(
        method = "updateAITasks",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/World;getBlock(III)Lnet/minecraft/block/Block;"),
        index = 1,
        require = 2)
    private int cc$ceilingAboveBat(int y) {
        return MathHelper.floor_double(this.posY) + 1;
    }
}
