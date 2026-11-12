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
package com.cardinalstar.cubicchunks.mixin.early.common.forge;

import java.util.ArrayList;
import java.util.Hashtable;

import net.minecraft.world.WorldServer;
import net.minecraftforge.common.DimensionManager;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.cardinalstar.cubicchunks.server.CubeProviderServer;
import com.cardinalstar.cubicchunks.server.chunkio.CubeLoaderServer;

@Mixin(value = DimensionManager.class, remap = false)
public abstract class MixinDimensionManager {

    @Shadow
    private static ArrayList<Integer> unloadQueue;

    @Shadow
    private static Hashtable<Integer, WorldServer> worlds;

    /**
     * Queues the cubic dimensions that {@link CubeLoaderServer#doGC()} found empty, after every world ticked and only
     * if
     * they are still ready to unload: a player or cubes can arrive later in the same tick (a portal or a teleporter
     * ticked by another world), and Forge 1.7.10 does not check queued dimensions again. Unload requests from other
     * mods are left as they are.
     */
    @Inject(method = "unloadWorlds", at = @At("HEAD"))
    private static void queueEmptyCubicDimensions(Hashtable<Integer, long[]> worldTickTimes, CallbackInfo ci) {
        for (int id : CubeLoaderServer.takeDimensionsToUnload()) {
            WorldServer world = worlds.get(id);
            if (world == null || unloadQueue.contains(id)) continue;
            if (world.getChunkProvider() instanceof CubeProviderServer provider
                && provider.getCubeLoader() instanceof CubeLoaderServer loader
                && loader.readyToUnloadDimension()) {
                unloadQueue.add(id);
            }
        }
    }
}
