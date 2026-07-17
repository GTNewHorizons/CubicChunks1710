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

import static com.cardinalstar.cubicchunks.util.Coords.blockToCube;
import static com.cardinalstar.cubicchunks.util.Coords.blockToLocal;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;

import net.minecraft.block.Block;
import net.minecraft.command.IEntitySelector;
import net.minecraft.entity.Entity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.MathHelper;
import net.minecraft.world.ChunkPosition;
import net.minecraft.world.EnumSkyBlock;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.world.ChunkEvent;
import net.minecraftforge.event.world.ChunkEvent.Load;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Intrinsic;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.Slice;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.cardinalstar.cubicchunks.CubicChunks;
import com.cardinalstar.cubicchunks.CubicChunksConfig;
import com.cardinalstar.cubicchunks.api.IColumn;
import com.cardinalstar.cubicchunks.api.ICube;
import com.cardinalstar.cubicchunks.api.IHeightMap;
import com.cardinalstar.cubicchunks.mixin.api.ICubicWorldInternal;
import com.cardinalstar.cubicchunks.util.Mods;
import com.cardinalstar.cubicchunks.world.column.ColumnTileEntityMap;
import com.cardinalstar.cubicchunks.world.column.CubeMap;
import com.cardinalstar.cubicchunks.world.column.EmptyEBS;
import com.cardinalstar.cubicchunks.world.core.ClientHeightMap;
import com.cardinalstar.cubicchunks.world.core.IColumnInternal;
import com.cardinalstar.cubicchunks.world.core.ServerHeightMap;
import com.cardinalstar.cubicchunks.world.core.StagingHeightMap;
import com.cardinalstar.cubicchunks.world.cube.BlankCube;
import com.cardinalstar.cubicchunks.world.cube.Cube;
import com.llamalad7.mixinextras.expression.Definition;
import com.llamalad7.mixinextras.expression.Expression;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;

/**
 * Modifies vanilla code in Chunk to use Cubes
 */
// TODO: redirect isChunkLoaded where needed
@SuppressWarnings("MissingOrInvalidOpcode")
@ParametersAreNonnullByDefault
@Mixin(value = Chunk.class, priority = 999)
public abstract class MixinChunk implements IColumn, IColumnInternal {

    @Shadow
    private ExtendedBlockStorage[] storageArrays;

    @Shadow
    @Final
    public int xPosition;
    @Shadow
    @Final
    public int zPosition;

    @Shadow
    @Mutable
    public Map<ChunkPosition, TileEntity> chunkTileEntityMap;

    @Shadow
    public int[] heightMap;
    @Shadow
    public int[] precipitationHeightMap;
    @Shadow
    public World worldObj;
    @Shadow
    public boolean isChunkLoaded;
    @Shadow
    public boolean isLightPopulated;
    @Shadow
    public boolean isModified;
    @Shadow
    public boolean field_150815_m;
    @Unique
    private CubeMap cubeMap;
    @Unique
    private IHeightMap opacityIndex;
    @Unique
    private Cube cachedCube; // todo: make it always nonnull using BlankCube
    @Unique
    private StagingHeightMap stagingHeightMap;
    @Unique
    private boolean isColumn = false;

    @Shadow
    public abstract byte[] getBiomeArray();

    @Shadow
    public boolean isTerrainPopulated;

    @Unique
    @SuppressWarnings({ "unchecked", "AddedMixinMembersNamePattern" })
    public <T extends World & ICubicWorldInternal> T getWorldObj() {
        return (T) this.worldObj;
    }

    // TODO: make it go through cube raw access methods
    // TODO: make cube an interface, use the implementation only here
    @Unique
    @Nullable
    private ExtendedBlockStorage getEBS_CubicChunks(int index) {
        if (!isColumn) {
            return storageArrays[index];
        }
        if (cachedCube != null && cachedCube.getY() == index) {
            return cachedCube.getStorage();
        }
        Cube cube = getWorldObj().getCubeCache()
            .getCube(this.xPosition, index, this.zPosition);
        if (!(cube instanceof BlankCube)) {
            cachedCube = cube;
        }
        return cube.getStorage();
    }

    // setEBS is unlikely to be used extremely frequently, no caching
    @Unique
    private void setEBS_CubicChunks(int index, ExtendedBlockStorage ebs) {
        if (index >= 0 && index < 16) {
            storageArrays[index] = ebs;
        }
        if (!isColumn) {
            return;
        }
        if (cachedCube != null && cachedCube.getY() == index) {
            cachedCube.setStorage(ebs);
            return;
        }
        Cube loaded = getWorldObj().getCubeCache()
            .getLoadedCube(this.xPosition, index, this.zPosition);
        if (loaded == null) {
            // BlankCube clientside. This is the only case where getEBS doesn't create cube
            return;
        }
        if (loaded.getStorage() == null) {
            loaded.setStorage(ebs);
        } else {
            throw new IllegalStateException(
                String.format(
                    "Attempted to set a Cube ExtendedBlockStorage that already exists. " + "This is not supported. "
                        + "CubePos(%d, %d, %d), loadedCube(%s), loadedCubeStorage(%s)",
                    this.xPosition,
                    index,
                    this.zPosition,
                    loaded,
                    loaded.getStorage()));
        }
    }

