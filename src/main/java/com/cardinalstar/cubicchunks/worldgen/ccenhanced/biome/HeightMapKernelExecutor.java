package com.cardinalstar.cubicchunks.worldgen.ccenhanced.biome;

import static org.lwjgl.opengl.GL20.glUseProgram;
import static org.lwjgl.opengl.GL43.glDispatchCompute;

import java.util.List;
import java.util.Map;

import com.cardinalstar.cubicchunks.api.worldgen.hwaccel.ComputePlan;
import com.cardinalstar.cubicchunks.api.worldgen.hwaccel.KernelConstantBuilder;
import com.cardinalstar.cubicchunks.api.worldgen.hwaccel.KernelExecutor;
import com.cardinalstar.cubicchunks.api.worldgen.hwaccel.KernelSubmission;
import com.cardinalstar.cubicchunks.api.worldgen.hwaccel.KernelSubmissionResult;
import com.cardinalstar.cubicchunks.api.worldgen.hwaccel.KernelSubmissionToken;
import com.cardinalstar.cubicchunks.api.worldgen.hwaccel.buffer.BufferAllocator;
import com.cardinalstar.cubicchunks.api.worldgen.hwaccel.buffer.BufferDataType;
import com.cardinalstar.cubicchunks.api.worldgen.hwaccel.buffer.BufferDescriptor;
import com.cardinalstar.cubicchunks.api.worldgen.hwaccel.buffer.GPUBuffer;
import com.google.common.collect.ImmutableMap;

/**
 * Blends per-biome rootHeight and heightVariation using Gaussian falloff over all biomes in
 * 4D climate space, then modulates the result with a height-variation noise layer.
 *
 * <p>
 * Weight for biome i = exp(-FALLOFF * dist_i²), normalized across all biomes.
 * Using all biomes (not a top-N subset) ensures no discontinuity when the nearest-biome set
 * changes between adjacent columns.
 *
 * <p>
 * Output: heightMap[col] = blendedRoot + blendedVar * hvNoise[col].
 */
public class HeightMapKernelExecutor implements KernelExecutor<Void> {

    /**
     * Controls how sharply weight falls off with climate-space distance.
     * Higher values make transitions crisper; lower values blend further across biome space.
     * Tune alongside the climate axis frequency scales in {@link com.cardinalstar.cubicchunks.worldgen.ccenhanced.climate.ClimateSystem}.
     */
    private static final float FALLOFF = 10.0f;

    private final int program;
    private final int biomeCount;

    /** Embeds per-biome rootHeight and heightVariation as GLSL constant arrays. */
    public HeightMapKernelExecutor(List<CCBiomeGenBase> biomes) {
        this.biomeCount = biomes.size();

        KernelConstantBuilder biomeRoot = new KernelConstantBuilder("float", "BIOME_ROOT");
        KernelConstantBuilder biomeVar = new KernelConstantBuilder("float", "BIOME_VAR");

        for (int i = 0; i < biomeCount; i++) {
            CCBiomeGenBase b = biomes.get(i);

            biomeRoot.annotate(b.biomeName);
            biomeRoot.add(b.rootHeight);

            biomeVar.annotate(b.biomeName);
            biomeVar.add(b.heightVariation);
        }

        String code = """
            #version 430 core

            layout(local_size_x = 16, local_size_z = 16) in;

            $uniform

            // distances[biome * 256 + columnOffset] — axis-major layout from BiomeDistanceKernelExecutor
            layout(std430, binding = 0) buffer Arena    { float arena[]; };
            layout(std430, binding = 1) readonly buffer Uniforms { HeightMapUniform uniforms[];   };

            $biomeRoot
            $biomeVar

            const float FALLOFF = $falloff;

            void main() {
                uint id = gl_WorkGroupID.x;

                uint x = gl_LocalInvocationID.x;
                uint z = gl_LocalInvocationID.z;

                uint distancesOffset = uniforms[id].distancesOffset;
                uint hvNoiseOffset = uniforms[id].hvNoiseOffset;
                uint heightMapOffset = uniforms[id].heightMapOffset;

                uint columnOffset = z << 4u | x;

                float blendedRoot = 0.0f;
                float blendedVar  = 0.0f;
                float totalWeight = 0.0f;

                for (int i = 0; i < $biomeCount; i++) {
                    float dist = arena[uint(i) * 256u + columnOffset + distancesOffset];
                    float w = exp(-FALLOFF * dist * dist);
                    blendedRoot  += BIOME_ROOT[i] * w;
                    blendedVar   += BIOME_VAR[i]  * w;
                    totalWeight  += w;
                }

                blendedRoot /= totalWeight;
                blendedVar  /= totalWeight;

                arena[columnOffset + heightMapOffset] = blendedRoot + blendedVar * arena[columnOffset + hvNoiseOffset];
            }
            """
            .replace("$uniform", HeightMapUniformGLStruct.SOURCE)
            .replace("$biomeRoot", biomeRoot.toString())
            .replace("$biomeVar", biomeVar.toString())
            .replace("$biomeCount", Integer.toString(biomeCount))
            .replace("$falloff", FALLOFF + "f");

        this.program = KernelExecutor.createProgram(code);
    }

    @Override
    public Map<String, BufferDescriptor> getOutputs(
        ComputePlan plan, KernelSubmissionToken submission, Void key,
        Map<String, BufferDescriptor> inputs
    ) {
        inputs.get("distances").assertLayout(BufferDataType.f32, 16, 16, biomeCount);
        inputs.get("hvNoise").assertLayout(BufferDataType.f32, 16, 16);

        return ImmutableMap.of("heightMap", plan.describeBuffer(submission, BufferDataType.f32, 16, 16));
    }

    @Override
    public KernelSubmissionResult[] submit(BufferAllocator alloc, KernelSubmission<Void>[] submissions) {
        glUseProgram(this.program);

        HeightMapUniformPrimitiveBuffer uniforms = new HeightMapUniformPrimitiveBuffer(submissions.length);

        KernelSubmissionResult[] results = new KernelSubmissionResult[submissions.length];

        uniforms.forEachFast((i, view) -> {
            GPUBuffer distances = submissions[i].inputs().get("distances");
            GPUBuffer hvNoise = submissions[i].inputs().get("hvNoise");
            GPUBuffer heightMap = alloc.alloc(BufferDataType.i32, 16, 16);

            results[i] = new KernelSubmissionResult(ImmutableMap.of("heightMap", heightMap));

            view.setDistancesOffset(distances.getBufferOffset() / 4);
            view.setHvNoiseOffset(hvNoise.getBufferOffset() / 4);
            view.setHeightMapOffset(heightMap.getBufferOffset() / 4);

            return true;
        });

        GPUBuffer uniformGPU = alloc.uniform(uniforms);

        alloc.bindSSBO(0);
        uniformGPU.bind(1);

        glDispatchCompute(submissions.length, 1, 1);

        alloc.unbindSSBO(0);
        uniformGPU.unbind(1);

        glUseProgram(0);

        return results;
    }
}
