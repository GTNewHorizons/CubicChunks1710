package com.cardinalstar.cubicchunks.worldgen.ccenhanced.climate;

import java.util.Random;

import com.cardinalstar.cubicchunks.api.worldgen.hwaccel.KernelBuilder;
import com.cardinalstar.cubicchunks.world.worldgen.noise.NoiseSampler;
import com.cardinalstar.cubicchunks.world.worldgen.noise.NormalizedSampler;
import com.cardinalstar.cubicchunks.world.worldgen.noise.OctavesSampler;
import com.cardinalstar.cubicchunks.world.worldgen.noise.ScaledSampler;

/**
 * ClimateAxis implementation backed by a ScaledNoise(OctavesSampler).
 *
 * <p>
 * Raw FBM output (approximately Gaussian) is normalised to [-1, 1] and then remapped
 * through a precomputed LUT to approximate a uniform distribution. This prevents
 * centre-biased noise from over-representing middle-range biomes in Voronoi lookup.
 */
public class NoiseClimateAxis implements ClimateAxis {

    private final String name;
    private final NoiseSampler noise;

    /**
     * @param name          Axis name (e.g. "temperature")
     * @param seed          RNG seed for the underlying SimplexNoiseSampler octaves
     * @param octaves       Number of FBM octaves
     * @param frequency     Base sampling frequency in 1/blocks (e.g. 1/4000)
     */
    public NoiseClimateAxis(String name, long seed, int octaves, double frequency) {
        this.name = name;
        this.noise = new NormalizedSampler(new ScaledSampler(new OctavesSampler(new Random(seed), octaves), frequency));
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public double sample(double x, double z) {
        return noise.sample(x, z);
    }

    @Override
    public void compileShader(KernelBuilder builder, String functionName) {
        String existingLogic = builder.logic.toString();

        String result = this.noise.compileKernel2D(builder, "x", "y");

        String function = """
              float $name(float x, float y) {
                  $logic
                  return $result;
              }
              """
            .replace("$name", functionName)
            .replace("$logic", builder.logic.toString())
            .replace("$result", result);

        builder.preamble.append(function);
        builder.logic.setLength(0);
        builder.logic.append(existingLogic);
    }
}
