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
package com.cardinalstar.cubicchunks.world;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import javax.annotation.Nonnull;
import javax.annotation.ParametersAreNonnullByDefault;

import net.minecraft.block.Block;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EnumCreatureType;
import net.minecraft.entity.IEntityLivingData;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.ChunkCoordinates;
import net.minecraft.world.ChunkCoordIntPair;
import net.minecraft.world.SpawnerAnimals;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraft.world.biome.BiomeGenBase;
import net.minecraftforge.event.ForgeEventFactory;

import org.joml.Vector3ic;

import com.cardinalstar.cubicchunks.api.CCAPI;
import com.cardinalstar.cubicchunks.api.ICube;
import com.cardinalstar.cubicchunks.api.util.Box;
import com.cardinalstar.cubicchunks.server.CubicPlayerManager;
import com.cardinalstar.cubicchunks.util.CubePos;
import com.cardinalstar.cubicchunks.util.HashSet3D;
import com.cardinalstar.cubicchunks.util.MathUtil;
import com.cardinalstar.cubicchunks.world.cube.Cube;
import com.gtnewhorizon.gtnhlib.blockpos.BlockPos;

import cpw.mods.fml.common.eventhandler.Event;

@ParametersAreNonnullByDefault
public class CubeSpawnerAnimals implements ISpawnerAnimals {

    private static final int CUBES_PER_CHUNK = 16;
    private static final int MOB_COUNT_DIV = (int) Math.pow(17.0D, 2.0D) * CUBES_PER_CHUNK;
    private static final int SPAWN_RADIUS = 8;
    private static final double MIN_SPAWN_DISTANCE_SQ = 24.0D * 24.0D;
    private static final double MAX_SPAWN_DISTANCE = 128.0D;

    @Nonnull
    private final HashSet3D cubesForSpawn = new HashSet3D();

    @Override
    public int findChunksForSpawning(WorldServer world, boolean hostileEnable, boolean peacefulEnable,
        boolean spawnOnSetTickRate) {
        if (!hostileEnable && !peacefulEnable) {
            return 0;
        }
        this.cubesForSpawn.clear();

        int cubeCount = addEligibleCubes(world, this.cubesForSpawn);
        int totalSpawnCount = 0;

        for (EnumCreatureType mobType : EnumCreatureType.values()) {
            if (!shouldSpawnType(mobType, hostileEnable, peacefulEnable, spawnOnSetTickRate)) {
                continue;
            }

            int worldEntityCount = world.countEntities(mobType, true);
            int maxEntityCount = mobType.getMaxNumberOfCreature() * cubeCount / MOB_COUNT_DIV;

            if (worldEntityCount > maxEntityCount) {
                continue;
            }

            List<BlockPos> spawnPositions = selectSpawnPositions(world, this.cubesForSpawn);
            Collections.shuffle(spawnPositions, world.rand);
            totalSpawnCount += spawnCreatureTypeAtPositions(mobType, world, spawnPositions);
        }

        return totalSpawnCount;
    }

    private int addEligibleCubes(WorldServer world, HashSet3D possibleCubes) {
        int cubeCount = 0;

        HashSet3D checkedCubes = new HashSet3D();

        for (EntityPlayer player : world.playerEntities) {
            CubePos center = CubePos.fromEntity(player);

            for (Vector3ic v : new Box(center.getX(), center.getY(), center.getZ(), SPAWN_RADIUS)) {
                if (checkedCubes.add(v.x(), v.y(), v.z())) cubeCount++;

                boolean isEdge = Math.abs(v.x() - center.getX()) == SPAWN_RADIUS
                    || Math.abs(v.y() - center.getY()) == SPAWN_RADIUS
                    || Math.abs(v.z() - center.getZ()) == SPAWN_RADIUS;
                boolean valid = !isEdge
                    && ((CubicPlayerManager) world.getPlayerManager()).isCubeWatched(v.x(), v.y(), v.z());

                if (valid) {
                    ICube cube = CCAPI.getLoadedCube(world, v.x(), v.y(), v.z());
                    if (cube != null && !cube.isEmpty()) {
                        possibleCubes.add(v.x(), v.y(), v.z());
                    }
                }
            }
        }

        return cubeCount;
    }

    private List<BlockPos> selectSpawnPositions(WorldServer world, HashSet3D possibleCubes) {
        Map<Long, CubeSelection> columns = new HashMap<>();

        possibleCubes.forEach((cubeX, cubeY, cubeZ) -> {
            long columnKey = ChunkCoordIntPair.chunkXZ2Int(cubeX, cubeZ);
            CubeSelection selection = columns.computeIfAbsent(
                columnKey,
                ignored -> new CubeSelection(
                    cubeX * Cube.SIZE + world.rand.nextInt(Cube.SIZE),
                    cubeZ * Cube.SIZE + world.rand.nextInt(Cube.SIZE)));
            int blockY = cubeY * Cube.SIZE + world.rand.nextInt(Cube.SIZE);
            if (world.getClosestPlayer(selection.blockX + 0.5D, blockY, selection.blockZ + 0.5D, MAX_SPAWN_DISTANCE)
                == null) {
                return;
            }

            selection.count++;
            if (world.rand.nextInt(selection.count) == 0) {
                selection.selected = new BlockPos(selection.blockX, blockY, selection.blockZ);
            }
        });

        List<BlockPos> selected = new ArrayList<>(columns.size());
        for (CubeSelection selection : columns.values()) {
            if (selection.selected != null) selected.add(selection.selected);
        }
        return selected;
    }

