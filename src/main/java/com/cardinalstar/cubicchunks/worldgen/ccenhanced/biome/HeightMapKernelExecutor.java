package com.cardinalstar.cubicchunks.worldgen.ccenhanced.biome;

import java.util.List;

import org.jetbrains.annotations.NotNull;

import com.cardinalstar.cubicchunks.api.worldgen.hwaccel.KernelBuilder;
import com.cardinalstar.cubicchunks.api.worldgen.hwaccel.StandardKernelExecutor;
import com.cardinalstar.cubicchunks.api.worldgen.hwaccel.buffer.BufferDataType;
import com.cardinalstar.cubicchunks.api.worldgen.hwaccel.buffer.BufferLayout;

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
public class HeightMapKernelExecutor extends StandardKernelExecutor<Void> {

    /**
     * Controls how sharply weight falls off with climate-space distance.
     * Higher values make transitions crisper; lower values blend further across biome space.
     * Tune alongside the climate axis frequency scales in
     * {@link com.cardinalstar.cubicchunks.worldgen.ccenhanced.climate.ClimateSystem}.
     */
    private static final float FALLOFF = 10.0f;

    @NotNull
    private final List<CCBiomeGenBase> biomes;

    /** Embeds per-biome rootHeight and heightVariation as GLSL constant arrays. */
    public HeightMapKernelExecutor(List<CCBiomeGenBase> biomes) {
        this.biomes = biomes;
    }

    @Override
    protected String generateKernel(KernelBuilder builder) {
        float[] rootHeight = new float[biomes.size()];
        float[] heightVariation = new float[biomes.size()];

        for (int i = 0; i < biomes.size(); i++) {
            CCBiomeGenBase b = biomes.get(i);

            rootHeight[i] = b.rootHeight;
            heightVariation[i] = b.heightVariation;
        }

        builder.addBufferMacros("ROOT_HEIGHT", builder.addConstant(rootHeight));
        builder.addBufferMacros("HEIGHT_VARIATION", builder.addConstant(heightVariation));
        builder.addMacro("FALLOFF", FALLOFF);
        builder.addMacro("BIOME_COUNT", biomes.size());

        builder.addInputBuffer("distance", new BufferLayout(BufferDataType.f32, 16, 16, biomes.size()));
        builder.addInputBuffer("hvNoise", new BufferLayout(BufferDataType.f32, 16, 16));

        builder.addOutputBuffer("height", new BufferLayout(BufferDataType.f32, 16, 16));

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

                uint columnOffset = y << 4u | x;

                float blendedRoot = 0.0f;
                float blendedVar  = 0.0f;
                float totalWeight = 0.0f;

                for (uint biome = 0u; biome < uint(BIOME_COUNT); biome++) {
                    float dist = GET_DISTANCE(biome * 256u + columnOffset);
                    float w = exp(-FALLOFF * dist * dist);
                    blendedRoot  += GET_ROOT_HEIGHT(biome) * w;
                    blendedVar   += GET_HEIGHT_VARIATION(biome)  * w;
                    totalWeight  += w;
                }

                blendedRoot /= totalWeight;
                blendedVar  /= totalWeight;

                SET_HEIGHT(columnOffset, blendedRoot + blendedVar * GET_HV_NOISE(columnOffset));
            }
            """;
    }
}
