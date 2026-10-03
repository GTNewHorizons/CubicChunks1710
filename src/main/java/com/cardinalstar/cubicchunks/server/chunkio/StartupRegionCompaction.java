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

    private StartupRegionCompaction() {}

    public static void begin() {
        worlds = new HashSet<>();
        dimensions = new HashSet<>();
    }

    public static void finish() {
        worlds = null;
        dimensions = null;
    }

    static void beforeOpen(Path world, Path dimension) throws IOException {
        if (worlds == null || !CubicChunksConfig.compactRegionsOnWorldLoad) return;

        Files.createDirectories(world);
        Path root = world.toRealPath();
        // Fail closed for storage being opened. The explicitly selected world root may itself be a symlink.
        compact(dimension.equals(world) ? root : dimension);
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
                    compact(saved);
                } catch (IOException e) {
                    CubicChunks.LOGGER.warn(
                        "Skipping startup compaction for saved dimension {}: {}. "
                            + "It will be checked again if opened during startup. "
                            + "Set compactRegionsOnWorldLoad=false to disable automatic compaction.",
                        saved,
                        e.getMessage());
                }
            }
            worlds.add(root);
        }
    }

    private static void compact(Path dimension) throws IOException {
        if (Files.isSymbolicLink(dimension)) {
            CubicChunks.LOGGER.warn("Skipping startup region compaction for symbolic link {}", dimension);
            return;
        }
        if (!Files.isDirectory(dimension)) return;
        Path path = dimension.toRealPath();
        if (dimensions.contains(path)) return;

        try (StorageMaintenanceLock ignored = StorageMaintenanceLock.openMaintenance(path)) {
            long start = System.nanoTime();
            CubicChunks.LOGGER.info("Checking region compaction during server startup for {}", path);
            RegionCompactor.Summary result = RegionCompactor.compactDimensionLocked(path);
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
        }
        dimensions.add(path);
    }
}