    @Inject(method = "<init>(Lnet/minecraft/world/World;II)V", at = @At(value = "RETURN"))
    private void cubicChunkColumn_construct(World world, int x, int z, CallbackInfo cbi) {
        // noinspection ConstantValue
        if (world == null) {
            // Some mods construct chunks with null world, ignore them
            return;
        }

        this.cubeMap = new CubeMap();
        // clientside we don't really need that much data. we actually only need top and bottom block Y positions
        if (world.isRemote) {
            this.opacityIndex = new ClientHeightMap((Chunk) (Object) this, heightMap);
        } else {
            this.opacityIndex = new ServerHeightMap(heightMap);
        }
        this.stagingHeightMap = new StagingHeightMap();
        // instead of redirecting access to this map, just make the map do the work
        this.chunkTileEntityMap = new ColumnTileEntityMap(this);

        if (!Mods.ChunkAPI.isModLoaded()) {
            Arrays.fill(getBiomeArray(), (byte) -1);
        }
    }

    /*
     * Light update code called from this:
     * if (addedNewCube) {
     * generateSkylightMap();
     * } else {
     * if (placingOpaque) {
     * if (placingNewTopBlock) {
     * relightBlock(x, y + 1, zPosition);
     * } else if (removingTopBlock) {
     * relightBlock(x, y, zPosition);
     * }
     * }
     * // equivalent to opacityDecreased || (opacityChanged && receivesLight)
     * // which means: propagateSkylight if it lets more light through, or (it receives any light and opacity changed)
     * if (opacityChanged && (opacityDecreased || blockReceivesLight)) {
     * propagateSkylightOcclusion(x, zPosition);
     * }
     * }
     */
    // ==============================================
    // generateSkylightMap
    // ==============================================

    @Inject(method = "generateSkylightMap", at = @At(value = "HEAD"), cancellable = true)
    private void generateSkylightMap_CubicChunks_Replace(CallbackInfo cbi) {
        if (isColumn) {
            // TODO: update skylight in cubes marked for update
            cbi.cancel();
        }
    }

    // ==============================================
    // propagateSkylightOcclusion
    // ==============================================

    @Inject(method = "propagateSkylightOcclusion", at = @At(value = "HEAD"), cancellable = true)
    private void propagateSkylightOcclusion_CubicChunks_Replace(int x, int z, CallbackInfo cbi) {
        if (isColumn) {
            cbi.cancel();
        }
    }

    // ==============================================
    // recheckGaps
    // ==============================================

    @Inject(method = "recheckGaps", at = @At(value = "HEAD"), cancellable = true)
    private void recheckGaps_CubicChunks_Replace(boolean p_150803_1_, CallbackInfo cbi) {
        if (isColumn) {
            cbi.cancel();
        }
    }

    // ==============================================
    // relightBlock
    // ==============================================

    // TODO is this too brittle?
    /**
     * Modifies the flag variable so that the code always gets into the branch with Chunk.relightBlock redirected below
     */
    @ModifyVariable(method = "func_150807_a", at = @At(value = "STORE"), name = "flag")
    private boolean setBlockStateInjectGenerateSkylightMapVanilla(boolean generateSkylight) {
        if (!isColumn) {
            return generateSkylight;
        }
        return false;
    }

