package com.cardinalstar.cubicchunks.worldgen;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;

import net.minecraft.block.Block;
import net.minecraft.entity.EnumCreatureType;
import net.minecraft.init.Blocks;
import net.minecraft.world.ChunkCoordIntPair;
import net.minecraft.world.ChunkPosition;
import net.minecraft.world.World;
import net.minecraft.world.biome.BiomeGenBase;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;
import net.minecraft.world.gen.NoiseGeneratorSimplex;
import net.minecraft.world.gen.feature.WorldGenTrees;

import org.joml.Vector3ic;

import com.cardinalstar.cubicchunks.CubicChunks;
import com.cardinalstar.cubicchunks.api.ICube;
import com.cardinalstar.cubicchunks.api.util.Box;
import com.cardinalstar.cubicchunks.api.worldgen.GenerationResult;
import com.cardinalstar.cubicchunks.api.worldgen.ITerrainHeightProvider;
import com.cardinalstar.cubicchunks.api.worldgen.IWorldGenerator;
import com.cardinalstar.cubicchunks.mixin.api.ICubicWorldInternal;
import com.cardinalstar.cubicchunks.server.chunkio.ICubeLoader;
import com.cardinalstar.cubicchunks.world.api.ICubeProviderServer.Requirement;
import com.cardinalstar.cubicchunks.world.core.IColumnInternal;
import com.cardinalstar.cubicchunks.world.cube.Cube;
import com.cardinalstar.cubicchunks.world.cube.blockview.IBlockView;

import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;

/**
 * Huge mountains with water filled valleys between them, made of stone with a grass (or sand below the sea) surface,
 * and scattered trees. Terrain is a single 2D heightmap, so there are no caves or overhangs.
 */
@ParametersAreNonnullByDefault
public class MountainWorldGenerator implements IWorldGenerator, ITerrainHeightProvider {

    /** the highest terrain block, kept below the top of the generation range so trees fit */
    public static final int MAX_TERRAIN_Y = 19900;
    /** water fills every block below this */
    public static final int SEA_LEVEL = 200;

    /**
     * Mountains rise from the valleys up to {@link #MAX_TERRAIN_Y} over roughly this many blocks, which gives slopes of
     * about 20 blocks up per block across.
     */
    private static final double MOUNTAIN_WAVELENGTH = 4000.0;
    private static final int MOUNTAIN_OCTAVES = 3;
    private static final double MOUNTAIN_PERSISTENCE = 0.45;
    /** mountain noise below this is valley floor */
    private static final double VALLEY_THRESHOLD = -0.05;
    /** mountain noise above this reaches the max height */
    private static final double PEAK_THRESHOLD = 0.7;

    /** valley floors vary around the sea level, so valleys have both lakes and land */
    private static final double VALLEY_FLOOR_Y = 190.0;
    private static final double VALLEY_FLOOR_VARIATION = 25.0;
    private static final double VALLEY_WAVELENGTH = 200.0;
    private static final int VALLEY_OCTAVES = 3;

    /** dirt (or sand) layers below the surface block */
    private static final int FILLER_DEPTH = 3;
    /** on average one tree per this many columns */
    private static final int COLUMNS_PER_TREE = 3;

    private static final BiomeGenBase BIOME = BiomeGenBase.plains;

    /** heightmaps of recently generated columns, every cube of a column needs it */
    private static final int HEIGHT_CACHE_SIZE = 256;

    private final World world;
    private final long seed;
    private final NoiseGeneratorSimplex mountainNoise;
    private final NoiseGeneratorSimplex valleyNoise;
    private final Long2ObjectLinkedOpenHashMap<int[]> heightCache = new Long2ObjectLinkedOpenHashMap<>();

    public MountainWorldGenerator(World world) {
        this.world = world;
        this.seed = world.getSeed();
        this.mountainNoise = new NoiseGeneratorSimplex(new Random(this.seed));
        this.valleyNoise = new NoiseGeneratorSimplex(new Random(this.seed + 1));
    }

    // =========//
    // terrain //
    // =========//

    /** @return the Y of the highest terrain block at the given block position */
    private int getTerrainHeight(int blockX, int blockZ) {
        double mountain = fractalNoise(
            mountainNoise,
            blockX / MOUNTAIN_WAVELENGTH,
            blockZ / MOUNTAIN_WAVELENGTH,
            MOUNTAIN_OCTAVES,
            MOUNTAIN_PERSISTENCE);
        double mountainRatio = Math
            .max(0.0, Math.min(1.0, (mountain - VALLEY_THRESHOLD) / (PEAK_THRESHOLD - VALLEY_THRESHOLD)));

        double valleyFloor = VALLEY_FLOOR_Y + (VALLEY_FLOOR_VARIATION
            * fractalNoise(valleyNoise, blockX / VALLEY_WAVELENGTH, blockZ / VALLEY_WAVELENGTH, VALLEY_OCTAVES, 0.5));

        return (int) (valleyFloor + ((MAX_TERRAIN_Y - valleyFloor) * mountainRatio));
    }

