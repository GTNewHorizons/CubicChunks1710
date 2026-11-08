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

import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.MathHelper;
import net.minecraft.world.World;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import com.cardinalstar.cubicchunks.world.ICubicWorld;
import com.llamalad7.mixinextras.sugar.Local;

/**
 * World class mixins related to block and entity ticking.
 */
@ParametersAreNonnullByDefault
@Mixin(World.class)
public abstract class MixinWorld_Tick implements ICubicWorld {

    @Shadow
    @Final
    public boolean isRemote;

    @Shadow
    public abstract boolean checkChunksExist(int x1, int y1, int z1, int x2, int y2, int z2);

    // //TODO: handle private isAreaLoaded correctly
    // @Shadow private boolean isAreaLoaded(int x1, int y1, int z1, int x2, int y2, int z2) {
    // throw new Error();
    // }

    // @Shadow public abstract boolean isValid(BlockPos pos);

    /**
     * Redirect {@code isAreaLoaded} here, to use Y coordinate of the entity.
     * <p>
     * Vanilla uses a constant Y because blocks below y=0 and above y=256 are never loaded, which means that entities
     * would be getting stuck there.
     */
    @Redirect(
        method = "updateEntityWithOptionalForce",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/World;checkChunksExist" + "(IIIIII)Z"),
        require = 1)
    private boolean canUpdateEntity(World _this, int startBlockX, int oldStartBlockY, int startBlockZ, int endBlockX,
        int oldEndBlockY, int endBlockZ, @Local(argsOnly = true) Entity entity) {

        // Prevent player rubberbanding
        if (entity instanceof EntityPlayer) return true;

        int entityPosY = MathHelper.floor_double(entity.posY);
        int entityPosX = MathHelper.floor_double(entity.posX);
        int entityPosZ = MathHelper.floor_double(entity.posZ);

        if (!(entityPosX >= -30000000 && entityPosX < 30000000
            && entityPosZ >= -30000000
            && entityPosZ < 30000000
            && entityPosY >= getMinHeight()
            && entityPosY < getMaxHeight())) {
            return true; // can tick everything outside of limits
        }

        int r = (endBlockX - startBlockX) >> 1;

        return checkChunksExist(
            entityPosX - r,
            entityPosY - r,
            entityPosZ - r,
            entityPosX + r,
            entityPosY + r,
            entityPosZ + r);
    }
}
