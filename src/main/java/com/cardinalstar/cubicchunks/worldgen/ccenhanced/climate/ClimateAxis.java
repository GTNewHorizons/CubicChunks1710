package com.cardinalstar.cubicchunks.worldgen.ccenhanced.climate;

import com.cardinalstar.cubicchunks.api.worldgen.hwaccel.KernelBuilder;

/**
 * A single independently-sampled noise axis used to locate a position in climate space.
 * Implementations should return values remapped to approximately [-1, 1].
 */
public interface ClimateAxis {

    String getName();

    /** Sample the axis at world coordinates (x, z). Returns a value in [-1, 1]. */
    double sample(double x, double z);

    void compileShader(KernelBuilder builder, String functionName);
}