    @Inject(
        method = "func_150807_a",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/chunk/Chunk;relightBlock(III)V"))
    private void setBlockState_CubicChunks_relightBlockReplace(int localX, int localY, int localZ, Block p_150807_4_,
        int newMeta, CallbackInfoReturnable<Boolean> cir, @Local(name = "j1") int oldHeightValue) {
        if (isColumn && this.getCube(blockToCube(localY))
            .isInitialLightingDone()) {
            // oldHeightValue is the previous block Y above the top block, so this is the "removing a block" case
            if (oldHeightValue == localY + 1) {
                getWorldObj().getLightingManager()
                    .doOnBlockSetLightUpdates(
                        (Chunk) (Object) this,
                        localX,
                        getHeightValue(localX, localZ),
                        localY,
                        localZ);
            } else {
                getWorldObj().getLightingManager()
                    .doOnBlockSetLightUpdates((Chunk) (Object) this, localX, oldHeightValue, localY + 1, localZ);
            }
        }
    }

    @Redirect(
        method = "func_150807_a",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/chunk/Chunk;getSavedLightValue(Lnet/minecraft/world/EnumSkyBlock;III)I"))
    private int setBlockState_CubicChunks_noGetLightFor(Chunk instance, EnumSkyBlock type, int x, int y, int z) {
        if (!isColumn) {
            return instance.getSavedLightValue(type, x, y, z);
        }
        return 0;
    }

    // make relightBlock no-op for cubic chunks, handles by injection above
    @Inject(method = "relightBlock", at = @At(value = "HEAD"), cancellable = true)
    private void relightBlock_CubicChunks_Replace(int x, int y, int z, CallbackInfo cbi) {
        if (isColumn) {
            cbi.cancel();
        }
    }

    // ==============================================
    // getBlockLightOpacity
    // ==============================================

    @Unique
    private void markDirty(Cube cube) {
        this.isModified = true;
        cube.markDirty();
    }

    @Unique
    private void markDirty(int cubeY) {
        this.isModified = true;

        if (isColumn) {
            this.getCube(cubeY)
                .markDirty();
        }
    }

    // ==============================================
    // getBlock
    // ==============================================

    @Redirect(
        method = "getBlock(III)Lnet/minecraft/block/Block;",
        at = @At(
            value = "FIELD",
            args = "array=length",
            target = "Lnet/minecraft/world/chunk/Chunk;storageArrays:[Lnet/minecraft/world/chunk/storage/ExtendedBlockStorage;"))
    private int getBlock_getMaxHeight(ExtendedBlockStorage[] ebs) {
        // Always return MAX_VALUE to no-op comparison, and rely on getBlock_getStorage to detect cube presence
        return Integer.MAX_VALUE;
    }

    @Redirect(
        method = "getBlock(III)Lnet/minecraft/block/Block;",
        at = @At(
            value = "FIELD",
            args = "array=get",
            target = "Lnet/minecraft/world/chunk/Chunk;storageArrays:[Lnet/minecraft/world/chunk/storage/ExtendedBlockStorage;"))
    private ExtendedBlockStorage getBlock_getStorage(ExtendedBlockStorage[] ebs, int y) {
        return getEBS_CubicChunks(y);
    }

    // ==============================================
    // getBlockMetadata
    // ==============================================

    @Redirect(
        method = "getBlockMetadata(III)I",
        at = @At(
            value = "FIELD",
            args = "array=length",
            target = "Lnet/minecraft/world/chunk/Chunk;storageArrays:[Lnet/minecraft/world/chunk/storage/ExtendedBlockStorage;"))
    private int getBlockMetadata_getMaxHeight(ExtendedBlockStorage[] ebs) {
        // Always return MAX_VALUE to no-op comparison, and rely on getBlockMetadata_getStorage to detect cube presence
        return Integer.MAX_VALUE;
    }

    @Redirect(
        method = "getBlockMetadata(III)I",
        at = @At(
            value = "FIELD",
            args = "array=get",
            target = "Lnet/minecraft/world/chunk/Chunk;storageArrays:[Lnet/minecraft/world/chunk/storage/ExtendedBlockStorage;"))
    private ExtendedBlockStorage getBlockMetadata_getStorage(ExtendedBlockStorage[] ebs, int y) {
        return getEBS_CubicChunks(y);
    }

    // ==============================================
    // setBlockWithMeta
    // ==============================================

    @Inject(
        method = "func_150807_a",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/chunk/storage/ExtendedBlockStorage;setExtBlockMetadata(IIII)V",
            shift = At.Shift.AFTER))
    private void onEBSSet_setBlockWithMeta_setOpacity(int x, int y, int z, Block block, int meta,
        CallbackInfoReturnable<Boolean> cir) {
        if (!isColumn) {
            return;
        }

        markDirty(blockToCube(y));

        if (getCube(blockToCube(y)).isSurfaceTracked()) {
            opacityIndex.onOpacityChange(blockToLocal(x), y, blockToLocal(z), block.getLightOpacity());
            getWorldObj().getLightingManager()
                .onHeightUpdate(x + 16 * this.xPosition, y, z + 16 * this.zPosition);
        } else {
            stagingHeightMap.onOpacityChange(blockToLocal(x), y, blockToLocal(z), block.getLightOpacity());
        }
    }

    @Redirect(
        method = "func_150807_a",
        at = @At(
            value = "FIELD",
            target = "Lnet/minecraft/world/chunk/Chunk;storageArrays:[Lnet/minecraft/world/chunk/storage/ExtendedBlockStorage;",
            args = "array=get"))
    private ExtendedBlockStorage setBlockWithMeta_CubicChunks_EBSGetRedirect(ExtendedBlockStorage[] array, int index) {
        return getEBS_CubicChunks(index);
    }

    @Definition(
        id = "storageArrays",
        field = "Lnet/minecraft/world/chunk/Chunk;storageArrays:[Lnet/minecraft/world/chunk/storage/ExtendedBlockStorage;")
    @Expression("this.storageArrays[? >> 4] = ?")
    @WrapOperation(method = "func_150807_a", at = @At("MIXINEXTRAS:EXPRESSION"))
    private void setBlockWithMeta_CubicChunks_EBSSetRedirect(ExtendedBlockStorage[] array, int index,
        ExtendedBlockStorage value, Operation<Void> original) {
        setEBS_CubicChunks(index, value);
    }

    @Redirect(
        method = "func_150807_a",
        at = @At(value = "FIELD", target = "Lnet/minecraft/world/chunk/Chunk;isModified:Z"))
    private void setIsModifiedFromSetBlockWithMeta_Field(Chunk chunk, boolean isModifiedIn, int x, int y, int z,
        Block block, int meta) {
        markDirty(blockToCube(y));
    }

    // ==============================================
    // setBlockMetadata
    // ==============================================

    @Inject(
        method = "setBlockMetadata",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/chunk/storage/ExtendedBlockStorage;setExtBlockMetadata" + "(IIII)V",
            shift = At.Shift.AFTER))
    private void onEBSSet_setBlockMetadata_setOpacity(int x, int y, int z, int meta,
        CallbackInfoReturnable<Boolean> cir) {
        if (!isColumn) {
            return;
        }

        Cube cube = this.getCube(blockToCube(y));
        markDirty(cube);

        Block block = cube.getBlock(x, y, z);

        if (cube.isSurfaceTracked()) {
            opacityIndex.onOpacityChange(blockToLocal(x), y, blockToLocal(z), block.getLightOpacity());
            getWorldObj().getLightingManager()
                .onHeightUpdate(x + 16 * this.xPosition, y, z + 16 * this.zPosition);
        } else {
            stagingHeightMap.onOpacityChange(blockToLocal(x), y, blockToLocal(z), block.getLightOpacity());
        }
    }

