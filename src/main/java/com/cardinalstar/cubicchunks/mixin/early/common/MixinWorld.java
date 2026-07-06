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

import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.function.Predicate;

import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;

import net.minecraft.block.Block;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.profiler.Profiler;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.world.ChunkCoordIntPair;
import net.minecraft.world.EnumSkyBlock;
import net.minecraft.world.GameRules;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraft.world.WorldProvider;
import net.minecraft.world.WorldServer;
import net.minecraft.world.WorldServerMulti;
import net.minecraft.world.WorldSettings;
import net.minecraft.world.WorldType;
import net.minecraft.world.biome.BiomeGenBase;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.IChunkProvider;
import net.minecraft.world.storage.ISaveHandler;
import net.minecraft.world.storage.MapStorage;
import net.minecraft.world.storage.WorldInfo;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Implements;
import org.spongepowered.asm.mixin.Interface;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.cardinalstar.cubicchunks.CubicChunks;
import com.cardinalstar.cubicchunks.api.ICube;
import com.cardinalstar.cubicchunks.api.IntRange;
import com.cardinalstar.cubicchunks.api.world.ICubicWorldType;
import com.cardinalstar.cubicchunks.lighting.LightingManager;
import com.cardinalstar.cubicchunks.mixin.api.ICubicWorldInternal;
import com.cardinalstar.cubicchunks.network.PacketEncoderWorldHeight;
import com.cardinalstar.cubicchunks.network.PacketEncoderWorldHeight.PacketWorldHeight;
import com.cardinalstar.cubicchunks.util.Coords;
import com.cardinalstar.cubicchunks.util.CubePos;
import com.cardinalstar.cubicchunks.util.ReflectionUtil;
import com.cardinalstar.cubicchunks.world.CubicChunksSavedData;
import com.cardinalstar.cubicchunks.world.ICubicWorld;
import com.cardinalstar.cubicchunks.world.ICubicWorldProvider;
import com.cardinalstar.cubicchunks.world.cube.Cube;
import com.cardinalstar.cubicchunks.world.cube.ICubeProvider;
import com.cardinalstar.cubicchunks.world.cube.ICubeProviderInternal;
import com.google.common.collect.ImmutableList;
import com.gtnewhorizon.gtnhlib.blockpos.BlockPos;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;

/**
 * Contains implementation of {@link ICubicWorld} interface.
 */
@ParametersAreNonnullByDefault
@Mixin(World.class)
@Implements(@Interface(iface = ICubicWorld.class, prefix = "world$"))
public abstract class MixinWorld implements ICubicWorldInternal {

    // these have to be here because of mixin limitation, they are used by MixinWorldServer
    @Shadow
    public abstract ISaveHandler getSaveHandler();

    // TODO FIGURE OUT WHERE THESE GO
    @Shadow
    public abstract boolean checkChunksExist(int minX, int minY, int minZ, int maxX, int maxY, int maxZ);

    @Shadow
    public abstract boolean doChunksNearChunkExist(int x, int y, int z, int radius);

    @Shadow(remap = false)
    @Final
    public MapStorage perWorldStorage;

    @Shadow
    protected IChunkProvider chunkProvider;

    @Shadow
    @Final
    @Mutable
    public WorldProvider provider;

    @Shadow
    @Final
    public Random rand;
    @Shadow
    @Final
    public boolean isRemote;
    @Shadow
    @Final
    public Profiler theProfiler;
    @Shadow
    @Final
    @Mutable
    protected ISaveHandler saveHandler;
    @Shadow
    protected boolean findingSpawnPoint;
    @Shadow
    protected WorldInfo worldInfo;
    @Shadow
    protected int updateLCG;

    @Shadow
    protected abstract boolean chunkExists(int i, int i1);

    @Nullable
    protected LightingManager lightingManager;
    protected int minHeight = 0, maxHeight = 256, fakedMaxHeight = 0;
    private int minGenerationHeight = 0, maxGenerationHeight = 256;
    // @Shadow public abstract boolean isValid(BlockPos pos);

    @Shadow
    public abstract GameRules getGameRules();

    @Shadow
    public abstract boolean isRaining();

    @Shadow
    public abstract boolean isThundering();

    // @Shadow public abstract boolean isRainingAt(BlockPos position);

    // @Shadow public abstract DifficultyInstance getDifficultyForLocation(BlockPos pos);

    @Shadow
    public abstract int getPrecipitationHeight(int blockX, int blockY);

