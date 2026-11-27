package com.cardinalstar.cubicchunks.world.convert;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.OverlappingFileLockException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import com.cardinalstar.cubicchunks.CubicChunks;

/**
 * Puts a finished conversion in place: the converted data is written into a work folder inside the world first, and
 * only when all of it exists are the original and the converted folders swapped, by renames listed in a journal.
 * <p>
 * Whatever interrupts a conversion, the world is in one of two states. Without a journal nothing of the world was
 * touched, and the work folder is discarded. With a journal every rename is known and {@link #recover} carries out
 * the rest. The originals are never deleted: they end up in a backup folder.
 * <p>
 * No path is the source of one journal step and the target of another, so a journal can be replayed from the start
 * however far it got: a step whose source is gone and whose target exists has been carried out.
 */
public final class ConversionInstall {

    /** Holds the converted data until it is installed, and the journal. Its presence marks an unfinished conversion. */
    static final String WORK = ".cubicchunks-conversion";
    /** One folder per conversion with the data it replaced. */
    static final String BACKUPS = ".cubicchunks-backup";

    private static final String JOURNAL = "install.journal";
    private static final String END = "end";
    private static final String MOVE = "move", REPLACE = "replace";
    /** Locked while a conversion works in the folder, so that no other game or server recovers it underneath. */
    private static final String LOCK = "in-progress.lock";

    /**
     * The worlds a conversion of this game is working on. Asked before the lock file: a second look at a lock file by
     * the process that holds it can release the lock on some systems.
     */
    private static final Set<Path> ACTIVE = new HashSet<>();

    private static final class Step {

        final String kind;
        final Path from, to;

        Step(String kind, Path from, Path to) {
            this.kind = kind;
            this.from = from;
            this.to = to;
        }
    }

    private final Path root, work;
    private final List<Step> steps = new ArrayList<>();
    private FileChannel lockChannel;

    ConversionInstall(Path root) throws IOException {
        this.root = root.toRealPath();
        this.work = this.root.resolve(WORK);
        synchronized (ACTIVE) {
            if (!ACTIVE.add(this.root)) throw new IOException("Another conversion of this world is in progress");
        }
        try {
            Files.createDirectories(work);
            lockChannel = lock(work);
            if (lockChannel == null) throw new IOException("Another conversion of this world is in progress");
            Files.createDirectories(staging());
        } catch (IOException | RuntimeException | Error e) {
            release();
            throw e;
        }
    }

    /** The locked lock file of a work folder, or null when a conversion holds it. */
    private static FileChannel lock(Path work) throws IOException {
        FileChannel channel = FileChannel.open(work.resolve(LOCK), StandardOpenOption.CREATE, StandardOpenOption.WRITE);
        try {
            if (channel.tryLock() != null) return channel;
        } catch (OverlappingFileLockException e) {
            // held in this game
        } catch (IOException | RuntimeException e) {
            channel.close();
            throw e;
        }
        channel.close();
        return null;
    }

    /** Gives the work folder up, whatever state it is in. */
    private void release() {
        try {
            if (lockChannel != null) lockChannel.close();
        } catch (IOException e) {
            CubicChunks.LOGGER.warn("Could not release the conversion lock of {}", root, e);
        }
        lockChannel = null;
        synchronized (ACTIVE) {
            ACTIVE.remove(root);
        }
    }

    /** Whether an install of the world in {@code root} was begun and not finished or taken back. */
    public static boolean isPending(Path root) {
        return Files.isRegularFile(
            root.resolve(WORK)
                .resolve(JOURNAL));
    }

    /** Where the converted data is written, laid out like the world folder. */
    Path staging() {
        return work.resolve("output");
    }

    /** Plans a rename to a path that does not exist. Both paths are inside the world folder. */
    void move(Path from, Path to) {
        steps.add(new Step(MOVE, from, to));
    }

    /** Plans a rename that replaces {@code to} if it exists. What it replaces is lost; it cannot be taken back. */
    void replace(Path from, Path to) {
        steps.add(new Step(REPLACE, from, to));
    }

    /**
     * Plans moving the folder {@code from} to {@code to}. An existing {@code to} keeps its contents and receives the
     * children of {@code from}.
     */
    void moveFolder(Path from, Path to) throws IOException {
        if (!Files.exists(to)) {
            move(from, to);
            return;
        }
        try (Stream<Path> children = Files.list(from)) {
            for (Path child : (Iterable<Path>) children::iterator) {
                Path target = to.resolve(
                    child.getFileName()
                        .toString());
                if (Files.exists(target)) throw new IOException("Cannot install over existing " + target);
                move(child, target);
            }
        }
    }

    /**
     * Writes the journal and carries out the planned renames. Nothing of the world has changed before this call, and
     * on any failure before the first rename nothing has changed after it.
     */
    void run() throws IOException {
        Path journal = work.resolve(JOURNAL);
        try {
            try {
                writeJournal(journal);
            } catch (IOException | RuntimeException failure) {
                Files.deleteIfExists(journal);
                clear(work, lockChannel);
                throw failure;
            }

            int done = 0;
            try {
                for (Step step : steps) {
                    apply(step);
                    done++;
                }
            } catch (IOException | RuntimeException failure) {
                // Take back what was done, so that the world is as it was. If that is not possible, the journal stays
                // and the install is finished by the next recover().
                try {
                    for (int i = done - 1; i >= 0; i--) {
                        Step step = steps.get(i);
                        if (step.kind.equals(REPLACE)) throw new IOException("Cannot take back " + step.to);
                        Files.move(step.to, step.from);
                    }
                    Files.delete(journal);
                    clear(work, lockChannel);
                } catch (IOException undo) {
                    failure.addSuppressed(undo);
                }
                throw failure;
            }

            // Installed. A journal that cannot be removed now is replayed later and finds every step done.
            try {
                Files.delete(journal);
                clear(work, lockChannel);
            } catch (IOException e) {
                CubicChunks.LOGGER.warn("Could not remove the conversion work folder {}", work, e);
            }
        } finally {
            release();
        }
    }

