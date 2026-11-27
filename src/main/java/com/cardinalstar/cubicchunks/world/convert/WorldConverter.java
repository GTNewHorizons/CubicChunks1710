package com.cardinalstar.cubicchunks.world.convert;

import java.io.File;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.nio.ByteBuffer;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.text.SimpleDateFormat;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import javax.annotation.ParametersAreNonnullByDefault;

/**
 * Converts a whole world save between vanilla's Anvil format and cube storage, offline: no world or server is running,
 * and no mod code handles the data. Block data, tile entities, entities and mods' tags are carried over as they are;
 * the converter only regroups them (16 sections of a chunk are 16 cubes of a column) and writes what the target format
 * keeps besides them.
 * <p>
 * The source's data is never changed. The result is written next to it and swapped in at the end, see
 * {@link ConversionInstall};
 * the replaced data stays in a backup folder inside the world.
 */
@ParametersAreNonnullByDefault
public abstract class WorldConverter {

    static final String ANVIL = "region";
    static final String[] CUBIC = { "region2d", "region3d" };
    static final String MARKER = "data/cubicchunks.world_format.dat";

    /** Cube Y range of a vanilla chunk. */
    static final int ANVIL_SECTIONS = 16;

    /**
     * Converts the world in {@code worldRoot}.
     *
     * @param cancelSignal set to stop; the world is then left as it was
     * @throws InterruptedIOException when cancelled
     * @throws IOException            when the conversion fails; the world is then left as it was
     */
    public final ConversionReport convertWorld(File worldRoot, ConversionProgress progress, AtomicBoolean cancelSignal)
        throws IOException {
        // the real folder: a world folder that is a link is not walked into otherwise
        Path root = worldRoot.toPath()
            .toRealPath();
        checkCancelled(cancelSignal);
        ConversionInstall.recover(root);

        ConversionReport report = new ConversionReport();
        List<Path> dimensions = prepare(root);
        requireSpace(root, sourceSize(dimensions));
        takeSessionLock(root);

        ConversionInstall install = new ConversionInstall(root);
        try {
            convert(root, dimensions, install, report, progress, cancelSignal);
            // Not cancellable from here on: the install either happens or does not.
            checkCancelled(cancelSignal);
            progress.update(progress.getCompleted(), progress.getTotal(), "Installing...");
        } catch (IOException | RuntimeException | Error failure) {
            try {
                install.discard();
            } catch (IOException cleanup) {
                failure.addSuppressed(cleanup);
            }
            throw failure;
        }
        install.run();
        progress.update(progress.getCompleted(), progress.getTotal(), "Done.");
        return report;
    }

    /** Checks that the world can be converted and returns its dimension folders, the world root first if it is one. */
    abstract List<Path> prepare(Path root) throws IOException;

    /** Writes the converted world into {@code install.staging()} and plans its install. */
    abstract void convert(Path root, List<Path> dimensions, ConversionInstall install, ConversionReport report,
        ConversionProgress progress, AtomicBoolean cancelSignal) throws IOException;

    /** The source storage folders inside a dimension folder. */
    abstract String[] sourceFolders();

    static void checkCancelled(AtomicBoolean cancelSignal) throws InterruptedIOException {
        if (cancelSignal.get()) throw new InterruptedIOException("World conversion cancelled");
    }

