package com.cardinalstar.cubicchunks.world;

/// A falling block that moves changes blocks, and every block change tells the six neighbours, which reads them and so
/// loads or generates their cubes; its collision check reads the block below it. While a falling block moves,
/// neighbours in cubes that are not loaded are not told and its collision check reads them as air.
public final class FallingBlockMove {

    private static int depth;

    private FallingBlockMove() {}

    public static void begin() {
        depth++;
    }

    public static void end() {
        depth--;
    }

    /// Server thread only: falling blocks only move there.
    public static boolean isInProgress() {
        return depth > 0;
    }
}
