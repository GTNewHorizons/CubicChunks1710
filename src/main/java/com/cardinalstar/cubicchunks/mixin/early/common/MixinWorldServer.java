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

import static com.cardinalstar.cubicchunks.util.ReflectionUtil.cast;

import java.io.File;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import javax.annotation.ParametersAreNonnullByDefault;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityTracker;
import net.minecraft.server.management.PlayerManager;
import net.minecraft.world.ChunkCoordIntPair;
import net.minecraft.world.NextTickListEntry;
import net.minecraft.world.SpawnerAnimals;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.IChunkProvider;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;
import net.minecraft.world.chunk.storage.IChunkLoader;
import net.minecraft.world.gen.ChunkProviderServer;
import net.minecraftforge.common.ForgeChunkManager;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Implements;
import org.spongepowered.asm.mixin.Interface;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.cardinalstar.cubicchunks.CubicChunks;
import com.cardinalstar.cubicchunks.api.IColumn;
import com.cardinalstar.cubicchunks.api.ICube;
import com.cardinalstar.cubicchunks.api.ICubicWorldServer;
import com.cardinalstar.cubicchunks.api.XYZMap;
import com.cardinalstar.cubicchunks.api.XZMap;
import com.cardinalstar.cubicchunks.mixin.api.ICubicWorldInternal;
import com.cardinalstar.cubicchunks.server.CubeProviderServer;
import com.cardinalstar.cubicchunks.server.CubicPlayerManager;
import com.cardinalstar.cubicchunks.server.SpawnCubes;
import com.cardinalstar.cubicchunks.util.CubePos;
import com.cardinalstar.cubicchunks.util.world.CubeSplitTicks;
import com.cardinalstar.cubicchunks.world.CubeSpawnerAnimals;
import com.cardinalstar.cubicchunks.world.ICubicWorldProvider;
import com.cardinalstar.cubicchunks.world.ISpawnerAnimals;
import com.cardinalstar.cubicchunks.world.chunkloader.CubicChunkManager;
import com.cardinalstar.cubicchunks.world.savedata.WorldFormatSavedData;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

/**
 * Implementation of {@link ICubicWorldServer} interface.
 */
@ParametersAreNonnullByDefault
@Mixin(WorldServer.class)
@Implements(@Interface(iface = ICubicWorldServer.class, prefix = "world$"))
public abstract class MixinWorldServer extends MixinWorld implements ICubicWorldInternal.Server {

    @Shadow
    @Mutable
    @Final
    private PlayerManager thePlayerManager;
    @Shadow
    @Mutable
    @Final
    private SpawnerAnimals animalSpawner;
    @Shadow
    @Mutable
    @Final
    private EntityTracker theEntityTracker;
    @Shadow
    public boolean levelSaving; // TODO DO WE NEED TO NEGATE THIS?

    @Unique
    private Map<Chunk, Set<ICube>> forcedChunksCubes;
    @Unique
    private XYZMap<ICube> forcedCubes;
    @Unique
    private XZMap<IColumn> forcedColumns;
    @Unique
    private SpawnCubes spawnArea;
    @Unique
    private boolean runningCompatibilityGenerator;
    // private VanillaNetworkHandler vanillaNetworkHandler;

    @Shadow
    public abstract boolean addWeatherEffect(Entity entityIn);

    @Shadow
    public abstract PlayerManager getPlayerManager();

    @Shadow
    public ChunkProviderServer theChunkProviderServer;
    @Unique
    private CubeSplitTicks cubeTicks;

    @Override
    public void initCubicWorldServer() {
        this.forcedChunksCubes = new HashMap<>();
        this.forcedCubes = new XYZMap<>();
        this.forcedColumns = new XZMap<>();
        cubeTicks = new CubeSplitTicks();
    }

    @Redirect(
        method = "<init>", // constructor
        at = @At(value = "NEW", target = "net/minecraft/world/SpawnerAnimals"))
    private SpawnerAnimals redirectSpawnerAnimals() {
        SpawnerAnimals animalsSpawner = new SpawnerAnimals();
        ISpawnerAnimals spawner = new CubeSpawnerAnimals();
        ISpawnerAnimals.Handler spawnHandler = cast(animalsSpawner);
        spawnHandler.setEntitySpawner(spawner);
        return animalsSpawner;
    }

    @Redirect(
        method = "<init>",
        at = @At(
            value = "NEW",
            target = "(Lnet/minecraft/world/WorldServer;)Lnet/minecraft/server/management/PlayerManager;"))
    private PlayerManager redirectPlayerManagerInit(WorldServer server) {
        return new CubicPlayerManager(server);
    }

    @Redirect(
        method = "createChunkProvider",
        at = @At(value = "NEW", target = "net/minecraft/world/gen/ChunkProviderServer"))
    private ChunkProviderServer redirectChunkProviderServer(WorldServer world, IChunkLoader chunkLoader,
        IChunkProvider chunkGenerator) {
        return new CubeProviderServer(world, chunkLoader, ((ICubicWorldProvider) world.provider).createCubeGenerator());
    }

