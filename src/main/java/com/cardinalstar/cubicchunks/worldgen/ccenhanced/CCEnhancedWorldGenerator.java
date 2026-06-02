package com.cardinalstar.cubicchunks.worldgen.ccenhanced;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;

import net.minecraft.entity.EnumCreatureType;
import net.minecraft.world.ChunkPosition;
import net.minecraft.world.World;
import net.minecraft.world.biome.BiomeGenBase;
import net.minecraft.world.chunk.Chunk;

import com.cardinalstar.cubicchunks.api.ICube;
import com.cardinalstar.cubicchunks.api.worldgen.GenerationResult;
import com.cardinalstar.cubicchunks.api.worldgen.IWorldGenerator;
import com.cardinalstar.cubicchunks.world.core.IColumnInternal;
import com.cardinalstar.cubicchunks.world.cube.Cube;
import com.cardinalstar.cubicchunks.worldgen.ccenhanced.climate.ClimateSystem;
import com.cardinalstar.cubicchunks.worldgen.ccenhanced.terrain.CCBiomeCache;
import com.cardinalstar.cubicchunks.worldgen.ccenhanced.terrain.CCTerrainGenerator;
import com.cardinalstar.cubicchunks.worldgen.ccenhanced.terrain.ColumnContext;

@ParametersAreNonnullByDefault
public class CCEnhancedWorldGenerator implements IWorldGenerator {

    /** ColumnContext LRU cache capacity: enough to cover a generous chunk-loading radius. */
    private static final int CONTEXT_CACHE_CAPACITY = 256;

    private final World world;
    private final ClimateSystem climateSystem;
    private final CCBiomeCache biomeCache;
    private final CCTerrainGenerator terrainGen;

    // LRU cache: column position key → ColumnContext computed during provideColumn,
    // read back during provideCube calls for the same column.
    private final LinkedHashMap<Long, ColumnContext> contextCache;

    public CCEnhancedWorldGenerator(World world) {
        this.world = world;
        long seed = world.getSeed();
        this.climateSystem = new ClimateSystem(seed);
        this.biomeCache = new CCBiomeCache(climateSystem);
        this.terrainGen = new CCTerrainGenerator(seed);
        this.contextCache = new LinkedHashMap<Long, ColumnContext>(CONTEXT_CACHE_CAPACITY, 0.75f, true) {

            @Override
            protected boolean removeEldestEntry(Map.Entry<Long, ColumnContext> eldest) {
                return size() > CONTEXT_CACHE_CAPACITY;
            }
        };
    }

    // -------------------------------------------------------------------------
    // Column
    // -------------------------------------------------------------------------

    @Override
    public GenerationResult<Chunk> provideColumn(World world, int columnX, int columnZ) {
        Chunk chunk = new Chunk(world, columnX, columnZ);
        ((IColumnInternal) chunk).setColumn(true);

        ColumnContext ctx = terrainGen.computeColumnContext(chunk, columnX, columnZ, biomeCache);
        contextCache.put(packKey(columnX, columnZ), ctx);

        List<Cube> cubes = new ArrayList<>(16);
        for (int cubeY = 0; cubeY < 16; cubeY++) {
            cubes.add(new Cube(chunk, cubeY, terrainGen.buildEbs(world, ctx, cubeY)));
        }

        return new GenerationResult<>(chunk, null, cubes);
    }

    // -------------------------------------------------------------------------
    // Cube
    // -------------------------------------------------------------------------

    @Override
    public GenerationResult<Cube> provideCube(@Nullable Chunk chunk, int cubeX, int cubeY, int cubeZ) {
        List<Chunk> generatedColumns = new ArrayList<>();
        List<Cube> generatedCubes = new ArrayList<>();

        // Generate column + all vanilla-range cubes if chunk is missing or
        // the requested cube falls in the vanilla range.
        if ((cubeY >= 0 && cubeY < 16) || chunk == null) {
            if (chunk == null) {
                chunk = new Chunk(world, cubeX, cubeZ);
                ((IColumnInternal) chunk).setColumn(true);
                generatedColumns.add(chunk);
            }
            ColumnContext ctx = getOrComputeContext(chunk, cubeX, cubeZ);
            for (int cy = 0; cy < 16; cy++) {
                generatedCubes.add(new Cube(chunk, cy, terrainGen.buildEbs(world, ctx, cy)));
            }
        }

        // Cubes outside [0, 16) are generated individually using the same terrain rules.
        if (cubeY < 0 || cubeY >= 16) {
            ColumnContext ctx = getOrComputeContext(chunk, cubeX, cubeZ);
            generatedCubes.add(new Cube(chunk, cubeY, terrainGen.buildEbs(world, ctx, cubeY)));
        }

        // Extract the requested cube as the primary result.
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
    }

    // -------------------------------------------------------------------------
    // Population
    // -------------------------------------------------------------------------

    @Override
    public void populate(Cube cube) {
        cube.markPopulated(Cube.POP_ALL);
    }

    // -------------------------------------------------------------------------
    // Structure stubs
    // -------------------------------------------------------------------------

    @Override
    public void recreateStructures(ICube cube) {}

    @Override
    public void recreateStructures(Chunk column) {}

    @Override
    public List<BiomeGenBase.SpawnListEntry> getPossibleCreatures(EnumCreatureType type, int x, int y, int z) {
        return BiomeGenBase.plains.getSpawnableList(type);
    }

    @Override
    @Nullable
    public ChunkPosition getNearestStructure(String name, int x, int y, int z) {
        return null;
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /** Returns the cached ColumnContext for this column, computing it on demand if absent. */
    private ColumnContext getOrComputeContext(Chunk chunk, int chunkX, int chunkZ) {
        long key = packKey(chunkX, chunkZ);
        ColumnContext ctx = contextCache.get(key);
        if (ctx == null) {
            ctx = terrainGen.computeColumnContext(chunk, chunkX, chunkZ, biomeCache);
            contextCache.put(key, ctx);
        }
        return ctx;
    }

    private static long packKey(int chunkX, int chunkZ) {
        return ((long) chunkX << 32) | (chunkZ & 0xFFFFFFFFL);
    }
}