    @Redirect(
        method = "setBlockMetadata",
        at = @At(
            value = "FIELD",
            target = "Lnet/minecraft/world/chunk/Chunk;storageArrays:[Lnet/minecraft/world/chunk/storage/ExtendedBlockStorage;",
            args = "array=get"))
    private ExtendedBlockStorage setBlockMetadata_CubicChunks_EBSGetRedirect(ExtendedBlockStorage[] array, int index) {
        return getEBS_CubicChunks(index);
    }

    @Redirect(
        method = "setBlockMetadata",
        at = @At(value = "FIELD", target = "Lnet/minecraft/world/chunk/Chunk;isModified:Z"))
    private void setIsModifiedFromSetBlockMetadata_Field(Chunk chunk, boolean isModifiedIn, int x, int y, int z,
        int meta) {
        markDirty(blockToCube(y));
    }

    // ==============================================
    // getLightFor
    // ==============================================

    @WrapMethod(method = "getSavedLightValue")
    private int replacedGetSavedLightValueForCC(EnumSkyBlock type, int x, int y, int z, Operation<Integer> original) {
        if (!isColumn) {
            return original.call(type, x, y, z);
        }

        if (((ICubicWorldInternal) worldObj).getLightingManager() != null) {
            ((ICubicWorldInternal) worldObj).getLightingManager()
                .onGetLight(type, x, y, z);
        }

        return this.getCube(blockToCube(y))
            .getCachedLightFor(type, x, y, z);
    }

    @Nullable
    @Redirect(
        method = "getSavedLightValue",
        at = @At(
            value = "FIELD",
            target = "Lnet/minecraft/world/chunk/Chunk;storageArrays:[Lnet/minecraft/world/chunk/storage/ExtendedBlockStorage;",
            args = "array=get"))
    private ExtendedBlockStorage getLightFor_CubicChunks_EBSGetRedirect(ExtendedBlockStorage[] array, int index) {
        return getEBS_CubicChunks(index);
    }

    // ==============================================
    // LIGHTING HOOKS
    // ==============================================

    @Inject(method = "getBlockLightValue", at = @At("HEAD"))
    private void onGetBlockLightValue(int x, int y, int z, int amount, CallbackInfoReturnable<Integer> cir) {
        if (!isColumn) {
            return;
        }

        if (getWorldObj().getLightingManager() != null) {
            getWorldObj().getLightingManager()
                .onGetLightSubtracted(x, y, z);
        }
    }

    // ==============================================
    // setLightValue
    // ==============================================

    @Redirect(
        method = "setLightValue",
        at = @At(
            value = "FIELD",
            target = "Lnet/minecraft/world/chunk/Chunk;storageArrays:[Lnet/minecraft/world/chunk/storage/ExtendedBlockStorage;",
            args = "array=get"))
    private ExtendedBlockStorage setLightValue_CubicChunks_EBSGetRedirect(ExtendedBlockStorage[] array, int index) {
        ExtendedBlockStorage ebs = getEBS_CubicChunks(index);

        return ebs == null ? EmptyEBS.INSTANCE : ebs;
    }

