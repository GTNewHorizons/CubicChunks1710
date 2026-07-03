package com.cardinalstar.cubicchunks.worldgen.ccenhanced.climate;

import static org.lwjgl.opengl.GL20.glUseProgram;
import static org.lwjgl.opengl.GL43.glDispatchCompute;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import net.minecraft.world.ChunkCoordIntPair;

import org.jetbrains.annotations.Nullable;

import com.cardinalstar.cubicchunks.api.worldgen.hwaccel.ComputePlan;
import com.cardinalstar.cubicchunks.api.worldgen.hwaccel.KernelBuilder;
import com.cardinalstar.cubicchunks.api.worldgen.hwaccel.KernelExecutor;
import com.cardinalstar.cubicchunks.api.worldgen.hwaccel.KernelSubmission;
import com.cardinalstar.cubicchunks.api.worldgen.hwaccel.KernelSubmissionResult;
import com.cardinalstar.cubicchunks.api.worldgen.hwaccel.KernelSubmissionToken;
import com.cardinalstar.cubicchunks.api.worldgen.hwaccel.Noise2DUniformGLStruct;
import com.cardinalstar.cubicchunks.api.worldgen.hwaccel.Noise2DUniformPrimitiveBuffer;
import com.cardinalstar.cubicchunks.api.worldgen.hwaccel.buffer.BufferAllocator;
import com.cardinalstar.cubicchunks.api.worldgen.hwaccel.buffer.BufferDataType;
import com.cardinalstar.cubicchunks.api.worldgen.hwaccel.buffer.BufferDescriptor;
import com.cardinalstar.cubicchunks.api.worldgen.hwaccel.buffer.ConstantHardwareBuffer;
import com.cardinalstar.cubicchunks.api.worldgen.hwaccel.buffer.GPUBuffer;
import com.google.common.collect.ImmutableMap;
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
     * Sample the climate at world coordinates (x, z).
     * Domain warping is applied to each axis independently using per-axis coordinate offsets.
     */
    public float[] sample(double x, double z, float @Nullable [] pooled) {
        if (pooled == null) pooled = new float[axes.size()];

        for (int i = 0; i < axes.size(); i++) {
            double ox = WARP_OFFSET_X[i];
            double oz = WARP_OFFSET_Z[i];
            // Sample warp displacement for this axis
            double wx = warp.sample(x + ox, z + oz);
            double wz = warp.sample(x + ox + 500.0, z + oz + 500.0);
            pooled[i] = (float) axes.get(i).sample(x + wx * WARP_MAG, z + wz * WARP_MAG);
        }

        return pooled;
    }

    /**
     * Creates a kernel that samples one climate axis for a chunk, writing into the axis-major noise buffer
     * at the slice for {@code axisIndex}. The first axis (0) allocates the output buffer; subsequent axes
     * receive it as input {@code "noise"} and write their slice into the same buffer, returning it unchanged.
     */
    public KernelExecutor<ChunkCoordIntPair> createAxisKernel(int axisIndex) {
        return new AxisSamplerKernelExecutor(axisIndex, axes.get(axisIndex));
    }

    private class AxisSamplerKernelExecutor implements KernelExecutor<ChunkCoordIntPair> {

        private final int axisIndex;
        private final int program;

        private final ConstantHardwareBuffer constants;

        public AxisSamplerKernelExecutor(int axisIndex, ClimateAxis axis) {
            this.axisIndex = axisIndex;

            KernelBuilder builder = new KernelBuilder();

            ClimateSystem.this.warp.compileShader(builder, "warp");
            axis.compileShader(builder, axis.getName());

            float warpOffsetX = (float) WARP_OFFSET_X[axisIndex];
            float warpOffsetZ = (float) WARP_OFFSET_Z[axisIndex];
            int axisBase = axisIndex * 256;

            String code =
                """
                #version 430 core

                layout(local_size_x = 16, local_size_z = 16) in;

                $uniform

                layout(std430, binding = 0) writeonly buffer Arena { float arena[]; };
                layout(std430, binding = 1) readonly buffer Uniforms { Noise2DUniform uniforms[]; };
                layout(std430, binding = 2) readonly buffer Constants { uint constants[]; };

                $preamble

                void main() {
                    uint id = gl_WorkGroupID.x;

                    uint x = gl_LocalInvocationID.x;
                    uint z = gl_LocalInvocationID.z;

                    int offsetX = uniforms[id].offsetX;
                    int offsetZ = uniforms[id].offsetZ;
                    uint outputOffset = uniforms[id].outputOffset;

                    int gx = offsetX + int(x);
                    int gz = offsetZ + int(z);

                    $logic

                    float warpX = warp(gx + $warpOffsetX, gz + $warpOffsetZ) * 200.0f;
                    float warpZ = warp(gx + $warpOffsetX + 500.0f, gz + $warpOffsetZ + 500.0f) * 200.0f;

                    arena[(z << 4) + x + outputOffset] = $axisFunc(gx + warpX, gz + warpZ);
                }
                """
                    .replace("$uniform", Noise2DUniformGLStruct.SOURCE)
                    .replace("$preamble", builder.preamble.toString())
                    .replace("$logic", builder.logic.toString())
                    .replace("$warpOffsetX", warpOffsetX + "f")
                    .replace("$warpOffsetZ", warpOffsetZ + "f")
                    .replace("$axisBase", Integer.toString(axisBase))
                    .replace("$axisFunc", axis.getName());

            this.program = KernelExecutor.createProgram(code);
            this.constants = builder.constants.finish();
        }

        @Override
        public Map<String, BufferDescriptor> getOutputs(
            ComputePlan plan, KernelSubmissionToken submission, ChunkCoordIntPair key,
            Map<String, BufferDescriptor> inputs
        ) {
            return ImmutableMap.of("noise", plan.describeBuffer(submission, BufferDataType.f32, 16, 16));
        }

        @Override
        public KernelSubmissionResult[] submit(BufferAllocator alloc, KernelSubmission<ChunkCoordIntPair>[] submissions) {
            glUseProgram(this.program);

            Noise2DUniformPrimitiveBuffer uniforms = new Noise2DUniformPrimitiveBuffer(submissions.length);

            KernelSubmissionResult[] results = new KernelSubmissionResult[submissions.length];

            uniforms.forEachFast((i, view) -> {
                var key = submissions[i].key();

                GPUBuffer output = alloc.alloc(BufferDataType.f32, 16, 16);
                results[i] = new KernelSubmissionResult(ImmutableMap.of("noise", output));

                view.setOffsetX(key.chunkXPos << 4);
                view.setOffsetZ(key.chunkZPos << 4);
                view.setOutputOffset(output.getBufferOffset() / 4);

                return true;
            });

            GPUBuffer uniformGPU = alloc.uniform(uniforms);

            alloc.bindSSBO(0);
            uniformGPU.bind(1);
            constants.bindSSBO(2);

            glDispatchCompute(submissions.length, 1, 1);

            alloc.unbindSSBO(0);
            uniformGPU.unbind(1);
            constants.unbindSSBO(2);

            glUseProgram(0);

            return results;
        }
    }
}
