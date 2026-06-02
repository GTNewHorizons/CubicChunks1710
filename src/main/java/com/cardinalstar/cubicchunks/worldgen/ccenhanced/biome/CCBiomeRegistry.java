package com.cardinalstar.cubicchunks.worldgen.ccenhanced.biome;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Registry for CC Enhanced biomes. Supports Voronoi nearest-neighbour lookup in 4D climate space.
 *
 * <p>
 * All biomes must be registered via {@link #register} during FML initialisation (preInit)
 * before any world creation, per FR-03.6.
 */
public class CCBiomeRegistry {

    private static final List<CCBiomeGenBase> biomes = new ArrayList<>();

    public static void register(CCBiomeGenBase biome) {
        biomes.add(biome);
    }

    public static List<CCBiomeGenBase> getBiomes() {
        return Collections.unmodifiableList(biomes);
    }

    /**
     * Voronoi nearest-neighbour lookup in 4D climate space. O(N) in biome count.
     *
     * <p>
     * Returns the top-n biomes by Euclidean distance to {@code climatePoint}, with
     * blend weights proportional to inverse-distance-squared, normalised to sum 1.
     *
     * @param climatePoint Query point: [temperature, humidity, continentalness, erosion]
     * @param n            Number of nearest biomes to return (clamped to biome count)
     */
    public static BiomeLookupResult lookup(float[] climatePoint, int n) {
        if (biomes.isEmpty()) throw new IllegalStateException("No biomes registered in CCBiomeRegistry");
        n = Math.min(n, biomes.size());

        // Compute squared distances to all registered biomes
        float[] dists = new float[biomes.size()];
        for (int i = 0; i < biomes.size(); i++) {
            dists[i] = distanceSq(climatePoint, biomes.get(i).climatePoint);
        }

        // Find indices of the n smallest distances, sorted closest-first
        int[] indices = topNSorted(dists, n);

        // Compute inverse-distance-squared weights, normalised
        float[] weights = new float[n];
        float sum = 0;
        for (int i = 0; i < n; i++) {
            weights[i] = 1.0f / (dists[indices[i]] + 1e-6f);
            sum += weights[i];
        }
        for (int i = 0; i < n; i++) weights[i] /= sum;

        CCBiomeGenBase[] neighbors = new CCBiomeGenBase[n];
        for (int i = 0; i < n; i++) neighbors[i] = biomes.get(indices[i]);

        return new BiomeLookupResult(neighbors[0], neighbors, weights);
    }

    /**
     * Squared Euclidean distance in climate space.
     * Uses the shorter array length to tolerate biomes with fewer axes than the current system.
     */
    static float distanceSq(float[] a, float[] b) {
        float d = 0;
        int len = Math.min(a.length, b.length);
        for (int i = 0; i < len; i++) {
            float diff = a[i] - b[i];
            d += diff * diff;
        }
        return d;
    }

    /**
     * Returns the indices of the n entries with the smallest values in {@code dists},
     * sorted in ascending order (index 0 = smallest distance = primary biome).
     */
    private static int[] topNSorted(float[] dists, int n) {
        int[] indices = new int[n];
        float[] best = new float[n];
        Arrays.fill(best, Float.MAX_VALUE);

        // Selection: keep the n smallest distances
        for (int i = 0; i < dists.length; i++) {
            // Find the slot in our top-n that holds the worst (largest) distance
            int worstSlot = 0;
            for (int j = 1; j < n; j++) {
                if (best[j] > best[worstSlot]) worstSlot = j;
            }
            if (dists[i] < best[worstSlot]) {
                best[worstSlot] = dists[i];
                indices[worstSlot] = i;
            }
        }

        // Insertion-sort the n results by distance (n is tiny — 3 by default)
        for (int i = 1; i < n; i++) {
            float key = best[i];
            int idx = indices[i];
            int j = i - 1;
            while (j >= 0 && best[j] > key) {
                best[j + 1] = best[j];
                indices[j + 1] = indices[j];
                j--;
            }
            best[j + 1] = key;
            indices[j + 1] = idx;
        }

        return indices;
    }
}
