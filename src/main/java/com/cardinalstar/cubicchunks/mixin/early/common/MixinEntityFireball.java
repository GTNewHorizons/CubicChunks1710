package com.cardinalstar.cubicchunks.mixin.early.common;

import net.minecraft.entity.Entity;
import net.minecraft.entity.projectile.EntityFireball;
import net.minecraft.util.MathHelper;
import net.minecraft.world.World;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(EntityFireball.class)
public abstract class MixinEntityFireball extends Entity {

    protected MixinEntityFireball(World world) {
        super(world);
    }

    /**
     * Vanilla truncates posY with (int), which rounds toward zero and checks the wrong cube for -1 < y < 0.
     */
    @ModifyArg(
        method = "onUpdate",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/World;blockExists(III)Z"),
        index = 1,
        require = 1)
    private int cc$floorBlockExistsY(int y) {
        return MathHelper.floor_double(this.posY);
    }
}
