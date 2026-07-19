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

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import javax.annotation.ParametersAreNonnullByDefault;

import net.minecraft.world.ChunkCoordIntPair;
import net.minecraft.world.World;
import net.minecraftforge.common.ForgeChunkManager;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.cardinalstar.cubicchunks.util.CubePos;
import com.cardinalstar.cubicchunks.world.chunkloader.CubicChunkManager;
import com.cardinalstar.cubicchunks.world.core.ICubicTicketInternal;

import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;

@ParametersAreNonnullByDefault
@Mixin(value = ForgeChunkManager.Ticket.class, remap = false)
public abstract class MixinTicket implements ICubicTicketInternal {

    private LinkedHashSet<CubePos> forcedCubes = new LinkedHashSet<>();
    private Map<ChunkCoordIntPair, IntSet> cubePosMap = new HashMap<>();
    private int entityChunkY;
    private int cubeDepth;

    @Override
    @Accessor
    public abstract void setEntityChunkX(int chunkX);

    @Override
    @Accessor
    public abstract void setEntityChunkZ(int chunkZ);

    @Override
    @Accessor
    public abstract int getEntityChunkX();

    @Override
    @Accessor
    public abstract int getEntityChunkZ();

    @Override
    public int getEntityChunkY() {
        return entityChunkY;
    }

    @Override
    public void setEntityChunkY(int cubeY) {
        this.entityChunkY = cubeY;
    }

    @Inject(
        method = "<init>(Ljava/lang/String;Lnet/minecraftforge/common/ForgeChunkManager$Type;Lnet/minecraft/world/World;)V",
        at = @At("RETURN"),
        remap = false)
    private void onConstruct(String modId, ForgeChunkManager.Type type, World world, CallbackInfo cbi) {
        this.cubeDepth = CubicChunkManager.getCubeDepthFor(modId);
    }

    @Override
    public void addRequestedCube(CubePos pos) {
        cubePosMap.computeIfAbsent(pos.chunkPos(), chunkPos -> new IntOpenHashSet(32))
            .add(pos.getY());
    }

    @Override
    public void removeRequestedCube(CubePos pos) {
        IntSet set = cubePosMap.get(pos.chunkPos());
        if (set != null) {
            set.remove(pos.getY());
            if (set.isEmpty()) {
                cubePosMap.remove(pos.chunkPos());
            }
        }
    }

    @Override
    public void setForcedChunkCubes(ChunkCoordIntPair location, IntSet yCoords) {
        cubePosMap.put(location, yCoords);
    }

    @Override
    public void clearForcedChunkCubes(ChunkCoordIntPair location) {
        cubePosMap.remove(location);
    }

    @Override
    public Map<ChunkCoordIntPair, IntSet> getAllForcedChunkCubes() {
        return Collections.unmodifiableMap(cubePosMap);
    }

    @Override
    public void setAllForcedChunkCubes(Map<ChunkCoordIntPair, IntSet> cubePosMap) {
        this.cubePosMap = cubePosMap;
    }

    @Override
    public int getMaxCubeDepth() {
        return cubeDepth;
    }

    @Override
    public Set<CubePos> requestedCubes() {
        return forcedCubes;
    }
}
