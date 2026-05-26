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

import javax.annotation.ParametersAreNonnullByDefault;

import net.minecraft.client.multiplayer.ChunkProviderClient;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.profiler.Profiler;
import net.minecraft.world.EnumDifficulty;
import net.minecraft.world.World;
import net.minecraft.world.WorldSettings;

import org.spongepowered.asm.mixin.Implements;
import org.spongepowered.asm.mixin.Interface;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.cardinalstar.cubicchunks.api.IntRange;
import com.cardinalstar.cubicchunks.client.CubeProviderClient;
import com.cardinalstar.cubicchunks.lighting.LightingManager;
import com.cardinalstar.cubicchunks.mixin.api.ICubicWorldInternal;
import com.cardinalstar.cubicchunks.mixin.early.common.MixinWorld;
import com.llamalad7.mixinextras.expression.Definition;
import com.llamalad7.mixinextras.expression.Expression;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

@ParametersAreNonnullByDefault
@Mixin(WorldClient.class)
@Implements(@Interface(iface = ICubicWorldInternal.Client.class, prefix = "world$"))
public abstract class MixinWorldClient extends MixinWorld implements ICubicWorldInternal.Client {

    @Shadow
    private ChunkProviderClient clientChunkProvider;

    @Inject(method = "<init>", at = @At("TAIL"))
    public void onInit(NetHandlerPlayClient p_i45063_1_, WorldSettings p_i45063_2_, int p_i45063_3_,
        EnumDifficulty p_i45063_4_, Profiler p_i45063_5_, CallbackInfo ci) {
        this.lightingManager = new LightingManager((World) (Object) this);
    }

    @Override
    public void initCubicWorldClient(IntRange heightRange, IntRange generationRange) {
        super.initCubicWorld(heightRange, generationRange);
        CubeProviderClient cubeProviderClient = new CubeProviderClient(this);
        this.chunkProvider = cubeProviderClient;
        this.clientChunkProvider = cubeProviderClient;
    }

    @Override
    public void tickCubicWorld() {
        if (getLightingManager() != null) getLightingManager().onTick();
    }

    @Override
    public CubeProviderClient getCubeCache() {
        return (CubeProviderClient) this.clientChunkProvider;
    }

    // Has to be in here because the world is intialized before initCubicWorldClient gets called. This causes a crash in
    // prepare spawn location on the client.
    @Redirect(
        method = "createChunkProvider",
        at = @At(
            value = "NEW",
            target = "(Lnet/minecraft/world/World;)Lnet/minecraft/client/multiplayer/ChunkProviderClient;"))
    private ChunkProviderClient redirectChunkProviderServer(World world) {
        return new CubeProviderClient(this);
    }

    @Definition(id = "rand", field = "Lnet/minecraft/client/multiplayer/WorldClient;rand:Ljava/util/Random;")
    @Definition(id = "nextInt", method = "Ljava/util/Random;nextInt(I)I")
    @Expression("this.rand.nextInt(8) > ?")
    @WrapOperation(method = "doVoidFogParticles", at = @At("MIXINEXTRAS:EXPRESSION"))
    public boolean decreaseVoidParticleAmount(int ignored, int blockY, Operation<Boolean> original) {
        return this.rand.nextInt(8) > Math.max(blockY, 4);
    }
}
