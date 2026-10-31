package com.cardinalstar.cubicchunks.mixin.early.client;

import net.minecraft.entity.Entity;
import net.minecraft.util.MathHelper;
import net.minecraft.world.World;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.cardinalstar.cubicchunks.world.ICubicWorld;
import com.llamalad7.mixinextras.expression.Definition;
import com.llamalad7.mixinextras.expression.Expression;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

@Mixin(Entity.class)
public abstract class MixinEntity {

    @Shadow
    public World worldObj;

    @Shadow
    public double posX;

    @Shadow
    public double posZ;

    @Shadow
    public abstract void setPosition(double x, double y, double z);

    @Definition(id = "posY", field = "Lnet/minecraft/entity/Entity;posY:D")
    @Expression("this.posY > 0.0")
    @WrapOperation(method = "preparePlayerToSpawn", at = @At("MIXINEXTRAS:EXPRESSION"), require = 1)
    private boolean cc$canClearSpawnCollision(double y, double zero, Operation<Boolean> original) {
        // Include the first air layer above the build ceiling, then stop even if obstructed.
        return y <= ((ICubicWorld) this.worldObj).getMaxHeight();
    }

    @Inject(method = "preparePlayerToSpawn", at = @At("RETURN"), require = 1)
    private void cc$finishSpawnPosition(CallbackInfo ci) {
        if (this.worldObj != null) {
            this.setPosition(this.posX, this.posY, this.posZ);
        }
    }

    @Shadow
    public double posY;

    @Definition(id = "worldObj", field = "Lnet/minecraft/entity/Entity;worldObj:Lnet/minecraft/world/World;")
    @Definition(id = "blockExists", method = "Lnet/minecraft/world/World;blockExists(III)Z")
    @Expression("this.worldObj.blockExists(?, ?, ?)")
    @WrapOperation(method = "getBrightnessForRender", at = @At("MIXINEXTRAS:EXPRESSION"))
    private boolean atYInRenderEntityCheck(World instance, int x, int y, int z, Operation<Boolean> original) {
        return original.call(instance, x, MathHelper.floor_double(this.posY), z);
    }
}