    @Shadow
    protected abstract void setActivePlayerChunksAndCheckLight();

    @Shadow
    public abstract boolean canLightningStrikeAt(int x, int y, int z);

    @Shadow
    public abstract boolean canBlockFreeze(int x, int y, int z, boolean byWater);

    @Shadow
    public abstract boolean setBlock(int x, int y, int z, Block blockIn);

    @Shadow
    public abstract boolean setBlock(int x, int y, int z, Block blockIn, int metaIn, int flags);

    @Shadow
    public abstract boolean isBlockFreezableNaturally(int x, int y, int z);

    // canSnowAt
    @Shadow
    public abstract boolean func_147478_e(int x, int y, int z, boolean checkLight);

    // @Shadow public abstract boolean isBlockLoaded(BlockPos pos);

    @Shadow
    public abstract BiomeGenBase getBiomeGenForCoords(int x, int z);

    // @Shadow public abstract boolean isBlockLoaded(BlockPos pos, boolean allowEmpty);

    @Shadow
    public abstract Chunk getChunkFromBlockCoords(int x, int z);

    @Shadow
    public abstract boolean canBlockSeeTheSky(int x, int y, int z);

    @Shadow
    public abstract void setLightValue(EnumSkyBlock type, int x, int y, int z, int lightValue);

    /*
     * This shadow method is used by MixinWorldServer, place in here for Bukkit compatibility.
     * As World#spawnEntity method is not getting overridden in CraftBukkit WorldServer class,
     * shadowing spawnEntity in WorldServer will break Bukkit compatibility.
     */
    @Shadow
    public abstract boolean spawnEntityInWorld(Entity entityIn);

    @Shadow
    public abstract IChunkProvider getChunkProvider();

    @Shadow
    protected abstract IChunkProvider createChunkProvider();

    @Redirect(
        method = "<init>(Lnet/minecraft/world/storage/ISaveHandler;Ljava/lang/String;Lnet/minecraft/world/WorldSettings;Lnet/minecraft/world/WorldProvider;Lnet/minecraft/profiler/Profiler;)V",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/World;createChunkProvider()Lnet/minecraft/world/chunk/IChunkProvider;"))
    public IChunkProvider noopCreateProvider(World instance) {
        // Done below manually
        return null;
    }

    @Inject(
        method = "<init>(Lnet/minecraft/world/storage/ISaveHandler;Ljava/lang/String;Lnet/minecraft/world/WorldSettings;Lnet/minecraft/world/WorldProvider;Lnet/minecraft/profiler/Profiler;)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/storage/WorldInfo;isInitialized()Z"))
    public void initWorld(ISaveHandler p_i45369_1_, String p_i45369_2_, WorldSettings p_i45369_3_,
        WorldProvider p_i45369_4_, Profiler p_i45369_5_, CallbackInfo ci) {

        // Some other world instantiation that we don't care about (fake dummy worlds, for instance)
        // noinspection ConstantValue
        if (!((Object) this instanceof WorldServer worldServer)) return;

        ((ICubicWorldInternal.Server) this).initCubicWorldServer();

        if (shouldSkipWorld(worldServer)) {
            CubicChunks.LOGGER.info(
                "Skipping world {} with type {} due to potential compatibility issues",
                this,
                this.worldInfo.getTerrainType());
            return;
        }

        CubicChunks.LOGGER.info("Initializing world {} with type {}", this, this.worldInfo.getTerrainType());

        IntRange generationRange = new IntRange(0, ((ICubicWorldProvider) this.provider).getOriginalActualHeight());

        WorldType type = this.worldInfo.getTerrainType();

        if (type instanceof ICubicWorldType && ((ICubicWorldType) type).hasCubicGeneratorForWorld(worldServer)) {
            generationRange = ((ICubicWorldType) type).calculateGenerationHeightRange(worldServer);
        }

        this.chunkProvider = createChunkProvider();

        CubicChunksSavedData savedData = CubicChunksSavedData.get(worldServer);

        this.initCubicWorld(new IntRange(savedData.minHeight, savedData.maxHeight), generationRange);

        this.lightingManager = new LightingManager((World) (Object) this);
    }

    protected void initCubicWorld(IntRange heightRange, IntRange generationRange) {
        // Set the world height boundaries to their highest and lowest values respectively
        this.minHeight = heightRange.getMin();
        this.maxHeight = heightRange.getMax();
        this.fakedMaxHeight = this.maxHeight;

        this.minGenerationHeight = generationRange.getMin();
        this.maxGenerationHeight = generationRange.getMax();
    }

