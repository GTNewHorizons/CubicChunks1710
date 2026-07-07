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

import java.util.Objects;

import javax.annotation.Nonnull;
import javax.annotation.ParametersAreNonnullByDefault;

import net.minecraft.block.Block;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.entity.Entity;
import net.minecraft.world.World;
import net.minecraft.world.WorldProvider;
import net.minecraft.world.WorldServer;
import net.minecraft.world.chunk.Chunk;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Group;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.cardinalstar.cubicchunks.api.IColumn;
import com.cardinalstar.cubicchunks.api.ICube;
import com.cardinalstar.cubicchunks.util.Coords;
import com.cardinalstar.cubicchunks.world.ICubicWorld;
import com.llamalad7.mixinextras.expression.Definition;
import com.llamalad7.mixinextras.expression.Expression;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;

import cpw.mods.fml.common.FMLCommonHandler;

/**
 * Contains fixes for hardcoded height checks and other height-related issues.
 */
@ParametersAreNonnullByDefault
@Mixin(World.class)
public abstract class MixinWorld_HeightLimit implements ICubicWorld {

    @Unique
    private boolean cc$isCubicWorld = isCubic();

    @Unique
    private boolean isCubic() {
        if (FMLCommonHandler.instance()
            .getSide()
            .isClient()) {
            if (((Object) this) instanceof WorldClient) {
                return true;
            }
        }

        // noinspection ConstantValue
        return ((Object) this) instanceof WorldServer;
    }

    @Shadow
    public int skylightSubtracted;

    @Shadow
    public boolean isRemote;

    @Shadow
    @Final
    public WorldProvider provider;

    @Shadow
    public abstract Block getBlock(int x, int y, int z);

    @Shadow
    protected abstract boolean chunkExists(int x, int z);

    // TODO WATCH func_147467_a
    // =================================================
    // Individual Height Limit Mixins
    // =================================================

    // blockExists
    @ModifyConstant(
        method = "blockExists",
        constant = @Constant(
            expandZeroConditions = Constant.Condition.GREATER_THAN_OR_EQUAL_TO_ZERO,
            intValue = 0,
            ordinal = 0))
    private int blockExists_heightLimits_min(int original) {
        return getMinHeight();
    }

    @ModifyConstant(method = "blockExists", constant = @Constant(intValue = 256, ordinal = 0))
    private int blockExists_heightLimits_max(int original) {
        return getMaxHeight();
    }

    // getBlock
    @ModifyConstant(
        method = "getBlock",
        constant = @Constant(
            expandZeroConditions = Constant.Condition.GREATER_THAN_OR_EQUAL_TO_ZERO,
            intValue = 0,
            ordinal = 0))
    private int getBlock_heightLimits_min(int original) {
        return getMinHeight();
    }

    @ModifyConstant(method = "getBlock", constant = @Constant(intValue = 256, ordinal = 0))
    private int getBlock_heightLimits_max(int original) {
        return getMaxHeight();
    }

    @Definition(id = "chunkExists", method = "Lnet/minecraft/world/World;chunkExists(II)Z")
    @Expression("this.chunkExists(?, ?)")
    @Redirect(method = "blockExists", at = @At("MIXINEXTRAS:EXPRESSION"))
    boolean redirectChunkExistsCubeExists(World instance, int p_72916_1_, int p_72916_2_,
        @Local(argsOnly = true, name = "p_72899_1_") int x, @Local(argsOnly = true, name = "p_72899_2_") int y,
        @Local(argsOnly = true, name = "p_72899_3_") int z) {
        if (cc$isCubicWorld) {
            return cubeExists(x >> 4, y >> 4, z >> 4);
        } else {
            return chunkExists(x >> 4, z >> 4);
        }
    }

    // checkChunksExist
    @ModifyConstant(
        method = "checkChunksExist",
        constant = @Constant(
            expandZeroConditions = Constant.Condition.GREATER_THAN_OR_EQUAL_TO_ZERO,
            intValue = 0,
            ordinal = 0))
    private int checkChunksExist_heightLimits_min(int original) {
        return getMinHeight();
    }

    @ModifyConstant(method = "checkChunksExist", constant = @Constant(intValue = 256, ordinal = 0))
    private int checkChunksExist_heightLimits_max(int original) {
        return getMaxHeight();
    }

