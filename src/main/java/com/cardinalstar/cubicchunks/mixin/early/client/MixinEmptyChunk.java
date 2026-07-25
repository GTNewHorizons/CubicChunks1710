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

import java.util.Collection;
import java.util.Collections;

import javax.annotation.ParametersAreNonnullByDefault;

import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.EmptyChunk;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.cardinalstar.cubicchunks.mixin.early.common.MixinChunk;
import com.cardinalstar.cubicchunks.world.column.CubeMap;
import com.cardinalstar.cubicchunks.world.cube.BlankCube;
import com.cardinalstar.cubicchunks.world.cube.Cube;

@ParametersAreNonnullByDefault
@Mixin(EmptyChunk.class)
public abstract class MixinEmptyChunk extends MixinChunk {

    private Cube blankCube;

    @Inject(method = "<init>", at = @At(value = "RETURN"))
    private void cubicChunkColumn_construct(World worldIn, int x, int z, CallbackInfo cbi) {
        blankCube = new BlankCube((Chunk) (Object) this);
    }

    @Override
    public Cube getCube(int cubeY) {
        return blankCube;
    }

    @Override
    public Cube removeCube(int cubeY) {
        return blankCube;
    }

    @Override
    public void addCube(Cube cube) {}

    @Override
    public Collection<Cube> getLoadedCubes() {
        return Collections.emptySet();
    }

    @Override
    public ExtendedBlockStorage[] getTickableStorages() {
        return CubeMap.ZERO_LEN_EBS_ARRAY;
    }

    @Override
    public Iterable<Cube> getLoadedCubes(int startY, int endY) {
        return Collections.emptySet();
    }
}