    @Definition(
        id = "storageArrays",
        field = "Lnet/minecraft/world/chunk/Chunk;storageArrays:[Lnet/minecraft/world/chunk/storage/ExtendedBlockStorage;")
    @Expression("this.storageArrays[? >> 4] = ?")
    @WrapOperation(method = "setLightValue", at = @At("MIXINEXTRAS:EXPRESSION"))
    private void setLightValue_CubicChunks_EBSSetRedirect(ExtendedBlockStorage[] array, int index,
        ExtendedBlockStorage value, Operation<Void> original) {
        setEBS_CubicChunks(index, value);
    }

    @Redirect(
        method = "setLightValue",
        at = @At(value = "FIELD", target = "Lnet/minecraft/world/chunk/Chunk;isModified:Z"))
    private void setIsModifiedFromSetLightValue_Field(Chunk chunk, boolean isModifiedIn, EnumSkyBlock type, int x,
        int y, int z, int value) {
        markDirty(blockToCube(y));
    }

    // ==============================================
    // getBlockLightValue
    // ==============================================

    @Nullable
    @Redirect(
        method = "getBlockLightValue",
        at = @At(
            value = "FIELD",
            target = "Lnet/minecraft/world/chunk/Chunk;storageArrays:[Lnet/minecraft/world/chunk/storage/ExtendedBlockStorage;",
            args = "array=get"))
    private ExtendedBlockStorage getBlockLightValue_CubicChunks_EBSGetRedirect(ExtendedBlockStorage[] array,
        int index) {
        return getEBS_CubicChunks(index);
    }

    // ==============================================
    // addEntity
    // ==============================================

    @ModifyConstant(
        method = "addEntity",
        constant = @Constant(expandZeroConditions = Constant.Condition.LESS_THAN_ZERO, intValue = 0),
        require = 1)
    private int addEntity_getMinY(int zero) {
        // For columns, no-op the comparison by returning min int
        // For chunks, return 0 (the vanilla value), since it delegates to entityLists
        return isColumn ? Integer.MIN_VALUE : 0;
    }

    @Redirect(
        method = "addEntity",
        at = @At(
            value = "FIELD",
            args = "array=length",
            target = "Lnet/minecraft/world/chunk/Chunk;entityLists:[Ljava/util/List;"),
        require = 2)
    private int addEntity_getMaxHeight(List<Entity>[] entityLists) {
        // For columns, no-op the comparison by returning max int
        // For chunks, return entityLists.length (the vanilla value), since it delegates to entityLists
        return isColumn ? Integer.MAX_VALUE : entityLists.length;
    }

    @Redirect(
        method = "addEntity",
        at = @At(
            value = "FIELD",
            args = "array=get",
            target = "Lnet/minecraft/world/chunk/Chunk;entityLists:[Ljava/util/List;"),
        require = 1)
    private List<Entity> addEntity_getEntityList(List<Entity>[] entityLists, int cubeY, Entity entity) {
        if (!isColumn) {
            // Chunks never check cubes, so use the built-in entityLists
            return entityLists[cubeY];
        }

        // Get the loaded cube, or make a new one
        return getCube(cubeY).getEntityContainer();
    }

    // ==============================================
    // removeEntityAtIndex
    // ==============================================

    @ModifyConstant(
        method = "removeEntityAtIndex",
        constant = @Constant(expandZeroConditions = Constant.Condition.LESS_THAN_ZERO, intValue = 0),
        require = 2,
        slice = @Slice(
            from = @At("HEAD"),
            to = @At(value = "INVOKE", target = "Ljava/util/List;remove(Ljava/lang/Object;)Z")))
    private int removeEntityAtIndex_getMinY(int zero) {
        // For columns, no-op the comparison by returning min int
        // For chunks, return 0 (the vanilla value), since it delegates to entityLists
        return isColumn ? Integer.MIN_VALUE : 0;
    }

    @Redirect(
        method = "removeEntityAtIndex",
        at = @At(
            value = "FIELD",
            args = "array=length",
            target = "Lnet/minecraft/world/chunk/Chunk;entityLists:[Ljava/util/List;"),
        require = 2)
    private int removeEntityAtIndex_getMaxHeight(List<Entity>[] entityLists) {
        // For columns, no-op the comparison by returning max int
        // For chunks, return entityLists.length (the vanilla value), since it delegates to entityLists
        return isColumn ? Integer.MAX_VALUE : entityLists.length;
    }

