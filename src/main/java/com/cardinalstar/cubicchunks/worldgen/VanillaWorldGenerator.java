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
package com.cardinalstar.cubicchunks.worldgen;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Random;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;

import net.minecraft.entity.EnumCreatureType;
import net.minecraft.init.Blocks;
import net.minecraft.world.ChunkCoordIntPair;
import net.minecraft.world.ChunkPosition;
import net.minecraft.world.World;
import net.minecraft.world.biome.BiomeGenBase;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.IChunkProvider;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;

import org.joml.Vector3i;
import org.joml.Vector3ic;

import com.cardinalstar.cubicchunks.CubicChunks;
import com.cardinalstar.cubicchunks.api.ICube;
import com.cardinalstar.cubicchunks.api.util.Box;
import com.cardinalstar.cubicchunks.api.world.Precalculable;
import com.cardinalstar.cubicchunks.api.worldgen.GenerationResult;
import com.cardinalstar.cubicchunks.api.worldgen.IWorldGenerator;
import com.cardinalstar.cubicchunks.api.worldgen.decoration.IWorldDecorator;
import com.cardinalstar.cubicchunks.mixin.api.ICubicWorldInternal;
import com.cardinalstar.cubicchunks.mixin.early.common.IGameRegistry;
import com.cardinalstar.cubicchunks.server.CubeProviderServer;
import com.cardinalstar.cubicchunks.server.chunkio.CubeInitLevel;
import com.cardinalstar.cubicchunks.server.chunkio.ICubeLoader;
import com.cardinalstar.cubicchunks.server.chunkio.IPreloadFailureDelegate;
import com.cardinalstar.cubicchunks.util.Coords;
import com.cardinalstar.cubicchunks.util.CubePos;
import com.cardinalstar.cubicchunks.world.CubicChunksSavedData;
import com.cardinalstar.cubicchunks.world.ICubicWorld;
import com.cardinalstar.cubicchunks.world.api.ICubeProviderServer.Requirement;
import com.cardinalstar.cubicchunks.world.core.IColumnInternal;
import com.cardinalstar.cubicchunks.world.cube.Cube;
import com.cardinalstar.cubicchunks.world.cube.blockview.ChunkBlockView;
import com.cardinalstar.cubicchunks.world.cube.blockview.IBlockView;
import com.cardinalstar.cubicchunks.world.cube.blockview.UniformBlockView;
import com.gtnewhorizon.gtnhlib.util.data.BlockMeta;
import com.gtnewhorizon.gtnhlib.util.data.ImmutableBlockMeta;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;

/**
 * A cube generator that tries to mirror vanilla world generation. Cubes in the normal world range will be copied from a
 * vanilla chunk generator, cubes above and below that will be filled with the most common block in the
 * topmost/bottommost layers.
 */
@ParametersAreNonnullByDefault
public class VanillaWorldGenerator implements IWorldGenerator, IPreloadFailureDelegate {

    @Nonnull
    private final IChunkProvider vanilla;
    @Nonnull
    private final World world;
    @Nonnull
    private final IWorldDecorator decorator;

    private final int vanillaGenerationHeight;
    private final int vanillaGenerationHeightCubes;

    private FillerInfo fillerInfo;

    /**
     * Create a new VanillaCompatibilityGenerator
     *
     * @param vanilla The vanilla generator to mirror
     * @param world   The world in which cubes are being generated
     */
    public VanillaWorldGenerator(IChunkProvider vanilla, World world, IWorldDecorator decorator) {
        this.fillerInfo = CubicChunksSavedData.get(world)
            .getFillerInfo();

        this.vanilla = vanilla;
        this.world = world;
        this.decorator = decorator;

        vanillaGenerationHeight = ((ICubicWorld) this.world).getMaxGenerationHeight();
        vanillaGenerationHeightCubes = Coords.blockCeilToCube(vanillaGenerationHeight);
    }

    private ICubeLoader getCubeLoader() {
        return getCubeProviderServer().getCubeLoader();
    }

    private CubeProviderServer getCubeProviderServer() {
        return ((ICubicWorldInternal.Server) world).getCubeCache();
    }

    private ImmutableBlockMeta getBottomFillerInfo() {
        if (fillerInfo.bottomFiller != null) return fillerInfo.bottomFiller;

        Chunk chunk = vanilla.provideChunk(0, 0);

        ((IColumnInternal) chunk).setColumn(false);

        fillerInfo.bottomFiller = analyzeBottomFiller(new ChunkBlockView(chunk));

        CubicChunksSavedData saveData = CubicChunksSavedData.get(world);
        saveData.setBottomFiller(fillerInfo.bottomFiller);

        return fillerInfo.bottomFiller;
    }

