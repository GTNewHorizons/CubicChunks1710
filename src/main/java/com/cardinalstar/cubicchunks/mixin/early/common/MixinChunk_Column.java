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
package com.cardinalstar.cubicchunks.mixin.early.common;

import javax.annotation.ParametersAreNonnullByDefault;

import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Implements;
import org.spongepowered.asm.mixin.Interface;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

import com.cardinalstar.cubicchunks.api.IColumn;
import com.cardinalstar.cubicchunks.api.IHeightMap;
import com.cardinalstar.cubicchunks.world.column.CubeMap;
import com.cardinalstar.cubicchunks.world.core.IColumnInternal;
import com.cardinalstar.cubicchunks.world.core.StagingHeightMap;
import com.cardinalstar.cubicchunks.world.cube.Cube;

/**
 * Implements the IColumn interface
 */
@ParametersAreNonnullByDefault
@Mixin(value = Chunk.class, priority = 2000)
// soft implements for IColumn and IColumnInternal
// we can't implement them directly as that causes FG6+ to reobfuscate IColumn#getHeightValue(int, int)
// into vanilla SRG name, which breaks API and mixins
@Implements({ @Interface(iface = IColumn.class, prefix = "chunk$"),
    @Interface(iface = IColumnInternal.class, prefix = "chunk_internal$") })
public abstract class MixinChunk_Column {

    /*
     * WARNING: WHEN YOU RENAME ANY OF THESE 3 FIELDS RENAME CORRESPONDING
     * FIELDS IN "cubicchunks.mixin.early.common.MixinChunk_Cubes" and
     * "cubicchunks.mixin.early.client.MixinChunk_Columns".
     */
    private CubeMap cubeMap;
    private IHeightMap opacityIndex;
    private Cube cachedCube;
    private StagingHeightMap stagingHeightMap;
    private boolean isColumn;

    @Shadow
    @Final
    public int zPosition;

    @Shadow
    @Final
    public int xPosition;

    @Shadow
    @Final
    private World worldObj;

    // @Shadow public boolean unloadQueued;

    @Shadow
    @Final
    private int[] heightMap;

    @Shadow
    public abstract ExtendedBlockStorage[] getBlockStorageArray();

}
