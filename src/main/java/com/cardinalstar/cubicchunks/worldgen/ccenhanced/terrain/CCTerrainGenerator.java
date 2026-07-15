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
import com.cardinalstar.cubicchunks.world.worldgen.noise.ScaledSampler;
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
 * Heightmap formula:
 *
 * <pre>
 * surfaceY = blendedRoot + blendedVar * hvNoise
 * </pre>
 *
 * rootHeight and heightVariation are in world-Y block coordinates; no additional scaling is applied.
 * surfaceY is "first air Y": block at y &lt; surfaceY is solid, block at y &gt;= surfaceY is air.
 */
@ParametersAreNonnullByDefault
public class CCTerrainGenerator {

    /** HV noise seed slot — distinct from the climate axis slots (0–3) and warp slot (-1). */
    private static final long HV_SEED_SLOT = 100L;

    private final NoiseSampler hvNoise;

    public CCTerrainGenerator(long worldSeed) {
        long hvSeed = Fnv1a64.hashStep(Fnv1a64.hashStep(Fnv1a64.initialState(), worldSeed), HV_SEED_SLOT);
        // 4 octaves; 1/400 base frequency → hills ~400 blocks wide
        hvNoise = new ScaledSampler(new OctavesSampler(new Random(hvSeed), 4), 1.0 / 400.0);
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

        float[] gridRoot = new float[16 * 16];
        float[] gridVar = new float[16 * 16];

        for (int bx = 0; bx < 16; bx++) {
            for (int bz = 0; bz < 16; bz++) {
                int idx = bz << 4 | bx;
                BiomeLookupResult r = grid[idx];
                gridRoot[idx] = r.blend(b -> b.rootHeight);
                gridVar[idx] = r.blend(b -> b.heightVariation);
            }
        }

        int originX = chunkX * 16;
        int originZ = chunkZ * 16;

        int[] surfaceY = new int[16 * 16];
        int minY = Integer.MAX_VALUE;
        int maxY = Integer.MIN_VALUE;

        for (int bx = 0; bx < 16; bx++) {
            for (int bz = 0; bz < 16; bz++) {
                int idx = bz << 4 | bx;

                double hv = hvNoise.sample(originX + bx, originZ + bz);

                int sy = (int) (gridRoot[idx] + gridVar[idx] * (float) hv);
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
}
