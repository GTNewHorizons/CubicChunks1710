package com.cardinalstar.cubicchunks.world.worldgen.noise;

import com.cardinalstar.cubicchunks.api.worldgen.hwaccel.KernelBuilder;

/// A generic noise sampler, typically used for worldgen.
/// The domain of the result MUST BE -1 to 1, but the distribution curve is undefined.
/// The coordinates can be any non-NaN/non-infinity value, positive or negative.
/// [SimplexSampler] will produce a 'boxy' standard distribution.
/// Use a [NormalizedSampler] to convert it into an approximately linear distribution.
/// Use a [ScaledSampler] to automatically multiply the passed-in coordinates.
/// Use an [OctavesSampler] to layer several simplex samplers on top of each other, to increase the complexity and
/// detail of the generated noise.
public interface NoiseSampler {

    double sample(double x, double y);

    double sample(double x, double y, double z);

    default String compileKernel2D(KernelBuilder builder, String x, String y) {
        throw new UnsupportedOperationException("Cannot compile NoiseSampler to OpenGL kernel: " + this);
    }

    default String compileKernel3D(KernelBuilder builder, String x, String y, String z) {
        throw new UnsupportedOperationException("Cannot compile NoiseSampler to OpenGL kernel: " + this);
    }
}
