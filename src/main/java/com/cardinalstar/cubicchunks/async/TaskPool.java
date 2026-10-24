package com.cardinalstar.cubicchunks.async;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import org.jetbrains.annotations.Nullable;

import com.cardinalstar.cubicchunks.CubicChunks;
import com.cardinalstar.cubicchunks.CubicChunksConfig;
import com.google.common.util.concurrent.AbstractFuture;

@SuppressWarnings({ "unused", "UnusedReturnValue" })
public class TaskPool {

    private static final AtomicInteger THREAD_COUNTER = new AtomicInteger(0);

    public static final ThreadFactory THREAD_FACTORY = runnable -> {
        int number = THREAD_COUNTER.incrementAndGet();
        String name = String.format("CC BG Thread %d", number);
        Thread thread = new Thread(runnable, name);
        thread.setDaemon(true);
        return thread;
    };

    private static final Object QUEUE_LOCK = new Object();
    private static final ArrayDeque<TaskContainer<?, ?>> TASK_QUEUE = new ArrayDeque<>(1024);

    private static final List<Thread> THREADS = new ArrayList<>();

    public static void init() {
        THREADS.clear();

        // Saves only run on these workers, so a 1-CPU default (or 0 in the config) must still start one.
        int count = Math.max(1, CubicChunksConfig.optimizations.backgroundThreads);

        for (int i = 0; i < count; i++) {
            Thread worker = new WorkerThread();
            THREADS.add(worker);
            worker.start();
        }
    }

    private static class WorkerThread extends Thread {

        private final AtomicBoolean cancelled = new AtomicBoolean(false);

        public WorkerThread() {
            super(String.format("CC BG Thread %d", THREAD_COUNTER.incrementAndGet()));

            setDaemon(true);
        }

        @Override
        public void run() {
            while (!cancelled.get()) {
                TaskContainer<?, ?> task;

                synchronized (QUEUE_LOCK) {
                    if (TASK_QUEUE.isEmpty()) {
                        try {
                            QUEUE_LOCK.wait();
                        } catch (InterruptedException e) {
                            continue;
                        }
                    }

                    task = TASK_QUEUE.poll();
                }

                if (task == null) continue;

                task.run();
            }
        }
    }

    public static final ITaskExecutor<Runnable, Void> RUNNABLE_EXECUTOR = tasks -> {
        for (var task : tasks) {
            task.getTask()
                .run();
            task.finish(null);
        }
    };

    public static Future<Void> submit(Runnable task) {
        return submit(RUNNABLE_EXECUTOR, task, null);
    }

    public static <TTask, TResult> Future<TResult> submit(ITaskExecutor<TTask, TResult> executor, TTask task) {
        return submit(executor, task, null);
    }

    public static <TTask, TResult> Future<TResult> submit(ITaskExecutor<TTask, TResult> executor, TTask task,
        @Nullable Consumer<TResult> callback) {
        TaskFuture<TTask, TResult> future = new TaskFuture<>(task, callback);

        synchronized (QUEUE_LOCK) {
            if (!TASK_QUEUE.isEmpty()) {
                TaskContainer<?, ?> end = TASK_QUEUE.peekLast();

                if (end.executor == executor) {
                    @SuppressWarnings("unchecked")
                    TaskContainer<TTask, TResult> end2 = (TaskContainer<TTask, TResult>) end;

                    // noinspection unchecked
                    if (end2.executor.canMerge((List<ITaskFuture<TTask, TResult>>) (List<?>) end2.tasks, task)) {
                        end2.tasks.add(future);
                        QUEUE_LOCK.notify();
                        return future;
                    }
                }
            }

            TaskContainer<TTask, TResult> container = new TaskContainer<>(executor);
            container.tasks.add(future);

            TASK_QUEUE.addLast(container);
            QUEUE_LOCK.notify();
            return future;
        }
    }

    public static void flush() {
        while (true) {
            synchronized (QUEUE_LOCK) {
                if (TASK_QUEUE.isEmpty()) return;
            }

            Thread.yield();
        }
    }

    public interface ITaskExecutor<TTask, TResult> {

        void execute(List<ITaskFuture<TTask, TResult>> tasks);

        default boolean canMerge(List<ITaskFuture<TTask, TResult>> tasks, TTask task) {
            return tasks.size() < 128;
        }
    }

    public interface ITaskFuture<TTask, TResult> {

        TTask getTask();

        void finish(TResult value);

        void fail(Throwable t);
    }

    private static class TaskFuture<TTask, TResult> extends AbstractFuture<TResult>
        implements ITaskFuture<TTask, TResult> {

        public final TTask task;
        @Nullable
        private final Consumer<TResult> callback;

        public TaskFuture(TTask task, @Nullable Consumer<TResult> callback) {
            this.task = task;
            this.callback = callback;
        }

        @Override
        public TTask getTask() {
            return task;
        }

        @Override
        public void finish(TResult value) {
            set(value);
            if (callback != null) callback.accept(value);
        }

        @Override
        public void fail(Throwable t) {
            setException(t);
        }
    }

    private static class TaskContainer<TTask, TResult> implements Runnable {

        private final ITaskExecutor<TTask, TResult> executor;
        private final List<TaskFuture<TTask, TResult>> tasks = new ArrayList<>(1);

        public TaskContainer(ITaskExecutor<TTask, TResult> executor) {
            this.executor = executor;
        }

        @Override
        public void run() {
            try {
                tasks.removeIf(TaskFuture::isCancelled);

                // noinspection unchecked
                executor.execute((List<ITaskFuture<TTask, TResult>>) (List<?>) tasks);
            } catch (Throwable t) {
                // Keep the worker alive and wake anyone waiting on a future the executor did not complete.
                CubicChunks.LOGGER.error("Could not run background task", t);
                for (TaskFuture<TTask, TResult> task : tasks) {
                    if (!task.isDone()) task.fail(t);
                }
            }
        }
    }
}
