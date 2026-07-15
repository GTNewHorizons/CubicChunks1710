package com.cardinalstar.cubicchunks.api.worldgen.hwaccel;

import static com.cardinalstar.cubicchunks.api.worldgen.hwaccel.KernelContext.check;
import static com.cardinalstar.cubicchunks.api.worldgen.hwaccel.KernelContext.getDevice;
import static org.lwjgl.vulkan.VK10.VK_ACCESS_SHADER_READ_BIT;
import static org.lwjgl.vulkan.VK10.VK_ACCESS_SHADER_WRITE_BIT;
import static org.lwjgl.vulkan.VK10.VK_ACCESS_TRANSFER_READ_BIT;
import static org.lwjgl.vulkan.VK10.VK_ACCESS_TRANSFER_WRITE_BIT;
import static org.lwjgl.vulkan.VK10.VK_COMMAND_BUFFER_LEVEL_PRIMARY;
import static org.lwjgl.vulkan.VK10.VK_COMMAND_BUFFER_USAGE_ONE_TIME_SUBMIT_BIT;
import static org.lwjgl.vulkan.VK10.VK_COMMAND_POOL_CREATE_RESET_COMMAND_BUFFER_BIT;
import static org.lwjgl.vulkan.VK10.VK_COMMAND_POOL_CREATE_TRANSIENT_BIT;
import static org.lwjgl.vulkan.VK10.VK_DESCRIPTOR_TYPE_STORAGE_BUFFER;
import static org.lwjgl.vulkan.VK10.VK_FENCE_CREATE_SIGNALED_BIT;
import static org.lwjgl.vulkan.VK10.VK_PIPELINE_BIND_POINT_COMPUTE;
import static org.lwjgl.vulkan.VK10.VK_PIPELINE_STAGE_COMPUTE_SHADER_BIT;
import static org.lwjgl.vulkan.VK10.VK_PIPELINE_STAGE_TRANSFER_BIT;
import static org.lwjgl.vulkan.VK10.VK_QUEUE_FAMILY_IGNORED;
import static org.lwjgl.vulkan.VK10.VK_SHADER_STAGE_COMPUTE_BIT;
import static org.lwjgl.vulkan.VK10.vkAllocateCommandBuffers;
import static org.lwjgl.vulkan.VK10.vkAllocateDescriptorSets;
import static org.lwjgl.vulkan.VK10.vkBeginCommandBuffer;
import static org.lwjgl.vulkan.VK10.vkCmdBindDescriptorSets;
import static org.lwjgl.vulkan.VK10.vkCmdCopyBuffer;
import static org.lwjgl.vulkan.VK10.vkCmdFillBuffer;
import static org.lwjgl.vulkan.VK10.vkCmdPipelineBarrier;
import static org.lwjgl.vulkan.VK10.vkCreateCommandPool;
import static org.lwjgl.vulkan.VK10.vkCreateDescriptorPool;
import static org.lwjgl.vulkan.VK10.vkCreateDescriptorSetLayout;
import static org.lwjgl.vulkan.VK10.vkCreateFence;
import static org.lwjgl.vulkan.VK10.vkCreatePipelineLayout;
import static org.lwjgl.vulkan.VK10.vkDestroyCommandPool;
import static org.lwjgl.vulkan.VK10.vkDestroyDescriptorPool;
import static org.lwjgl.vulkan.VK10.vkDestroyDescriptorSetLayout;
import static org.lwjgl.vulkan.VK10.vkDestroyFence;
import static org.lwjgl.vulkan.VK10.vkDestroyPipelineLayout;
import static org.lwjgl.vulkan.VK10.vkEndCommandBuffer;
import static org.lwjgl.vulkan.VK10.vkFreeCommandBuffers;
import static org.lwjgl.vulkan.VK10.vkFreeDescriptorSets;
import static org.lwjgl.vulkan.VK10.vkQueueSubmit;
import static org.lwjgl.vulkan.VK10.vkResetCommandBuffer;
import static org.lwjgl.vulkan.VK10.vkResetFences;
import static org.lwjgl.vulkan.VK10.vkUpdateDescriptorSets;
import static org.lwjgl.vulkan.VK10.vkWaitForFences;

