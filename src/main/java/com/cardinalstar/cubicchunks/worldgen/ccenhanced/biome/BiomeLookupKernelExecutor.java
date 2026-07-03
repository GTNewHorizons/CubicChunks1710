package com.cardinalstar.cubicchunks.worldgen.ccenhanced.biome;

import static org.lwjgl.opengl.GL20.glUseProgram;
import static org.lwjgl.opengl.GL43.glDispatchCompute;

import java.util.Map;

import com.cardinalstar.cubicchunks.api.worldgen.hwaccel.ComputePlan;
import com.cardinalstar.cubicchunks.api.worldgen.hwaccel.KernelExecutor;
import com.cardinalstar.cubicchunks.api.worldgen.hwaccel.KernelSubmission;
import com.cardinalstar.cubicchunks.api.worldgen.hwaccel.KernelSubmissionResult;
import com.cardinalstar.cubicchunks.api.worldgen.hwaccel.KernelSubmissionToken;
import com.cardinalstar.cubicchunks.api.worldgen.hwaccel.buffer.BufferAllocator;
import com.cardinalstar.cubicchunks.api.worldgen.hwaccel.buffer.BufferDataType;
import com.cardinalstar.cubicchunks.api.worldgen.hwaccel.buffer.BufferDescriptor;
import com.cardinalstar.cubicchunks.api.worldgen.hwaccel.buffer.GPUBuffer;
import com.google.common.collect.ImmutableMap;

public class BiomeLookupKernelExecutor implements KernelExecutor<Void> {

    private final int program;
    private final int biomeCount;

    public BiomeLookupKernelExecutor(int biomeCount) {
        this.biomeCount = biomeCount;

        String code =
            """
            #version 430 core

            layout(local_size_x = 16, local_size_z = 16) in;

            $uniform

            layout(std430, binding = 0) buffer Arena { uint arena[]; };
            layout(std430, binding = 1) readonly buffer Uniforms { BiomeLookupUniform uniforms[]; };

            const uint BIOME_STRIDE = 16 * 16;

            void main() {
                uint id = gl_WorkGroupID.x;

                uint x = gl_LocalInvocationID.x;
                uint z = gl_LocalInvocationID.z;

                uint distanceOffset = uniforms[id].distanceOffset;
                uint closestOffset = uniforms[id].closestOffset;
                uint weightsOffset = uniforms[id].weightsOffset;

                uint columnOffset = z << 4 | x;

                // Single pass: maintain top-4 nearest in registers via insertion sort.
                float dist[4] = float[4](1.0e30f, 1.0e30f, 1.0e30f, 1.0e30f);
                int   idx[4]  = int[4](-1, -1, -1, -1);

                for (uint i = 0; i < $biomeCount; i++) {
                    float value = uintBitsToFloat(arena[BIOME_STRIDE * i + columnOffset + distanceOffset]);

                    if (value < dist[3]) {
                        dist[3] = value; idx[3] = int(i);
                        if (dist[2] > dist[3]) { float t = dist[2]; dist[2] = dist[3]; dist[3] = t; int ti = idx[2]; idx[2] = idx[3]; idx[3] = ti; }
                        if (dist[1] > dist[2]) { float t = dist[1]; dist[1] = dist[2]; dist[2] = t; int ti = idx[1]; idx[1] = idx[2]; idx[2] = ti; }
                        if (dist[0] > dist[1]) { float t = dist[0]; dist[0] = dist[1]; dist[1] = t; int ti = idx[0]; idx[0] = idx[1]; idx[1] = ti; }
                    }
                }

                // Write closest biome indices
                arena[columnOffset * 4u + 0u + closestOffset] = uint(idx[0]);
                arena[columnOffset * 4u + 1u + closestOffset] = uint(idx[1]);
                arena[columnOffset * 4u + 2u + closestOffset] = uint(idx[2]);
                arena[columnOffset * 4u + 3u + closestOffset] = uint(idx[3]);

                // Weights are inverse distance — closer biome gets higher weight.
                float invA = 1.0f / max(dist[0], 1e-6f);
                float invB = 1.0f / max(dist[1], 1e-6f);
                float invC = 1.0f / max(dist[2], 1e-6f);
                float invD = 1.0f / max(dist[3], 1e-6f);

                float norm = 1.0f / (invA + invB + invC + invD);

                // Write biome weights
                arena[columnOffset * 4 + 0 + weightsOffset] = floatBitsToUint(invA * norm);
                arena[columnOffset * 4 + 1 + weightsOffset] = floatBitsToUint(invB * norm);
                arena[columnOffset * 4 + 2 + weightsOffset] = floatBitsToUint(invC * norm);
                arena[columnOffset * 4 + 3 + weightsOffset] = floatBitsToUint(invD * norm);
            }
            """
                .replace("$uniform", BiomeLookupUniformGLStruct.SOURCE)
                .replace("$biomeCount", Integer.toString(biomeCount));

        this.program = KernelExecutor.createProgram(code);
    }

    @Override
    public Map<String, BufferDescriptor> getOutputs(
        ComputePlan plan, KernelSubmissionToken submission, Void key,
        Map<String, BufferDescriptor> inputs
    ) {
        inputs.get("distances").assertLayout(BufferDataType.f32, 16, 16, biomeCount);

        return ImmutableMap.of(
            "closestBiomes", plan.describeBuffer(submission, BufferDataType.i32, 16, 16, 4),
            "biomeWeights", plan.describeBuffer(submission, BufferDataType.f32, 16, 16, 4));
    }

    @Override
    public KernelSubmissionResult[] submit(BufferAllocator alloc, KernelSubmission<Void>[] submissions) {
        glUseProgram(this.program);

        BiomeLookupUniformPrimitiveBuffer uniforms = new BiomeLookupUniformPrimitiveBuffer(submissions.length);

        KernelSubmissionResult[] results = new KernelSubmissionResult[submissions.length];

        uniforms.forEachFast((i, view) -> {
            GPUBuffer distances = submissions[i].inputs().get("distances");
            GPUBuffer closestBiomes = alloc.alloc(BufferDataType.i32, 16, 16, 4);
            GPUBuffer biomeWeights = alloc.alloc(BufferDataType.f32, 16, 16, 4);

            results[i] = new KernelSubmissionResult(ImmutableMap.of("closestBiomes", closestBiomes, "biomeWeights", biomeWeights));

            view.setDistanceOffset(distances.getBufferOffset() / 4);
            view.setClosestOffset(closestBiomes.getBufferOffset() / 4);
            view.setWeightsOffset(biomeWeights.getBufferOffset() / 4);

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
