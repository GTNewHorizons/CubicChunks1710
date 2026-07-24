package com.cardinalstar.cubicchunks.mixin.early.mod;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.cardinalstar.cubicchunks.util.JourneyMapRenderGuard;

@Pseudo
@Mixin(targets = "journeymap.client.cartography.ChunkRenderController", remap = false)
public abstract class MixinJourneyMapChunkRenderController {

    @Inject(method = "renderChunk", at = @At("HEAD"), remap = false)
    private void beginSurfaceRender(@Coerce Object regionCoord, @Coerce Object mapType, @Coerce Object chunkMetadata,
        CallbackInfoReturnable<Boolean> cir) {
        JourneyMapRenderGuard.begin(mapType);
    }

    @Inject(method = "renderChunk", at = @At("RETURN"), remap = false)
    private void endSurfaceRender(@Coerce Object regionCoord, @Coerce Object mapType, @Coerce Object chunkMetadata,
        CallbackInfoReturnable<Boolean> cir) {
        JourneyMapRenderGuard.end();
    }
}
