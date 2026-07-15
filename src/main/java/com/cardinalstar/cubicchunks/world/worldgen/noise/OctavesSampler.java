package com.cardinalstar.cubicchunks.world.worldgen.noise;

import java.util.Random;
import java.util.function.Supplier;

import com.cardinalstar.cubicchunks.api.worldgen.hwaccel.KernelBuilder;

/// Layers several samplers on top of each other.
/// More octaves increase the CPU cost linearly, but increase the complexity and detail of the returned noise.
/// Each octave has an increasing scale (smaller features) and a decreasing amplitude (smaller effect).
public class OctavesSampler implements NoiseSampler {

    private final NoiseSampler[] octaves;
    private final double[] amplitudes, scales;
    private final double norm;

    public OctavesSampler(Supplier<NoiseSampler> samplers, int octaves) {
        this.octaves = new NoiseSampler[octaves];
        this.amplitudes = new double[octaves];
        this.scales = new double[octaves];

        for (int i = 0; i < octaves; i++) {
            this.octaves[i] = samplers.get();
            this.amplitudes[i] = 1d / Math.pow(2d, i);
            this.scales[i] = Math.pow(2d, i);
        }

        double sum = 0;

        for (double amp : amplitudes) {
            sum += amp;
        }

        this.norm = 1d / sum;
    }

    public OctavesSampler(Random rng, int octaves) {
        this(() -> new SimplexSampler(rng), octaves);
    }

    @Override
    public double sample(double x, double y) {
        double value = 0;

        for (int i = 0, octavesLength = octaves.length; i < octavesLength; i++) {
            NoiseSampler sampler = octaves[i];
            double scale = scales[i];

            value += sampler.sample(x * scale, y * scale) * amplitudes[i];
        }

        return value * norm;
    }

    @Override
    public double sample(double x, double y, double z) {
        double value = 0;

        for (int i = 0, octavesLength = octaves.length; i < octavesLength; i++) {
            NoiseSampler sampler = octaves[i];
            double scale = scales[i];

            value += sampler.sample(x * scale, y * scale, z * scale) * amplitudes[i];
        }

        return value * norm;
    }

    @Override
    public String compileKernel2D(KernelBuilder builder, String x, String y) {
        String result = builder.createName("octaves");
        builder.logic.append("  float ")
            .append(result)
            .append(" = 0.0f;\n");

        for (int i = 0, octavesLength = octaves.length; i < octavesLength; i++) {
            NoiseSampler sampler = octaves[i];
            double scale = scales[i];
            double amplitude = amplitudes[i];

            String value = sampler
                .compileKernel2D(builder, String.format("(%s) * %ff", x, scale), String.format("(%s) * %ff", y, scale));

            builder.logic.append("  ")
                .append(result)
                .append(" += ")
                .append(value)
                .append(" * ")
                .append((float) amplitude)
                .append("f;\n");
        }

        builder.logic.append("  ")
            .append(result)
            .append(" *= ")
            .append((float) this.norm)
            .append("f;\n");

        return result;
    }

    @Override
    public String compileKernel3D(KernelBuilder builder, String x, String y, String z) {
        String result = builder.createName("octaves");
        builder.logic.append("  float ")
            .append(result)
            .append(" = 0.0f;\n");

        for (int i = 0, octavesLength = octaves.length; i < octavesLength; i++) {
            NoiseSampler sampler = octaves[i];
            double scale = scales[i];
            double amplitude = amplitudes[i];

            String value = sampler.compileKernel3D(
                builder,
                String.format("(%s) * %ff", x, scale),
                String.format("(%s) * %ff", y, scale),
                String.format("(%s) * %ff", z, scale));

            builder.logic.append("  ")
                .append(result)
                .append(" += ")
                .append(value)
                .append(" * ")
                .append((float) amplitude)
                .append("f;\n");
        }

        builder.logic.append("  ")
            .append(result)
            .append(" *= ")
            .append((float) this.norm)
            .append("f;\n");

        return result;
    }
}
