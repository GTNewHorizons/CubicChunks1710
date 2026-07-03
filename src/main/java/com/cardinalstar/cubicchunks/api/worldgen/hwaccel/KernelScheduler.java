package com.cardinalstar.cubicchunks.api.worldgen.hwaccel;

import static org.lwjgl.opengl.ARBSync.glClientWaitSync;
import static org.lwjgl.opengl.ARBSync.glDeleteSync;
import static org.lwjgl.opengl.ARBSync.glFenceSync;
import static org.lwjgl.opengl.GL11.glFlush;
import static org.lwjgl.opengl.GL11.glGetError;
import static org.lwjgl.opengl.GL30.glBindBufferBase;
import static org.lwjgl.opengl.GL32.GL_ALREADY_SIGNALED;
import static org.lwjgl.opengl.GL32.GL_SYNC_GPU_COMMANDS_COMPLETE;
import static org.lwjgl.opengl.GL42.glMemoryBarrier;
import static org.lwjgl.opengl.GL43.GL_SHADER_STORAGE_BARRIER_BIT;
import static org.lwjgl.opengl.GL43.GL_SHADER_STORAGE_BUFFER;
import static org.lwjgl.opengl.GL44.GL_CLIENT_MAPPED_BUFFER_BARRIER_BIT;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.opengl.GLSync;
import org.lwjgl.util.glu.GLU;

import com.cardinalstar.cubicchunks.api.worldgen.hwaccel.buffer.ArenaHardwareBuffer;
import com.cardinalstar.cubicchunks.api.worldgen.hwaccel.buffer.BufferAllocator;
import com.cardinalstar.cubicchunks.api.worldgen.hwaccel.buffer.BufferDataType;
import com.cardinalstar.cubicchunks.api.worldgen.hwaccel.buffer.BufferDescriptor;
import com.cardinalstar.cubicchunks.api.worldgen.hwaccel.buffer.GPUBuffer;
import com.cardinalstar.cubicchunks.async.CallingThread;
import com.cardinalstar.cubicchunks.async.ThreadType;
import com.cardinalstar.cubicchunks.util.DataUtils;
import com.cardinalstar.cubicchunks.util.JavaUtils;
import com.cardinalstar.cubicchunks.util.MathUtil;
import com.github.bsideup.jabel.Desugar;
import it.unimi.dsi.fastutil.Pair;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap.Entry;
import it.unimi.dsi.fastutil.objects.Object2IntMaps;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;

@SuppressWarnings("rawtypes")
public class KernelScheduler {

    @Desugar
    private record TerminalResult(Map<String, ByteBuffer> data, TerminalTask task) {}

    // Carries the plan-local-to-global index offsets alongside the job.
    @Desugar
    private record GlobalJob(int planBase, int bufferBase, KernelJob job) {}

    private interface Task {}

    @Desugar
    private record SubmitTask(List<ComputePlan> plans, AtomicBoolean ready) implements Task {}
    @Desugar
    private record ExecTask(Runnable fn, Lock lock, Condition condition) implements Task {}

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

    private final Runnable pollForTasks = this::poll;
    private Runnable state = pollForTasks;

