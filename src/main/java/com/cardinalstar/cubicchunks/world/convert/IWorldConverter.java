package com.cardinalstar.cubicchunks.world.convert;

import java.io.File;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;

import javax.annotation.ParametersAreNonnullByDefault;

/**
 * Contract for offline world format converters.
 * Implementations must be safe to call from a background thread.
 */
@ParametersAreNonnullByDefault
public interface IWorldConverter {

    /** Convert all supported dimensions. Implementations may defer finalization until all have succeeded. */
    default void convertWorld(File worldRoot, ConversionProgress progress, AtomicBoolean cancelSignal) throws IOException {
        for (Path dimension : dimensionRoots(worldRoot.toPath())) {
            checkCancelled(cancelSignal);
            boolean overworld = dimension.equals(worldRoot.toPath());
            progress.setDimension(overworld ? "Overworld" : dimension.getFileName().toString());
            convert(dimension.toFile(), overworld, progress, cancelSignal);
            checkCancelled(cancelSignal);
        }
    }

    static List<Path> dimensionRoots(Path worldRoot) throws IOException {
        List<Path> dimensions = new ArrayList<>();
        dimensions.add(worldRoot);
        try (Stream<Path> children = Files.list(worldRoot)) {
            children.filter(Files::isDirectory)
                .filter(p -> p.getFileName().toString().startsWith("DIM")
                    || p.getFileName().toString().startsWith("PERSONAL_DIM"))
                .sorted()
                .forEach(dimensions::add);
        }
        return dimensions;
    }

    static void checkCancelled(AtomicBoolean cancelSignal) throws InterruptedIOException {
        if (cancelSignal.get()) throw new InterruptedIOException("World conversion cancelled");
    }

    /**
     * Runs the conversion.
     *
     * @param dimensionRoot the root folder containing the current dimension's data
     * @param isOverworld   true when the dimension is the overworld
     * @param progress      mutable progress state; update atomically throughout
     * @param cancelSignal  check this periodically; abort cleanly if {@code true}
     * @throws IOException on unrecoverable I/O failure
     */
    void convert(File dimensionRoot, boolean isOverworld, ConversionProgress progress, AtomicBoolean cancelSignal) throws IOException;
}