import java.io.Closeable;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;
import java.nio.LongBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.vulkan.VkBufferCopy;
import org.lwjgl.vulkan.VkBufferMemoryBarrier;
import org.lwjgl.vulkan.VkCommandBuffer;
import org.lwjgl.vulkan.VkCommandBufferAllocateInfo;
import org.lwjgl.vulkan.VkCommandBufferBeginInfo;
import org.lwjgl.vulkan.VkCommandPoolCreateInfo;
import org.lwjgl.vulkan.VkDescriptorBufferInfo;
import org.lwjgl.vulkan.VkDescriptorPoolCreateInfo;
import org.lwjgl.vulkan.VkDescriptorPoolSize;
import org.lwjgl.vulkan.VkDescriptorSetAllocateInfo;
import org.lwjgl.vulkan.VkDescriptorSetLayoutBinding;
import org.lwjgl.vulkan.VkDescriptorSetLayoutCreateInfo;
import org.lwjgl.vulkan.VkDevice;
import org.lwjgl.vulkan.VkFenceCreateInfo;
import org.lwjgl.vulkan.VkPipelineLayoutCreateInfo;
import org.lwjgl.vulkan.VkPushConstantRange;
import org.lwjgl.vulkan.VkSubmitInfo;
import org.lwjgl.vulkan.VkWriteDescriptorSet;

import com.cardinalstar.cubicchunks.api.worldgen.hwaccel.buffer.BufferDescriptor;
import com.cardinalstar.cubicchunks.api.worldgen.hwaccel.buffer.GPUBuffer;
import com.cardinalstar.cubicchunks.api.worldgen.hwaccel.buffer.VulkanArenaAllocator;
import com.cardinalstar.cubicchunks.api.worldgen.hwaccel.buffer.VulkanConstantPool;
import com.cardinalstar.cubicchunks.async.CallingThread;
import com.cardinalstar.cubicchunks.async.ThreadType;
import com.cardinalstar.cubicchunks.util.DataUtils;
import com.cardinalstar.cubicchunks.util.JavaUtils;
import com.cardinalstar.cubicchunks.util.MathUtil;
import com.github.bsideup.jabel.Desugar;

import it.unimi.dsi.fastutil.Pair;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap.Entry;
import it.unimi.dsi.fastutil.objects.Object2IntMaps;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import lombok.Getter;
import me.eigenraven.lwjgl3ify.api.Lwjgl3Aware;

@Lwjgl3Aware
@SuppressWarnings("rawtypes")
public class KernelScheduler implements Closeable {

    @Desugar
    private record TerminalResult(Map<String, ByteBuffer> data, TerminalTask task) {}

    @Desugar
    private record ArenaRange(long offset, long byteLen) {}

    @Desugar
    private record CopyRegion(long srcOffset, long dstOffset, long byteLen) {}

    // Carries the plan-local-to-global index offsets alongside the job.
    @Desugar
    private record GlobalJob(int planBase, int bufferBase, KernelJob job) {}

    private interface Task {
    }

    @Desugar
    private record SubmitTask(List<ComputePlan> plans, AtomicBoolean ready) implements Task {}

    @Desugar
    private record ExecTask(Runnable fn, AtomicBoolean done) implements Task {}

    public static final Logger LOGGER = LogManager.getLogger("CC-KernelScheduler");

    // EMA parameters for per-executor timing estimates.
    private static final double EMA_ALPHA = 0.1;
    // Initial per-kernel estimate used before any measurements are available.
    private static final long DEFAULT_ESTIMATE_NS = 100_000L; // 0.1 ms

    private final LinkedBlockingQueue<Task> tasks = new LinkedBlockingQueue<>();
    private final LinkedBlockingQueue<TerminalResult> results = new LinkedBlockingQueue<>();

    // Per-executor EMA: long[0] = ema nanoseconds per dispatch, long[1] = sample count.
    private final Map<KernelExecutor, long[]> executorEma = new IdentityHashMap<>();
    private final Object emaLock = new Object();

    private long gpuBudgetNs = 20_000_000L; // 20 ms default, configurable

    private final LongBuffer lp;
    private final PointerBuffer pp;
    private final IntBuffer ip;

    private long descriptorSetLayout0, descriptorSetLayout1, descriptorSetLayout2;

    @Getter
    private long pipelineLayout;

    private long descriptorPool;
    private long descriptorSet0, descriptorSet1, descriptorSet2;

    private long commandPool;
    @Getter
    private VkCommandBuffer commandBuffer;

    private long fence;

    private final VulkanArenaAllocator arenaAllocator = new VulkanArenaAllocator();

    @Getter
    private VulkanBuffer arena, readback;
    @Getter
    private final VulkanConstantPool constants;

    private final Set<KernelExecutor<?>> knownExecutors = new HashSet<>();

