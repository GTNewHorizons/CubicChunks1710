package com.cardinalstar.cubicchunks.api.worldgen.hwaccel;

import java.util.Map;

import net.minecraft.world.ChunkCoordIntPair;

import org.jetbrains.annotations.NotNull;

import com.cardinalstar.cubicchunks.api.worldgen.hwaccel.buffer.BufferDataType;
import com.cardinalstar.cubicchunks.api.worldgen.hwaccel.buffer.BufferLayout;
import com.cardinalstar.cubicchunks.world.worldgen.noise.NoiseSampler;
import com.google.common.collect.ImmutableMap;

public class Noise2DKernelExecutor extends StandardKernelExecutor<ChunkCoordIntPair> {

    @NotNull
    private final NoiseSampler sampler;

    public Noise2DKernelExecutor(@NotNull NoiseSampler sampler) {
        this.sampler = sampler;
    }

    @Override
    protected String generateKernel(KernelBuilder builder) {
        builder.addParameter(BufferDataType.f32, "offsetX");
        builder.addParameter(BufferDataType.f32, "offsetY");

        builder.addOutputBuffer("noise", new BufferLayout(BufferDataType.f32, 16, 16));

        String result2d = sampler.compileKernel2D(builder, "gx", "gy");

        return """
            #version 460

            layout(local_size_x = 16, local_size_y = 16) in;

            layout(set = 0, binding = 0) readonly buffer Constants { uint constants[]; };
            layout(set = 1, binding = 0) buffer Arena { uint arena[]; };

            $pc

            $preamble

            void main() {
                uint x = gl_GlobalInvocationID.x;
                uint y = gl_GlobalInvocationID.y;

                float gx = GET_OFFSET_X + float(x);
                float gy = GET_OFFSET_Y + float(y);

                $logic

                SET_NOISE((y << 4u) | x, $result);
            }
            """.replace("$logic", builder.logic.toString())
            .replace("$result", result2d);
    }

    @Override
    protected Map<String, Number> getParameters(ChunkCoordIntPair key) {
        return ImmutableMap.of("offsetX", (float) (key.chunkXPos << 4), "offsetY", (float) (key.chunkZPos << 4));
    }
}
