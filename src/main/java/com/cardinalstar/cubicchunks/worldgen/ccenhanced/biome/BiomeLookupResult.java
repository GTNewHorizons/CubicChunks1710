package com.cardinalstar.cubicchunks.worldgen.ccenhanced.biome;

/**
 * Result of a Voronoi biome lookup: the top-N nearest biomes in climate space
 * along with their inverse-distance-squared blend weights.
 */
public class BiomeLookupResult {

    /** The single nearest biome in climate space (== neighbors[0]). */
    public final CCBiomeGenBase primary;
    /** Top-N nearest biomes sorted by climate distance; index 0 is the primary (closest). */
    public final CCBiomeGenBase[] neighbors;
    /** Blend weights corresponding to neighbors[], normalised to sum to 1. */
    public final float[] weights;

    public BiomeLookupResult(CCBiomeGenBase primary, CCBiomeGenBase[] neighbors, float[] weights) {
        this.primary = primary;
        this.neighbors = neighbors;
        this.weights = weights;
    }

    /**
     * Compute the blended value of a float field across all neighbors.
     * Usage: {@code result.blend(b -> b.rootHeight)}
     */
    public float blend(BiomeFloatGetter getter) {
        float sum = 0;
        for (int i = 0; i < neighbors.length; i++) {
            sum += weights[i] * getter.get(neighbors[i]);
        }
        return sum;
    }

    @FunctionalInterface
    public interface BiomeFloatGetter {

        float get(CCBiomeGenBase biome);
    }
}