    private ImmutableBlockMeta analyzeBottomFiller(IBlockView blockView) {
        Object2IntOpenHashMap<ImmutableBlockMeta> histogram = new Object2IntOpenHashMap<>();

        // Scan three layers for top and bottom cubes to guard against bedrock walls
        for (int y = 0; y < 3; y++) {
            for (int x = 0; x < Cube.SIZE; x++) {
                for (int z = 0; z < Cube.SIZE; z++) {
                    histogram.addTo(new BlockMeta(blockView.getBlock(x, y, z), blockView.getBlockMetadata(x, y, z)), 1);
                }
            }
        }

        var bottomBlock = histogram.object2IntEntrySet()
            .stream()
            .filter(e -> {
                if (e.getKey()
                    .getBlock() == Blocks.bedrock) return false;
                if (e.getKey()
                    .getBlock() == Blocks.air) return false;
                if (!e.getKey()
                    .getBlock()
                    .isNormalCube()) return false;

                return true;
            })
            .max(Comparator.comparingInt(Object2IntMap.Entry::getIntValue));

        return bottomBlock.map(Map.Entry::getKey)
            .orElse(new BlockMeta(Blocks.air, 0));
    }

    private ImmutableBlockMeta getTopFillerInfo() {
        if (fillerInfo.topFiller != null) return fillerInfo.topFiller;

        Chunk chunk = vanilla.provideChunk(0, 0);

        ((IColumnInternal) chunk).setColumn(false);

        fillerInfo.topFiller = analyzeTopFiller(new ChunkBlockView(chunk));

        CubicChunksSavedData saveData = CubicChunksSavedData.get(world);
        saveData.setTopFiller(fillerInfo.topFiller);

        return fillerInfo.topFiller;
    }

    private ImmutableBlockMeta analyzeTopFiller(IBlockView blockView) {
        Object2IntOpenHashMap<ImmutableBlockMeta> histogram = new Object2IntOpenHashMap<>();

        int top = blockView.getBounds()
            .getY2();

        // Scan three layers for top and bottom cubes to guard against bedrock walls
        for (int y = top - 1; y > top - 4; y--) {
            for (int x = 0; x < Cube.SIZE; x++) {
                for (int z = 0; z < Cube.SIZE; z++) {
                    histogram.addTo(new BlockMeta(blockView.getBlock(x, y, z), blockView.getBlockMetadata(x, y, z)), 1);
                }
            }
        }

        var topBlock = histogram.object2IntEntrySet()
            .stream()
            .filter(
                e -> e.getKey()
                    .getBlock() != Blocks.bedrock)
            .max(Comparator.comparingInt(Object2IntMap.Entry::getIntValue));

        return topBlock.map(Map.Entry::getKey)
            .orElse(new BlockMeta(Blocks.air, 0));
    }

    @Override
    public void onColumnPreloadFailed(ChunkCoordIntPair pos) {
        if (vanilla instanceof Precalculable precalc) {
            for (int dx = -2; dx <= 2; dx++) {
                for (int dz = -2; dz <= 2; dz++) {
                    precalc.precalculate(pos.chunkXPos + dx, 0, pos.chunkZPos + dz);
                }
            }
        }
    }

    @Override
    public GenerationResult<Chunk> provideColumn(World world, int columnX, int columnZ) {
        Chunk base = vanilla.provideChunk(columnX, columnZ);

        List<Cube> cubes = new ArrayList<>();

        ExtendedBlockStorage[] ebses = base.getBlockStorageArray();

        for (int ebsY = 0; ebsY < 16; ebsY++) {
            var ebs = ebsY >= vanillaGenerationHeightCubes ? null : ebses[ebsY];

            Cube c = new Cube(base, ebsY, ebs);

            try {
                decorator.generate(world, c);
            } catch (Throwable t) {
                CubicChunks.LOGGER.error("Could not run generation for cube {},{},{}", columnX, ebsY, columnZ, t);
            }

            cubes.add(c);
        }

        Arrays.fill(ebses, null);

        ((IColumnInternal) base).setColumn(true);

        return new GenerationResult<>(base, null, cubes);
    }

    @Override
    public void recreateStructures(ICube cube) {

    }

    @Override
    public void recreateStructures(Chunk column) {
        vanilla.recreateStructures(column.xPosition, column.zPosition);
    }

