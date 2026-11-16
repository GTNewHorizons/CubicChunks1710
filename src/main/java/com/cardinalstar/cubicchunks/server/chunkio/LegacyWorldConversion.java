package com.cardinalstar.cubicchunks.server.chunkio;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.cardinalstar.cubicchunks.CubicChunks;

/**
 * Temporary: converts worlds saved by older CubicChunks versions (0.1 and 0.2.0) to the current storage format the
 * first time they are opened. This class, {@link StorageConversion}, {@link CompactTables} and the version 2 reader of
 * {@link CubeBundleCodec} are only in the first releases with bundle storage and are then dropped; a world in an old
 * format has to be opened once with one of those.
 * <p>
 * The whole save is converted, every dimension, before any of its storage is opened. The caller changes the world's
 * format marker only afterwards, so an interrupted conversion runs again on the next start and skips what is done.
 */
public final class LegacyWorldConversion {

    private static final Set<String> FORMATS = new HashSet<>(
        Arrays.asList("cubicchunks:anvil3d", "cubicchunks:anvil3d-compact-empty", "cubicchunks:anvil3d-bundles"));
    /** How deep below the world folder a dimension's custom save folder is looked for. */
    private static final int MAX_DEPTH = 3;

    private LegacyWorldConversion() {}

    public static boolean handles(String format) {
        return FORMATS.contains(format);
    }

    /**
     * Converts every dimension of the save, so that the current storage can read it. Throws if that fails, and the
     * caller then leaves the world in its old format; only records that cannot be read at all are left behind.
     */
    public static void convert(Path world, String format) {
        try {
            List<Path> dimensions = new ArrayList<>();
            collect(world, MAX_DEPTH, new HashSet<>(), dimensions);
            Collections.sort(dimensions);
            CubicChunks.LOGGER.warn(
                "Converting {} dimensions of this world from {} to the current storage format. Older CubicChunks"
                    + " versions will not be able to open it afterwards, and large worlds take a while.",
                dimensions.size(),
                format);
            long unreadable = 0;
            for (Path dimension : dimensions) unreadable += convertDimension(world, dimension);
            if (unreadable > 0) CubicChunks.LOGGER.error(
                "{} cubes and columns of this world could not be read and were left as they were; the world is"
                    + " otherwise converted. If startup compaction then refuses a damaged region file, set"
                    + " compactRegionsOnWorldLoad=false in config/cubicchunks.cfg to open the world",
                unreadable);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not convert the world's storage; it is left in its old format", e);
        } finally {
            StartupRegionCompaction.clearProgress();
        }
    }

    /** Returns how many cubes and columns could not be read. */
    private static long convertDimension(Path world, Path dimension) throws IOException {
        String label = dimension.equals(world) ? "DIM0"
            : world.relativize(dimension)
                .toString();
        long start = System.nanoTime();
        CompactTables.convert(dimension);
        StorageConversion.Summary result = StorageConversion.convert(
            dimension,
            (done, total) -> StartupRegionCompaction.updateConversionProgress(label, done, total, start));
        CubicChunks.LOGGER.info(
            "Storage conversion for {}: {} regions checked, {} cubes and {} columns rewritten, {} left as they were"
                + " because they could not be read, in {} ms",
            dimension,
            result.regions,
            result.cubes,
            result.columns,
            result.failed,
            (System.nanoTime() - start) / 1_000_000);
        return result.failed;
    }

    /**
     * Adds {@code directory} if it holds a dimension's storage, and searches its folders down to {@code depth}.
     * Symbolic
     * links are followed, since a dimension moved to another disk works in play; {@code seen} (real paths) stops loops.
     */
    private static void collect(Path directory, int depth, Set<Path> seen, List<Path> out) throws IOException {
        if (!seen.add(directory.toRealPath())) return;
        if (hasStorage(directory)) out.add(directory);
        if (depth == 0) return;
        try (DirectoryStream<Path> children = Files.newDirectoryStream(directory, Files::isDirectory)) {
            for (Path child : children) {
                String name = child.getFileName()
                    .toString();
                if (!name.startsWith(".") && !name.startsWith("region")) collect(child, depth - 1, seen, out);
            }
        }
    }

    /** Whether the folder holds a dimension's cube storage, of any version. */
    public static boolean hasStorage(Path directory) {
        return Files.isDirectory(directory.resolve("region3d")) || Files.isDirectory(directory.resolve("region2d"))
            || Files.isDirectory(directory.resolve(CompactTables.DIRECTORY));
    }
}
