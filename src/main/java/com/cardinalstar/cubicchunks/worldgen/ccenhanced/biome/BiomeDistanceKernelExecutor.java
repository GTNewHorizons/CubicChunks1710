package com.cardinalstar.cubicchunks.worldgen.ccenhanced.biome;

import static org.lwjgl.opengl.GL20.glUseProgram;
import static org.lwjgl.opengl.GL43.glDispatchCompute;

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

public class BiomeDistanceKernelExecutor implements KernelExecutor<Void> {

    @org.jetbrains.annotations.NotNull
    private final CCBiomeGenBase[] biomes;

    private final int program;

    public BiomeDistanceKernelExecutor(CCBiomeGenBase[] biomes) {
        this.biomes = biomes;

        KernelConstantBuilder biomeCoords = new KernelConstantBuilder("float", "BIOME_COORDS");

        for (int i = 0; i < biomes.length; i++) {
            biomeCoords.annotate(biomes[i].biomeName);
            biomeCoords.add(biomes[i].climatePoint);
        }

        String code =
            """
            #version 430 core

            layout(local_size_x = 16, local_size_y = 16, local_size_z = 1) in;

            $uniform

            layout(std430, binding = 0) buffer Arena { float arena[]; };
            layout(std430, binding = 1) readonly buffer Uniforms { BiomeDistanceUniform uniforms[]; };

            $biomeCoords

            void main() {
                uint id = gl_WorkGroupID.x;

                uint x = gl_LocalInvocationID.x;
                uint y = gl_LocalInvocationID.y;
                uint biome = gl_LocalInvocationID.z + gl_WorkGroupID.z;

                uint pixelIndex = y * 16u + x;
                uint biomeIndex = biome * 4u;

                uint temperatureOffset = uniforms[id].temperatureOffset;
                uint humidityOffset = uniforms[id].humidityOffset;
                uint continentalnessOffset = uniforms[id].continentalnessOffset;
                uint erosionOffset = uniforms[id].erosionOffset;
                uint distanceOffset = uniforms[id].distanceOffset;

                float a = arena[pixelIndex + temperatureOffset] - BIOME_COORDS[biomeIndex + 0u];
                float b = arena[pixelIndex + humidityOffset] - BIOME_COORDS[biomeIndex + 1u];
                float c = arena[pixelIndex + continentalnessOffset] - BIOME_COORDS[biomeIndex + 2u];
                float d = arena[pixelIndex + erosionOffset] - BIOME_COORDS[biomeIndex + 3u];

                arena[biome * 256u + pixelIndex + distanceOffset] = sqrt(a * a + b * b + c * c + d * d);
            }
            """
                .replace("$biomeCount", Integer.toString(biomes.length))
                .replace("$uniform", BiomeDistanceUniformGLStruct.SOURCE)
                .replace("$biomeCoords", biomeCoords.toString());

        this.program = KernelExecutor.createProgram(code);
    }

    @Override
    public Map<String, BufferDescriptor> getOutputs(
        ComputePlan plan, KernelSubmissionToken submission, Void key,
        Map<String, BufferDescriptor> inputs
    ) {
        inputs.get("temperature").assertLayout(BufferDataType.f32, 16, 16);
        inputs.get("humidity").assertLayout(BufferDataType.f32, 16, 16);
        inputs.get("continentalness").assertLayout(BufferDataType.f32, 16, 16);
        inputs.get("erosion").assertLayout(BufferDataType.f32, 16, 16);

        return ImmutableMap.of("distances", plan.describeBuffer(submission, BufferDataType.f32, 16, 16, biomes.length));
    }

    @Override
    public KernelSubmissionResult[] submit(BufferAllocator alloc, KernelSubmission<Void>[] submissions) {
        glUseProgram(this.program);

        BiomeDistanceUniformPrimitiveBuffer uniforms = new BiomeDistanceUniformPrimitiveBuffer(submissions.length);

        KernelSubmissionResult[] results = new KernelSubmissionResult[submissions.length];

        uniforms.forEachFast((i, view) -> {
            GPUBuffer output = alloc.alloc(BufferDataType.f32, 16, 16, biomes.length);
            results[i] = new KernelSubmissionResult(ImmutableMap.of("distances", output));

            Map<String, GPUBuffer> inputs = submissions[i].inputs();

            view.setTemperatureOffset(inputs.get("temperature").getBufferOffset() / 4);
            view.setHumidityOffset(inputs.get("humidity").getBufferOffset() / 4);
            view.setContinentalnessOffset(inputs.get("continentalness").getBufferOffset() / 4);
            view.setErosionOffset(inputs.get("erosion").getBufferOffset() / 4);
            view.setDistanceOffset(output.getBufferOffset() / 4);

            return true;
        });

        GPUBuffer uniformGPU = alloc.uniform(uniforms);

        alloc.bindSSBO(0);
        uniformGPU.bind(1);

        glDispatchCompute(submissions.length, 1, biomes.length);

        alloc.unbindSSBO(0);
        uniformGPU.unbind(1);

        glUseProgram(0);

        return results;
    }
}