    @Redirect(
        method = "removeEntityAtIndex",
        at = @At(
            value = "FIELD",
            args = "array=get",
            target = "Lnet/minecraft/world/chunk/Chunk;entityLists:[Ljava/util/List;"),
        require = 1)
    private List<Entity> removeEntityAtIndex_getEntityList(List<Entity>[] entityLists, int cubeY, Entity entity,
        int $cubeY) {
        if (!isColumn) {
            // Chunks never check cubes, so use the built-in entityLists
            return entityLists[cubeY];
        }

        // Get the loaded cube, or make a new one
        return getCube(cubeY).getEntityContainer();
    }

    // ==============================================
    // addTileEntity
    // ==============================================

    @Redirect(
        method = "addTileEntity(Lnet/minecraft/tileentity/TileEntity;)V",
        at = @At(value = "FIELD", target = "Lnet/minecraft/world/chunk/Chunk;isChunkLoaded:Z"))
    private boolean addTileEntity_isChunkLoadedCubeRedirect(Chunk chunk, TileEntity te) {
        if (!isColumn) {
            return isChunkLoaded;
        }
        ICube cube = this.getLoadedCube(blockToCube(te.yCoord));
        return cube != null && cube.isCubeLoaded();
    }

    // ==============================================
    // removeTileEntity
    // ==============================================

    @Redirect(
        method = "removeTileEntity",
        at = @At(value = "FIELD", target = "Lnet/minecraft/world/chunk/Chunk;isChunkLoaded:Z"))
    private boolean removeTileEntity_isChunkLoadedCubeRedirect(Chunk chunk, int x, int y, int z) {
        if (!isColumn) {
            return isChunkLoaded;
        }
        ICube cube = this.getLoadedCube(blockToCube(y));
        return cube != null && cube.isCubeLoaded();
    }

    // ==============================================
    // onLoad
    // ==============================================

    @Inject(method = "onChunkLoad", at = @At("HEAD"), cancellable = true)
    private void onChunkLoad_CubicChunks(CallbackInfo cbi) {
        if (!isColumn) {
            return;
        }
        cbi.cancel();
        this.isChunkLoaded = true;
        MinecraftForge.EVENT_BUS.post(new Load((Chunk) (Object) this));
    }

    // ==============================================
    // onUnload
    // ==============================================

    @Inject(method = "onChunkUnload", at = @At("HEAD"), cancellable = true)
    private void onChunkUnload_CubicChunks(CallbackInfo cbi) {
        if (!isColumn) {
            return;
        }
        cbi.cancel();
        this.isChunkLoaded = false;

        for (Cube cube : cubeMap) {
            cube.onCubeUnload();
        }

        MinecraftForge.EVENT_BUS.post(new ChunkEvent.Unload((Chunk) (Object) this));
    }

    // ==============================================
    // getEntitiesWithinAABBForEntity
    // ==============================================

    @Inject(method = "getEntitiesWithinAABBForEntity", at = @At("HEAD"), cancellable = true)
    private void getEntitiesWithinAABBForEntity_CubicChunks(@Nullable Entity entityIn, AxisAlignedBB aabb,
        List<Entity> listToFill, IEntitySelector filter, CallbackInfo cbi) {
        if (!isColumn) {
            return;
        }
        cbi.cancel();

        int minY = MathHelper.floor_double((aabb.minY - World.MAX_ENTITY_RADIUS) / Cube.SIZE_D);
        int maxY = MathHelper.floor_double((aabb.maxY + World.MAX_ENTITY_RADIUS) / Cube.SIZE_D);
        minY = MathHelper
            .clamp_int(minY, blockToCube(getWorldObj().getMinHeight()), blockToCube(getWorldObj().getMaxHeight()));
        maxY = MathHelper
            .clamp_int(maxY, blockToCube(getWorldObj().getMinHeight()), blockToCube(getWorldObj().getMaxHeight()));

        for (Cube cube : cubeMap.cubes(minY, maxY)) {
            if (cube.getEntityContainer()
                .isEmpty()) {
                continue;
            }
            for (Entity entity : cube.getEntityContainer()) {
                if (!entity.boundingBox.intersectsWith(aabb) || entity == entityIn) {
                    continue;
                }
                if (filter == null || filter.isEntityApplicable(entity)) {
                    listToFill.add(entity);
                }

                Entity[] parts = entity.getParts();

                if (parts != null) {
                    for (Entity part : parts) {
                        if (part != entityIn && part.boundingBox.intersectsWith(aabb)
                            && (filter == null || filter.isEntityApplicable(entity))) {
                            listToFill.add(part);
                        }
                    }
                }
            }
        }
    }

    // ==============================================
    // getEntitiesOfTypeWithinAABB
    // ==============================================

