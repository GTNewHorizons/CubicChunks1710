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
package com.cardinalstar.cubicchunks.api.world;

import javax.annotation.ParametersAreNonnullByDefault;

import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraft.world.chunk.IChunkProvider;

import org.jetbrains.annotations.NotNull;

import com.cardinalstar.cubicchunks.api.IntRange;
import com.cardinalstar.cubicchunks.api.worldgen.IWorldGenerator;

/// Implemented on [WorldType] references to override other world generators. World types take priority over
/// [WorldProvider]s that implement [ICubicWorldProvider]. When neither interface is present,
/// [WorldProvider#createChunkGenerator()] is called and the result is wrapped by a [VanillaWorldGenerator].
@ParametersAreNonnullByDefault
public interface ICubicWorldType {

    /// @deprecated New parameter: implement [#createCubeGenerator(WorldServer, IChunkProvider)] instead.
    @Deprecated
    @NotNull
    default IWorldGenerator createCubeGenerator(World world) {
        throw new UnsupportedOperationException();
    }

    @NotNull
    default IWorldGenerator createCubeGenerator(WorldServer world, IChunkProvider chunkGenerator) {
        return createCubeGenerator(world);
    }

    /// @deprecated Renamed: implement [#getGenerationRange(WorldServer)] instead.
    @Deprecated
    default IntRange calculateGenerationHeightRange(WorldServer world) {
        throw new UnsupportedOperationException();
    }

    default IntRange getGenerationRange(WorldServer world) {
        return calculateGenerationHeightRange(world);
    }

    /// @deprecated Changed parameter type: implement [#hasCubicGeneratorForWorld(WorldServer) ] instead.
    @Deprecated
    default boolean hasCubicGeneratorForWorld(World object) {
        throw new UnsupportedOperationException();
    }

    default boolean hasCubicGeneratorForWorld(WorldServer world) {
        return hasCubicGeneratorForWorld((World) world);
    }
}
