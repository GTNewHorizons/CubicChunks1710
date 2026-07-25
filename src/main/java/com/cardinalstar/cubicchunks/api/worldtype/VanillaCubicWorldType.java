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
package com.cardinalstar.cubicchunks.api.worldtype;

import javax.annotation.ParametersAreNonnullByDefault;

import net.minecraft.world.WorldServer;
import net.minecraft.world.WorldType;
import net.minecraft.world.chunk.IChunkProvider;

import org.jetbrains.annotations.NotNull;

import com.cardinalstar.cubicchunks.api.IntRange;
import com.cardinalstar.cubicchunks.api.world.ICubicWorldType;
import com.cardinalstar.cubicchunks.api.worldgen.BuiltinWorldDecorators;
import com.cardinalstar.cubicchunks.api.worldgen.IWorldGenerator;
import com.cardinalstar.cubicchunks.worldgen.VanillaWorldGenerator;

@ParametersAreNonnullByDefault
public class VanillaCubicWorldType extends WorldType implements ICubicWorldType {

    public static VanillaCubicWorldType INSTANCE;

    public static final String vanillaCubicLevelString = "VanillaCubic";

    private VanillaCubicWorldType() {
        super(vanillaCubicLevelString);
    }

    public static void init() {
        INSTANCE = new VanillaCubicWorldType();
    }

    @Override
    public @NotNull IWorldGenerator createCubeGenerator(WorldServer world, IChunkProvider chunkGenerator) {
        return new VanillaWorldGenerator(chunkGenerator, world, BuiltinWorldDecorators.CUBIC_VANILLA);
    }

    @Override
    public IntRange getGenerationRange(WorldServer world) {
        return new IntRange(0, world.provider.getActualHeight());
    }

    @Override
    public boolean hasCubicGeneratorForWorld(WorldServer object) {
        return true;
    }
}