    private int spawnCreatureTypeAtPositions(EnumCreatureType mobType, WorldServer world,
        List<BlockPos> spawnPositions) {
        ChunkCoordinates spawnPoint = world.getSpawnPoint();
        int posX, posY, posZ;

        int totalSpawned = 0;

        nextChunk: for (BlockPos blockpos : spawnPositions) {
            Block block = world.getBlock(blockpos.x, blockpos.y, blockpos.z);

            if (block.isNormalCube() || !(block.getMaterial() == mobType.getCreatureMaterial())) {
                continue;
            }
            int blockX = blockpos.getX();
            int blockY = blockpos.getY();
            int blockZ = blockpos.getZ();

            int currentPackSize = 0;

            for (int spawnAttempt = 0; spawnAttempt < 3; ++spawnAttempt) {
                int entityBlockX = blockX;
                int entityY = blockY;
                int entityBlockZ = blockZ;
                int searchRadius = 6;
                BiomeGenBase.SpawnListEntry biomeMobs = null;
                IEntityLivingData entityData = null;

                Random rand = world.rand;
                for (int attempt = 0; attempt < 4; ++attempt) {
                    entityBlockX += rand.nextInt(searchRadius) - rand.nextInt(searchRadius);
                    entityY += rand.nextInt(1) - rand.nextInt(1);
                    entityBlockZ += rand.nextInt(searchRadius) - rand.nextInt(searchRadius);
                    posX = entityBlockX;
                    posY = entityY;
                    posZ = entityBlockZ;

                    if (!SpawnerAnimals.canCreatureTypeSpawnAtLocation(mobType, world, posX, posY, posZ)) {
                        continue;
                    }

                    float entityX = (float) entityBlockX + 0.5F;
                    float entityZ = (float) entityBlockZ + 0.5F;

                    EntityPlayer closestPlayer = world.getClosestPlayer(entityX, entityY, entityZ, MAX_SPAWN_DISTANCE);
                    if (closestPlayer == null) {
                        continue;
                    }
                    double deltaX = closestPlayer.posX - entityX;
                    double deltaY = closestPlayer.posY - entityY;
                    double deltaZ = closestPlayer.posZ - entityZ;
                    if (deltaX * deltaX + deltaY * deltaY + deltaZ * deltaZ < MIN_SPAWN_DISTANCE_SQ
                        || MathUtil.distanceSq(entityX, entityY, entityZ, spawnPoint) < MIN_SPAWN_DISTANCE_SQ) {
                        continue;
                    }
                    if (biomeMobs == null) {
                        biomeMobs = world.spawnRandomCreature(mobType, posX, posY, posZ);

                        if (biomeMobs == null) {
                            break;
                        }
                    }

                    EntityLiving toSpawn;

                    try {
                        toSpawn = biomeMobs.entityClass.getConstructor(new Class[] { World.class })
                            .newInstance(world);
                    } catch (Exception exception) {
                        exception.printStackTrace();
                        // TODO: throw when entity creation fails
                        return totalSpawned;
                    }

                    toSpawn.setLocationAndAngles(entityX, entityY, entityZ, rand.nextFloat() * 360.0F, 0.0F);

                    Event.Result canSpawn = ForgeEventFactory.canEntitySpawn(toSpawn, world, entityX, entityY, entityZ);
                    if (canSpawn == Event.Result.ALLOW || (canSpawn == Event.Result.DEFAULT && toSpawn.getCanSpawnHere()
                        && toSpawn.getCanSpawnHere())) {
                        if (!ForgeEventFactory.doSpecialSpawn(toSpawn, world, entityX, entityY, entityZ)) {
                            entityData = toSpawn.onSpawnWithEgg(entityData);
                        }

                        if (toSpawn.getCanSpawnHere()) {
                            if (world.spawnEntityInWorld(toSpawn)) {
                                currentPackSize++;
                                totalSpawned++;
                            } else {
                                toSpawn.setDead();
                            }
                        } else {
                            toSpawn.setDead();
                        }

                        if (currentPackSize >= ForgeEventFactory.getMaxSpawnPackSize(toSpawn)) {
                            continue nextChunk;
                        }
                    }
                }
            }
        }
        return totalSpawned;
    }

    private static final class CubeSelection {

        private final int blockX;
        private final int blockZ;
        private int count;
        private BlockPos selected;

        private CubeSelection(int blockX, int blockZ) {
            this.blockX = blockX;
            this.blockZ = blockZ;
        }
    }

    private static boolean shouldSpawnType(EnumCreatureType type, boolean hostile, boolean peaceful,
        boolean spawnOnSetTickRate) {
        return !((type.getPeacefulCreature() && !peaceful) || (!type.getPeacefulCreature() && !hostile)
            || (type.getAnimal() && !spawnOnSetTickRate));
    }

}
