package com.cardinalstar.cubicchunks.mixin.early.common;

import net.minecraft.crash.CrashReport;
import net.minecraft.crash.CrashReportCategory;
import net.minecraft.profiler.Profiler;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ReportedException;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraft.world.WorldSettings;
import net.minecraft.world.storage.ISaveHandler;
import net.minecraft.world.storage.WorldInfo;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.cardinalstar.cubicchunks.mixin.api.WorldExt;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;

@Mixin(World.class)
public abstract class MixinWorld_DeferInit implements WorldExt {

    @Shadow
    protected WorldInfo worldInfo;

    @Shadow
    protected abstract void initialize(WorldSettings p_72963_1_);

    @Shadow
    public abstract CrashReportCategory addWorldInfoToCrashReport(CrashReport report);

    @Unique
    private WorldSettings settings;

    @Override
    public void cc$initialize() {
        if (!this.worldInfo.isInitialized()) {
            try {
                this.initialize(this.settings);
                this.settings = null;
            } catch (Throwable throwable1) {
                CrashReport crashreport = CrashReport.makeCrashReport(throwable1, "Exception initializing level");

                try {
                    this.addWorldInfoToCrashReport(crashreport);
                } catch (Throwable throwable) {
                    ;
                }

                throw new ReportedException(crashreport);
            }

            this.worldInfo.setServerInitialized(true);
        }
    }

    @WrapOperation(
        method = "<init>(Lnet/minecraft/world/storage/ISaveHandler;Ljava/lang/String;Lnet/minecraft/world/WorldSettings;Lnet/minecraft/world/WorldProvider;Lnet/minecraft/profiler/Profiler;)V",
        at = @At(
            value = "INVOKE", target = "Lnet/minecraft/world/storage/WorldInfo;isInitialized()Z"
        )
    )
    public boolean deferInit(WorldInfo instance, Operation<Boolean> original, @Local(argsOnly = true) WorldSettings settings) {
        this.settings = settings;
        // isInitialized(): false -> inverted to disable init
        return true;
    }

    @Mixin(WorldServer.class)
    public static class MixinWorldServer {

        @Inject(method = "<init>", at = @At("TAIL"))
        public void doInit(
            MinecraftServer p_i45284_1_, ISaveHandler p_i45284_2_, String p_i45284_3_, int p_i45284_4_,
            WorldSettings p_i45284_5_, Profiler p_i45284_6_, CallbackInfo ci
        ) {
            ((WorldExt) this).cc$initialize();
        }
    }
}