    public KernelScheduler() {
        lp = MemoryUtil.memAllocLong(1);
        pp = MemoryUtil.memAllocPointer(1);
        ip = MemoryUtil.memAllocInt(1);

        createDescriptorSetLayouts();
        createPipelineLayout();

        createDescriptorPool();
        allocateDescriptorSets();

        createCommandPool();
        createFence();

        constants = new VulkanConstantPool(KernelContext.getVmaAllocator());
        arena = VulkanBuffer.allocDeviceLocal(KernelContext.getVmaAllocator(), KernelContext.CHUNK_SIZE);
        readback = VulkanBuffer.allocHostVisible(KernelContext.getVmaAllocator(), KernelContext.CHUNK_SIZE);
    }

    @Override
    public void close() {
        VkDevice device = getDevice();

        knownExecutors.forEach(KernelExecutor::close);

        if (fence != 0) vkDestroyFence(device, fence, null);

        if (commandBuffer != null) vkFreeCommandBuffers(device, commandPool, commandBuffer);
        if (commandPool != 0) vkDestroyCommandPool(device, commandPool, null);

        if (descriptorSet0 != 0) vkFreeDescriptorSets(device, descriptorPool, descriptorSet0);
        if (descriptorSet1 != 0) vkFreeDescriptorSets(device, descriptorPool, descriptorSet1);
        if (descriptorSet2 != 0) vkFreeDescriptorSets(device, descriptorPool, descriptorSet2);
        if (descriptorPool != 0) vkDestroyDescriptorPool(device, descriptorPool, null);

        if (pipelineLayout != 0) vkDestroyPipelineLayout(device, pipelineLayout, null);

        if (descriptorSetLayout0 != 0) vkDestroyDescriptorSetLayout(device, descriptorSetLayout0, null);
        if (descriptorSetLayout1 != 0) vkDestroyDescriptorSetLayout(device, descriptorSetLayout1, null);
        if (descriptorSetLayout2 != 0) vkDestroyDescriptorSetLayout(device, descriptorSetLayout2, null);

        MemoryUtil.memFree(lp);
        MemoryUtil.memFree(pp);
        MemoryUtil.memFree(ip);
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    @CallingThread(ThreadType.SERVER)
    public void setGpuBudget(long ns) {
        this.gpuBudgetNs = ns;
    }

    @CallingThread(ThreadType.SERVER)
    public long getGpuBudget() {
        return gpuBudgetNs;
    }

    /**
     * Estimates the GPU wall-clock cost of executing all kernels in {@code plan} based on
     * the exponential moving average of observed dispatch times per executor type.
     * Returns a conservative over-estimate before any measurements are available.
     */
    @CallingThread(ThreadType.SERVER)
    public long estimatePlanCost(ComputePlan plan) {
        synchronized (emaLock) {
            long total = 0;
            for (var e : plan.submits.int2ObjectEntrySet()) {
                total += getEstimateNs(
                    e.getValue()
                        .submission()
                        .executor());
            }
            return total;
        }
    }

    @CallingThread(ThreadType.SERVER)
    public void submit(List<ComputePlan> plans) {
        if (plans.isEmpty()) return;

        AtomicBoolean ready = new AtomicBoolean(false);
        tasks.add(new SubmitTask(plans, ready));

        while (!ready.get()) {
            processResults();

            Thread.yield();
            JavaUtils.onSpinWait();
        }

        // Drain any results added between the last poll and ready being set.
        processResults();
    }

    @CallingThread(ThreadType.SERVER)
    public void runAndWait(Runnable fn) {
        AtomicBoolean done = new AtomicBoolean(false);
        tasks.add(new ExecTask(fn, done));
        while (!done.get()) {
            Thread.yield();
            JavaUtils.onSpinWait();
        }
    }

    @CallingThread(ThreadType.SERVER)
    public void processResults() {
        TerminalResult result;

        while ((result = results.poll()) != null) {
            result.task()
                .execute(result.data());
        }
    }

    @CallingThread(ThreadType.SERVER)
    public void compileExecutor(KernelExecutor<?> executor) {
        runAndWait(() -> { executor.compile(this.constants); });
    }

    @CallingThread(ThreadType.CLIENT)
    public void run() {
        while (true) {
            Task task;

            try {
                task = tasks.take();
            } catch (InterruptedException e) {
                continue;
            }

            if (task instanceof SubmitTask submit) {
                this.submitPlans(submit.plans, submit.ready);
            } else if (task instanceof ExecTask exec) {
                exec.fn()
                    .run();
                exec.done()
                    .set(true);
            }
        }
    }

    @CallingThread(ThreadType.CLIENT)
    private void submitPlans(List<ComputePlan> plans, AtomicBoolean ready) {
        LOGGER.info("Processing batch with {} plans", plans.size());

        int totalJobs = 0;
        int totalBuffers = 0;
        int[] planBase = new int[plans.size()];
        int[] bufferBase = new int[plans.size()];

        for (int p = 0; p < plans.size(); p++) {
            planBase[p] = totalJobs;
            bufferBase[p] = totalBuffers;
            totalJobs += plans.get(p).nextSubmission;
            totalBuffers += plans.get(p).nextBuffer;
        }

        GlobalJob[] globalJobs = new GlobalJob[totalJobs];

        for (int p = 0; p < plans.size(); p++) {
            for (Entry<KernelJob> e : plans.get(p).submits.int2ObjectEntrySet()) {
                globalJobs[planBase[p] + e.getIntKey()] = new GlobalJob(planBase[p], bufferBase[p], e.getValue());
            }
        }

        int maxLevel = totalJobs > 0 ? plans.stream()
            .mapToInt(p -> p.maxLevel)
            .max()
            .getAsInt() : 0;

        int arenaLength = 0;

        for (GlobalJob job : globalJobs) {
            for (var output : job.job.outputs()
                .values()) {
                arenaLength += MathUtil.alignTo(output.getBufferLength(), 16);
            }
        }

        arenaLength *= 2;

        GPUBuffer[] buffers = new GPUBuffer[totalBuffers];

        // Track how many real (non-cached) dispatches each executor made, for EMA timing.
        Object2IntOpenHashMap<KernelExecutor> dispatchCounts = new Object2IntOpenHashMap<>();

        // Pre-compute readback capacity from terminal descriptors so we can resize
        // before recording vkCmdFillBuffer, avoiding use of a destroyed VkBuffer.
        int neededReadback = 0;
        {
            Set<Integer> seen = new HashSet<>();
            for (int p = 0; p < plans.size(); p++) {
                int bb = bufferBase[p];
                for (var terminal : plans.get(p).terminals) {
                    for (BufferDescriptor desc : terminal.inputs()
                        .values()) {
                        int gid = bb + desc.bufferId();
                        if (seen.add(gid)) {
                            neededReadback += desc.getBufferLength();
                        }
                    }
                }
            }
        }
        if (readback.byteLen() < neededReadback) {
            readback = readback
                .resize(KernelContext.getVmaAllocator(), MathUtil.alignTo(neededReadback, KernelContext.CHUNK_SIZE));
        }

        long batchStart = System.nanoTime();

        beginVulkanBatch();

        constants.update(commandBuffer);

        arenaAllocator.reset();

        if (arena.byteLen() < arenaLength) {
            arena = arena
                .resize(KernelContext.getVmaAllocator(), MathUtil.alignTo(arenaLength, KernelContext.CHUNK_SIZE));
        }

        bindDescriptorBuffers();

        vkCmdFillBuffer(commandBuffer, arena.buffer(), 0, arena.byteLen(), 0);
        vkCmdFillBuffer(commandBuffer, readback.buffer(), 0, readback.byteLen(), 0);

        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkBufferMemoryBarrier.Buffer barriers = VkBufferMemoryBarrier.calloc(2, stack);

            barriers.get(0)
                .sType$Default()
                .srcAccessMask(VK_ACCESS_TRANSFER_WRITE_BIT)
                .dstAccessMask(VK_ACCESS_SHADER_READ_BIT)
                .srcQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
                .dstQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
                .buffer(arena.buffer())
                .offset(0)
                .size(arena.byteLen());

            barriers.get(1)
                .sType$Default()
                .srcAccessMask(VK_ACCESS_TRANSFER_WRITE_BIT)
                .dstAccessMask(VK_ACCESS_SHADER_READ_BIT)
                .srcQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
                .dstQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
                .buffer(readback.buffer())
                .offset(0)
                .size(readback.byteLen());

            vkCmdPipelineBarrier(
                commandBuffer,
                VK_PIPELINE_STAGE_TRANSFER_BIT,
                VK_PIPELINE_STAGE_COMPUTE_SHADER_BIT,
                0,
                null,
                barriers,
                null);
        }

        for (int l = 0; l <= maxLevel; l++) {
            Map<KernelExecutor, List<Pair<GlobalJob, KernelSubmission>>> submits = new HashMap<>();

            for (int i = 0; i < totalJobs; i++) {
                KernelJob job = globalJobs[i].job();
                KernelSubmissionToken submission = job.submission();

                if (submission.level() == l) {
                    var s = submits.computeIfAbsent(submission.executor(), $ -> new ArrayList<>());

                    Map<String, GPUBuffer> inputs = new HashMap<>();

                    // Resolve input buffers using the plan's buffer-base offset.
                    // All dependency jobs are guaranteed submitted (level ordering ensures this).
                    for (Map.Entry<String, BufferDescriptor> e : job.inputs()
                        .entrySet()) {
                        inputs.put(
                            e.getKey(),
                            buffers[globalJobs[i].bufferBase() + e.getValue()
                                .bufferId()]);
                    }

                    // noinspection unchecked
                    s.add(Pair.of(globalJobs[i], new KernelSubmission(submission.key(), inputs)));

                    dispatchCounts.addTo(submission.executor(), 1);
                }
            }

            submits.forEach((exec, submissionList) -> {
                try {
                    KernelSubmission[] submissionArray = DataUtils
                        .mapToArray(submissionList, KernelSubmission[]::new, Pair::right);

                    @SuppressWarnings("unchecked")
                    KernelSubmissionResult[] results = exec.submit(commandBuffer, arenaAllocator, submissionArray);

                    List<ArenaRange> dispatchOutputRanges = new ArrayList<>();

                    for (int i = 0; i < results.length; i++) {
                        var pair = submissionList.get(i);

                        // Write output buffers into the global buffer array using the plan's buffer-base offset.
                        for (Map.Entry<String, GPUBuffer> e : results[i].outputs()
                            .entrySet()) {
                            BufferDescriptor outDesc = pair.left()
                                .job()
                                .outputs()
                                .get(e.getKey());
                            GPUBuffer outBuf = e.getValue();
                            buffers[pair.left()
                                .bufferBase() + outDesc.bufferId()] = outBuf;
                            dispatchOutputRanges
                                .add(new ArenaRange(outBuf.getBufferOffset(), outBuf.getBufferLength()));
                        }
                    }

                    insertOutputBarriers(commandBuffer, dispatchOutputRanges);
                } catch (Throwable t) {
                    LOGGER.error(
                        "Error while submitting compute dispatches for kernel executor {}.\nKeys: {}",
                        exec,
                        submissionList,
                        t);
                    throw new RuntimeException(
                        "Error while submitting compute dispatched for kernel executor " + exec,
                        t);
                }
            });
        }

        // --- Step D: identify terminal outputs and build CopyRegion list ---
        //
        // Collect the set of global buffer indices referenced by any terminal across all plans.
        // Each unique terminal buffer gets a slot in the readback buffer.

        // Map from global buffer index to readback byte offset.
        Int2IntOpenHashMap terminalReadbackOffsets = new Int2IntOpenHashMap();
        int readbackOffset = 0;

        for (int p = 0; p < plans.size(); p++) {
            ComputePlan plan = plans.get(p);
            int bb = bufferBase[p];

            for (var terminal : plan.terminals) {
                for (BufferDescriptor desc : terminal.inputs()
                    .values()) {
                    int globalBufIdx = bb + desc.bufferId();
                    if (!terminalReadbackOffsets.containsKey(globalBufIdx)) {
                        terminalReadbackOffsets.put(globalBufIdx, readbackOffset);
                        GPUBuffer buf = buffers[globalBufIdx];
                        readbackOffset += buf.getBufferLength();
                    }
                }
            }
        }

        List<CopyRegion> copies = new ArrayList<>();

        terminalReadbackOffsets.forEach((int bufId, int offset) -> {
            GPUBuffer buf = buffers[bufId];
            copies.add(new CopyRegion(buf.getBufferOffset(), offset, buf.getBufferLength()));
        });

        submitVulkanBatch(commandBuffer, copies);

        // GPU is now idle; read terminal data from the persistently mapped readback buffer.
        long batchElapsed = System.nanoTime() - batchStart;
        updateEMAs(dispatchCounts, batchElapsed);

        LOGGER.info("Batch took {} ms", String.format("%.2f", batchElapsed / 1000000f));

        // Buffer descriptors in terminal inputs use local buffer IDs; offset by bufferBase[p].
        for (int p = 0; p < plans.size(); p++) {
            ComputePlan plan = plans.get(p);
            int bb = bufferBase[p];

            // Cache downloads within a plan: multiple terminals may share the same buffer.
            ByteBuffer[] downloads = new ByteBuffer[plan.nextBuffer];

            for (var terminal : plan.terminals) {
                Map<String, ByteBuffer> inputs = resolveReadbackDownloads(
                    terminal.inputs(),
                    bb,
                    buffers,
                    downloads,
                    terminalReadbackOffsets,
                    readback.mapped());
                this.results.add(new TerminalResult(inputs, terminal.task()));
            }
        }

        ready.set(true);
    }

