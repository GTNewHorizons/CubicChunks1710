package com.cardinalstar.cubicchunks.worldgen.ccenhanced.terrain;

import java.util.Random;

import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;

import net.minecraft.init.Blocks;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;

import com.cardinalstar.cubicchunks.world.worldgen.noise.NoiseSampler;
import com.cardinalstar.cubicchunks.world.worldgen.noise.OctavesSampler;
import com.cardinalstar.cubicchunks.world.worldgen.noise.ScaledNoise;
import com.cardinalstar.cubicchunks.worldgen.ccenhanced.biome.BiomeLookupResult;
import com.gtnewhorizon.gtnhlib.hash.Fnv1a64;

/**
 * Computes the terrain heightmap and fills ExtendedBlockStorage sections.
 *
 * <p>
 * Step 3: pure heightmap terrain, all solid blocks are stone. Surface painting
 * (grass/dirt/sand) and 3D perturbation are added in subsequent steps.
 *
 * <p>
 * Heightmap formula (SDD §4.3):
 * 
 * <pre>
 * surfaceY = SEA_LEVEL + blendedRoot * BASE_SCALE + blendedVar * hvNoise * VAR_SCALE
 * </pre>
 * 
 * surfaceY is "first air Y": block at y &lt; surfaceY is solid, block at y &gt;= surfaceY is air.
 */
@ParametersAreNonnullByDefault
public class CCTerrainGenerator {

    // Heightmap formula constants
    public static final int SEA_LEVEL = 64;
    public static final float BASE_SCALE = 64.0f;
    public static final float VAR_SCALE = 64.0f;

    /** Sum of amplitudes for 4 octaves: 2*(1 - 0.5^4) = 1.875. Used to normalize hvNoise. */
    private static final double HV_AMP_NORM = 2.0 * (1.0 - Math.pow(0.5, 4));

    /** HV noise seed slot — distinct from the climate axis slots (0–3) and warp slot (-1). */
    private static final long HV_SEED_SLOT = 100L;

    private final NoiseSampler hvNoise;

    public CCTerrainGenerator(long worldSeed) {
        long hvSeed = Fnv1a64.hashStep(Fnv1a64.hashStep(Fnv1a64.initialState(), worldSeed), HV_SEED_SLOT);
        // 4 octaves; 1/400 base frequency → hills ~400 blocks wide
        hvNoise = new ScaledNoise(new OctavesSampler(new Random(hvSeed), 4), 1.0 / 400.0);
    }

    // -------------------------------------------------------------------------
    // Column context
    // -------------------------------------------------------------------------

