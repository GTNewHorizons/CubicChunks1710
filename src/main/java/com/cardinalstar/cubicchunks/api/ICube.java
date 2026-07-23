package com.cardinalstar.cubicchunks.api;/*
                                          * This file is part of Cubic Chunks Mod, licensed under the MIT License (MIT).
                                          * Copyright (c) 2015-2021 OpenCubicChunks
                                          * Copyright (c) 2015-2021 contributors
                                          * Permission is hereby granted, free of charge, to any person obtaining a copy
                                          * of this software and associated documentation files (the "Software"), to
                                          * deal
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
                                          * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
                                          * FROM,
                                          * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
                                          * THE SOFTWARE.
                                          */

import java.util.EnumSet;
import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;

import net.minecraft.block.Block;
import net.minecraft.entity.Entity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.ChunkPosition;
import net.minecraft.world.EnumSkyBlock;
import net.minecraft.world.World;
import net.minecraft.world.biome.BiomeGenBase;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;

import org.jetbrains.annotations.NotNull;

import com.cardinalstar.cubicchunks.api.worldgen.IWorldGenerator;
import com.cardinalstar.cubicchunks.server.chunkio.CubeInitLevel;
import com.cardinalstar.cubicchunks.util.CubePos;
import com.cardinalstar.cubicchunks.world.ICubicWorld;
import com.gtnewhorizon.gtnhlib.blockpos.BlockPos;

@ParametersAreNonnullByDefault
public interface ICube extends XYZAddressable, MetaContainer {

    /**
     * Side length of a cube
     */
    int SIZE = 16;
    double SIZE_D = 16.0D;

    /**
     * Retrieve the raw light level at the specified location
     *
     * @param lightType The type of light (sky or block light)
     * @param x         The x position at which light should be checked
     * @param y         The y position at which light should be checked
     * @param z         The zPosition position at which light should be checked
     *
     * @return the light level
     */
    int getSavedLightValue(EnumSkyBlock lightType, int x, int y, int z);

    /**
     * Set the raw light level at the specified location
     *
     * @param lightType The type of light (sky or block light)
     * @param x         The x position at which light should be updated
     * @param y         The y position at which light should be updated
     * @param z         The zPosition position at which light should be updated
     * @param light     the light level
     */
    void setLightFor(EnumSkyBlock lightType, int x, int y, int z, int light);

    Block getBlock(int x, int y, int z);

    int getBlockMetadata(int x, int y, int z);

    /**
     * Retrieve the tile entity at the specified location
     *
     * @param x target x location
     * @param y target y location
     * @param z target zPosition location
     *
     * @return the tile entity at the specified location, or {@code null} if there is no entity.
     *         This will not create the tile entity
     */
    @Nullable
    TileEntity getTileEntityUnsafe(int x, int y, int z);

    /**
     * Retrieves the tile entity at the specified location if there is one. If not, it creates
     * the tile entity if there isn't one at the location and the block at the specified location can
     * hold a tile entity.
     *
     * @param x target x location
     * @param y target y location
     * @param z target zPosition location
     * @return Returns a tile entity if the block at the location can create one.
     *         Will always create the tile entity if it can.
     */
    TileEntity getBlockTileEntityInChunk(int x, int y, int z);

    /**
     * Add a tile entity to this cube
     *
     * @param tileEntity The tile entity to add
     */
    void addTileEntity(TileEntity tileEntity);

    void setBlockTileEntityInChunk(int x, int y, int z, TileEntity tileEntityIn);

    /**
     * Check if there are any non-air blocks in this cube
     *
     * @return {@code true} if this cube contains only air blocks, {@code false} otherwise
     */
    boolean isEmpty();

    /**
     * Convert an integer-encoded address to a local block to a global block position
     *
     * @param localAddress the address of the block
     *
     * @return the block position
     */
    // BlockPos localAddressToBlockPos(int localAddress);

    /**
     * @param <T> dummy generic parameter to return a type that is both {@link World} and {@link ICubicWorld}
     * @return this cube's world
     */
    <T extends World & ICubicWorld> T getWorld();

