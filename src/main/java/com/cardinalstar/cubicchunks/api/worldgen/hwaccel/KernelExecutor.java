package com.cardinalstar.cubicchunks.api.worldgen.hwaccel;

import java.io.Closeable;
import java.util.Map;

import org.lwjgl.vulkan.VkCommandBuffer;

import com.cardinalstar.cubicchunks.api.worldgen.hwaccel.buffer.BufferAllocator;
import com.cardinalstar.cubicchunks.api.worldgen.hwaccel.buffer.BufferDescriptor;
import com.cardinalstar.cubicchunks.api.worldgen.hwaccel.buffer.ConstantBuffer;
import com.cardinalstar.cubicchunks.async.CallingThread;
import com.cardinalstar.cubicchunks.async.ThreadType;

import me.eigenraven.lwjgl3ify.api.Lwjgl3Aware;

@Lwjgl3Aware
public interface KernelExecutor<Key> extends Closeable {

    @CallingThread(ThreadType.WORKER)
    @Override
    void close();

    @CallingThread(ThreadType.WORKER)
    boolean isCompiled();

    @CallingThread(ThreadType.WORKER)
    void compile(ConstantBuffer constants);

    @CallingThread(ThreadType.SERVER)
    Map<String, BufferDescriptor> getOutputs(ComputePlan plan, KernelSubmissionToken submission, Key key,
        Map<String, BufferDescriptor> inputs);

    @CallingThread(ThreadType.WORKER)
    KernelSubmissionResult[] submit(VkCommandBuffer commands, BufferAllocator alloc,
        KernelSubmission<Key>[] submissions);
}