    /**
     * Computes surfaceY for all 256 block columns in the chunk, writes to chunk.heightMap,
     * and returns a ColumnContext holding the values plus min/max for fast-path cube skipping.
     */
    public ColumnContext computeColumnContext(Chunk chunk, int chunkX, int chunkZ, CCBiomeCache biomeCache) {

        BiomeLookupResult[] grid = biomeCache.getGrid(chunkX, chunkZ);

        // Pre-compute blended terrain params at each of the 25 grid points.
        // This avoids calling blend() 256 times; only 25 blend computations needed.
        float[] gridRoot = new float[CCBiomeCache.GRID_SIZE * CCBiomeCache.GRID_SIZE];
        float[] gridVar = new float[CCBiomeCache.GRID_SIZE * CCBiomeCache.GRID_SIZE];
        for (int gi = 0; gi < CCBiomeCache.GRID_SIZE; gi++) {
            for (int gj = 0; gj < CCBiomeCache.GRID_SIZE; gj++) {
                int idx = gi + gj * CCBiomeCache.GRID_SIZE;
                BiomeLookupResult r = grid[idx];
                gridRoot[idx] = r.blend(b -> b.rootHeight);
                gridVar[idx] = r.blend(b -> b.heightVariation);
            }
        }

        int originX = chunkX * 16;
        int originZ = chunkZ * 16;

        int[] surfaceY = new int[256];
        int minY = Integer.MAX_VALUE;
        int maxY = Integer.MIN_VALUE;

        for (int bx = 0; bx < 16; bx++) {
            for (int bz = 0; bz < 16; bz++) {
                int gx = bx / CCBiomeCache.GRID_STRIDE;
                int gz = bz / CCBiomeCache.GRID_STRIDE;
                float fx = (bx % CCBiomeCache.GRID_STRIDE) / (float) CCBiomeCache.GRID_STRIDE;
                float fz = (bz % CCBiomeCache.GRID_STRIDE) / (float) CCBiomeCache.GRID_STRIDE;

                float root = bilerp(gridRoot, gx, gz, fx, fz);
                float var = bilerp(gridVar, gx, gz, fx, fz);

                double hv = hvNoise.sample(originX + bx, originZ + bz) / HV_AMP_NORM;

                int sy = (int) (SEA_LEVEL + root * BASE_SCALE + var * (float) hv * VAR_SCALE);
                surfaceY[bx + bz * 16] = sy;

                // Write to vanilla heightmap. Convention: store surfaceY (first-air-Y).
                chunk.heightMap[bz << 4 | bx] = sy;

                if (sy < minY) minY = sy;
                if (sy > maxY) maxY = sy;
            }
        }

        return new ColumnContext(surfaceY, minY, maxY);
    }

    // -------------------------------------------------------------------------
    // Cube filling
    // -------------------------------------------------------------------------

    /**
     * Builds an ExtendedBlockStorage for the given cube, filling solid positions with stone.
     * Returns null for fully-air cubes (optimization: avoids allocating empty EBS objects).
     *
     * <p>
     * Solid rule: block at world-Y y is solid iff y &lt; surfaceY.
     */
    @Nullable
    public ExtendedBlockStorage buildEbs(World world, ColumnContext ctx, int cubeY) {
        int cubeMinY = cubeY * 16;
        int cubeMaxY = cubeMinY + 15;

        // Fast path: cube entirely above the highest surface → all air
        if (cubeMinY >= ctx.maxSurfaceY) return null;

        int yBase = cubeMinY;
        boolean storeSkylight = !world.provider.hasNoSky;
        ExtendedBlockStorage ebs = new ExtendedBlockStorage(yBase, storeSkylight);

        // Fast path: cube entirely below the lowest surface → all stone
        if (cubeMaxY < ctx.minSurfaceY) {
            for (int x = 0; x < 16; x++) {
                for (int z = 0; z < 16; z++) {
                    for (int y = 0; y < 16; y++) {
                        ebs.func_150818_a(x, y, z, Blocks.stone);
                    }
                }
            }
            return ebs;
        }

        // General case: per-column density check
        for (int bx = 0; bx < 16; bx++) {
            for (int bz = 0; bz < 16; bz++) {
                int sy = ctx.getSurfaceY(bx, bz);
                // Stone for all y in [cubeMinY, min(cubeMaxY, sy-1)]
                int top = Math.min(cubeMaxY, sy - 1);
                for (int y = cubeMinY; y <= top; y++) {
                    ebs.func_150818_a(bx, y - cubeMinY, bz, Blocks.stone);
                }
            }
        }

        return ebs;
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /** Bilinear interpolation over a flat GRID_SIZE×GRID_SIZE float array. */
    private static float bilerp(float[] grid, int gx, int gz, float fx, float fz) {
        int g = CCBiomeCache.GRID_SIZE;
        float v00 = grid[gx + gz * g];
        float v10 = grid[(gx + 1) + gz * g];
        float v01 = grid[gx + (gz + 1) * g];
        float v11 = grid[(gx + 1) + (gz + 1) * g];
        return v00 * (1 - fx) * (1 - fz) + v10 * fx * (1 - fz) + v01 * (1 - fx) * fz + v11 * fx * fz;
    }
}