    @Override
    public int getMinHeight() {
        return this.minHeight;
    }

    @Override
    public int getMaxHeight() {
        return this.maxHeight;
    }

    @Override
    public int getMinGenerationHeight() {
        return this.minGenerationHeight;
    }

    @Override
    public int getMaxGenerationHeight() {
        return this.maxGenerationHeight;
    }

    @Override
    public ICubeProviderInternal getCubeCache() {
        return (ICubeProviderInternal) this.chunkProvider;
    }

    @Override
    public LightingManager getLightingManager() {
        assert this.lightingManager != null;
        return this.lightingManager;
    }

    @Override
    public boolean testForCubes(int minBlockX, int minBlockY, int minBlockZ, int maxBlockX, int maxBlockY,
        int maxBlockZ, Predicate<ICube> test) {
        // convert block bounds to chunk bounds
        int minCubeX = minBlockX >> 4;
        int minCubeY = minBlockY >> 4;
        int minCubeZ = minBlockZ >> 4;
        int maxCubeX = maxBlockX >> 4;
        int maxCubeY = maxBlockY >> 4;
        int maxCubeZ = maxBlockZ >> 4;

        for (int cubeX = minCubeX; cubeX <= maxCubeX; cubeX++) {
            for (int cubeY = minCubeY; cubeY <= maxCubeY; cubeY++) {
                for (int cubeZ = minCubeZ; cubeZ <= maxCubeZ; cubeZ++) {
                    Cube cube = this.getCubeCache()
                        .getLoadedCube(cubeX, cubeY, cubeZ);
                    if (!test.test(cube)) {
                        return false;
                    }
                }
            }
        }

        return true;
    }

