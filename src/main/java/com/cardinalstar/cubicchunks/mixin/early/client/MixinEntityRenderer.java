package com.cardinalstar.cubicchunks.mixin.early.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.EntityRenderer;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.Vec3;
import net.minecraft.world.WorldProvider;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.cardinalstar.cubicchunks.util.MathUtil;
import com.llamalad7.mixinextras.sugar.Local;

@Mixin(EntityRenderer.class)
public class MixinEntityRenderer {

    @Shadow
    private Minecraft mc;

    @Shadow
    private float fogColorRed;

    @Shadow
    private float fogColorGreen;

    @Shadow
    private float fogColorBlue;

    // Redirects the dot product that changes the fog hue when you look at the sun to only look at the skylight at the
    // player's feet instead of the direction the player is facing.
    @Redirect(
        method = "updateFogColor",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/util/Vec3;dotProduct(Lnet/minecraft/util/Vec3;)D"))
    public double disableDotProduct(Vec3 instance, Vec3 vec, @Local(argsOnly = true) float partialTicks) {
        EntityLivingBase player = this.mc.renderViewEntity;

        int skylight = (player.getBrightnessForRender(partialTicks) & 0xf00000) >> 20;

        return skylight / 16d;
    }

    // Makes underground fog darker
    @Inject(
        method = "updateFogColor",
        at = @At(
            value = "INVOKE",
            target = "Lorg/lwjgl/opengl/GL11;glClearColor(FFFF)V",
            shift = At.Shift.BEFORE,
            remap = false))
    public void makeFogDarkerUnderground(float partialTicks, CallbackInfo ci) {

        EntityLivingBase player = this.mc.renderViewEntity;

        double playerY = player.lastTickPosY + (player.posY - player.lastTickPosY) * (double) partialTicks;

        float depthColour = (float) MathUtil.clamp(playerY / 32d, 0.25, 1);

        this.fogColorRed *= depthColour;
        this.fogColorGreen *= depthColour;
        this.fogColorBlue *= depthColour;
    }

    // Disable existing void fog logic entirely for cubic worlds
    @Redirect(
        method = "setupFog",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/WorldProvider;getWorldHasVoidParticles()Z"))
    public boolean disableVoidFog(WorldProvider instance) {
        return false;
    }
}
