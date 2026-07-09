package com.cardinalstar.cubicchunks.worldgen.ccenhanced.climate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import net.minecraft.world.ChunkCoordIntPair;

import org.jetbrains.annotations.Nullable;

import com.cardinalstar.cubicchunks.api.worldgen.hwaccel.KernelBuilder;
import com.cardinalstar.cubicchunks.api.worldgen.hwaccel.KernelExecutor;
import com.cardinalstar.cubicchunks.api.worldgen.hwaccel.StandardKernelExecutor;
import com.cardinalstar.cubicchunks.api.worldgen.hwaccel.buffer.BufferDataType;
import com.cardinalstar.cubicchunks.api.worldgen.hwaccel.buffer.BufferLayout;
import com.google.common.collect.ImmutableMap;
import com.gtnewhorizon.gtnhlib.hash.Fnv1a64;

/**
 * Holds the ordered list of climate axes, applies per-axis domain warping, and produces ClimatePoints for (x, z) world
 * coordinates.
 *
 * <p>
 * Axis indices (0=temperature, 1=humidity, 2=continentalness, 3=erosion). Adding a fifth axis in a future update
 * requires only appending to the axis list and extending the offset arrays.
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

    private final List<ClimateAxis> axes;
    private final ClimateAxis warp;

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
        this.warp = new NoiseClimateAxis("warp", warpSeed, 2, WARP_FREQ);
    }

    private void addAxis(long worldSeed, int axisIndex, String name, int octaves, double frequency) {
        long seed = Fnv1a64.hashStep(Fnv1a64.hashStep(Fnv1a64.initialState(), worldSeed), (long) axisIndex);
        axes.add(new NoiseClimateAxis(name, seed, octaves, frequency));
    }

    /**
     * Sample the climate at world coordinates (x, z). Domain warping is applied to each axis independently using
     * per-axis coordinate offsets.
     */
    public float[] sample(double x, double z, float @Nullable [] pooled) {
        if (pooled == null) pooled = new float[axes.size()];

        for (int i = 0; i < axes.size(); i++) {
            double ox = WARP_OFFSET_X[i];
            double oz = WARP_OFFSET_Z[i];
            // Sample warp displacement for this axis
            double wx = warp.sample(x + ox, z + oz);
            double wz = warp.sample(x + ox + 500.0, z + oz + 500.0);
            pooled[i] = (float) axes.get(i)
                .sample(x + wx * WARP_MAG, z + wz * WARP_MAG);
        }

        return pooled;
    }

    /**
     * Creates a kernel that samples one climate axis for a chunk, writing into the axis-major noise buffer at the slice
     * for {@code axisIndex}. The first axis (0) allocates the output buffer; subsequent axes receive it as input
     * {@code "noise"} and write their slice into the same buffer, returning it unchanged.
     */
    public KernelExecutor<ChunkCoordIntPair> createAxisKernel(int axisIndex) {
        return new AxisSamplerKernelExecutor(axisIndex, axes.get(axisIndex));
    }

    private class AxisSamplerKernelExecutor extends StandardKernelExecutor<ChunkCoordIntPair> {

        private final int axisIndex;
        private final ClimateAxis axis;

        public AxisSamplerKernelExecutor(int axisIndex, ClimateAxis axis) {
            this.axisIndex = axisIndex;
            this.axis = axis;
        }

        @Override
        protected String generateKernel(KernelBuilder builder) {
            builder.addParameter(BufferDataType.i32, "offsetX");
            builder.addParameter(BufferDataType.i32, "offsetZ");
            builder.addOutputBuffer("noise", new BufferLayout(BufferDataType.f32, 16, 16));

            ClimateSystem.this.warp.compileShader(builder, "warp");
            axis.compileShader(builder, axis.getName());

            builder.addMacro("WARP_OFFSET_X", (float) WARP_OFFSET_X[axisIndex]);
            builder.addMacro("WARP_OFFSET_Z", (float) WARP_OFFSET_Z[axisIndex]);
            builder.addMacro("AXIS_OUTPUT_OFFSET", axisIndex * 256);

            return """
                #version 460

                layout(local_size_x = 16, local_size_y = 16) in;

                layout(set = 0, binding = 0) readonly buffer Constants { uint constants[]; };
                layout(set = 1, binding = 0) buffer Arena { uint arena[]; };

                $pc

                $preamble

                void main() {
                    uint x = gl_GlobalInvocationID.x;
                    uint z = gl_GlobalInvocationID.y;

                    int gx = GET_OFFSET_X + int(x);
                    int gz = GET_OFFSET_Z + int(z);

                    $logic

                    float warpX = warp(gx + WARP_OFFSET_X, gz + WARP_OFFSET_Z) * 200.0f;
                    float warpZ = warp(gx + WARP_OFFSET_X + 500.0f, gz + WARP_OFFSET_Z + 500.0f) * 200.0f;

                    SET_NOISE(AXIS_OUTPUT_OFFSET + ((z << 4) | x), $axisFunc(gx + warpX, gz + warpZ));
                }
                """.replace("$logic", builder.logic.toString())
                .replace("$axisFunc", axis.getName());
        }

        @Override
        protected Map<String, Number> getParameters(ChunkCoordIntPair key) {
            return ImmutableMap.of("offsetX", key.chunkXPos << 4, "offsetZ", key.chunkZPos << 4);
        }
    }
}
