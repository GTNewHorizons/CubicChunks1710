package com.cardinalstar.cubicchunks.api.worldgen.hwaccel;

import static org.lwjgl.opengl.GL20.glUseProgram;
import static org.lwjgl.opengl.GL43.glDispatchCompute;

import java.util.Map;

import net.minecraft.world.ChunkCoordIntPair;

import com.cardinalstar.cubicchunks.api.worldgen.hwaccel.buffer.BufferAllocator;
import com.cardinalstar.cubicchunks.api.worldgen.hwaccel.buffer.BufferDataType;
import com.cardinalstar.cubicchunks.api.worldgen.hwaccel.buffer.BufferDescriptor;
import com.cardinalstar.cubicchunks.api.worldgen.hwaccel.buffer.ConstantHardwareBuffer;
import com.cardinalstar.cubicchunks.api.worldgen.hwaccel.buffer.GPUBuffer;
import com.cardinalstar.cubicchunks.world.worldgen.noise.NoiseSampler;
import com.google.common.collect.ImmutableMap;

public class Noise2DKernelExecutor implements KernelExecutor<ChunkCoordIntPair> {

    private final int program;

    private final ConstantHardwareBuffer constants;

    public Noise2DKernelExecutor(NoiseSampler sampler) {
        KernelBuilder builder = new KernelBuilder();

        String result2d = sampler.compileKernel2D(builder, "gx", "gz");

        String code2 =
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

                arena[(z << 4) + x + outputOffset] = $result;
            }
            """;

        code2 = code2.replace("$preamble", builder.preamble.toString())
            .replace("$logic", builder.logic.toString())
            .replace("$result", result2d)
            .replace("$uniform", Noise2DUniformGLStruct.SOURCE);

        this.program = KernelExecutor.createProgram(code2);
        this.constants = builder.constants.finish();
    }

    @Override
    public Map<String, BufferDescriptor> getOutputs(
        ComputePlan plan,
        KernelSubmissionToken submission,
        ChunkCoordIntPair key,
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
