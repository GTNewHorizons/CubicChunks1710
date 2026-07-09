package com.cardinalstar.cubicchunks.api.worldgen.hwaccel;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.cardinalstar.cubicchunks.api.worldgen.hwaccel.buffer.BufferDataType;
import com.cardinalstar.cubicchunks.api.worldgen.hwaccel.buffer.BufferDescriptor;
import com.cardinalstar.cubicchunks.api.worldgen.hwaccel.buffer.BufferLayout;
import com.google.common.collect.ImmutableMap;

import it.unimi.dsi.fastutil.ints.Int2ObjectLinkedOpenHashMap;

public class ComputePlan {

    int nextSubmission, nextBuffer, maxLevel;

    final Int2ObjectLinkedOpenHashMap<KernelJob> submits = new Int2ObjectLinkedOpenHashMap<>();

    final List<Terminal> terminals = new ArrayList<>();

    public BufferDescriptor describeBuffer(KernelSubmissionToken submission, BufferDataType dataType, int lenX) {
        return describeBuffer(submission, dataType, lenX, 1, 1);
    }

    public BufferDescriptor describeBuffer(KernelSubmissionToken submission, BufferDataType dataType, int lenX,
        int lenY) {
        return describeBuffer(submission, dataType, lenX, lenY, 1);
    }

    public BufferDescriptor describeBuffer(KernelSubmissionToken submission, BufferDataType dataType, int lenX,
        int lenY, int lenZ) {
        return new BufferDescriptor(submission, nextBuffer++, dataType, lenX, lenY, lenZ);
    }

    public BufferDescriptor describeBuffer(KernelSubmissionToken submission, BufferLayout layout) {
        return describeBuffer(submission, layout.dataType(), layout.lenX(), layout.lenY(), layout.lenZ());
    }

    public <Key> Map<String, BufferDescriptor> submit(KernelExecutor<Key> executor, Key key) {
        return submit(executor, key, ImmutableMap.of());
    }

    public Map<String, BufferDescriptor> submit(KernelExecutor<Void> executor, Map<String, BufferDescriptor> inputs) {
        return submit(executor, null, inputs);
    }

    public <Key> Map<String, BufferDescriptor> submit(KernelExecutor<Key> executor, Key key,
        Map<String, BufferDescriptor> inputs) {
        int id = nextSubmission++;

        int level = inputs.values()
            .stream()
            .mapToInt(
                d -> d.submission()
                    .level())
            .max()
            .orElse(-1) + 1;
        this.maxLevel = Math.max(this.maxLevel, level);

        KernelSubmissionToken submission = new KernelSubmissionToken(executor, key, id, level);

        Map<String, BufferDescriptor> outputs = executor.getOutputs(this, submission, key, inputs);

        submits.put(id, new KernelJob(submission, ImmutableMap.copyOf(inputs), ImmutableMap.copyOf(outputs)));

        return outputs;
    }

    public void terminal(Map<String, BufferDescriptor> inputs, TerminalTask task) {
        this.terminals.add(new Terminal(inputs, task));
    }
}
