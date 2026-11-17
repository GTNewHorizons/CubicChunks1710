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
package com.cardinalstar.cubicchunks.event.handlers;

import java.io.IOException;
import java.util.List;

import javax.annotation.ParametersAreNonnullByDefault;

import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraft.world.WorldServerMulti;
import net.minecraft.world.chunk.IChunkProvider;
import net.minecraft.world.gen.ChunkProviderServer;
import net.minecraftforge.event.world.WorldEvent;

import com.cardinalstar.cubicchunks.CubicChunks;
import com.cardinalstar.cubicchunks.mixin.api.ICubicWorldInternal;
import com.cardinalstar.cubicchunks.server.chunkio.ICubeLoader;
import com.cardinalstar.cubicchunks.util.ReflectionUtil;
import com.cardinalstar.cubicchunks.world.ICubicWorld;
import com.cardinalstar.cubicchunks.world.cube.ICubeProviderInternal;
import com.google.common.collect.ImmutableList;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.PlayerEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.relauncher.Side;

@ParametersAreNonnullByDefault
public class CommonEventHandler {

    @SubscribeEvent
    public void onWorldServerTick(TickEvent.WorldTickEvent evt) {
        WorldServer world = (WorldServer) evt.world;
        // Forge (at least version 11.14.3.1521) doesn't call this event for client world.
        if (evt.phase == TickEvent.Phase.END && evt.side == Side.SERVER) {
            ((ICubicWorldInternal) world).tickCubicWorld();
        }
    }

    @SubscribeEvent
    public void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        // VanillaNetworkHandler.removeBedrockPlayer((EntityPlayerMP) event.player);
    }

    @SuppressWarnings("unchecked")
    private final List<Class<?>> allowedServerWorldClasses = ImmutableList.copyOf(
        new Class[] { WorldServer.class, WorldServerMulti.class,
            // non-existing classes will be Objects
            ReflectionUtil.getClassOrDefault("WorldServerOF", Object.class), // OptiFine's WorldServer, no package
            ReflectionUtil.getClassOrDefault("WorldServerMultiOF", Object.class), // OptiFine's WorldServerMulti, no
                                                                                  // package
            ReflectionUtil.getClassOrDefault("net.optifine.override.WorldServerOF", Object.class), // OptiFine's
                                                                                                   // WorldServer
            ReflectionUtil.getClassOrDefault("net.optifine.override.WorldServerMultiOF", Object.class), // OptiFine's
                                                                                                        // WorldServerMulti
            ReflectionUtil.getClassOrDefault("com.forgeessentials.multiworld.WorldServerMultiworld", Object.class) // ForgeEssentials
                                                                                                                   // world
        });

    @SuppressWarnings("unchecked")
    private final List<Class<? extends IChunkProvider>> allowedServerChunkProviderClasses = ImmutableList
        .copyOf(new Class[] { ChunkProviderServer.class });

    private boolean shouldSkipWorld(World world) {
        return !allowedServerWorldClasses.contains(world.getClass()) || !allowedServerChunkProviderClasses.contains(
            world.getChunkProvider()
                .getClass());
    }

    @SubscribeEvent
    public void onWorldUnload(WorldEvent.Unload event) {
        if (event.world.isRemote) {
            return;
        }

        ICubicWorld world = (ICubicWorld) event.world;

        ICubeLoader loader = ((ICubeProviderInternal.Server) world.getCubeCache()).getCubeLoader();
        try {
            loader.close();
        } catch (IOException e) {
            CubicChunks.LOGGER.catching(e);
        }
    }
}
