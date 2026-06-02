package com.cardinalstar.cubicchunks.worldgen.ccenhanced.climate;

import java.util.Random;

import com.cardinalstar.cubicchunks.world.worldgen.noise.NoiseSampler;
import com.cardinalstar.cubicchunks.world.worldgen.noise.OctavesSampler;
import com.cardinalstar.cubicchunks.world.worldgen.noise.ScaledNoise;

/**
 * ClimateAxis implementation backed by a ScaledNoise(OctavesSampler).
 *
 * <p>
 * Raw FBM output (approximately Gaussian) is normalised to [-1, 1] and then remapped
 * through a precomputed LUT to approximate a uniform distribution. This prevents
 * centre-biased noise from over-representing middle-range biomes in Voronoi lookup.
 */
public class NoiseClimateAxis implements ClimateAxis {

    private static final int LUT_SIZE = 1024;

    private final String name;
    private final NoiseSampler noise;
    private final float[] remapLut;
    /** Sum of octave amplitudes; used to normalise FBM output to [-1, 1] before LUT lookup. */
    private final double amplitudeNorm;

    /**
     * @param name          Axis name (e.g. "temperature")
     * @param seed          RNG seed for the underlying SimplexNoiseSampler octaves
     * @param octaves       Number of FBM octaves
     * @param frequency     Base sampling frequency in 1/blocks (e.g. 1/4000)
     * @param remapExponent Power-curve exponent for distribution remapping; &lt;1 spreads extremes
     */
    public NoiseClimateAxis(String name, long seed, int octaves, double frequency, double remapExponent) {
        this.name = name;
        this.noise = new ScaledNoise(new OctavesSampler(new Random(seed), octaves), frequency);
        this.remapLut = buildLut(remapExponent);
        // Sum of amplitudes for n octaves: 2 * (1 - 0.5^n)
        this.amplitudeNorm = 2.0 * (1.0 - Math.pow(0.5, octaves));
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public double sample(double x, double z) {
        double raw = noise.sample(x, z);
        double normalized = raw / amplitudeNorm;
        return remap(normalized);
    }

    private float remap(double v) {
        v = Math.max(-1.0, Math.min(1.0, v));
        double t = (v + 1.0) * 0.5 * (LUT_SIZE - 1);
        int lo = (int) t;
        int hi = Math.min(lo + 1, LUT_SIZE - 1);
        float frac = (float) (t - lo);
        return remapLut[lo] + frac * (remapLut[hi] - remapLut[lo]);
    }

    private static float[] buildLut(double exponent) {
        float[] lut = new float[LUT_SIZE];
        for (int i = 0; i < LUT_SIZE; i++) {
            double v = (i / (double) (LUT_SIZE - 1)) * 2.0 - 1.0; // map i → [-1, 1]
            lut[i] = (float) (Math.signum(v) * Math.pow(Math.abs(v), exponent));
        }
        return lut;
    }
}