    /**
     * The folders inside {@code root} (itself included, first) that directly hold one of the storage folders
     * {@code names} with at least one file. Dimensions are found by what they contain, as mods name and nest their
     * save folders freely.
     */
    static List<Path> dimensions(Path root, String... names) throws IOException {
        List<Path> found = new ArrayList<>();
        Files.walkFileTree(root, Collections.emptySet(), 3, new SimpleFileVisitor<Path>() {

            @Override
            public FileVisitResult preVisitDirectory(Path directory, BasicFileAttributes attributes)
                throws IOException {
                String name = directory.getFileName()
                    .toString();
                if (!directory.equals(root) && (name.startsWith(".") || isStorageFolder(name)))
                    return FileVisitResult.SKIP_SUBTREE;
                for (String storage : names) {
                    if (holdsFiles(directory.resolve(storage))) {
                        found.add(directory);
                        break;
                    }
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFileFailed(Path file, IOException failure) {
                return FileVisitResult.CONTINUE;
            }
        });
        Collections.sort(found);
        if (found.remove(root)) found.add(0, root);
        return found;
    }

    /** The dimension folders that hold Anvil region files. */
    static List<Path> anvilDimensions(Path root) throws IOException {
        List<Path> dimensions = new ArrayList<>();
        for (Path dimension : dimensions(root, ANVIL)) {
            if (!regionFiles(dimension).isEmpty()) dimensions.add(dimension);
        }
        return dimensions;
    }

    /** The region files ({@code r.X.Z.mca}) of a dimension folder, sorted. */
    static List<Path> regionFiles(Path dimension) throws IOException {
        List<Path> files = new ArrayList<>();
        Path folder = dimension.resolve(ANVIL);
        if (!Files.isDirectory(folder)) return files;
        try (Stream<Path> children = Files.list(folder)) {
            for (Path child : (Iterable<Path>) children::iterator) {
                if (Files.isRegularFile(child) && AnvilRegionReader.regionCoords(
                    child.getFileName()
                        .toString())
                    != null) files.add(child);
            }
        }
        files.sort(null);
        return files;
    }

    private static boolean isStorageFolder(String name) {
        return name.equals(ANVIL) || name.equals(CUBIC[0]) || name.equals(CUBIC[1]);
    }

    static boolean holdsFiles(Path folder) throws IOException {
        if (!Files.isDirectory(folder)) return false;
        try (Stream<Path> children = Files.list(folder)) {
            return children.anyMatch(Files::isRegularFile);
        }
    }

    private long sourceSize(List<Path> dimensions) throws IOException {
        long size = 0;
        for (Path dimension : dimensions) {
            for (String name : sourceFolders()) {
                Path folder = dimension.resolve(name);
                if (!Files.isDirectory(folder)) continue;
                try (Stream<Path> children = Files.list(folder)) {
                    for (Path child : (Iterable<Path>) children::iterator) {
                        if (Files.isRegularFile(child)) size += Files.size(child);
                    }
                }
            }
        }
        return size;
    }

    /** The converted world is written next to the original, which is kept: both have to fit. */
    private static void requireSpace(Path root, long sourceSize) throws IOException {
        long needed = sourceSize + sourceSize / 4 + (64L << 20);
        long usable;
        try {
            usable = Files.getFileStore(root)
                .getUsableSpace();
        } catch (IOException | RuntimeException e) {
            return; // unknown: let the conversion find out
        }
        if (usable > 0 && usable < needed) {
            throw new IOException(
                String.format(
                    Locale.ROOT,
                    "Not enough free disk space: the conversion needs about %d MB, %d MB are free",
                    needed >> 20,
                    usable >> 20));
        }
    }

    /**
     * Vanilla's session lock: a game or server that still has this world open notices the new owner and stops saving
     * into it instead of writing into a world that is being replaced.
     */
    private static void takeSessionLock(Path root) throws IOException {
        Path lock = root.resolve("session.lock");
        if (!Files.exists(lock)) return;
        Files.write(
            lock,
            ByteBuffer.allocate(8)
                .putLong(System.currentTimeMillis())
                .array());
    }

    /** A new folder for the data this conversion replaces. */
    static Path newBackupFolder(Path root, String kind) {
        String stamp = new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.ROOT).format(new Date());
        Path backups = root.resolve(ConversionInstall.BACKUPS);
        Path backup = backups.resolve(stamp + "-" + kind);
        for (int i = 2; Files.exists(backup); i++) backup = backups.resolve(stamp + "-" + kind + "-" + i);
        return backup;
    }

    interface Sink<T> {

        void accept(T result) throws IOException;
    }

    /**
     * Runs {@code tasks} on several threads and hands their results to {@code sink} on the calling thread, in the
     * order of the tasks. Only a few results wait at a time, so memory use does not grow with the world.
     */
    static <T> void runOrdered(Iterator<Callable<T>> tasks, Sink<T> sink, AtomicBoolean cancelSignal)
        throws IOException {
        int threads = Math.max(
            1,
            Math.min(
                8,
                Runtime.getRuntime()
                    .availableProcessors() - 1));
        AtomicInteger number = new AtomicInteger();
        ExecutorService pool = Executors.newFixedThreadPool(threads, runnable -> {
            Thread thread = new Thread(runnable, "CC-WorldConverter-" + number.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        });
        ArrayDeque<Future<T>> waiting = new ArrayDeque<>();
        try {
            while (tasks.hasNext() || !waiting.isEmpty()) {
                checkCancelled(cancelSignal);
                while (tasks.hasNext() && waiting.size() < threads * 2) waiting.add(pool.submit(tasks.next()));
                T result;
                try {
                    result = waiting.remove()
                        .get();
                } catch (ExecutionException e) {
                    Throwable cause = e.getCause();
                    if (cause instanceof IOException) throw (IOException) cause;
                    if (cause instanceof RuntimeException) throw (RuntimeException) cause;
                    if (cause instanceof Error) throw (Error) cause;
                    throw new IOException(cause);
                } catch (InterruptedException e) {
                    Thread.currentThread()
                        .interrupt();
                    throw new InterruptedIOException("World conversion interrupted");
                }
                sink.accept(result);
            }
        } finally {
            // Running tasks finish their batch. They are never interrupted: an interrupt during file I/O closes the
            // file's channel under the storage that owns it, which then cannot close and release its lock.
            for (Future<T> pending : waiting) pending.cancel(false);
            pool.shutdown();
            try {
                // no task may still read the source or the storage when the caller closes them
                while (!pool.awaitTermination(1, TimeUnit.MINUTES)) {}
            } catch (InterruptedException e) {
                Thread.currentThread()
                    .interrupt();
            }
        }
    }
}
