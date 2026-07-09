package com.cardinalstar.cubicchunks.world.worldgen.noise;

import com.cardinalstar.cubicchunks.api.worldgen.hwaccel.KernelBuilder;

/// Scales another sampler by a certain amount in each axis.
/// Effects are the opposite of what you'd expect - scaling by 2 in an axis shrinks the noise by half along that axis.
public class ScaledSampler implements NoiseSampler {

    private final NoiseSampler base;
    private final double scaleX;
    private final double scaleY;
    private final double scaleZ;

    public ScaledSampler(NoiseSampler base, double scaleX, double scaleY, double scaleZ) {
        this.base = base;
        this.scaleX = scaleX;
        this.scaleY = scaleY;
        this.scaleZ = scaleZ;
    }

    public ScaledSampler(NoiseSampler base, double scale) {
        this(base, scale, scale, scale);
    }

    @Override
    public double sample(double x, double y) {
        return base.sample(x * scaleX, y * scaleY);
    }

    @Override
    public double sample(double x, double y, double z) {
        return base.sample(x * scaleX, y * scaleY, z * scaleZ);
    }

    @Override
    public String compileKernel2D(KernelBuilder builder, String x, String y) {
        return base
            .compileKernel2D(builder, String.format("(%s) * %ff", x, scaleX), String.format("(%s) * %ff", y, scaleY));
    }

    @Override
    public String compileKernel3D(KernelBuilder builder, String x, String y, String z) {
        return base.compileKernel3D(
            builder,
            String.format("(%s) * %ff", x, scaleX),
            String.format("(%s) * %ff", y, scaleY),
            String.format("(%s) * %ff", z, scaleZ));
    }
}
