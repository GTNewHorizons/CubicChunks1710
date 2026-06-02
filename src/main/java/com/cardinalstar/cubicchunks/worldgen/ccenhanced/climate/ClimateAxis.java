package com.cardinalstar.cubicchunks.worldgen.ccenhanced.climate;

/**
 * A single independently-sampled noise axis used to locate a position in climate space.
 * Implementations should return values remapped to approximately [-1, 1].
 */
public interface ClimateAxis {

    String getName();

    /** Sample the axis at world coordinates (x, z). Returns a value in [-1, 1]. */
    double sample(double x, double z);

    /**
     * Default position on this axis for biomes that were registered before this axis existed
     * or whose climate point is shorter than the current axis list.
     * Most axes should return 0; some (e.g. a future "weirdness" axis) may prefer a different neutral.
     */
    default float defaultValue() {
        return 0.0f;
    }
}
