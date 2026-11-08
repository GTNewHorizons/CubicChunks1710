package com.cardinalstar.cubicchunks.mixin.early.common;

import net.minecraft.util.MathHelper;
import net.minecraft.world.World;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.cardinalstar.cubicchunks.util.SpreadPlayerSurface;
import com.cardinalstar.cubicchunks.world.cube.ICubeProvider;

@Mixin(targets = "net.minecraft.command.CommandSpreadPlayers$Position")
public class MixinSpreadPlayerPosition {

    @Shadow
    double field_111101_a;

    @Shadow
    double field_111100_b;

    @Inject(method = "func_111098_b", at = @At("HEAD"), cancellable = true, require = 1)
    private void cc$checkSurface(World world, CallbackInfoReturnable<Boolean> cir) {
        if (world.getChunkProvider() instanceof ICubeProvider) {
            int landing = this.cc$landing(world);
            // Where the loaded terrain does not decide, vanilla's check runs, as without this fix.
            if (landing != SpreadPlayerSurface.NOT_LOADED) {
                cir.setReturnValue(landing != SpreadPlayerSurface.UNSAFE);
            }
        }
    }

    @Inject(method = "func_111092_a", at = @At("HEAD"), cancellable = true, require = 1)
    private void cc$landingHeight(World world, CallbackInfoReturnable<Integer> cir) {
        if (world.getChunkProvider() instanceof ICubeProvider) {
            int landing = this.cc$landing(world);
            // A check that vanilla decided, or a teammate's landing, may have loaded unsafe terrain since: then land
            // like vanilla, as the check did not reject it.
            if (landing != SpreadPlayerSurface.NOT_LOADED && landing != SpreadPlayerSurface.UNSAFE) {
                cir.setReturnValue(landing);
            }
        }
    }

    @Unique
    private int cc$landing(World world) {
        return SpreadPlayerSurface
            .find(world, MathHelper.floor_double(this.field_111101_a), MathHelper.floor_double(this.field_111100_b));
    }
}