    @Override
    public GenerationResult<Cube> provideCube(@Nullable Chunk chunk, int cubeX, int cubeY, int cubeZ) {
        try {
            WorldgenHangWatchdog.startWorldGen();

            List<Chunk> generatedColumns = new ArrayList<>();
            List<Cube> generatedCubes = new ArrayList<>();

            if (cubeY >= 0 && cubeY < 16 || chunk == null) {
                Chunk base = vanilla.provideChunk(cubeX, cubeZ);

                boolean newChunk = true;

                if (chunk != null) {
                    newChunk = false;

                    CubicChunks.LOGGER.error(
                        "Needed to regenerate a cube within the vanilla chunk for a chunk that already exists: something is fucky ({},{},{})",
                        cubeX,
                        cubeY,
                        cubeZ,
                        new Exception());
                } else {
                    chunk = base;
                    generatedColumns.add(chunk);
                }

                ExtendedBlockStorage[] ebses = base.getBlockStorageArray();

                for (int ebsY = 0; ebsY < 16; ebsY++) {
                    var ebs = ebsY >= vanillaGenerationHeightCubes ? null : ebses[ebsY];

                    Cube c = new Cube(base, ebsY, ebs);

                    try {
                        decorator.generate(world, c);
                    } catch (Throwable t) {
                        CubicChunks.LOGGER.error("Could not run generation for cube {},{},{}", cubeX, ebsY, cubeZ, t);
                    }

                    generatedCubes.add(c);
                }

                if (newChunk) {
                    Arrays.fill(base.getBlockStorageArray(), null);

                    ((IColumnInternal) base).setColumn(true);
                }
            }

            if (cubeY < 0 || cubeY >= 16) {
                ImmutableBlockMeta filler = cubeY < 0 ? getBottomFillerInfo() : getTopFillerInfo();
                IBlockView cubeData = new UniformBlockView(filler);

                Cube cube = new Cube(chunk, cubeY, cubeData);

                try {
                    decorator.generate(world, cube);
                } catch (Throwable t) {
                    CubicChunks.LOGGER.error("Could not run generation for cube {},{},{}", cubeX, cubeY, cubeZ, t);
                }

                generatedCubes.add(cube);
            }

            Cube primary = null;

            for (int i = 0; i < generatedCubes.size(); i++) {
                Cube c = generatedCubes.get(i);

                if (c.getY() == cubeY) {
                    primary = c;
                    generatedCubes.remove(i);
                    break;
                }
            }

            return new GenerationResult<>(primary, generatedColumns, generatedCubes);
        } finally {
            WorldgenHangWatchdog.endWorldGen();
        }
    }

    @Override
    public void populate(Cube cube) {
        ICubeLoader loader = getCubeLoader();

        int cx = cube.getX();
        int cy = cube.getY();
        int cz = cube.getZ();

        try {
            WorldgenHangWatchdog.startWorldGen();

            // Generate all relevant cubes and store them in an array cache
            loader.cacheCubes(getCubesToGenerate(cx, cy, cz), Requirement.GENERATE);

            if (cy >= 0 && cy < 16) {
                for (int x = -1; x <= 1; x++) {
                    for (int z = -1; z <= 1; z++) {
                        ((IColumnInternal) loader.getColumn(cx + x, cz + z, Requirement.GENERATE))
                            .recalculateStagingHeightmap();
                    }
                }
            }

            for (Vector3ic v : getCubesToPopulate(cx, cy, cz)) {
                Cube center = loader.getCube(v.x(), v.y(), v.z(), Requirement.GENERATE);

                if (!center.isPopulated()) {
                    decorator.populate(world, center);

                    if (v.y() == 0) {
                        // For some bizarre reason, MC offsets its block positions by +8 when populating. This means
                        // that when a chunk
                        // gets populated, it's actually populating the 16x16 blocks centered on the +x/+z corner. This
                        // is how every MC
                        // populator works for some reason (who decided this???). As a result, we need to generate the 3
                        // columns in the
                        // negative directions (-x,z, x,-z, -x,-z).

                        populateChunk(loader, v.x(), v.z());
                    }

                    center.markPopulated(Cube.POP_000);
                }
            }

            for (Vector3ic v : getFullyPopulatedCubes(cx, cy, cz)) {
                Cube center = loader.getCube(v.x(), v.y(), v.z(), Requirement.GENERATE);

                center.markPopulated(Cube.POP_ALL);
                loader.onCubeGenerated(center);
            }
        } catch (Throwable t) {
            CubicChunks.LOGGER.error("Could not run non-vanilla population for cube {},{},{}", cx, cy, cz, t);
        } finally {
            WorldgenHangWatchdog.endWorldGen();
            loader.uncacheCubes();
        }
    }