    // setBlock
    @ModifyConstant(
        method = "setBlock",
        constant = @Constant(expandZeroConditions = Constant.Condition.LESS_THAN_ZERO, intValue = 0, ordinal = 0))
    private int setBlock_heightLimits_min(int original) {
        return getMinHeight();
    }

    @ModifyConstant(method = "setBlock", constant = @Constant(intValue = 256, ordinal = 0))
    private int setBlock_heightLimits_max(int original) {
        return getMaxHeight();
    }

    // getBlockMetadata
    @ModifyConstant(
        method = "getBlockMetadata",
        constant = @Constant(expandZeroConditions = Constant.Condition.LESS_THAN_ZERO, intValue = 0, ordinal = 0))
    private int getBlockMetadata_heightLimits_min(int original) {
        return getMinHeight();
    }

    @ModifyConstant(method = "getBlockMetadata", constant = @Constant(intValue = 256, ordinal = 0))
    private int getBlockMetadata_heightLimits_max(int original) {
        return getMaxHeight();
    }

    // setBlockMetadataWithNotify
    @ModifyConstant(
        method = "setBlockMetadataWithNotify",
        constant = @Constant(expandZeroConditions = Constant.Condition.LESS_THAN_ZERO, intValue = 0, ordinal = 0))
    private int setBlockMetadataWithNotify_heightLimits_min(int original) {
        return getMinHeight();
    }

    @ModifyConstant(method = "setBlockMetadataWithNotify", constant = @Constant(intValue = 256, ordinal = 0))
    private int setBlockMetadataWithNotify_heightLimits_max(int original) {
        return getMaxHeight();
    }

    // getFullBlockLightValue
    @Definition(id = "y", local = @Local(argsOnly = true, ordinal = 1, type = int.class))
    @Expression("y < 0")
    @WrapOperation(method = "getFullBlockLightValue", at = @At("MIXINEXTRAS:EXPRESSION"))
    private boolean getFullBlockLightValue_heightLimits_min(int left, int right, Operation<Boolean> original) {
        return left < getMinHeight();
    }

    @ModifyConstant(method = "getFullBlockLightValue", constant = @Constant(intValue = 256, ordinal = 0))
    private int getFullBlockLightValue_heightLimits_max(int original) {
        return getMaxHeight();
    }

    @ModifyConstant(method = "getFullBlockLightValue", constant = @Constant(intValue = 255, ordinal = 0))
    private int getFullBlockLightValue_heightLimits_maxDefault(int original) {
        return getMaxHeight();
    }

    // ================= getBlockLightValue_do ======================
    @Definition(id = "y", local = @Local(argsOnly = true, ordinal = 1, type = int.class))
    @Expression("y < 0")
    @WrapOperation(method = "getBlockLightValue_do", at = @At("MIXINEXTRAS:EXPRESSION"))
    private boolean getBlockLightValue_do_heightLimits_min(int left, int right, Operation<Boolean> original) {
        return left < getMinHeight();
    }

    @ModifyConstant(method = "getBlockLightValue_do", constant = @Constant(intValue = 256, ordinal = 0))
    private int getBlockLightValue_do_heightLimits_max(int original) {
        return getMaxHeight();
    }

    @ModifyConstant(method = "getBlockLightValue_do", constant = @Constant(intValue = 255, ordinal = 0))
    private int getBlockLightValue_do_heightLimits_maxDefault(int original) {
        return getMaxHeight();
    }

    // getSavedLightValue
    @ModifyConstant(
        method = "getSavedLightValue",
        constant = @Constant(expandZeroConditions = Constant.Condition.LESS_THAN_ZERO, intValue = 0, ordinal = 0))
    private int getSavedLightValue_heightLimits_min(int original) {
        return getMinHeight();
    }

    @ModifyConstant(method = "getSavedLightValue", constant = @Constant(intValue = 0, ordinal = 0))
    private int getSavedLightValue_heightLimits_minDefault(int original) {
        return getMinHeight();
    }

    @ModifyConstant(method = "getSavedLightValue", constant = @Constant(intValue = 256, ordinal = 0))
    private int getSavedLightValue_heightLimits_max(int original) {
        return getMaxHeight();
    }

    @ModifyConstant(method = "getSavedLightValue", constant = @Constant(intValue = 255, ordinal = 0))
    private int getSavedLightValue_heightLimits_maxDefault(int original) {
        return getMaxHeight();
    }

