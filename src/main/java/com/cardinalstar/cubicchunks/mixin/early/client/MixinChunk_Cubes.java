/*
 * This file is part of Cubic Chunks Mod, licensed under the MIT License (MIT).
 * Copyright (c) 2015-2021 OpenCubicChunks
 * Copyright (c) 2015-2021 contributors
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package com.cardinalstar.cubicchunks.mixin.early.client;

import javax.annotation.ParametersAreNonnullByDefault;

import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.cardinalstar.cubicchunks.api.IColumn;
import com.cardinalstar.cubicchunks.api.IHeightMap;
import com.cardinalstar.cubicchunks.world.column.CubeMap;
import com.cardinalstar.cubicchunks.world.cube.Cube;

/**
 * Modifies vanilla code in Chunk to use Cubes. Client side only.
 */
@ParametersAreNonnullByDefault
@Mixin(Chunk.class)
public abstract class MixinChunk_Cubes implements IColumn {

    protected MixinChunk_Cubes() {}

    @Shadow
    public abstract ExtendedBlockStorage[] getBlockStorageArray();

    @Shadow
    @Final
    private ExtendedBlockStorage[] storageArrays;
    /*
     * WARNING: WHEN YOU RENAME ANY OF THESE 3 FIELDS RENAME CORRESPONDING
     * FIELDS IN "cubicchunks.mixin.early.common.MixinChunk_Cubes" and
     * "cubicchunks.mixin.early.common.MixinChunk_Columns".
     */
    private CubeMap cubeMap;
    private IHeightMap opacityIndex;
    private Cube cachedCube; // todo: make it always nonnull using BlankCube

    private boolean isColumn = false;

    // ==============================================
    // generateHeightMap
    // ==============================================

    @Inject(method = "generateHeightMap", at = @At(value = "HEAD"), cancellable = true)
    private void generateHeightMap_CubicChunks_Cancel(CallbackInfo cbi) {
        if (isColumn) {
            cbi.cancel();
        }
    }

    // ==============================================
    // fillChunk
    // ==============================================

    @Inject(method = "fillChunk", at = @At(value = "HEAD"))
    private void fillChunk_CubicChunks_NotSupported(byte[] p_76607_1_, int p_76607_2_, int p_76607_3_,
        boolean p_76607_4_, CallbackInfo ci) {
        if (false) if (isColumn) {
            throw new UnsupportedOperationException("setting storage arrays it not supported with cubic chunks");
        }
    }
}