    // -------------------------------------------------------------------------
    // Vulkan dispatch helpers
    // -------------------------------------------------------------------------

    @CallingThread(ThreadType.CLIENT)
    private void beginVulkanBatch() {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            check(vkResetCommandBuffer(commandBuffer, 0));
            check(
                vkBeginCommandBuffer(
                    commandBuffer,
                    VkCommandBufferBeginInfo.calloc(stack)
                        .sType$Default()
                        .flags(VK_COMMAND_BUFFER_USAGE_ONE_TIME_SUBMIT_BIT)));
            vkCmdBindDescriptorSets(
                commandBuffer,
                VK_PIPELINE_BIND_POINT_COMPUTE,
                pipelineLayout,
                0,
                stack.longs(descriptorSet0, descriptorSet1),
                null);
        }
    }

    @CallingThread(ThreadType.CLIENT)
    private void insertOutputBarriers(VkCommandBuffer cmd, List<ArenaRange> outputs) {
        if (outputs.isEmpty()) return;

        long arenaBuf = arena.buffer();

        VkBufferMemoryBarrier.Buffer barriers = VkBufferMemoryBarrier.calloc(outputs.size());

        try {
            for (int i = 0; i < outputs.size(); i++) {
                ArenaRange r = outputs.get(i);
                barriers.get(i)
                    .sType$Default()
                    .srcAccessMask(VK_ACCESS_SHADER_WRITE_BIT)
                    .dstAccessMask(VK_ACCESS_SHADER_READ_BIT)
                    .srcQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
                    .dstQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
                    .buffer(arenaBuf)
                    .offset(r.offset())
                    .size(r.byteLen());
            }

            vkCmdPipelineBarrier(
                cmd,
                VK_PIPELINE_STAGE_COMPUTE_SHADER_BIT,
                VK_PIPELINE_STAGE_COMPUTE_SHADER_BIT,
                0,
                null,
                barriers,
                null);
        } finally {
            barriers.free();
        }
    }

    @CallingThread(ThreadType.WORKER)
    private void submitVulkanBatch(VkCommandBuffer cmd, List<CopyRegion> toCopy) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            // COMPUTE → TRANSFER: scoped to terminal output regions only
            long arenaBuf = arena.buffer();
            long readbackBuf = readback.buffer();

            if (!toCopy.isEmpty()) {
                VkBufferMemoryBarrier.Buffer copyBarriers = VkBufferMemoryBarrier.calloc(toCopy.size());

                try {
                    for (int i = 0; i < toCopy.size(); i++) {
                        CopyRegion r = toCopy.get(i);
                        copyBarriers.get(i)
                            .sType$Default()
                            .srcAccessMask(VK_ACCESS_SHADER_WRITE_BIT)
                            .dstAccessMask(VK_ACCESS_TRANSFER_READ_BIT)
                            .srcQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
                            .dstQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
                            .buffer(arenaBuf)
                            .offset(r.srcOffset())
                            .size(r.byteLen());
                    }

                    vkCmdPipelineBarrier(
                        cmd,
                        VK_PIPELINE_STAGE_COMPUTE_SHADER_BIT,
                        VK_PIPELINE_STAGE_TRANSFER_BIT,
                        0,
                        null,
                        copyBarriers,
                        null);
                } finally {
                    copyBarriers.free();
                }

                var copyRegions = VkBufferCopy.calloc(toCopy.size());

                try {
                    for (int i = 0; i < toCopy.size(); i++) {
                        KernelScheduler.CopyRegion r = toCopy.get(i);
                        copyRegions.get(i)
                            .srcOffset(r.srcOffset())
                            .dstOffset(r.dstOffset())
                            .size(r.byteLen());
                    }

                    vkCmdCopyBuffer(cmd, arenaBuf, readbackBuf, copyRegions);
                } finally {
                    copyRegions.free();
                }
            }

            check(vkEndCommandBuffer(cmd));

            check(vkResetFences(getDevice(), stack.longs(fence)));
            check(
                vkQueueSubmit(
                    KernelContext.getComputeQueue(),
                    VkSubmitInfo.calloc(stack)
                        .sType$Default()
                        .pCommandBuffers(stack.pointers(cmd)),
                    fence));
            check(vkWaitForFences(getDevice(), stack.longs(fence), true, Long.MAX_VALUE));
        }
    }

    /// Reads terminal input buffers from the Vulkan readback buffer's mapped view.
    /// Each buffer that has not yet been sliced is extracted from the readback mapped ByteBuffer
    /// using the pre-computed readback offset tracked during CopyRegion construction.
    @CallingThread(ThreadType.CLIENT)
    private Map<String, ByteBuffer> resolveReadbackDownloads(Map<String, BufferDescriptor> terminalInputs,
        int bufferBaseOffset, GPUBuffer[] buffers, ByteBuffer[] downloads, Int2IntOpenHashMap terminalReadbackOffsets,
        ByteBuffer readbackMapped) {
        Map<String, ByteBuffer> out = new HashMap<>();

        for (var input : terminalInputs.entrySet()) {
            int localBufId = input.getValue()
                .bufferId();

            if (downloads[localBufId] == null) {
                int globalBufIdx = bufferBaseOffset + localBufId;
                GPUBuffer buf = buffers[globalBufIdx];
                int byteLen = buf.getBufferLength();
                int rbOffset = terminalReadbackOffsets.get(globalBufIdx);

                ByteBuffer slice = ByteBuffer.allocateDirect(byteLen)
                    .order(ByteOrder.nativeOrder());

                MemoryUtil
                    .memCopy(MemoryUtil.memAddress(readbackMapped) + rbOffset, MemoryUtil.memAddress(slice), byteLen);

                downloads[localBufId] = slice;
            }

            out.put(input.getKey(), downloads[localBufId]);
        }

        return out;
    }

    // -------------------------------------------------------------------------
    // EMA timing
    // -------------------------------------------------------------------------

    @CallingThread(ThreadType.CLIENT)
    private void updateEMAs(Object2IntOpenHashMap<KernelExecutor> dispatchCounts, long totalElapsedNs) {
        int totalDispatches = dispatchCounts.values()
            .intStream()
            .sum();

        if (totalDispatches == 0) return;
        // Attribute wall-clock time equally across all dispatched kernels.
        // This is approximate (kernels overlap on the GPU), but sufficient for budgeting.
        long nsPerDispatch = totalElapsedNs / totalDispatches;

        synchronized (emaLock) {
            for (var e : Object2IntMaps.fastIterable(dispatchCounts)) {
                recordSample(e.getKey(), nsPerDispatch);
            }
        }
    }

    @CallingThread(ThreadType.SERVER)
    private long getEstimateNs(KernelExecutor exec) {
        long[] ema = executorEma.get(exec);
        return (ema == null || ema[1] == 0) ? DEFAULT_ESTIMATE_NS : ema[0];
    }

    @CallingThread(ThreadType.CLIENT)
    private void recordSample(KernelExecutor exec, long sampleNs) {
        long[] ema = executorEma.computeIfAbsent(exec, k -> new long[2]);
        ema[0] = ema[1] == 0 ? sampleNs : (long) (EMA_ALPHA * sampleNs + (1 - EMA_ALPHA) * ema[0]);
        ema[1]++;
    }

    private void createDescriptorSetLayouts() {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkDescriptorSetLayoutBinding.Buffer binding = VkDescriptorSetLayoutBinding.calloc(1, stack)
                .binding(0)
                .descriptorType(VK_DESCRIPTOR_TYPE_STORAGE_BUFFER)
                .descriptorCount(1)
                .stageFlags(VK_SHADER_STAGE_COMPUTE_BIT);

            VkDescriptorSetLayoutCreateInfo layoutInfo = VkDescriptorSetLayoutCreateInfo.calloc(stack)
                .sType$Default()
                .pBindings(binding);

            check(vkCreateDescriptorSetLayout(getDevice(), layoutInfo, null, lp));
            descriptorSetLayout0 = lp.get(0);

            check(vkCreateDescriptorSetLayout(getDevice(), layoutInfo, null, lp));
            descriptorSetLayout1 = lp.get(0);

            check(vkCreateDescriptorSetLayout(getDevice(), layoutInfo, null, lp));
            descriptorSetLayout2 = lp.get(0);
        }
    }

    private void createPipelineLayout() {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkPushConstantRange.Buffer pushConstantRange = VkPushConstantRange.calloc(1, stack)
                .stageFlags(VK_SHADER_STAGE_COMPUTE_BIT)
                .offset(0)
                .size(128);

            VkPipelineLayoutCreateInfo layoutInfo = VkPipelineLayoutCreateInfo.calloc(stack)
                .sType$Default()
                .pSetLayouts(stack.longs(descriptorSetLayout0, descriptorSetLayout1, descriptorSetLayout2))
                .pPushConstantRanges(pushConstantRange);

            check(vkCreatePipelineLayout(getDevice(), layoutInfo, null, lp));
            pipelineLayout = lp.get(0);
        }
    }

    private void createDescriptorPool() {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            // Three descriptor sets (set 0 = constants, set 1 = arena, set 2 = dynamic custom), each with one SSBO
            // binding.
            VkDescriptorPoolSize.Buffer poolSize = VkDescriptorPoolSize.calloc(1, stack)
                .type(VK_DESCRIPTOR_TYPE_STORAGE_BUFFER)
                .descriptorCount(3);

            VkDescriptorPoolCreateInfo poolInfo = VkDescriptorPoolCreateInfo.calloc(stack)
                .sType$Default()
                .maxSets(3)
                .pPoolSizes(poolSize);

            check(vkCreateDescriptorPool(getDevice(), poolInfo, null, lp));
            descriptorPool = lp.get(0);
        }
    }

    private void allocateDescriptorSets() {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkDescriptorSetAllocateInfo allocInfo = VkDescriptorSetAllocateInfo.calloc(stack)
                .sType$Default()
                .descriptorPool(descriptorPool)
                .pSetLayouts(stack.longs(descriptorSetLayout0, descriptorSetLayout1, descriptorSetLayout2));

            LongBuffer pSets = stack.mallocLong(3);
            check(vkAllocateDescriptorSets(getDevice(), allocInfo, pSets));
            descriptorSet0 = pSets.get(0);
            descriptorSet1 = pSets.get(1);
            descriptorSet2 = pSets.get(2);
        }
    }

    private void bindDescriptorBuffers() {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkDescriptorBufferInfo.Buffer bufInfoConstants = VkDescriptorBufferInfo.calloc(1, stack)
                .buffer(
                    constants.getDeviceBuffer()
                        .buffer())
                .offset(0)
                .range(
                    constants.getDeviceBuffer()
                        .byteLen());

            VkDescriptorBufferInfo.Buffer bufInfoArena = VkDescriptorBufferInfo.calloc(1, stack)
                .buffer(arena.buffer())
                .offset(0)
                .range(arena.byteLen());

            VkWriteDescriptorSet.Buffer writes = VkWriteDescriptorSet.calloc(2, stack);

            writes.get(0)
                .sType$Default()
                .dstSet(descriptorSet0)
                .dstBinding(0)
                .descriptorCount(1)
                .descriptorType(VK_DESCRIPTOR_TYPE_STORAGE_BUFFER)
                .pBufferInfo(bufInfoConstants);

            writes.get(1)
                .sType$Default()
                .dstSet(descriptorSet1)
                .dstBinding(0)
                .descriptorCount(1)
                .descriptorType(VK_DESCRIPTOR_TYPE_STORAGE_BUFFER)
                .pBufferInfo(bufInfoArena);

            vkUpdateDescriptorSets(getDevice(), writes, null);
        }
    }

    private void createCommandPool() {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkCommandPoolCreateInfo poolInfo = VkCommandPoolCreateInfo.calloc(stack)
                .sType$Default()
                .flags(VK_COMMAND_POOL_CREATE_RESET_COMMAND_BUFFER_BIT | VK_COMMAND_POOL_CREATE_TRANSIENT_BIT)
                .queueFamilyIndex(KernelContext.getComputeQueueFamily());

            check(vkCreateCommandPool(getDevice(), poolInfo, null, lp));
            commandPool = lp.get(0);

            VkCommandBufferAllocateInfo allocInfo = VkCommandBufferAllocateInfo.calloc(stack)
                .sType$Default()
                .commandPool(commandPool)
                .level(VK_COMMAND_BUFFER_LEVEL_PRIMARY)
                .commandBufferCount(1);

            check(vkAllocateCommandBuffers(getDevice(), allocInfo, pp));
            commandBuffer = new VkCommandBuffer(pp.get(0), getDevice());
        }
    }

    private void createFence() {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkFenceCreateInfo fenceInfo = VkFenceCreateInfo.calloc(stack)
                .sType$Default()
                .flags(VK_FENCE_CREATE_SIGNALED_BIT); // pre-signaled; first wait is a no-op
            check(vkCreateFence(getDevice(), fenceInfo, null, lp));
            fence = lp.get(0);
        }
    }
}