    // setLightValue
    @ModifyConstant(
        method = "setLightValue",
        constant = @Constant(
            expandZeroConditions = Constant.Condition.GREATER_THAN_OR_EQUAL_TO_ZERO,
            intValue = 0,
            ordinal = 0))
    private int setLightValue_heightLimits_min(int original) {
        return getMinHeight();
    }

    @ModifyConstant(method = "setLightValue", constant = @Constant(intValue = 256, ordinal = 0))
    private int setLightValue_heightLimits_max(int original) {
        return getMaxHeight();
    }

    // getTileEntity
    @ModifyConstant(
        method = "getTileEntity",
        constant = @Constant(
            expandZeroConditions = Constant.Condition.GREATER_THAN_OR_EQUAL_TO_ZERO,
            intValue = 0,
            ordinal = 0))
    private int getTileEntity_heightLimits_min(int original) {
        return getMinHeight();
    }

    @ModifyConstant(method = "getTileEntity", constant = @Constant(intValue = 256, ordinal = 0))
    private int getTileEntity_heightLimits_max(int original) {
        return getMaxHeight();
    }

    // canBlockFreezeBody
    @ModifyConstant(
        method = "canBlockFreezeBody(IIIZ)Z",
        constant = @Constant(
            expandZeroConditions = Constant.Condition.GREATER_THAN_OR_EQUAL_TO_ZERO,
            intValue = 0,
            ordinal = 0),
        remap = false)
    private int canBlockFreezeBody_heightLimits_min(int original) {
        return getMinHeight();
    }

    @ModifyConstant(
        method = "canBlockFreezeBody(IIIZ)Z",
        constant = @Constant(intValue = 256, ordinal = 0),
        remap = false)
    private int canBlockFreezeBody_heightLimits_max(int original) {
        return getMaxHeight();
    }

    // canSnowAtBody
    @ModifyConstant(
        method = "canSnowAtBody",
        constant = @Constant(
            expandZeroConditions = Constant.Condition.GREATER_THAN_OR_EQUAL_TO_ZERO,
            intValue = 0,
            ordinal = 0),
        remap = false)
    private int canSnowAtBody_heightLimits_min(int original) {
        return getMinHeight();
    }

    @ModifyConstant(method = "canSnowAtBody", constant = @Constant(intValue = 256, ordinal = 0), remap = false)
    private int canSnowAtBody_heightLimits_max(int original) {
        return getMaxHeight();
    }

    // getBlockLightOpacity
    @ModifyConstant(
        method = "getBlockLightOpacity",
        constant = @Constant(expandZeroConditions = Constant.Condition.LESS_THAN_ZERO, intValue = 0, ordinal = 0),
        remap = false)
    private int getBlockLightOpacity_heightLimits_min(int original) {
        return getMinHeight();
    }

    @ModifyConstant(method = "getBlockLightOpacity", constant = @Constant(intValue = 256, ordinal = 0), remap = false)
    private int getBlockLightOpacity_heightLimits_max(int original) {
        return getMaxHeight();
    }

