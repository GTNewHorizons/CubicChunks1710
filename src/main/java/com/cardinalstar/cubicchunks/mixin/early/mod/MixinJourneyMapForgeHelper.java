package com.cardinalstar.cubicchunks.mixin.early.mod;

import static com.cardinalstar.cubicchunks.util.Coords.blockToCube;

import net.minecraft.world.chunk.Chunk;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.cardinalstar.cubicchunks.api.IColumn;
import com.cardinalstar.cubicchunks.api.IHeightMap;
import com.cardinalstar.cubicchunks.util.Coords;
import com.cardinalstar.cubicchunks.world.cube.Cube;

@Pseudo
@Mixin(targets = "journeymap.client.forge.helper.impl.ForgeHelper_1_7_10", remap = false)
public abstract class MixinJourneyMapForgeHelper {

    @Inject(method = "hasChunkData", at = @At("RETURN"), cancellable = true, remap = false)
    private void waitForCubicSurfaceData(Chunk chunk, CallbackInfoReturnable<Boolean> cir) {
        if (!cir.getReturnValue()) {
            return;
        }

        IColumn column = (IColumn) chunk;
        IHeightMap heightMap = column.getOpacityIndex();

        for (int localX = 0; localX < 16; localX++) {
            for (int localZ = 0; localZ < 16; localZ++) {
                int surfaceY = heightMap.getTopBlockY(localX, localZ);
                if (surfaceY == Coords.NO_HEIGHT) {
                    continue;
                }

                Cube surfaceCube = column.getLoadedCube(blockToCube(surfaceY));
                if (surfaceCube == null || surfaceCube.isEmpty()) {
                    cir.setReturnValue(false);
                    return;
                }
            }
        }
    }
}
