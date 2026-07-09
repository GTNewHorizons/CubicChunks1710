package com.cardinalstar.cubicchunks.worldgen.ccenhanced.biome;

import com.cardinalstar.cubicchunks.api.worldgen.hwaccel.KernelBuilder;
import com.cardinalstar.cubicchunks.api.worldgen.hwaccel.StandardKernelExecutor;
import com.cardinalstar.cubicchunks.api.worldgen.hwaccel.buffer.BufferDataType;
import com.cardinalstar.cubicchunks.api.worldgen.hwaccel.buffer.BufferLayout;

public class BiomeLookupKernelExecutor extends StandardKernelExecutor<Void> {

    private final int biomeCount;

    public BiomeLookupKernelExecutor(int biomeCount) {
        this.biomeCount = biomeCount;
    }

    @Override
    protected String generateKernel(KernelBuilder builder) {
        builder.addInputBuffer("distance", new BufferLayout(BufferDataType.f32, 16, 16, biomeCount));

        builder.addOutputBuffer("closestBiome", new BufferLayout(BufferDataType.i32, 16, 16, 4));
        builder.addOutputBuffer("biomeWeight", new BufferLayout(BufferDataType.f32, 16, 16, 4));

        builder.addMacro("BIOME_COUNT", biomeCount);

        return """
            #version 460

            layout(local_size_x = 16, local_size_y = 16) in;

            layout(set = 0, binding = 0) readonly buffer Constants { uint constants[]; };
            layout(set = 1, binding = 0) buffer Arena { uint arena[]; };

            $preamble

            $pc

            const uint BIOME_STRIDE = 16u * 16u;

            void main() {
                uint x = gl_GlobalInvocationID.x;
                uint y = gl_GlobalInvocationID.y;

                uint columnOffset = (y << 4u) | x;

                // Single pass: maintain top-4 nearest in registers via insertion sort.
                float dist[4] = float[4](1.0e30f, 1.0e30f, 1.0e30f, 1.0e30f);
                int   idx[4]  = int[4](-1, -1, -1, -1);

                for (uint i = 0u; i < uint(BIOME_COUNT); i++) {
                    float value = GET_DISTANCE(BIOME_STRIDE * i + columnOffset);

                    if (value < dist[3]) {
                        dist[3] = value; idx[3] = int(i);
                        if (dist[2] > dist[3]) { float t = dist[2]; dist[2] = dist[3]; dist[3] = t; int ti = idx[2]; idx[2] = idx[3]; idx[3] = ti; }
                        if (dist[1] > dist[2]) { float t = dist[1]; dist[1] = dist[2]; dist[2] = t; int ti = idx[1]; idx[1] = idx[2]; idx[2] = ti; }
                        if (dist[0] > dist[1]) { float t = dist[0]; dist[0] = dist[1]; dist[1] = t; int ti = idx[0]; idx[0] = idx[1]; idx[1] = ti; }
                    }
                }

                // Write closest biome indices
                SET_CLOSEST_BIOME(columnOffset * 4u + 0u, uint(idx[0]));
                SET_CLOSEST_BIOME(columnOffset * 4u + 1u, uint(idx[1]));
                SET_CLOSEST_BIOME(columnOffset * 4u + 2u, uint(idx[2]));
                SET_CLOSEST_BIOME(columnOffset * 4u + 3u, uint(idx[3]));

                // Weights are inverse distance — closer biome gets higher weight.
                float invA = 1.0f / max(dist[0], 1e-6f);
                float invB = 1.0f / max(dist[1], 1e-6f);
                float invC = 1.0f / max(dist[2], 1e-6f);
                float invD = 1.0f / max(dist[3], 1e-6f);

                float norm = 1.0f / (invA + invB + invC + invD);

                // Write biome weights
                SET_BIOME_WEIGHT(columnOffset * 4u + 0u, invA * norm);
                SET_BIOME_WEIGHT(columnOffset * 4u + 1u, invB * norm);
                SET_BIOME_WEIGHT(columnOffset * 4u + 2u, invC * norm);
                SET_BIOME_WEIGHT(columnOffset * 4u + 3u, invD * norm);
            }
            """;
    }
}