    // /**
    // * @param pos block positionn
    // * @return light value at the given position
    // *
    // * @author Barteks2x
    // * @reason Replace {@link World#getLight(BlockPos)} with method that works outside of 0..255 height range. It
    // would
    // * be possible to fix it using @Redirect and @ModifyConstant but this way is much cleaner, especially for simple
    // * method. A @{@link ModifyConstant} wouldn't work because it can't replace comparison to 0. This is because there
    // * is a special instruction to compare something to 0, so the constant is never used.
    // * <p>
    // * Note: The getLight method is used in parts of game logic and entity rendering code. Doesn't directly affect
    // block
    // * rendering.
    // */
    // @Overwrite
    // public int getFullBlockLightValue(BlockPos pos) {
    // if (pos.getY() < this.getMinHeight()) {
    // return 0;
    // }
    // if (pos.getY() >= this.getMaxHeight()) {
    // //CubicChunks edit
    // //return default light value above maxHeight instead of the same value as at maxHeight
    // return EnumSkyBlock.SKY.defaultLightValue;
    // //CubicChunks end
    // }
    // return this.getChunk(pos).getLightSubtracted(pos, 0);
    // }
    //
    // /**
    // * This getLight method is used in parts of game logic and entity rendering code.
    // * Doesn't directly affect block rendering.
    // */
    // @Group(name = "getLightHeightOverride", max = 4)
    // @ModifyConstant(
    // method = "getLight(Lnet/minecraft/util/math/BlockPos;Z)I",
    // constant = @Constant(intValue = 0, expandZeroConditions = Constant.Condition.LESS_THAN_ZERO, ordinal = 0),
    // slice = @Slice(
    // from = @At(value = "INVOKE", target = "Lnet/minecraft/util/math/BlockPos;getY()I"),
    // to = @At(value = "INVOKE",
    // target =
    // "Lnet/minecraft/world/World;getChunk(Lnet/minecraft/util/math/BlockPos;)Lnet/minecraft/world/chunk/Chunk;")
    // ))
    // private int getLightGetYReplace(int zero) {
    // return getMinHeight();
    // }
    //
    // /**
    // * Modify constant 255 in {@link World#getLight(BlockPos)} used in case tha height check didn't pass.
    // * When max height is exceeded vanilla clamps the value to 255 (maxHeight - 1 = actual max allowed block Y).
    // */
    // @Group(name = "getLightHeightOverride")
    // @ModifyConstant(method = "getLight(Lnet/minecraft/util/math/BlockPos;Z)I",
    // constant = {@Constant(intValue = 255), @Constant(intValue = 256)}, require = 2)
    // private int getLightGetReplacementYTooHigh(int original) {
    // return this.getMaxHeight() + original - 256;
    // }
    //
    // /**
    // * Redirect 0 constant in getLightFor(EnumSkyBlock, BlockPos)
    // * so that getLightFor returns light at y=minHeight when below minHeight.
    // */
    // @Group(name = "getLightForHeightOverride", min = 2, max = 2)
    // @ModifyConstant(method = "getLightFor",
    // constant = @Constant(intValue = 0, expandZeroConditions = Constant.Condition.LESS_THAN_ZERO))
    // private int getLightForGetMinYReplace(int origY) {
    // return this.getMinHeight();
    // }

    /**
     * Conditionally replaces isAreaLoaded with Cubic Chunks implementation
     * (continues with vanilla code if it's not a cubic chunks world).
     * World.isAreaLoaded is used to check if some things can be updated (like light).
     * If it returns false - update doesn't happen. This fixes it
     * <p>
     * NOTE: there are some methods that use it incorrectly
     * ie. by checking it at some constant height (usually 0 or 64).
     * These places need to be modified.
     *
     * @author Barteks2x
     */
    @Group(name = "exists", max = 1)
    @Inject(method = "checkChunksExist(IIIIII)Z", at = @At(value = "HEAD"), cancellable = true, require = 1)
    private void checkChunksExistInject(int xStart, int yStart, int zStart, int xEnd, int yEnd, int zEnd,
        @Nonnull CallbackInfoReturnable<Boolean> cbi) {
        cbi.setReturnValue(this.testForCubes(xStart, yStart, zStart, xEnd, yEnd, zEnd, Objects::nonNull));
    }

    // NOTE: This may break some things