    private void writeJournal(Path journal) throws IOException {
        List<String> lines = new ArrayList<>();
        for (Step step : steps) lines.add(step.kind + "\t" + relative(step.from) + "\t" + relative(step.to));
        lines.add(END);
        Path temporary = work.resolve(JOURNAL + ".tmp");
        Files.write(temporary, lines, StandardCharsets.UTF_8);
        try (FileChannel channel = FileChannel.open(temporary, StandardOpenOption.WRITE)) {
            channel.force(true);
        }
        Files.move(temporary, journal, StandardCopyOption.ATOMIC_MOVE);
    }

    /** Removes the work folder of a conversion that is not installed, as after a failure or a cancel. */
    void discard() throws IOException {
        try {
            clear(work, lockChannel);
        } finally {
            release();
        }
    }

    /**
     * Finishes or discards an interrupted conversion of the world in {@code root}. Cheap when there is none. Called
     * before a world is converted, listed or opened.
     *
     * @return true when an interrupted install was finished
     * @throws IOException when the install cannot be finished, or the world is being converted right now
     */
    public static boolean recover(Path root) throws IOException {
        Path work = root.resolve(WORK);
        if (!Files.exists(work)) return false;
        Path real = root.toRealPath();
        synchronized (ACTIVE) {
            if (ACTIVE.contains(real)) throw new IOException("The world in " + root + " is being converted");
        }
        FileChannel lock = lock(work);
        if (lock == null) throw new IOException("The world in " + root + " is being converted");
        try {
            Path journal = work.resolve(JOURNAL);
            List<String> lines = Files.isRegularFile(journal) ? Files.readAllLines(journal, StandardCharsets.UTF_8)
                : null;
            if (lines == null || lines.isEmpty()
                || !lines.get(lines.size() - 1)
                    .equals(END)) {
                // interrupted before the install began: the world itself is untouched
                CubicChunks.LOGGER.warn("Discarding the unfinished world conversion in {}", work);
                Files.deleteIfExists(journal);
                clear(work, lock);
                return false;
            }

            CubicChunks.LOGGER.warn("Finishing the interrupted world conversion of {}", root);
            for (String line : lines.subList(0, lines.size() - 1)) {
                String[] parts = line.split("\t", -1);
                if (parts.length != 3 || !parts[0].equals(MOVE) && !parts[0].equals(REPLACE)) {
                    throw new IOException("Damaged conversion journal " + journal);
                }
                apply(new Step(parts[0], root.resolve(parts[1]), root.resolve(parts[2])));
            }
            Files.delete(journal);
            try {
                clear(work, lock);
            } catch (IOException e) {
                CubicChunks.LOGGER.warn("Could not remove the conversion work folder {}", work, e);
            }
            return true;
        } finally {
            lock.close();
        }
    }

    /** One journal step. A step that was already carried out is recognised and skipped. */
    private static void apply(Step step) throws IOException {
        boolean hasFrom = Files.exists(step.from), hasTo = Files.exists(step.to);
        if (!hasFrom && hasTo) return;
        if (!hasFrom || hasTo && !step.kind.equals(REPLACE)) {
            throw new IOException(
                "Cannot continue the world conversion: " + (hasFrom ? "both " : "neither ")
                    + step.from
                    + (hasFrom ? " and " : " nor ")
                    + step.to
                    + " exist");
        }
        Files.createDirectories(step.to.getParent());
        if (step.kind.equals(REPLACE)) {
            Files.move(step.from, step.to, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } else {
            Files.move(step.from, step.to, StandardCopyOption.ATOMIC_MOVE);
        }
    }

    /**
     * Deletes a work folder whose journal is gone. The lock is held until only the lock file is left, so that nobody
     * else starts on the folder while it is being removed.
     */
    private static void clear(Path work, FileChannel lock) throws IOException {
        if (!Files.exists(work)) return;
        try (Stream<Path> children = Files.list(work)) {
            for (Path child : (Iterable<Path>) children::iterator) {
                if (!child.getFileName()
                    .toString()
                    .equals(LOCK)) deleteTree(child);
            }
        }
        if (lock != null) lock.close();
        Files.deleteIfExists(work.resolve(LOCK));
        Files.deleteIfExists(work);
    }

    private String relative(Path path) throws IOException {
        String relative = root.relativize(path)
            .toString()
            .replace('\\', '/');
        if (relative.startsWith("..") || relative.indexOf('\t') >= 0 || relative.indexOf('\n') >= 0) {
            throw new IOException("Cannot record " + path + " in the conversion journal");
        }
        return relative;
    }

    static void deleteTree(Path path) throws IOException {
        if (!Files.exists(path)) return;
        Files.walkFileTree(path, new SimpleFileVisitor<Path>() {

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) throws IOException {
                Files.delete(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path directory, IOException failure) throws IOException {
                if (failure != null) throw failure;
                Files.delete(directory);
                return FileVisitResult.CONTINUE;
            }
        });
    }
}
