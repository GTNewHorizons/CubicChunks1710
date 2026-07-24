package com.cardinalstar.cubicchunks.mixin.early.mod;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.cardinalstar.cubicchunks.util.JourneyMapRenderGuard;

@Pseudo
@Mixin(targets = "journeymap.client.cartography.ChunkPainter", remap = false)
public abstract class MixinJourneyMapChunkPainter {

    @Shadow(remap = false)
    @Final
    private Graphics2D g2D;

    @Shadow(remap = false)
    @Final
    private BufferedImage img;

    @Inject(method = "finishPainting", at = @At("HEAD"), cancellable = true, remap = false)
    private void discardIncompleteSurfaceRender(CallbackInfo ci) {
        if (JourneyMapRenderGuard.shouldDiscard(img)) {
            g2D.dispose();
            ci.cancel();
        }
    }
}