    private static final Vector3ic[] AFFECTED_CUBES = { new Vector3i(1, 0, 0), new Vector3i(0, 1, 0),
        new Vector3i(1, 1, 0), new Vector3i(0, 0, 1), new Vector3i(1, 0, 1), new Vector3i(0, 1, 1),
        new Vector3i(1, 1, 1), };

    // private static final short[] CUBE_FLAGS = { Cube.POP_100, Cube.POP_010, Cube.POP_110, Cube.POP_001, Cube.POP_101,
    // Cube.POP_011, Cube.POP_111, };

    private Box getCubesToGenerate(int x, int y, int z) {
        if (y >= 0 && y < 16) {
            return new Box(x - 1, -1, z - 1, x + 1, 16, z + 1);
        } else {
            return new Box(x - 1, y - 1, z - 1, x + 1, y + 1, z + 1);
        }
    }

    private Box getCubesToPopulate(int x, int y, int z) {
        if (y >= 0 && y < 16) {
            return new Box(x - 1, -1, z - 1, x, 15, z);
        } else {
            return new Box(x - 1, y - 1, z - 1, x, y, z);
        }
    }

    private Box getFullyPopulatedCubes(int x, int y, int z) {
        if (y >= 0 && y < 16) {
            return new Box(x, 0, z, x, 15, z);
        } else {
            return new Box(x, y, z, x, y, z);
        }
    }

    private void populateChunk(ICubeLoader loader, int columnX, int columnZ) {
        Chunk column = loader.getColumn(columnX, columnZ, Requirement.GENERATE);

        column.isTerrainPopulated = true;
        column.isModified = true;

        try {
            ((ICubicWorldInternal.Server) world).fakeWorldHeight(256);

            vanilla.populate(vanilla, columnX, columnZ);

            applyModGenerators(columnX, columnZ, world, vanilla, world.getChunkProvider());
        } catch (Throwable t) {
            CubicChunks.LOGGER.error("Could not populate column {},{}", columnX, columnZ, t);
        } finally {
            ((ICubicWorldInternal.Server) world).fakeWorldHeight(0);
        }
    }

    // First provider is the ChunkProviderGenerate/Hell/End/Flat second is the serverChunkProvider
    private void applyModGenerators(int x, int z, World world, IChunkProvider vanillaGen, IChunkProvider provider) {
        List<cpw.mods.fml.common.IWorldGenerator> generators = IGameRegistry.getSortedGeneratorList();
        if (generators == null) {
            IGameRegistry.computeGenerators();
            generators = IGameRegistry.getSortedGeneratorList();
            assert generators != null;
        }
        long worldSeed = world.getSeed();
        Random fmlRandom = new Random(worldSeed);
        long xSeed = fmlRandom.nextLong() >> 2 + 1L;
        long zSeed = fmlRandom.nextLong() >> 2 + 1L;
        long chunkSeed = (xSeed * x + zSeed * z) ^ worldSeed;

        for (cpw.mods.fml.common.IWorldGenerator generator : generators) {
            fmlRandom.setSeed(chunkSeed);
            try {
                ((ICubicWorldInternal.Server) world).fakeWorldHeight(256);
                generator.generate(fmlRandom, x, z, world, vanillaGen, provider);
            } finally {
                ((ICubicWorldInternal.Server) world).fakeWorldHeight(0);
            }
        }
    }

    @Override
    public void onCubePreloadFailed(CubePos pos, CubeInitLevel actual, CubeInitLevel wanted) {
        boolean generate = actual.ordinal() < CubeInitLevel.Generated.ordinal()
            && wanted.ordinal() >= CubeInitLevel.Generated.ordinal();
        boolean populate = actual.ordinal() < CubeInitLevel.Populated.ordinal()
            && wanted.ordinal() >= CubeInitLevel.Populated.ordinal();

        if (generate) {
            if (vanilla instanceof Precalculable precalc) {
                for (int dx = -2; dx <= 2; dx++) {
                    for (int dz = -2; dz <= 2; dz++) {
                        precalc.precalculate(pos.getX() + dx, 0, pos.getZ() + dz);
                    }
                }
            }

            decorator.pregenerate(world, pos);
        }

        if (populate) {
            decorator.prepopulate(world, pos);
        }
    }

    @Override
    public List<BiomeGenBase.SpawnListEntry> getPossibleCreatures(EnumCreatureType creatureType, int x, int y, int z) {
        return vanilla.getPossibleCreatures(creatureType, x, y, z);
    }

    @Override
    public ChunkPosition getNearestStructure(String name, int x, int y, int z) {
        return vanilla.func_147416_a(world, name, x, y, z);
    }
}
