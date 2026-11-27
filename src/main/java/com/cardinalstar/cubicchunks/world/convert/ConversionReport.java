package com.cardinalstar.cubicchunks.world.convert;

import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;

import javax.annotation.Nullable;

/** What a conversion did and what it could not carry over. Counters are updated by the worker threads. */
public final class ConversionReport {

    /** Dimensions and chunks (columns) converted. */
    public final AtomicInteger dimensions = new AtomicInteger(), chunks = new AtomicInteger();
    /** Chunks whose saved data is damaged. They are left out, as the game would regenerate them. */
    public final AtomicInteger unreadableChunks = new AtomicInteger();
    /** Entities, tile entities and scheduled ticks at heights the target cannot hold. */
    public final AtomicInteger droppedOutOfRange = new AtomicInteger();
    /**
     * To CC: chunks with block data in a layout the converter cannot read. Their blocks are carried over unchanged,
     * and the game builds their height map and light when it loads them.
     */
    public final AtomicInteger chunksLeftToGame = new AtomicInteger();
    /**
     * To CC: cubes with blocks whose names are not registered in the running game. They are complete; only their part
     * of the column's height map is added by the game when it loads them.
     */
    public final AtomicInteger cubesLeftToGame = new AtomicInteger();
    /** To vanilla: chunks with cubes between Y 0 and 255 that were never generated; they are empty there. */
    public final AtomicInteger incompleteChunks = new AtomicInteger();

    /** The folder that holds the replaced data, set once the conversion is installed. */
    @Nullable
    public volatile Path backup;
}
