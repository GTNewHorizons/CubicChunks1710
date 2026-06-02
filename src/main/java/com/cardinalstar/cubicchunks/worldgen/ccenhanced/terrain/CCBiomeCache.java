package com.cardinalstar.cubicchunks.worldgen.ccenhanced.terrain;

import java.util.LinkedHashMap;
import java.util.Map;

import com.cardinalstar.cubicchunks.worldgen.ccenhanced.biome.BiomeLookupResult;
import com.cardinalstar.cubicchunks.worldgen.ccenhanced.biome.CCBiomeRegistry;
import com.cardinalstar.cubicchunks.worldgen.ccenhanced.climate.ClimatePoint;
import com.cardinalstar.cubicchunks.worldgen.ccenhanced.climate.ClimateSystem;

/**
 * World-level LRU cache mapping (chunkX, chunkZ) to a 5×5 biome lookup grid.
 *
 * <p>
 * The 5×5 grid is sampled at block offsets {0, 4, 8, 12, 16} in both X and Z
 * (indices 0–4 per axis). Position 16 falls in the adjacent chunk — this is valid
 * because ClimateSystem depends only on world coordinates, not on loaded chunk data.
 *
 * <p>
 * During cube generation, the per-column biome blend weights are bilinearly
 * interpolated from the grid using {@code (bx/4, bz/4)} as the grid indices.
 *
 * <p>
 * Also used by canyon/trench edge blending to query neighbor-chunk biomes without
 * requiring those chunks to be loaded.
 */
public class CCBiomeCache {

    /** Grid size: samples every 4 blocks → 5 sample points covers [0, 16] inclusive. */
    public static final int GRID_SIZE = 5;
    public static final int GRID_STRIDE = 4; // blocks between grid samples

    private static final int CACHE_CAPACITY = 512;

    private final ClimateSystem climate;

    // Access-order LinkedHashMap used as an LRU cache. Key: chunkX<<32|chunkZ (unsigned).
    private final LinkedHashMap<Long, BiomeLookupResult[]> cache;

    public CCBiomeCache(ClimateSystem climate) {
        this.climate = climate;
        this.cache = new LinkedHashMap<Long, BiomeLookupResult[]>(CACHE_CAPACITY, 0.75f, true) {

            @Override
            protected boolean removeEldestEntry(Map.Entry<Long, BiomeLookupResult[]> eldest) {
                return size() > CACHE_CAPACITY;
            }
        };
    }

    /**
     * Returns the cached 5×5 BiomeLookupResult grid for the given chunk, computing it on demand.
     * Array is indexed as {@code grid[gx + gz * GRID_SIZE]} where gx, gz ∈ [0, 4].
     */
    public BiomeLookupResult[] getGrid(int chunkX, int chunkZ) {
        long key = packKey(chunkX, chunkZ);
        BiomeLookupResult[] grid = cache.get(key);
        if (grid == null) {
            grid = computeGrid(chunkX, chunkZ);
            cache.put(key, grid);
        }
        return grid;
    }

    private BiomeLookupResult[] computeGrid(int chunkX, int chunkZ) {
        BiomeLookupResult[] grid = new BiomeLookupResult[GRID_SIZE * GRID_SIZE];
        int originX = chunkX * 16;
        int originZ = chunkZ * 16;
        for (int gi = 0; gi < GRID_SIZE; gi++) {
            for (int gj = 0; gj < GRID_SIZE; gj++) {
                double bx = originX + gi * GRID_STRIDE;
                double bz = originZ + gj * GRID_STRIDE;
                ClimatePoint cp = climate.sample(bx, bz);
                grid[gi + gj * GRID_SIZE] = CCBiomeRegistry.lookup(cp.values, 3);
            }
        }
        return grid;
    }

    private static long packKey(int chunkX, int chunkZ) {
        return ((long) chunkX << 32) | (chunkZ & 0xFFFFFFFFL);
    }
}
