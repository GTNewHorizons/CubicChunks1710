package com.cardinalstar.cubicchunks.server.chunkio;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.cardinalstar.cubicchunks.CubicChunks;
import com.cardinalstar.cubicchunks.CubicChunksConfig;

/** Server-thread-only maintenance, bounded by Forge's server startup events. */
public final class StartupRegionCompaction {

    private static Set<Path> worlds;
    private static Set<Path> dimensions;
    // Immutable snapshots are published by the server and rendered only on the client thread.
    private static volatile Progress progress;
    private static long lastProgressLog;

    private StartupRegionCompaction() {}

    public static void begin() {
        progress = null;
        worlds = new HashSet<>();
        dimensions = new HashSet<>();
    }

    public static void finish() {
        progress = null;
        worlds = null;
        dimensions = null;
    }

    public static Progress getProgress() {
        return progress;
    }

    public static final class Progress {

        public final String dimension;
        public final String phase;
        public final int completed, total, percent;
        private final long started;

        Progress(String dimension, RegionCompactor.Phase phase, int completed, int total, long started) {
            this(
                dimension,
                phase.name()
                    .toLowerCase(java.util.Locale.ROOT),
                completed,
                total,
                phase == RegionCompactor.Phase.DISCOVERING ? -1
                    : (phase == RegionCompactor.Phase.COMPACTING ? 50 : 0)
                        + (total == 0 ? 50 : (int) (50L * completed / total)),
                started);
        }

        Progress(String dimension, String phase, int completed, int total, int percent, long started) {
            this.dimension = dimension;
            this.phase = phase;
            this.completed = completed;
            this.total = total;
            this.percent = percent;
            this.started = started;
        }

        /** Whether this is the storage conversion rather than the compaction. */
        public boolean converting() {
            return CONVERTING.equals(phase);
        }

        public long elapsedSeconds() {
            return (System.nanoTime() - started) / 1_000_000_000;
        }
    }

    private static void updateProgress(String dimension, RegionCompactor.Phase phase, int completed, int total,
        long started) {
        Progress snapshot = new Progress(dimension, phase, completed, total, started);
        progress = snapshot;
        long now = System.nanoTime();
        if (now - lastProgressLog >= 5_000_000_000L) {
            lastProgressLog = now;
            CubicChunks.LOGGER.info(
                "Region compaction {}: {} {}/{} regions ({} s)",
                snapshot.dimension,
                snapshot.phase,
                completed,
                total,
                snapshot.elapsedSeconds());
        }
    }

    private static final String CONVERTING = "converting";

    private static void updateConversionProgress(String dimension, int completed, int total, long started) {
        Progress snapshot = new Progress(
            dimension,
            CONVERTING,
            completed,
            total,
            total == 0 ? 100 : (int) (100L * completed / total),
            started);
        progress = snapshot;
        long now = System.nanoTime();
        if (now - lastProgressLog >= 5_000_000_000L) {
            lastProgressLog = now;
            CubicChunks.LOGGER.info(
                "Storage conversion {}: {}/{} regions ({} s)",
                dimension,
                completed,
                total,
                snapshot.elapsedSeconds());
        }
    }

    /**
     * Startup maintenance before a dimension's storage opens: with {@code convertStorageOnWorldLoad}, a bundle storage
     * world is converted completely ({@link StorageConversion}); with {@code compactRegionsOnWorldLoad}, region files
     * are compacted afterwards. On the world's first open both cover every saved dimension folder.
     */
    static void beforeOpen(Path world, Path dimension, boolean bundled) throws IOException {
        if (worlds == null) return;
        boolean convert = bundled && CubicChunksConfig.convertStorageOnWorldLoad;
        if (!convert && !CubicChunksConfig.compactRegionsOnWorldLoad) return;

        Files.createDirectories(world);
        Path root = world.toRealPath();
        // Fail closed for storage being opened. The explicitly selected world root may itself be a symlink.
        maintain(dimension.equals(world) ? root : dimension, root, convert);
        if (!worlds.contains(root)) {
            List<Path> savedDimensions = new ArrayList<>();
            savedDimensions.add(root);
            try (DirectoryStream<Path> children = Files.newDirectoryStream(root)) {
                for (Path child : children) {
                    if (child.getFileName()
                        .toString()
                        .matches("DIM(?:_SPACESTATION)?-?\\d+")
                        && (Files.isDirectory(child, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(child))) {
                        savedDimensions.add(child);
                    }
                }
            }
            Collections.sort(savedDimensions);
            for (Path saved : savedDimensions) {
                try {
                    maintain(saved, root, convert);
                } catch (IOException e) {
                    CubicChunks.LOGGER.warn(
                        "Skipping startup maintenance for saved dimension {}: {}. "
                            + "It will be checked again if opened during startup. "
                            + "Set compactRegionsOnWorldLoad=false and convertStorageOnWorldLoad=false to disable it.",
                        saved,
                        e.toString());
                }
            }
            worlds.add(root);
        }
    }

    private static void maintain(Path dimension, Path root, boolean convert) throws IOException {
        if (Files.isSymbolicLink(dimension)) {
            CubicChunks.LOGGER.warn("Skipping startup region maintenance for symbolic link {}", dimension);
            return;
        }
        if (!Files.isDirectory(dimension)) return;
        Path path = dimension.toRealPath();
        if (dimensions.contains(path)) return;
        String label = path.equals(root) ? "DIM0"
            : path.getFileName()
                .toString();
        if (convert) {
            try {
                long start = System.nanoTime();
                lastProgressLog = start;
                CubicChunks.LOGGER.info("Converting stored cubes and columns of {} to bundle storage", path);
                StorageConversion.Summary result = StorageConversion
                    .convert(path, (completed, total) -> updateConversionProgress(label, completed, total, start));
                CubicChunks.LOGGER.info(
                    "Storage conversion for {}: {} regions checked, {} cubes and {} columns rewritten, {} left in their"
                        + " own records because together they are too large for one, {} left as they were after errors,"
                        + " in {} ms",
                    path,
                    result.regions,
                    result.cubes,
                    result.columns,
                    result.tooLarge,
                    result.failed,
                    (System.nanoTime() - start) / 1_000_000);
            } finally {
                progress = null;
            }
        }
        if (!CubicChunksConfig.compactRegionsOnWorldLoad) {
            dimensions.add(path);
            return;
        }

        try (StorageMaintenanceLock ignored = StorageMaintenanceLock.openMaintenance(path)) {
            long start = System.nanoTime();
            lastProgressLog = start;
            CubicChunks.LOGGER.info("Checking region compaction during server startup for {}", path);
            RegionCompactor.Summary result = RegionCompactor.compactDimensionLocked(
                path,
                (phase, completed, total) -> updateProgress(label, phase, completed, total, start));
            if (result.skippedLink != null) {
                CubicChunks.LOGGER
                    .warn("Skipping region compaction for {} because {} is a symbolic link", path, result.skippedLink);
            } else {
                CubicChunks.LOGGER.info(
                    "Region compaction for {}: {} regions checked, {} compacted, {} uninitialized skipped, {} bytes reclaimed in {} ms",
                    path,
                    result.regions,
                    result.changed,
                    result.uninitialized,
                    result.reclaimed,
                    (System.nanoTime() - start) / 1_000_000);
            }
        } finally {
            progress = null;
        }
        dimensions.add(path);
    }
}
