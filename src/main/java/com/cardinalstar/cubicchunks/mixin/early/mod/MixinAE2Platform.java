package com.cardinalstar.cubicchunks.mixin.early.mod;

import net.minecraft.world.WorldServer;
import net.minecraft.world.chunk.Chunk;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.cardinalstar.cubicchunks.server.CubicPlayerManager;

@Pseudo
@Mixin(targets = "appeng.util.Platform", remap = false)
public abstract class MixinAE2Platform {

    @Inject(method = "sendChunk", at = @At("HEAD"), cancellable = true)
    private static void resendCubicChunkSections(Chunk chunk, int sectionMask, CallbackInfo ci) {
        if (!(chunk.worldObj instanceof WorldServer world)) {
            return;
        }

        ((CubicPlayerManager) world.getPlayerManager()).resendChunkSections(chunk, sectionMask);
        ci.cancel();
    }
}