    @Inject(method = "getEntitiesOfTypeWithinAAAB", at = @At("HEAD"), cancellable = true)
    private <T extends Entity> void getEntitiesOfTypeWithinAAAB_CubicChunks(Class<? extends T> entityClass,
        AxisAlignedBB aabb, List<T> listToFill, IEntitySelector filter, CallbackInfo cbi) {
        if (!isColumn) {
            return;
        }
        cbi.cancel();

        int minY = MathHelper.floor_double((aabb.minY - World.MAX_ENTITY_RADIUS) / Cube.SIZE_D);
        int maxY = MathHelper.floor_double((aabb.maxY + World.MAX_ENTITY_RADIUS) / Cube.SIZE_D);
        minY = MathHelper
            .clamp_int(minY, blockToCube(getWorldObj().getMinHeight()), blockToCube(getWorldObj().getMaxHeight()));
        maxY = MathHelper
            .clamp_int(maxY, blockToCube(getWorldObj().getMinHeight()), blockToCube(getWorldObj().getMaxHeight()));

        for (Cube cube : cubeMap.cubes(minY, maxY)) {
            for (Entity entity : cube.getEntityContainer()) {
                if (entityClass.isAssignableFrom(entity.getClass()) && entity.boundingBox.intersectsWith(aabb)
                    && (filter == null || filter.isEntityApplicable(entity))) {
                    listToFill.add((T) entity);
                }
            }
        }
    }

    // public boolean needsSaving(boolean p_76601_1_) - TODO: needsSaving

    // ==============================================
    // getPrecipitationHeight
    // ==============================================

    @Inject(method = "getPrecipitationHeight", at = @At(value = "HEAD"), cancellable = true)
    private void getPrecipitationHeight_CubicChunks_Replace(int x, int z, CallbackInfoReturnable<Integer> cbi) {
        if (isColumn) {
            int localX = blockToLocal(x);
            int localZ = blockToLocal(z);
            int index = localX | localZ << 4;

            if (precipitationHeightMap[index] == -999) {
                precipitationHeightMap[index] = -1;

                for (Cube cube : cubeMap.cubes(
                    blockToCube(CubicChunks.MAX_SUPPORTED_BLOCK_Y),
                    blockToCube(CubicChunks.MIN_SUPPORTED_BLOCK_Y))) {
                    if (cube.getStorage() == null) {
                        continue;
                    }

                    for (int localY = Cube.SIZE - 1; localY >= 0; localY--) {
                        Block block = cube.getBlock(localX, localY, localZ);
                        if (block.getMaterial()
                            .blocksMovement()
                            || block.getMaterial()
                                .isLiquid()) {
                            precipitationHeightMap[index] = cube.getCoords()
                                .getMinBlockY() + localY
                                + 1;
                            cbi.setReturnValue(precipitationHeightMap[index]);
                            return;
                        }
                    }
                }
            }

            cbi.setReturnValue(precipitationHeightMap[index]);
        }
    }

    // ==============================================
    // onTick
    // ==============================================

    // TODO: check if we are out of time earlier?
    @Inject(method = "func_150804_b", at = @At(value = "RETURN"))
    private void onTick_CubicChunks_TickCubes(boolean tryToTickFaster, CallbackInfo cbi) {
        if (!isColumn) {
            return;
        }
        this.field_150815_m = true;
        this.isLightPopulated = true;
        this.isTerrainPopulated = true;
        // do nothing, we tick cubes directly
    }

    // ==============================================
    // isEmptyBetween
    // ==============================================

    /**
     * @param startY bottom Y coordinate
     * @param endY   top Y coordinate
     * @return true if the specified height range is empty
     * @author Barteks2x
     * @reason original function limited to storage arrays.
     */
    @Overwrite
    public boolean getAreLevelsEmpty(int startY, int endY) {
        if (startY < getWorldObj().getMinHeight()) {
            startY = getWorldObj().getMinHeight();
        }

        if (endY >= getWorldObj().getMaxHeight()) {
            endY = getWorldObj().getMaxHeight() - 1;
        }

        for (int i = blockToCube(startY); i <= blockToCube(endY); i++) {
            ExtendedBlockStorage extendedblockstorage = getEBS_CubicChunks(i);

            if (extendedblockstorage != null && !extendedblockstorage.isEmpty()) {
                return false;
            }
        }

        return true;
    }

    // ==============================================
    // setStorageArrays
    // ==============================================

    @Inject(method = "setStorageArrays", at = @At(value = "HEAD"))
    private void setStorageArrays_CubicChunks_NotSupported(ExtendedBlockStorage[] newStorageArrays, CallbackInfo cbi) {
        if (isColumn) {
            throw new UnsupportedOperationException("setting storage arrays it not supported with cubic chunks");
        }
    }

    // ==============================================
    // checkLight
    // ==============================================

    @Inject(method = "func_150809_p()V", at = @At(value = "HEAD"), cancellable = true)
    private void checkLight_CubicChunks_NotSupported(CallbackInfo cbi) {
        if (isColumn) {
            // we use FirstLightProcessor instead
            cbi.cancel();
        }
    }

