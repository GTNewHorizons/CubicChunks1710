/*
 * This file is part of Cubic Chunks Mod, licensed under the MIT
 * License (MIT).
 * Copyright (c) 2015-2021 OpenCubicChunks
 * Copyright (c) 2015-2021 contributors
 * Permission is hereby granted, free of charge, to any person
 * obtaining a copy
 * of this software and associated documentation files (the
 * "Software"), to deal
 * in the Software without restriction, including without limitation
 * the rights
 * to use, copy, modify, merge, publish, distribute, sublicense,
 * and/or sell
 * copies of the Software, and to permit persons to whom the Software
 * is
 * furnished to do so, subject to the following conditions:
 * The above copyright notice and this permission notice shall be
 * included in
 * all copies or substantial portions of the Software.
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 * EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
 * MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT
 * SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR
 * OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE,
 * ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER
 * DEALINGS IN
 * THE SOFTWARE.
 */

package com.cardinalstar.cubicchunks.world.column;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Random;

import javax.annotation.Nullable;

import net.minecraft.command.IEntitySelector;
import net.minecraft.entity.Entity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.world.EnumSkyBlock;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;

import com.cardinalstar.cubicchunks.api.IColumn;
import com.cardinalstar.cubicchunks.api.ICube;
import com.cardinalstar.cubicchunks.api.IHeightMap;
import com.cardinalstar.cubicchunks.world.cube.BlankCube;

public class EmptyColumn extends Chunk implements IColumn {

    private final ICube emptyCube;

    public EmptyColumn(World worldIn, int x, int z) {
        super(worldIn, x, z);
        this.emptyCube = new BlankCube(this);
    }

    @Override
    public int getHeight(int x, int y, int z) {
        return 0;
    }

    /**
     * Returns the value in the height map at this x, zPosition coordinate in the chunk
     */
    @Override
    public int getHeightValue(int x, int z) {
        return 0;
    }

    // overrides for IColumn and IColumnInternal
    // we can't implement them directly as that causes FG6+ to reobfuscate IColumn#getHeightValue(int, int)
    // into vanilla SRG name, which breaks API and mixins
    // @Override
    public int getHeightValue(int localX, int blockY, int localZ) {
        return 0;
    }

    // @Override
    public boolean shouldTick() {
        return false;
    }

    // @Override
    public IHeightMap getOpacityIndex() {
        return null;
    }

    // @Override
    public Collection<? extends ICube> getLoadedCubes() {
        return Collections.emptyList();
    }

    @Override
    public ExtendedBlockStorage[] getTickableStorages() {
        return CubeMap.ZERO_LEN_EBS_ARRAY;
    }

    // @Override
    public Iterable<? extends ICube> getLoadedCubes(int startY, int endY) {
        return Collections.emptyList();
    }

    @Nullable
    // @Override
    public ICube getLoadedCube(int cubeY) {
        return null;
    }

    // @Override
    public ICube getCube(int cubeY) {
        return emptyCube;
    }

    // @Override
    public void addCube(ICube cube) {
        throw new RuntimeException("This should never be called!");
    }

    @Nullable
    // @Override
    public ICube removeCube(int cubeY) {
        return null;
    }

    // @Override
    public boolean hasLoadedCubes() {
        return false;
    }

    // @Override
    public void preCacheCube(ICube cube) {

    }

    // @Override
    public int getX() {
        return 0;
    }

    // @Override
    public int getZ() {
        return 0;
    }

    // @Override
    // public ChunkPrimer getCompatGenerationPrimer() {
    // return null;
    // }

    // @Override
    public void removeFromStagingHeightmap(ICube cube) {

    }

    // @Override
    public void addToStagingHeightmap(ICube cube) {

    }

    // @Override
    public int getTopYWithStaging(int localX, int localZ) {
        return 0;
    }

    /**
     * Generates the height map for a chunk from scratch
     */
    @Override
    public void generateHeightMap() {}

    /**
     * Generates the initial skylight map for the chunk upon generation or load.
     */
    @Override
    public void generateSkylightMap() {}

    // @Override
    // public IBlockState getBlockState(BlockPos pos) {
    // return Blocks.AIR.getDefaultState();
    // }

    // @Override
    // public int getBlockLightOpacity(BlockPos pos) {
    // return 255;
    // }

    @Override
    public int getSavedLightValue(EnumSkyBlock type, int x, int y, int z) {
        return type.defaultLightValue;
    }

    @Override
    public void setLightValue(EnumSkyBlock type, int x, int y, int z, int value) {}

    // @Override
    // public int getLightSubtracted(int x, int y, int zPosition, int amount) {
    // return 0;
    // }

    /**
     * Adds an entity to the chunk.
     */
    @Override
    public void addEntity(Entity entityIn) {}

    /**
     * removes entity using its y chunk coordinate as its index
     */
    @Override
    public void removeEntity(Entity entityIn) {}

    /**
     * Removes entity at the specified index from the entity array.
     */
    @Override
    public void removeEntityAtIndex(Entity entityIn, int index) {}

    @Override
    public boolean canBlockSeeTheSky(int x, int y, int z) {
        return false;
    }

    @Nullable
    @Override
    public TileEntity getTileEntityUnsafe(int x, int y, int z) {
        return null;
    }

    @Override
    public void addTileEntity(TileEntity tileEntityIn) {}

    // ADD TILE ENTITY
    @Override
    public void func_150812_a(int x, int y, int z, TileEntity tileEntityIn) {}

    @Override
    public void removeTileEntity(int x, int y, int z) {}

    /**
     * Called when this Chunk is loaded by the ChunkProvider
     */
    @Override
    public void onChunkLoad() {}

    /**
     * Called when this Chunk is unloaded by the ChunkProvider
     */
    @Override
    public void onChunkUnload() {}

    /**
     * Sets the isModified flag for this Chunk
     */
    // @Override
    // public void markDirty() {
    // }

    /**
     * Fills the given list of all entities that intersect within the given bounding box that aren't the passed entity.
     */
    @Override
    public void getEntitiesWithinAABBForEntity(Entity entityIn, AxisAlignedBB aabb, List<Entity> listToFill,
        IEntitySelector selector) {}

    /**
     * Gets all entities that can be assigned to the specified class.
     */
    @Override
    public <T> void getEntitiesOfTypeWithinAAAB(Class<T> entityClass, AxisAlignedBB aabb, List<T> listToFill,
        IEntitySelector selector) {}

    /**
     * Returns true if this Chunk needs to be saved
     */
    @Override
    public boolean needsSaving(boolean p_76601_1_) {
        return false;
    }

    @Override
    public Random getRandomWithSeed(long seed) {
        return new Random(
            this.worldObj.getSeed() + ((long) this.xPosition * this.xPosition * 4987142)
                + (long) (this.xPosition * 5947611L)
                + ((long) this.zPosition * this.zPosition) * 4392871L
                + (this.zPosition * 389711L) ^ seed);
    }

    @Override
    public boolean isEmpty() {
        return true;
    }

    /**
     * Returns whether the ExtendedBlockStorages containing levels (in blocks) from arg 1 to arg 2 are fully empty
     * (true) or not (false).
     */
    // @Override
    // public boolean isEmptyBetween(int startY, int endY) {
    // return true;
    // }
}
