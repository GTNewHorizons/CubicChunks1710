package com.cardinalstar.cubicchunks.world.convert;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.annotation.ParametersAreNonnullByDefault;

/**
 * Contract for offline world format converters.
 * Implementations must be safe to call from a background thread.
 */
@ParametersAreNonnullByDefault
public interface IWorldConverter {

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