    @Override
    public boolean testForCubes(CubePos start, CubePos end, Predicate<? super ICube> cubeAllowed) {
        // convert block bounds to chunk bounds
        int minCubeX = start.getX();
        int minCubeY = start.getY();
        int minCubeZ = start.getZ();
        int maxCubeX = end.getX();
        int maxCubeY = end.getY();
        int maxCubeZ = end.getZ();

        for (int cubeX = minCubeX; cubeX <= maxCubeX; cubeX++) {
            for (int cubeY = minCubeY; cubeY <= maxCubeY; cubeY++) {
                for (int cubeZ = minCubeZ; cubeZ <= maxCubeZ; cubeZ++) {
                    Cube cube = this.getCubeCache()
                        .getLoadedCube(cubeX, cubeY, cubeZ);
                    if (!cubeAllowed.test(cube)) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    @Override
    public Cube getCubeFromCubeCoords(int cubeX, int cubeY, int cubeZ) {
        return this.getCubeCache()
            .getCube(cubeX, cubeY, cubeZ);
    }

    @Override
    public Cube getCubeFromBlockCoords(int blockX, int blockY, int blockZ) {
        return this.getCubeFromCubeCoords(blockToCube(blockX), blockToCube(blockY), blockToCube(blockZ));
    }

    @Override
    public Cube getCubeFromBlockCoords(BlockPos pos) {
        return this.getCubeFromBlockCoords(blockToCube(pos.getX()), blockToCube(pos.getY()), blockToCube(pos.getZ()));
    }

    @Override
    public int getEffectiveHeight(int blockX, int blockZ) {
        return this.chunkProvider.provideChunk(blockToCube(blockX), blockToCube(blockZ))
            .getHeightValue(blockToLocal(blockX), blockToLocal(blockZ));
    }

    // suppress mixin warning when running with -Dmixin.checks.interfaces=true
    @Override
    public void tickCubicWorld() {
        // pretend this method doesn't exist
        throw new NoSuchMethodError(
            "World.tickCubicWorld: Classes extending World need to implement tickCubicWorld in CubicChunks");
    }

    @Override
    public void fakeWorldHeight(int height) {
        this.fakedMaxHeight = height;
    }

    /**
     * Some mod's world generation will try to do their work over the whole world height.
     * This allows to fake the world height for them.
     *
     * @return world height
     * @author Barteks2x
     * @reason Optionally return fake height
     */
    @Overwrite
    public int getHeight() {
        if (fakedMaxHeight != 0) {
            return fakedMaxHeight;
        }
        return this.provider.getHeight();
    }

    @Override
    public void setHeightBounds(int minHeight, int maxHeight) {
        if (minHeight >= this.minGenerationHeight && maxHeight <= this.maxHeight) return;

        this.minHeight = minHeight;
        this.maxHeight = maxHeight;

        if (!this.isRemote) {
            CubicChunksSavedData savedData = CubicChunksSavedData.get((World) (Object) this);
            savedData.minHeight = minHeight;
            savedData.maxHeight = maxHeight;

            PacketWorldHeight packet = PacketEncoderWorldHeight.create(minHeight, maxHeight);

            for (EntityPlayer player : this.playerEntities) {
                packet.sendToPlayer((EntityPlayerMP) player);
            }
        }
    }

    @Inject(method = "updateLightByType", at = @At("HEAD"), cancellable = true)
    private void updateLightByType(EnumSkyBlock lightType, int x, int y, int z, CallbackInfoReturnable<Boolean> ci) {
        ci.setReturnValue(getLightingManager() != null && getLightingManager().checkLightFor(lightType, x, y, z));
    }

    /**
     * @param x                x block position
     * @param y                y block position
     * @param z                z block position
     * @param unusedTileEntity tile entity instance, unused
     * @param ci               callback info
     *
     * @author Foghrye4
     * @reason Original {@link World#markTileEntityChunkModified}
     *         called by TileEntities whenever they need to force Chunk to save
     *         valuable info they changed. Because now we store TileEntities in
     *         Cubes instead of Chunks, it will be quite reasonable to force
     *         Cubes to save themselves.
     */
    @Inject(method = "markTileEntityChunkModified", at = @At("HEAD"), cancellable = true)
    private void onMarkChunkDirty(int x, int y, int z, TileEntity unusedTileEntity, CallbackInfo ci) {
        Cube cube = this.getCubeCache()
            .getLoadedCube(CubePos.fromBlockCoords(x, y, z));
        if (cube != null) {
            cube.markDirty();
        }
        ci.cancel();
    }

    @Shadow
    public List<EntityPlayer> playerEntities;

    @Inject(method = "getTopSolidOrLiquidBlock", at = @At("HEAD"), cancellable = true)
    private void getTopSolidOrLiquidBlockCubicChunks(int x, int z, CallbackInfoReturnable<Integer> cir) {
        Chunk chunk = this.getChunkFromBlockCoords(x, z);
        int currentY = getPrecipitationHeight(x, z);
        int minY = currentY - 64;
        while (currentY >= minY) {
            int nextY = currentY - 1;
            Block block = chunk.getBlock(Coords.blockToLocal(x), nextY, Coords.blockToLocal(z));

            if (block.getMaterial()
                .blocksMovement() && !block.isLeaves((IBlockAccess) this, x, nextY, z)
                && !block.isFoliage((IBlockAccess) this, x, nextY, z)) {
                break;
            }
            currentY = nextY;
        }
        cir.setReturnValue(currentY);
    }

    @Override
    public boolean isBlockColumnLoaded(int x, int y, int z) {
        return this.chunkExists(blockToCube(x), blockToCube(z));
    }

    @Override
    public boolean cubeExists(int x, int y, int z) {
        return ((ICubeProvider) this.chunkProvider).cubeExists(x, y, z);
    }

    @ModifyConstant(method = "getCollidingBoundingBoxes", constant = @Constant(intValue = 64), require = 1)
    private int collidingBoxFix1(int constant, @Local(argsOnly = true) AxisAlignedBB box) {
        return (int) ((box.maxY - box.minY) / 2 + box.minY);
    }

    @ModifyConstant(method = "func_147461_a", constant = @Constant(intValue = 64), require = 1)
    private int collidingBoxFix2(int constant, @Local(argsOnly = true) AxisAlignedBB box) {
        return (int) ((box.maxY - box.minY) / 2 + box.minY);
    }

    private static final List<Class<?>> allowedServerWorldClasses = ImmutableList.copyOf(
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

    private boolean shouldSkipWorld(World world) {
        return !allowedServerWorldClasses.contains(world.getClass());
    }

    @WrapOperation(
        method = "setActivePlayerChunksAndCheckLight",
        at = @At(value = "INVOKE", target = "Ljava/util/Set;add(Ljava/lang/Object;)Z"))
    public boolean skipUnloadedChunks(Set<ChunkCoordIntPair> instance, Object coord2, Operation<Boolean> original) {
        ChunkCoordIntPair coord = (ChunkCoordIntPair) coord2;

        if (!this.chunkExists(coord.chunkXPos, coord.chunkZPos)) return false;

        return instance.add(coord);
    }
}