    @Inject(method = "getChunkSaveLocation", at = @At("HEAD"), cancellable = true, remap = false)
    private void redirectChunkSaveLocation(CallbackInfoReturnable<File> cir) {
        if (this.theChunkProviderServer == null) {
            WorldFormatSavedData format = WorldFormatSavedData.get((WorldServer) (Object) this);

            // noinspection DataFlowIssue
            cir.setReturnValue(
                format.getFormat()
                    .getWorldSaveDirectory(this.saveHandler, (WorldServer) (Object) this)
                    .toFile());
        }
    }

    @WrapOperation(
        method = { "scheduleBlockUpdateWithPriority", "func_147446_b" },
        at = @At(value = "INVOKE", target = "Ljava/util/TreeSet;add(Ljava/lang/Object;)Z", remap = false))
    public boolean redirectAdd(TreeSet<NextTickListEntry> instance, Object o, Operation<Boolean> original) {
        cubeTicks.add((NextTickListEntry) o);
        return original.call(instance, o);
    }

    @WrapOperation(
        method = "tickUpdates",
        at = @At(value = "INVOKE", target = "Ljava/util/TreeSet;remove(Ljava/lang/Object;)Z", remap = false))
    public boolean redirectRemove(TreeSet<NextTickListEntry> instance, Object o, Operation<Boolean> original) {
        cubeTicks.remove((NextTickListEntry) o);
        return original.call(instance, o);
    }

    @Override
    public void setSpawnArea(SpawnCubes spawn) {
        this.spawnArea = spawn;
    }

    @Override
    public SpawnCubes getSpawnArea() {
        return spawnArea;
    }

    @Override
    public CubeSplitTicks getScheduledTicks() {
        return cubeTicks;
    }

    @Override
    public void tickCubicWorld() {
        getLightingManager().onTick();
        if (this.spawnArea != null) {
            this.spawnArea.update((World) (Object) this);
        }
    }

    @Override
    public CubeProviderServer getCubeCache() {
        return (CubeProviderServer) this.chunkProvider;
    }

    @Override
    public void removeForcedCube(ICube cube) {
        if (!forcedChunksCubes.get(cube.getColumn())
            .remove(cube)) {
            CubicChunks.LOGGER.error("Trying to remove forced cube " + cube.getCoords() + ", but it's not forced!");
        }
        forcedCubes.remove(cube);
        if (forcedChunksCubes.get(cube.getColumn())
            .isEmpty()) {
            forcedChunksCubes.remove(cube.getColumn());
            forcedColumns.remove(cube.getColumn());
        }
    }

    @Override
    public void addForcedCube(ICube cube) {
        if (!forcedChunksCubes.computeIfAbsent(cube.getColumn(), chunk -> new HashSet<>())
            .add(cube)) {
            CubicChunks.LOGGER.error("Trying to add forced cube " + cube.getCoords() + ", but it's already forced!");
        }
        forcedCubes.put(cube);
        forcedColumns.put(cube.getColumn());
    }

    @Override
    public XYZMap<ICube> getForcedCubes() {
        return forcedCubes;
    }

    @Override
    public XZMap<IColumn> getForcedColumns() {
        return forcedColumns;
    }

    @Override
    public void unloadOldCubes() {
        this.getCubeCache()
            .getCubeLoader()
            .doGC();
    }

    /**
     * CubicChunks equivalent of {@link ForgeChunkManager#forceChunk(ForgeChunkManager.Ticket, ChunkCoordIntPair)}.
     *
     * Can accept tickets from different worlds.
     */
    @Override
    public void forceChunk(ForgeChunkManager.Ticket ticket, CubePos cube) {
        CubicChunkManager.forceChunk(ticket, cube);
    }

    /**
     * CubicChunks equivalent of {@link ForgeChunkManager#reorderChunk(ForgeChunkManager.Ticket, ChunkCoordIntPair)}
     *
     * Can accept tickets from different worlds.
     */
    @Override
    public void reorderChunk(ForgeChunkManager.Ticket ticket, CubePos chunk) {
        CubicChunkManager.reorderChunk(ticket, chunk);
    }

    /**
     * CubicChunks equivalent of {@link ForgeChunkManager#unforceChunk(ForgeChunkManager.Ticket, ChunkCoordIntPair)}
     *
     * Can accept tickets from different worlds.
     */
    @Override
    public void unforceChunk(ForgeChunkManager.Ticket ticket, CubePos chunk) {
        CubicChunkManager.unforceChunk(ticket, chunk);
    }

    @WrapOperation(
        method = "func_147456_g",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/chunk/Chunk;getBlockStorageArray()[Lnet/minecraft/world/chunk/storage/ExtendedBlockStorage;"))
    private ExtendedBlockStorage[] getTickableStorages(Chunk column, Operation<ExtendedBlockStorage[]> original) {
        return ((IColumn) column).getTickableStorages();
    }

    /// Immediate block updates rarely work well in CC. Vanilla expects there to be a hard limit to the number of steps
    /// something can take, but CC removes many of those limits so it's better to just disable the feature for now.
    @Redirect(
        method = "scheduleBlockUpdateWithPriority",
        at = @At(value = "FIELD", target = "Lnet/minecraft/world/WorldServer;scheduledUpdatesAreImmediate:Z"))
    private boolean disableImmediateBlockUpdates(WorldServer instance) {
        return false;
    }
}
