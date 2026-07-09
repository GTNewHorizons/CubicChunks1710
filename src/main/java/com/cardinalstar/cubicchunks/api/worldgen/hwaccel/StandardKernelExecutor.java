package com.cardinalstar.cubicchunks.api.worldgen.hwaccel;

import static org.lwjgl.vulkan.VK10.vkCmdDispatch;

import java.util.HashMap;
import java.util.Map;

import org.lwjgl.vulkan.VkCommandBuffer;

import com.cardinalstar.cubicchunks.api.worldgen.hwaccel.buffer.BufferAllocator;
import com.cardinalstar.cubicchunks.api.worldgen.hwaccel.buffer.BufferDescriptor;
import com.cardinalstar.cubicchunks.api.worldgen.hwaccel.buffer.BufferLayout;
import com.cardinalstar.cubicchunks.api.worldgen.hwaccel.buffer.ConstantBuffer;
import com.cardinalstar.cubicchunks.api.worldgen.hwaccel.buffer.GPUBuffer;
import com.google.common.collect.ImmutableMap;

import me.eigenraven.lwjgl3ify.api.Lwjgl3Aware;

@Lwjgl3Aware
public abstract class StandardKernelExecutor<Key> implements KernelExecutor<Key> {

    private final Map<String, BufferLayout> inputs = new HashMap<>(), outputs = new HashMap<>();

    private ComputePipeline pipeline;
    private PushConstantLayout pushConstants;

    @Override
    public boolean isCompiled() {
        return pipeline != null;
    }

    @Override
    public void compile(ConstantBuffer constants) {
        KernelBuilder builder = new KernelBuilder(constants);

        String code = generateKernel(builder);

        code = code.replace("$preamble", builder.preamble)
            .replace("$pc", builder.pushConstants.getPushConstantDefinition());

        this.pipeline = new ComputePipeline(this.toString(), code);
        this.pushConstants = builder.pushConstants;
        this.inputs.putAll(builder.inputs);
        this.outputs.putAll(builder.outputs);
    }

    protected abstract String generateKernel(KernelBuilder builder);

    @Override
    public void close() {
        pipeline.destroy();
    }

    @Override
    public Map<String, BufferDescriptor> getOutputs(ComputePlan plan, KernelSubmissionToken submission, Key key,
        Map<String, BufferDescriptor> inputs) {

        this.inputs.forEach(
            (name, layout) -> {
                inputs.get(name)
                    .assertLayout(layout);
            });

        var outputs = ImmutableMap.<String, BufferDescriptor>builder();

        this.outputs.forEach((name, layout) -> { outputs.put(name, plan.describeBuffer(submission, layout)); });

        return outputs.build();
    }

    @Override
    public KernelSubmissionResult[] submit(VkCommandBuffer commands, BufferAllocator alloc,
        KernelSubmission<Key>[] submissions) {
        pipeline.bind(commands);

        KernelSubmissionResult[] results = new KernelSubmissionResult[submissions.length];

        for (int i = 0; i < submissions.length; i++) {
            var outputs = ImmutableMap.<String, GPUBuffer>builder();

            this.outputs.forEach((name, layout) -> {
                GPUBuffer buffer = alloc.alloc(layout);

                outputs.put(name, buffer);
            });

            var outputMap = outputs.build();

            results[i] = new KernelSubmissionResult(outputMap);

            Map<String, GPUBuffer> buffers = new HashMap<>();
            buffers.putAll(submissions[i].inputs());
            buffers.putAll(outputMap);

            pushConstants.upload(
                commands,
                KernelContext.getScheduler()
                    .getPipelineLayout(),
                buffers,
                getParameters(submissions[i].key()));

            dispatch(commands);
        }

        return results;
    }

    protected Map<String, Number> getParameters(Key key) {
        return ImmutableMap.of();
    }

    protected void dispatch(VkCommandBuffer commands) {
        vkCmdDispatch(commands, 1, 1, 1);
    }
}