    /** @return the summed octaves of the noise, normalized to about -1 to 1 */
    private static double fractalNoise(NoiseGeneratorSimplex noise, double x, double z, int octaves,
        double persistence) {
        double sum = 0.0;
        double amplitudeSum = 0.0;
        double amplitude = 1.0;
        double frequency = 1.0;
        for (int octave = 0; octave < octaves; octave++) {
            sum += noise.func_151605_a(x * frequency, z * frequency) * amplitude;
            amplitudeSum += amplitude;
            amplitude *= persistence;
            frequency *= 2.0;
        }
        return sum / amplitudeSum;
    }

    /** @return the terrain height of each block column of the column (index: x * 16 + z) */
    private int[] getColumnHeights(int columnX, int columnZ) {
        long key = ChunkCoordIntPair.chunkXZ2Int(columnX, columnZ);
        synchronized (heightCache) {
            int[] cached = heightCache.getAndMoveToLast(key);
            if (cached != null) return cached;
        }

        int[] heights = new int[Cube.SIZE * Cube.SIZE];
        for (int x = 0; x < Cube.SIZE; x++) {
            for (int z = 0; z < Cube.SIZE; z++) {
                heights[(x * Cube.SIZE) + z] = getTerrainHeight((columnX * Cube.SIZE) + x, (columnZ * Cube.SIZE) + z);
            }
        }

        synchronized (heightCache) {
            heightCache.putAndMoveToLast(key, heights);
            if (heightCache.size() > HEIGHT_CACHE_SIZE) {
                heightCache.removeFirst();
            }
        }
        return heights;
    }

    @Override
    public int getMaxTerrainHeight(int columnX, int columnZ) {
        // water fills the valleys up to the sea level
        int maxHeight = SEA_LEVEL - 1;
        for (int height : getColumnHeights(columnX, columnZ)) {
            maxHeight = Math.max(maxHeight, height);
        }
        return maxHeight;
    }

    private static Block getBlock(int blockY, int terrainHeight) {
        if (blockY > terrainHeight) {
            return (blockY < SEA_LEVEL) ? Blocks.water : Blocks.air;
        }

        boolean underwater = terrainHeight < SEA_LEVEL;
        if (blockY == terrainHeight) {
            return underwater ? Blocks.sand : Blocks.grass;
        }
        if (blockY > (terrainHeight - FILLER_DEPTH - 1)) {
            return underwater ? Blocks.sand : Blocks.dirt;
        }
        return Blocks.stone;
    }

    // ============//
    // generation //
    // ============//

    private Chunk createColumn(int columnX, int columnZ) {
        Chunk column = new Chunk(world, columnX, columnZ);
        Arrays.fill(column.getBiomeArray(), (byte) BIOME.biomeID);
        ((IColumnInternal) column).setColumn(true);
        return column;
    }

    @Override
    public GenerationResult<Chunk> provideColumn(World world, int columnX, int columnZ) {
        return new GenerationResult<>(createColumn(columnX, columnZ));
    }

    @Override
    public GenerationResult<Cube> provideCube(@Nullable Chunk chunk, int cubeX, int cubeY, int cubeZ) {
        try {
            WorldgenHangWatchdog.startWorldGen();

            List<Chunk> generatedColumns = new ArrayList<>();
            if (chunk == null) {
                chunk = createColumn(cubeX, cubeZ);
                generatedColumns.add(chunk);
            }

            int[] heights = getColumnHeights(cubeX, cubeZ);
            int minBlockY = cubeY * Cube.SIZE;
            int maxBlockY = minBlockY + Cube.SIZE - 1;

            // most cubes are entirely inside a mountain or above the terrain, skip the per-block fill for those
            int minHeight = Integer.MAX_VALUE;
            int maxHeight = Integer.MIN_VALUE;
            for (int height : heights) {
                minHeight = Math.min(minHeight, height);
                maxHeight = Math.max(maxHeight, height);
            }
            if (maxBlockY < (minHeight - FILLER_DEPTH)) {
                Cube cube = new Cube(chunk, cubeY, createUniformStorage(minBlockY, Blocks.stone));
                return new GenerationResult<>(cube, generatedColumns, null);
            }
            if (minBlockY > maxHeight && minBlockY >= SEA_LEVEL) {
                Cube cube = new Cube(chunk, cubeY, (ExtendedBlockStorage) null);
                return new GenerationResult<>(cube, generatedColumns, null);
            }

            Cube cube = new Cube(chunk, cubeY, new IBlockView() {

                @Override
                public Block getBlock(int x, int y, int z) {
                    return MountainWorldGenerator.getBlock(minBlockY + y, heights[(x * Cube.SIZE) + z]);
                }

                @Override
                public int getBlockMetadata(int x, int y, int z) {
                    return 0;
                }
            });

            return new GenerationResult<>(cube, generatedColumns, null);
        } finally {
            WorldgenHangWatchdog.endWorldGen();
        }
    }

