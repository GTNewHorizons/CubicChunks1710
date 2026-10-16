package com.cardinalstar.cubicchunks.mixin.early.client;

import net.minecraft.client.LoadingScreenRenderer;
import net.minecraft.client.resources.I18n;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

import com.cardinalstar.cubicchunks.server.chunkio.StartupRegionCompaction;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;

@Mixin(LoadingScreenRenderer.class)
public class MixinLoadingScreenRenderer {

    @Shadow
    private String currentlyDisplayedText;
    @Shadow
    private String field_73727_a;

    @WrapMethod(method = "setLoadingProgress")
    private void cubicchunks$compactionProgress(int progress, Operation<Void> original) {
        StartupRegionCompaction.Progress snapshot = StartupRegionCompaction.getProgress();
        if (snapshot == null) {
            original.call(progress);
            return;
        }
        String title = currentlyDisplayedText;
        String detail = field_73727_a;
        try {
            currentlyDisplayedText = I18n.format("cubicchunks.compaction.title");
            field_73727_a = I18n.format(
                "cubicchunks.compaction." + snapshot.phase,
                snapshot.dimension,
                snapshot.completed,
                snapshot.total,
                snapshot.elapsedSeconds());
            original.call(snapshot.percent);
        } finally {
            currentlyDisplayedText = title;
            field_73727_a = detail;
        }
    }
}
