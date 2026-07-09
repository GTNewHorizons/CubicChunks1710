package com.cardinalstar.cubicchunks.worldgen.ccenhanced.biome;

import static org.lwjgl.vulkan.VK10.vkCmdDispatch;

import org.lwjgl.vulkan.VkCommandBuffer;

import com.cardinalstar.cubicchunks.api.worldgen.hwaccel.KernelBuilder;
import com.cardinalstar.cubicchunks.api.worldgen.hwaccel.StandardKernelExecutor;
import com.cardinalstar.cubicchunks.api.worldgen.hwaccel.buffer.BufferDataType;
import com.cardinalstar.cubicchunks.api.worldgen.hwaccel.buffer.BufferLayout;

public class BiomeDistanceKernelExecutor extends StandardKernelExecutor<Void> {

    @org.jetbrains.annotations.NotNull
    private final CCBiomeGenBase[] biomes;

    public BiomeDistanceKernelExecutor(CCBiomeGenBase[] biomes) {
        this.biomes = biomes;
    }

    @Override
    protected String generateKernel(KernelBuilder builder) {
        float[] climatePoints = new float[biomes.length * 4];

        for (int i = 0; i < biomes.length; i++) {
            System.arraycopy(biomes[i].climatePoint, 0, climatePoints, i * 4, 4);
        }

        builder.addBufferMacros("BIOME_COORDS", builder.addConstant(climatePoints));

        builder.addInputBuffer("temperature", new BufferLayout(BufferDataType.f32, 16, 16));
        builder.addInputBuffer("humidity", new BufferLayout(BufferDataType.f32, 16, 16));
        builder.addInputBuffer("continentalness", new BufferLayout(BufferDataType.f32, 16, 16));
        builder.addInputBuffer("erosion", new BufferLayout(BufferDataType.f32, 16, 16));

        builder.addOutputBuffer("distance", new BufferLayout(BufferDataType.f32, 16, 16, biomes.length));

        return """
            #version 460

            layout(local_size_x = 16, local_size_y = 16) in;

            layout(set = 0, binding = 0) readonly buffer Constants { uint constants[]; };
            layout(set = 1, binding = 0) buffer Arena { uint arena[]; };

            $preamble

            $pc

            void main() {
                uint x = gl_GlobalInvocationID.x;
                uint y = gl_GlobalInvocationID.y;
                uint biome = gl_GlobalInvocationID.z;

                uint pixelIndex = y * 16u + x;
                uint biomeIndex = biome * 4u;

                float a = GET_TEMPERATURE(pixelIndex) - GET_BIOME_COORDS(biomeIndex + 0u);
                float b = GET_HUMIDITY(pixelIndex) - GET_BIOME_COORDS(biomeIndex + 1u);
                float c = GET_CONTINENTALNESS(pixelIndex) - GET_BIOME_COORDS(biomeIndex + 2u);
                float d = GET_EROSION(pixelIndex) - GET_BIOME_COORDS(biomeIndex + 3u);

                SET_DISTANCE(biome * 256u + pixelIndex, sqrt(a * a + b * b + c * c + d * d));
            }
            """;
    }

    @Override
    protected void dispatch(VkCommandBuffer commands) {
        vkCmdDispatch(commands, 1, 1, biomes.length);
    }
}