    /**
     * Fills the storage's block array directly, instead of setting every block separately, which looks up the block's
     * ID for each one.
     */
    private ExtendedBlockStorage createUniformStorage(int minBlockY, Block block) {
        ExtendedBlockStorage storage = new ExtendedBlockStorage(minBlockY, !world.provider.hasNoSky);

        int id = Block.getIdFromBlock(block);
        if (id > 0xFF) {
            // would need the MSB array, which can only be created on the client
            for (int x = 0; x < Cube.SIZE; x++) {
                for (int y = 0; y < Cube.SIZE; y++) {
                    for (int z = 0; z < Cube.SIZE; z++) {
                        storage.func_150818_a(x, y, z, block);
                    }
                }
            }
            return storage;
        }

        Arrays.fill(storage.getBlockLSBArray(), (byte) id);
        // counts the blocks, an empty storage counts as air
        storage.removeInvalidBlocks();
        return storage;
    }

    // ============//
    // population //
    // ============//

    /**
     * Like vanilla, each cube places its trees offset by 8 blocks, so they can reach into the cubes at +1 but not
     * further. A cube is complete once it and the cubes at -1 have placed theirs.
     */
    @Override
    public void populate(Cube cube) {
        ICubeLoader loader = getCubeLoader();

        int cx = cube.getX();
        int cy = cube.getY();
        int cz = cube.getZ();

        try {
            WorldgenHangWatchdog.startWorldGen();

            loader.cacheCubes(new Box(cx - 1, cy - 1, cz - 1, cx + 1, cy + 1, cz + 1));

            for (Vector3ic v : new Box(cx - 1, cy - 1, cz - 1, cx, cy, cz)) {
                Cube center = loader.getCube(v.x(), v.y(), v.z(), Requirement.GENERATE);

                if (!center.isPopulated()) {
                    placeTree(loader, center);
                    center.markPopulated(Cube.POP_000);
                }
            }

            cube.markPopulated(Cube.POP_ALL);
            loader.onCubeGenerated(cube);
        } catch (Throwable t) {
            CubicChunks.LOGGER.error("Could not populate cube {},{},{}", cx, cy, cz, t);
        } finally {
            WorldgenHangWatchdog.endWorldGen();
            loader.uncacheCubes();
        }
    }

    /**
     * Each column has a 1 in {@link #COLUMNS_PER_TREE} chance for a tree, placed by the cube its trunk starts in.
     * Trunks start 8 to 23 blocks into the cube's area, so the tree (at most 7 high, leaves 2 wide) stays within the
     * cube and the ones at +1.
     */
    private void placeTree(ICubeLoader loader, Cube cube) {
        int cx = cube.getX();
        int cy = cube.getY();
        int cz = cube.getZ();

        Random random = new Random(seed ^ (cx * 341873128712L) ^ (cz * 132897987541L));
        if (random.nextInt(COLUMNS_PER_TREE) != 0) return;

        int treeX = (cx * Cube.SIZE) + 8 + random.nextInt(Cube.SIZE);
        int treeZ = (cz * Cube.SIZE) + 8 + random.nextInt(Cube.SIZE);
        int groundY = getTerrainHeight(treeX, treeZ);
        if (groundY < SEA_LEVEL) return;

        int trunkY = groundY + 1;
        int minTrunkY = (cy * Cube.SIZE) + 8;
        if (trunkY < minTrunkY || trunkY >= (minTrunkY + Cube.SIZE)) return;

        // the tree can reach into the cubes at +1, make sure they exist before placing blocks there
        for (Vector3ic v : new Box(cx, cy, cz, cx + 1, cy + 1, cz + 1)) {
            loader.getCube(v.x(), v.y(), v.z(), Requirement.GENERATE);
        }

        new WorldGenTrees(false).generate(world, random, treeX, trunkY, treeZ);
    }

    private ICubeLoader getCubeLoader() {
        return ((ICubicWorldInternal.Server) world).getCubeCache()
            .getCubeLoader();
    }

    // =======//
    // other //
    // =======//

    @Override
    public void recreateStructures(ICube cube) {}

    @Override
    public void recreateStructures(Chunk column) {}

    @Override
    public List<BiomeGenBase.SpawnListEntry> getPossibleCreatures(EnumCreatureType type, int x, int y, int z) {
        return BIOME.getSpawnableList(type);
    }

    @Nullable
    @Override
    public ChunkPosition getNearestStructure(String name, int x, int y, int z) {
        return null;
    }
}
