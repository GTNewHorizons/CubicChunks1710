package com.cardinalstar.cubicchunks.worldgen.ccenhanced.terrain;

import com.cardinalstar.cubicchunks.util.Coords;
import com.cardinalstar.cubicchunks.worldgen.ccenhanced.biome.BiomeLookupResult;
import com.cardinalstar.cubicchunks.worldgen.ccenhanced.biome.CCBiomeRegistry;
import com.cardinalstar.cubicchunks.worldgen.ccenhanced.climate.ClimateSystem;
import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;

/**
 * World-level LRU cache mapping (chunkX, chunkZ) to a 5×5 biome lookup grid.
 *
 * <p>
 * The 16×16 grid provides one biome lookup per block column within the chunk.
 *
 * <p>
 * During cube generation, the per-column biome blend weights are looked up directly
 * by block position within the chunk.
 *
 * <p>
 * Also used by canyon/trench edge blending to query neighbor-chunk biomes without
 * requiring those chunks to be loaded.
 */
public class CCBiomeCache {

    private static final int CACHE_CAPACITY = 512;

    private final ClimateSystem climate;

    // Access-order LinkedHashMap used as an LRU cache. Key: chunkX<<32|chunkZ (unsigned).
    private final Long2ObjectLinkedOpenHashMap<BiomeLookupResult[]> cache = new Long2ObjectLinkedOpenHashMap<>(CACHE_CAPACITY, 0.75f);

    public CCBiomeCache(ClimateSystem climate) {
        this.climate = climate;
    }

    /**
     * Returns the cached 5×5 BiomeLookupResult grid for the given chunk, computing it on demand.
     * Array is indexed as {@code grid[gx + gz * GRID_SIZE]} where gx, gz ∈ [0, 4].
     */
    public BiomeLookupResult[] getGrid(int chunkX, int chunkZ) {
        long key = Coords.packChunk(chunkX, chunkZ);
        BiomeLookupResult[] grid = cache.getAndMoveToFirst(key);

        if (grid == null) {
            grid = computeGrid(chunkX, chunkZ);
            cache.putAndMoveToFirst(key, grid);

            while (cache.size() > CACHE_CAPACITY) cache.removeLast();
        }

        return grid;
    }

    private BiomeLookupResult[] computeGrid(int chunkX, int chunkZ) {
        BiomeLookupResult[] grid = new BiomeLookupResult[16 * 16];
        int originX = chunkX << 4;
        int originZ = chunkZ << 4;

        float[] climatePoint = null;

        for (int localX = 0; localX < 16; localX++) {
            for (int localZ = 0; localZ < 16; localZ++) {
                int bx = originX + localX;
                int bz = originZ + localZ;
                climatePoint = climate.sample(bx, bz, climatePoint);
                grid[localZ << 4 | localX] = CCBiomeRegistry.lookup(climatePoint, 3);
            }
        }

        return grid;
    }
}