    @Redirect(
        method = "updateEntityWithOptionalForce",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/World;chunkExists(II)Z", ordinal = 0))
    private boolean updateEntityWithOptionalForce_chunkExists0(World world, int chunkX, int chunkZ, Entity entity,
        boolean force) {
        assert this == (Object) world;
        ICube cube = this.getCubeCache()
            .getLoadedCube(entity.chunkCoordX, entity.chunkCoordY, entity.chunkCoordZ);
        return cube != null;
    }

    @Redirect(
        method = "updateEntityWithOptionalForce",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/World;chunkExists(II)Z", ordinal = 1))
    private boolean updateEntityWithOptionalForce_chunkExists1(World world, int chunkX, int chunkZ, Entity entity,
        boolean force) {
        assert this == (Object) world;
        int x = Coords.blockToCube(entity.posX);
        int y = Coords.blockToCube(entity.posY);
        int z = Coords.blockToCube(entity.posZ);

        ICube cube = this.getCubeCache()
            .getLoadedCube(x, y, z);

        return cube != null;
    }

    // TODO FIX
    // private int updateEntities_enityChunkBlockY;
    //
    // @Inject(method = "updateEntities",
    // at = @At(value = "INVOKE", target = "Lnet/minecraft/world/World;chunkExists(II)Z", ordinal = 0),
    // locals = LocalCapture.CAPTURE_FAILHARD,
    // require = 1)
    // private void updateEntities_chunkExists0_getLocals(CallbackInfo cbi, int i, Entity entity, int chunkX, int
    // chunkZ) {
    // updateEntities_enityChunkBlockY = cubeToMinBlock(entity.chunkCoordY);
    // }
    //
    // @Inject(method = "updateEntities",
    // at = @At(value = "INVOKE", target = "Lnet/minecraft/world/World;chunkExists(II)Z", ordinal = 1),
    // locals = LocalCapture.CAPTURE_FAILHARD,
    // require = 1)
    // private void updateEntities_chunkExists1_getLocals(CallbackInfo ci, int i, Entity entity, int chunkX, CrashReport
    // crashreport, CrashReportCategory crashreportcategory, int chunkZ) {
    // updateEntities_enityChunkBlockY = cubeToMinBlock(entity.chunkCoordY);
    // }
    //
    // @Inject(method = "updateEntities",
    // at = @At(value = "INVOKE", target = "Lnet/minecraft/world/World;chunkExists(II)Z", ordinal = 2),
    // locals = LocalCapture.CAPTURE_FAILHARD,
    // require = 1)
    // private void updateEntities_chunkExists2_getLocals(CallbackInfo cbi, int i, Entity entity, int chunkX,
    // CrashReport crashreport, CrashReportCategory crashreportcategory, int chunkZ) {
    // updateEntities_enityChunkBlockY = cubeToMinBlock(entity.chunkCoordY);
    // }
    //
    // @Inject(method = "updateEntities",
    // at = @At(value = "INVOKE", target = "Lnet/minecraft/world/World;chunkExists(II)Z", ordinal = 3),
    // locals = LocalCapture.CAPTURE_FAILHARD,
    // require = 1)
    // private void updateEntities_chunkExists3_getLocals(CallbackInfo cbi, int i, Entity entity, int chunkX,
    // CrashReport crashreport, CrashReportCategory crashreportcategory, int chunkZ) {
    // updateEntities_enityChunkBlockY = cubeToMinBlock(entity.chunkCoordY);
    // }
    //
    // @Redirect(method = "updateEntities",
    // at = @At(value = "INVOKE", target = "Lnet/minecraft/world/World;chunkExists(II)Z", ordinal = 1))
    // private boolean updateEntities_chunkExists(World world, int chunkX, int chunkZ) {
    // assert this == (Object) world;
    // if (isCubicWorld()) {
    // return this.blockExists(cubeToMinBlock(chunkX), updateEntities_enityChunkBlockY, cubeToMinBlock(chunkZ));
    // } else {
    // return this.chunkExists(chunkX, chunkZ);
    // }
    // }

    // TODO If we want 3D biomes this needs to get Y as well. I don't think this will be good for compat.
    // @Inject(method = "getBiomeGenForCoords", at = @At("HEAD"), cancellable = true)
    // private void getBiome(int x, int y, int z, CallbackInfoReturnable<BiomeGenBase> ci) {
    // if (!this.isCubicWorld())
    // return;
    // ICube cube =
    // this.getCubeCache().getLoadedCube(Coords.blockToCube(x),Coords.blockToCube(y),Coords.blockToCube(z));
    // /*
    // * Using return here function will keep callback not cancelled,
    // * therefore "vanilla" function, which will get biome from chunk, will
    // * be called. Since cube is null there is no way to retrieve chunk
    // * faster, than using vanilla way.
    // */
    // if (cube == null)
    // return;
    // BiomeGenBase biome = cube.getBiome(x, y, z);
    // ci.setReturnValue(biome);
    // }

    @Redirect(
        method = "spawnEntityInWorld",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/World;chunkExists(II)Z"))
    private boolean spawnEntity_isChunkLoaded(World world, int chunkX, int chunkZ, Entity ent) {
        assert this == (Object) world;
        return this.chunkExists(chunkX, chunkZ);
    }

    @Redirect(
        method = "markAndNotifyBlock",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/chunk/Chunk;func_150802_k()Z"))
    private boolean isValidForRendering(Chunk column, @Local(argsOnly = true, ordinal = 0) int x,
        @Local(argsOnly = true, ordinal = 1) int y, @Local(argsOnly = true, ordinal = 2) int z) {
        ICube cube = ((IColumn) column).getCube(Coords.blockToCube(y));

        if (cube == null || cube.isEmpty()) return false;

        return cube.isPopulated();
    }

    @Redirect(
        method = "getBiomeGenForCoordsBody",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/World;blockExists(III)Z"))
    private boolean checkColumnExists(World instance, int x, int zero, int z) {
        return ((ICubicWorld) instance).getCubeCache()
            .getLoadedColumn(x >> 4, z >> 4) != null;
    }
}