    /**
     * @param <T> dummy generic parameter to return a type that is both {@link Chunk} and {@link IColumn}
     * @return this cube's column
     */
    @SuppressWarnings({ "deprecation", "RedundantSuppression" })
    <T extends Chunk & IColumn> T getColumn();

    /**
     * Retrieve this cube's x coordinate in cube space
     *
     * @return cube x position
     */
    int getX();

    /**
     * Retrieve this cube's y coordinate in cube space
     *
     * @return cube y position
     */
    int getY();

    /**
     * Retrieve this cube's zPosition coordinate in cube space
     *
     * @return cube zPosition position
     */
    int getZ();

    /**
     * @return this cube's position
     */
    CubePos getCoords();

    /**
     * Check whether a given global block position is contained in this cube
     *
     * @param x the x position of the block
     * @param y the y position of the block
     * @param z the zPosition position of the block
     *
     * @return {@code true} if the position is within this cube, {@code false} otherwise
     */
    boolean containsCoordinate(int x, int y, int z);

    @Nullable
    ExtendedBlockStorage getStorage();

    /// Returns true when this cube's [ExtendedBlockStorage] needs to be ticked.
    boolean shouldTick();

    /**
     * Retrieve a map of positions to their respective tile entities
     *
     * @return a map containing all tile entities in this cube
     */
    Map<ChunkPosition, TileEntity> getTileEntityMap();

    /**
     * Returns the internal entity container.
     *
     * @return the entity container
     */
    List<Entity> getEntitySet();

    void addEntity(Entity entity);

    boolean removeEntity(Entity entity);

    /**
     * Check if any modifications happened to this cube since it was loaded from disk
     *
     * @return {@code true} if this cube should be written back to disk
     */
    boolean needsSaving(boolean saveAll);

    /**
     * Marks this cube for saving.
     */
    void markDirty();

    /**
     * Check whether this cube was populated, i.e. if this cube was passed as argument to
     * {@link IWorldGenerator#populate(ICube)}. Check there for more information regarding
     * population.
     *
     * @return {@code true} if this cube has been populated, {@code false} otherwise
     */
    boolean isPopulated();

    /**
     * Check whether this cube was fully populated, i.e. if any cube potentially writing to this cube was passed as an
     * argument to {@link IWorldGenerator#populate(ICube)}. Check there for more
     * information regarding population
     *
     * @return {@code true} if this cube has been populated, {@code false} otherwise
     */
    boolean isFullyPopulated();

    /**
     * Check whether this cube's initial diffuse skylight has been calculated
     *
     * @return {@code true} if it has been calculated, {@code false} otherwise
     */
    boolean isInitialLightingDone();

    default CubeInitLevel getInitLevel() {
        if (isFullyPopulated() && isInitialLightingDone()) {
            return CubeInitLevel.Lit;
        } else if (isFullyPopulated()) {
            return CubeInitLevel.Populated;
        } else {
            return CubeInitLevel.Generated;
        }
    }

    default boolean isInitializedToLevel(CubeInitLevel initLevel) {
        return getInitLevel().ordinal() >= initLevel.ordinal();
    }

    boolean isCubeLoaded();

    boolean hasLightUpdates();

    @NotNull
    BiomeGenBase getBiome(int x, int y, int z);

    /**
     * Set biome at a cube-local 4x4x4 block segment.
     *
     * @param x     cube-local block X coordinate
     * @param y     cube-local block Y coordinate
     * @param z     cube-local block Z coordinate
     * @param biome The biome at the given cube coordinates, or null to defer to the column
     */
    void setBiome(int x, int y, int z, BiomeGenBase biome);

    BlockPos localAddressToBlockPos(int localAddress);

    /**
     * Returns a set of reasons this cube is forced to remain loaded if it's forced to remain loaded,
     * or empty enum set if it can be unloaded.
     *
     * @return EnumSet of reasons for this cube to stay loaded. Empty if it can be unloaded.
     */
    EnumSet<ForcedLoadReason> getForceLoadStatus();

    enum ForcedLoadReason {
        SPAWN_AREA,
        PLAYER,
        MOD_TICKET,
        OTHER
    }
}