    private final ArenaHardwareBuffer arena = new ArenaHardwareBuffer();

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    @CallingThread(ThreadType.SERVER)
    public void setGpuBudget(long ns) { this.gpuBudgetNs = ns; }
    @CallingThread(ThreadType.SERVER)
    public long getGpuBudget()        { return gpuBudgetNs;    }

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
                total += getEstimateNs(e.getValue().submission().executor());
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
        Lock lock = new ReentrantLock();
        Condition condition = lock.newCondition();
        tasks.add(new ExecTask(fn, lock, condition));
        try {
            lock.lock();
            condition.await();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } finally {
            lock.unlock();
        }
    }

    @CallingThread(ThreadType.SERVER)
    public void processResults() {
        TerminalResult result;

        while ((result = results.poll()) != null) {
            result.task().execute(result.data());
        }
    }

    @CallingThread(ThreadType.CLIENT)
    public void tick() {
        state.run();
    }

    @CallingThread(ThreadType.CLIENT)
    private void poll() {
        Task task;

        while ((task = this.tasks.poll()) != null) {
            if (task instanceof SubmitTask submit) {
                this.submitPlans(submit.plans, submit.ready);
            } else if (task instanceof ExecTask exec) {
                exec.fn().run();
                try {
                    exec.lock().lock();
                    exec.condition().signalAll();
                } finally {
                    exec.lock().unlock();
                }
            }
        }
    }

    @CallingThread(ThreadType.CLIENT)
    private void submitPlans(List<ComputePlan> plans, AtomicBoolean ready) {
        LOGGER.info("Processing batch with {} plans", plans.size());

        // --- Step A: build the global job table ---
        //
        // Each plan's submission IDs start at 0. We assign a planBase (cumulative submission
        // count) and bufferBase (cumulative buffer-slot count) so that every job and every
        // buffer slot has a globally unique index across the whole batch.
        int totalJobs    = 0;
        int totalBuffers = 0;
        int[] planBase   = new int[plans.size()];
        int[] bufferBase = new int[plans.size()];
        for (int p = 0; p < plans.size(); p++) {
            planBase[p]   = totalJobs;
            bufferBase[p] = totalBuffers;
            totalJobs    += plans.get(p).nextSubmission;
            totalBuffers += plans.get(p).nextBuffer;
        }

        GlobalJob[] globalJobs = new GlobalJob[totalJobs];

        for (int p = 0; p < plans.size(); p++) {
            for (Entry<KernelJob> e : plans.get(p).submits.int2ObjectEntrySet()) {
                globalJobs[planBase[p] + e.getIntKey()] =
                    new GlobalJob(planBase[p], bufferBase[p], e.getValue());
            }
        }

        int maxLevel = totalJobs > 0 ? plans.stream().mapToInt(p -> p.maxLevel).max().getAsInt() : 0;

        int arenaLength = 0;

        for (GlobalJob job : globalJobs) {
            for (var output : job.job.outputs().values()) {
                arenaLength += MathUtil.alignTo(output.getBufferLength(), KernelContext.getSSBOAlignment());
            }
        }

        checkError();

        // --- Step C: wave dispatch ---
        //
        // All jobs at the same level are independent of each other and can be dispatched
        // without a barrier between them. A single barrier is issued between consecutive
        // levels. This amortises the barrier cost across all plans in the batch: for N chunks
        // the pipeline uses 6 barriers total instead of 6 per chunk.

        arenaLength *= 2;

        if (this.arena.getByteLen() < arenaLength) {
            this.arena.resize(arenaLength);
        } else {
            this.arena.reset();
        }

        Allocator alloc = new Allocator(arena);

        GPUBuffer[] buffers = new GPUBuffer[totalBuffers];

        // Track how many real (non-cached) dispatches each executor made, for EMA timing.
        Object2IntOpenHashMap<KernelExecutor> dispatchCounts = new Object2IntOpenHashMap<>();

        long batchStart = System.nanoTime();

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
                    for (Map.Entry<String, BufferDescriptor> e : job.inputs().entrySet()) {
                        inputs.put(e.getKey(), buffers[globalJobs[i].bufferBase() + e.getValue().bufferId()]);
                    }

                    //noinspection unchecked
                    s.add(Pair.of(globalJobs[i], new KernelSubmission(submission.key(), inputs)));

                    dispatchCounts.addTo(submission.executor(), 1);
                }
            }

            submits.forEach((exec, submissionList) -> {
                KernelSubmission[] submissionArray = DataUtils.mapToArray(submissionList, KernelSubmission[]::new, Pair::right);

                checkError("pre " + exec.toString());

                @SuppressWarnings("unchecked")
                KernelSubmissionResult[] results = exec.submit(alloc, submissionArray);

                checkError("post " + exec.toString());

                for (int i = 0; i < results.length; i++) {
                    var pair = submissionList.get(i);

                    // Write output buffers into the global buffer array using the plan's buffer-base offset.
                    for (Map.Entry<String, GPUBuffer> e : results[i].outputs().entrySet()) {
                        BufferDescriptor outDesc = pair.left().job().outputs().get(e.getKey());
                        buffers[pair.left().bufferBase() + outDesc.bufferId()] = e.getValue();
                    }
                }
            });

            glMemoryBarrier(GL_SHADER_STORAGE_BARRIER_BIT);
        }

        GLSync sync = glFenceSync(GL_SYNC_GPU_COMMANDS_COMPLETE, 0);
        glFlush();

        checkError();

        this.state = () -> {
            if (glClientWaitSync(sync, 0, 0) == GL_ALREADY_SIGNALED) {
                checkError();

                this.state = pollForTasks;

                glDeleteSync(sync);

                checkError();

                long batchElapsed = System.nanoTime() - batchStart;
                updateEMAs(dispatchCounts, batchElapsed);

                LOGGER.info("Batch took {} ms", batchElapsed / 1000000);

                // Buffer descriptors in terminal inputs use local buffer IDs; offset by bufferBase[p].
                for (int p = 0; p < plans.size(); p++) {
                    ComputePlan plan = plans.get(p);
                    int bb = bufferBase[p];

                    // Cache downloads within a plan: multiple terminals may share the same buffer.
                    ByteBuffer[] downloads = new ByteBuffer[plan.nextBuffer];

                    for (var terminal : plan.terminals) {
                        Map<String, ByteBuffer> inputs = resolveDownloads(terminal.inputs(), bb, buffers, downloads);
                        this.results.add(new TerminalResult(inputs, terminal.task()));
                    }
                }

                checkError();

                ready.set(true);
            }
        };
    }

    private void checkError() {
        int error = glGetError();

        if (error != 0) {
            LOGGER.error("GL Error: {}", GLU.gluErrorString(error), new Exception());
        }
    }

    private void checkError(String info) {
        int error = glGetError();

        if (error != 0) {
            LOGGER.error("GL Error ({}): {}", info, GLU.gluErrorString(error), new Exception());
        }
    }

    /// Copies any not-yet-downloaded buffers referenced by `terminalInputs` from the mapped arena
    /// into fresh direct ByteBuffers and returns them.
    @CallingThread(ThreadType.CLIENT)
    private Map<String, ByteBuffer> resolveDownloads(
        Map<String, BufferDescriptor> terminalInputs,
        int bufferBaseOffset,
        GPUBuffer[] buffers,
        ByteBuffer[] downloads
    ) {
        Map<String, ByteBuffer> out = new HashMap<>();

        for (var input : terminalInputs.entrySet()) {
            int localBufId = input.getValue().bufferId();

            if (downloads[localBufId] == null) {
                GPUBuffer buf = buffers[bufferBaseOffset + localBufId];

                ByteBuffer downloaded = ByteBuffer.allocateDirect(buf.getBufferLength()).order(ByteOrder.nativeOrder());

                buf.download(downloaded);

                downloads[localBufId] = downloaded;
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
        int totalDispatches = dispatchCounts.values().intStream().sum();

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
        ema[0] = ema[1] == 0 ? sampleNs : (long)(EMA_ALPHA * sampleNs + (1 - EMA_ALPHA) * ema[0]);
        ema[1]++;
    }

    private static class Allocator implements BufferAllocator {

        /// The primary arena buffer, which will be closed after all plans have completed.
        public final ArenaHardwareBuffer arena;

        public Allocator(ArenaHardwareBuffer arena) {
            this.arena = arena;
        }

        @Override
        public GPUBuffer alloc(BufferDataType dataType, int lenX, int lenY, int lenZ) {
            GPUBuffer subbuffer = arena.alloc(dataType, lenX, lenY, lenZ);

            if (subbuffer != null) return subbuffer;

            throw new IllegalStateException("Arena GPU buffer overflowed: something is allocating more memory than it planned for");
        }

        @Override
        public void bindSSBO(int index) {
            glBindBufferBase(GL_SHADER_STORAGE_BUFFER, index, arena.getSsbo());
        }

        @Override
        public void unbindSSBO(int index) {
            glBindBufferBase(GL_SHADER_STORAGE_BUFFER, index, 0);
        }
    }
}
