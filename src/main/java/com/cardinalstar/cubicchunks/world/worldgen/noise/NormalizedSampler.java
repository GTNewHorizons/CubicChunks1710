package com.cardinalstar.cubicchunks.world.worldgen.noise;

import com.cardinalstar.cubicchunks.api.worldgen.hwaccel.KernelBuilder;
import com.cardinalstar.cubicchunks.util.MathUtil;

/// A sampler that remaps the 'boxy' normal distribution of a [SimplexSampler] to an approximately linear distribution.
public class NormalizedSampler implements NoiseSampler {

    private static final double REMAP_EXPONENT = 0.65;

    private static final int LUT_SIZE = 1024;

    private static final float[] REMAP_LOOKUP_TABLE;

    static {
        REMAP_LOOKUP_TABLE = buildLut(REMAP_EXPONENT);
    }

    private static float remap(double v) {
        v = MathUtil.clamp(v, -1.0, 1.0);
        double t = (v + 1.0) * 0.5 * (LUT_SIZE - 1);
        int lo = (int) t;
        int hi = Math.min(lo + 1, LUT_SIZE - 1);
        float frac = (float) (t - lo);
        return REMAP_LOOKUP_TABLE[lo] + frac * (REMAP_LOOKUP_TABLE[hi] - REMAP_LOOKUP_TABLE[lo]);
    }

    private static float[] buildLut(double exponent) {
        float[] lut = new float[LUT_SIZE];
        for (int i = 0; i < LUT_SIZE; i++) {
            double v = i / (double) (LUT_SIZE - 1) * 2.0 - 1.0; // map i → [-1, 1]
            lut[i] = (float) (Math.signum(v) * Math.pow(Math.abs(v), exponent));
        }
        return lut;
    }

    private final NoiseSampler base;

    public NormalizedSampler(NoiseSampler base) {
        this.base = base;
    }

    @Override
    public double sample(double x, double y) {
        return remap(base.sample(x, y));
    }

    @Override
    public double sample(double x, double y, double z) {
        return remap(base.sample(x, y, z));
    }

    @Override
    public String compileKernel2D(KernelBuilder builder, String x, String y) {
        String inner = base.compileKernel2D(builder, x, y);
        String clamped = builder.createName("norm_in");
        String result = builder.createName("normalized");
        builder.logic
            .append("  float ").append(clamped).append(" = clamp(").append(inner).append(", -1.0f, 1.0f);\n")
            .append("  float ").append(result).append(" = sign(").append(clamped)
            .append(") * pow(abs(").append(clamped).append("), ").append((float) REMAP_EXPONENT).append("f);\n");
        return result;
    }

    @Override
    public String compileKernel3D(KernelBuilder builder, String x, String y, String z) {
        String inner = base.compileKernel3D(builder, x, y, z);
        String clamped = builder.createName("norm_in");
        String result = builder.createName("normalized");
        builder.logic
            .append("  float ").append(clamped).append(" = clamp(").append(inner).append(", -1.0f, 1.0f);\n")
            .append("  float ").append(result).append(" = sign(").append(clamped)
            .append(") * pow(abs(").append(clamped).append("), ").append((float) REMAP_EXPONENT).append("f);\n");
        return result;
    }
}