    // private void setSkylightUpdated() - noone should use it

    // private void checkLightSide(EnumFacing facing) - noone should use it

    // private boolean checkLight(int x, int zPosition) - TODO: checkLight

    // ==============================================
    // removeInvalidTileEntity
    // ==============================================

    @Redirect(
        method = "removeInvalidTileEntity",
        at = @At(value = "FIELD", target = "Lnet/minecraft/world/chunk/Chunk;isChunkLoaded:Z"))
    private boolean removeInvalidTileEntity_isChunkLoadedCubeRedirect(Chunk chunk, int x, int y, int z) {
        if (!isColumn) {
            return isChunkLoaded;
        }
        ICube cube = this.getLoadedCube(blockToCube(y));
        return cube != null && cube.isCubeLoaded();
    }

    // ==============================================
    // enqueueRelightChecks
    // ==============================================

    @Inject(method = "enqueueRelightChecks", at = @At(value = "HEAD"), cancellable = true)
    private void enqueueRelightChecks_CubicChunks(CallbackInfo cbi) {
        if (!isColumn) {
            return;
        }

        cbi.cancel();

        if (CubicChunksConfig.relightChecksPerTickPerColumn > 0
            && (!worldObj.isRemote || CubicChunksConfig.doClientLightFixes)) {
            cubeMap.enqueueRelightChecks();
        }
    }

    @Override
    public void setColumn(boolean isColumn) {
        this.isColumn = isColumn;
    }

    @Override
    public Cube getLoadedCube(int cubeY) {
        if (cachedCube != null && cachedCube.getY() == cubeY) {
            return cachedCube;
        }

        return this.cubeMap.get(cubeY);
    }

    @Override
    public Cube getCube(int cubeY) {
        Cube loaded = getLoadedCube(cubeY);

        if (loaded != null) return loaded;

        // No loaded cube, try to load or generate one
        return getCubicWorld().getCubeCache()
            .getCube(xPosition, cubeY, zPosition);
    }

    @Override
    public void addCube(Cube cube) {
        this.cubeMap.put(cube);

        Arrays.fill(this.precipitationHeightMap, -999);

        cube.installIntoChunk();
    }

    @Override
    public Cube removeCube(int cubeY) {
        if (cachedCube != null && cachedCube.getY() == cubeY) {
            cachedCube = null;
        }

        Cube removed = this.cubeMap.remove(cubeY);

        if (removed != null) {
            removed.uninstallFromChunk();
        }

        return removed;
    }

    @Override
    public void removeFromStagingHeightmap(ICube cube) {
        stagingHeightMap.removeStagedCube(cube);
    }

    @Override
    public void addToStagingHeightmap(ICube cube) {
        stagingHeightMap.addStagedCube(cube);
    }

    @Override
    public void recalculateStagingHeightmap() {
        stagingHeightMap.recalculate();
    }

    @Override
    public int getTopYWithStaging(int localX, int localZ) {
        if (!isColumn) {
            return heightMap[localZ << 4 | localX] - 1;
        }
        return Math.max(opacityIndex.getTopBlockY(localX, localZ), stagingHeightMap.getTopBlockY(localX, localZ));
    }

    @Override
    public boolean hasLoadedCubes() {
        return !cubeMap.isEmpty();
    }

    @Unique
    @SuppressWarnings({ "unchecked", "AddedMixinMembersNamePattern" })
    public <T extends World & ICubicWorldInternal> T getCubicWorld() {
        return (T) this.worldObj;
    }

    @Override
    public boolean shouldTick() {
        for (Cube cube : cubeMap) {
            if (cube.getTickets()
                .shouldTick()) {
                return true;
            }
        }

        return false;
    }

    @Override
    public IHeightMap getOpacityIndex() {
        return this.opacityIndex;
    }

    @Override
    public Collection<? extends Cube> getLoadedCubes() {
        return this.cubeMap.all();
    }

    @Override
    public ExtendedBlockStorage[] getTickableStorages() {
        return this.cubeMap.getTickableStorages();
    }

    @Override
    public Iterable<? extends Cube> getLoadedCubes(int startY, int endY) {
        return this.cubeMap.cubes(startY, endY);
    }

    @Override
    public void preCacheCube(Cube cube) {
        this.cachedCube = cube;
    }

    @Override
    @Intrinsic
    public int getX() {
        return xPosition;
    }

    @Override
    @Intrinsic
    public int getZ() {
        return zPosition;
    }

    @Override
    public int getHeightValue(int localX, int blockY, int localZ) {
        return getTopYWithStaging(localX, localZ) + 1;
    }

    /**
     * @author Barteks2x
     * @reason go through staging heightmap
     */
    @Override
    @Overwrite
    public int getHeightValue(int localX, int localZ) {
        return getTopYWithStaging(localX, localZ) + 1;
    }
}
