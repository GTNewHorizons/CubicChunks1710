package com.cardinalstar.cubicchunks.worldgen.ccenhanced.climate;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import com.cardinalstar.cubicchunks.world.worldgen.noise.NoiseSampler;
import com.cardinalstar.cubicchunks.world.worldgen.noise.OctavesSampler;
import com.cardinalstar.cubicchunks.world.worldgen.noise.ScaledNoise;
import com.gtnewhorizon.gtnhlib.hash.Fnv1a64;

/**
 * Holds the ordered list of climate axes, applies per-axis domain warping, and produces
 * ClimatePoints for (x, z) world coordinates.
 *
 * <p>
 * Axis indices (0=temperature, 1=humidity, 2=continentalness, 3=erosion). Adding a fifth
 * axis in a future update requires only appending to the axis list and extending the offset arrays.
 */
public class ClimateSystem {

    // Standard axis indices
    public static final int TEMPERATURE = 0;
    public static final int HUMIDITY = 1;
    public static final int CONTINENTALNESS = 2;
    public static final int EROSION = 3;

    // Domain warp parameters
    private static final double WARP_FREQ = 1.0 / 800.0;
    private static final double WARP_MAG = 200.0;
    // Per-axis offsets — large irrational values to decorrelate samples from the same noise field.
    private static final double[] WARP_OFFSET_X = { 0.0, 3141.5, 2718.2, 1414.2 };
    private static final double[] WARP_OFFSET_Z = { 1000.0, 4142.1, 1618.0, 2236.0 };

    private static final double REMAP_EXPONENT = 0.65;

    private final List<ClimateAxis> axes;
    private final NoiseSampler warpNoise;

    public ClimateSystem(long worldSeed) {
        axes = new ArrayList<>();
        // Axis 0: temperature — continental scale
        addAxis(worldSeed, TEMPERATURE, "temperature", 4, 1.0 / 4000.0);
        // Axis 1: humidity — regional scale
        addAxis(worldSeed, HUMIDITY, "humidity", 4, 1.0 / 1500.0);
        // Axis 2: continentalness — very slow; controls ocean vs. land masses
        addAxis(worldSeed, CONTINENTALNESS, "continentalness", 3, 1.0 / 5000.0);
        // Axis 3: erosion — regional flatness
        addAxis(worldSeed, EROSION, "erosion", 4, 1.0 / 2000.0);

        // Warp noise: 2 octaves, 1/800 frequency. Use axisIndex=-1 as seed slot.
        long warpSeed = Fnv1a64.hashStep(Fnv1a64.hashStep(Fnv1a64.initialState(), worldSeed), -1L);
        warpNoise = new ScaledNoise(new OctavesSampler(new Random(warpSeed), 2), WARP_FREQ);
    }

    private void addAxis(long worldSeed, int axisIndex, String name, int octaves, double frequency) {
        long seed = Fnv1a64.hashStep(Fnv1a64.hashStep(Fnv1a64.initialState(), worldSeed), (long) axisIndex);
        axes.add(new NoiseClimateAxis(name, seed, octaves, frequency, REMAP_EXPONENT));
    }

    /**
     * Sample the climate at world coordinates (x, z).
     * Domain warping is applied to each axis independently using per-axis coordinate offsets.
     */
    public ClimatePoint sample(double x, double z) {
        float[] values = new float[axes.size()];
        for (int i = 0; i < axes.size(); i++) {
            double ox = warpOffsetX(i);
            double oz = warpOffsetZ(i);
            // Sample warp displacement for this axis
            double wx = warpNoise.sample(x + ox, z + oz);
            double wz = warpNoise.sample(x + ox + 500.0, z + oz + 500.0);
            values[i] = (float) axes.get(i)
                .sample(x + wx * WARP_MAG, z + wz * WARP_MAG);
        }
        return new ClimatePoint(values);
    }

    public int numAxes() {
        return axes.size();
    }

    private double warpOffsetX(int i) {
        return i < WARP_OFFSET_X.length ? WARP_OFFSET_X[i] : i * 1000.0;
    }

    private double warpOffsetZ(int i) {
        return i < WARP_OFFSET_Z.length ? WARP_OFFSET_Z[i] : i * 1000.0 + 500.0;
    }
}
