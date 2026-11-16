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
package com.cardinalstar.cubicchunks.api.worldgen;

import java.util.List;

import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;

import net.minecraft.entity.EnumCreatureType;
import net.minecraft.world.ChunkPosition;
import net.minecraft.world.World;
import net.minecraft.world.biome.BiomeGenBase;
import net.minecraft.world.chunk.Chunk;

import com.cardinalstar.cubicchunks.api.ICube;
import com.cardinalstar.cubicchunks.world.cube.Cube;
import com.cardinalstar.cubicchunks.worldgen.VanillaWorldGenerator;

/// Something that generates a cubic world.
/// @see IWorldGeneratorType IWorldGeneratorType, the factory that produces an [IWorldGenerator].
/// @see VanillaWorldGenerator VanillaWorldGenerator, a wrapper around a standard, non-CC-aware [IChunkProvider].
@ParametersAreNonnullByDefault
public interface IWorldGenerator {

    /// Generate new cubes. This can return multiple cubes and columns, but the generation result must always include
    /// the cube asked for by the parameters. The generation result does not have to be deterministic as long as the
    /// contained cubes always have a column -- the same provideCube request can return different sets of cubes each
    /// time it runs. An individual cube should never be regenerated once it's returned from this method, as that
    /// indicates a bug in the cube handling code, but it can happen so the generator should gracefully handle it.
    ///
    /// @param chunk The chunk for the cube, if one is available
    /// @param cubeX the cube's X coordinate
    /// @param cubeY the cube's Y coordinate
    /// @param cubeZ the cube's Z coordinate
    GenerationResult<Cube> provideCube(@Nullable Chunk chunk, int cubeX, int cubeY, int cubeZ);

    /// Generate a new column. This follows the same contract as [#provideCube(Chunk, int, int, int)]. What it means to
    /// 'generate a column' is defined by the generator. If this method does not generate a cube, then
    /// [#provideCube(Chunk, int, int, int)] will be called afterwards. Fast generators can skip generating any cubes in
    /// this method entirely, but some (like [VanillaWorldGenerator]) may need to generate a specific set of cubes for
    /// the column to be valid.
    GenerationResult<Chunk> provideColumn(World world, int columnX, int columnZ);

    /// Populate a cube with multi-block structures that can cross cube boundaries such as trees and ore veins.
    ///
    /// Note: Unlike vanilla this method will NEVER cause recursive generation, thus the area that it populates is not
    /// as strict. Generation should still be restricted as the player might see something generate in a chunk they have
    /// already been sent.
    ///
    /// This method should generate all cubes that may be affected by the population of `cube` before population.
    /// It is also responsible for calling [Cube#setPopulationStatus(short)] when a cube is populated. Failing to do so
    /// will break the cube loader, because it relies on a cube's own population flags to determine whether it is
    /// populated or not.
    ///
    /// The generator is completely in control over which cubes get populated, when, and how. The only requirement is
    /// that the given cube is populated after this method returns.
    ///
    /// @param cube The cube to populate
    void populate(Cube cube);

    /// Called to reload structures that apply to `cube`. Mostly used to prepare calls to
    /// [IWorldGenerator#getPossibleCreatures(EnumCreatureType, int, int, int)]
    ///
    /// @param cube The cube being loaded
    ///
    /// @see IWorldGenerator#recreateStructures(Chunk) for the 2D-equivalent of this method
    void recreateStructures(ICube cube);

    /// Called to reload structures that apply to `column`. Mostly used to prepare calls to
    /// [IWorldGenerator#getPossibleCreatures(EnumCreatureType, int, int, int)]
    ///
    /// @param column The column being loaded
    ///
    /// @see IWorldGenerator#recreateStructures(ICube) for the 3D-equivalent of this method
    void recreateStructures(Chunk column);

    /// Retrieve a list of creature classes eligible for spawning at the specified location.
    ///
    /// @param type the creature type that we are interested in spawning
    /// @param x the x position we want to spawn creatures at
    /// @param y the y position we want to spawn creatures at
    /// @param z the z position we want to spawn creatures at
    ///
    /// @return a list of creature classes that can spawn here. Example: Calling this method inside a nether fortress
    /// returns EntityBlaze, EntityPigZombie, EntitySkeleton, and EntityMagmaCube
    List<BiomeGenBase.SpawnListEntry> getPossibleCreatures(EnumCreatureType type, int x, int y, int z);

    /// Gets the closest structure with name `name`. This is primarily used when an eye of ender is trying to find
    /// a stronghold.
    ///
    /// @param name the name of the structure
    ///
    /// @return the position of the structure, or `null` if none could be found
    @Nullable
    ChunkPosition getNearestStructure(String name, int x, int y, int z);
}
