package com.cardinalstar.cubicchunks.world.heightmap;

import java.util.concurrent.TimeUnit;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

import com.cardinalstar.cubicchunks.util.XSTR;

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 3, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(1)
@State(Scope.Thread)
public class YIntervalTreeBenchmark {

    // A tree pre-populated with a single dense interval [0, 255] (typical surface column)
    private YIntervalTree dense;
    // A tree pre-populated with 128 disjoint singleton intervals (every other Y in 0..255)
    private YIntervalTree sparse;
    // A fresh empty tree, reset per invocation for mutation benchmarks
    private YIntervalTree fresh;

    private XSTR rng;

    // Y values to add/remove in the mixed benchmark, pre-generated to avoid RNG overhead
    private int[] ops;

    @Setup(Level.Trial)
    public void setupTrial() {
        rng = new XSTR(12345L);

        dense = new YIntervalTree();
        for (int y = 0; y <= 255; y++) dense.add(y);

        sparse = new YIntervalTree();
        for (int y = 0; y <= 255; y += 2) sparse.add(y);

        // Pre-generate 1024 random Y values in [-128, 383) for the mixed benchmark
        ops = new int[1024];
        XSTR opRng = new XSTR(99999L);
        for (int i = 0; i < ops.length; i++) {
            ops[i] = opRng.nextInt(512) - 128;
        }
    }

    @Setup(Level.Invocation)
    public void resetFresh() {
        fresh = new YIntervalTree();
    }

    // ---- add ----------------------------------------------------------------

    /**
     * Sequential adds that merge into a single dense interval.
     * Exercises the common case: building up a terrain column block by block.
     */
    @Benchmark
    public YIntervalTree addDense() {
        for (int y = 0; y <= 255; y++) fresh.add(y);
        return fresh;
    }

    /**
     * Alternating adds that stay as disjoint singleton intervals.
     * Stresses the treap with many nodes (max intervals case).
     */
    @Benchmark
    public YIntervalTree addSparse() {
        for (int y = 0; y <= 255; y += 2) fresh.add(y);
        return fresh;
    }

    // ---- remove -------------------------------------------------------------

    /**
     * Removes all elements from a dense [0,255] interval one by one from the top.
     * Each remove shrinks the top interval until it's gone.
     */
    @Benchmark
    public YIntervalTree removeDenseTop() {
        dense = new YIntervalTree();
        for (int y = 0; y <= 255; y++) dense.add(y);
        for (int y = 255; y >= 0; y--) dense.remove(y);
        return dense;
    }

    /**
     * Removes every other element from a dense interval, creating 128 splits.
     * Exercises the split-in-middle path repeatedly.
     */
    @Benchmark
    public YIntervalTree removeDenseAlternating() {
        dense = new YIntervalTree();
        for (int y = 0; y <= 255; y++) dense.add(y);
        for (int y = 0; y <= 255; y += 2) dense.remove(y);
        return dense;
    }

    // ---- queries ------------------------------------------------------------

    /**
     * getTopY on a dense column — O(1) cache hit.
     */
    @Benchmark
    public int getTopY_dense() {
        return dense.getTopY();
    }

    /**
     * getTopY on a sparse column — O(1) cache hit.
     */
    @Benchmark
    public int getTopY_sparse() {
        return sparse.getTopY();
    }

    /**
     * getTopAirY from Y=0 on a dense [0,255] column.
     * Returns 256 — one findPred call.
     */
    @Benchmark
    public int getTopAirY_dense() {
        return dense.getTopAirY(0);
    }

    /**
     * getTopAirY from Y=127 on a sparse column (every other Y occupied).
     * Y=127 is air; returns 127 immediately after one findPred.
     */
    @Benchmark
    public int getTopAirY_sparseAir() {
        return sparse.getTopAirY(127);
    }

    /**
     * getTopAirY from Y=128 on a sparse column (Y=128 is occupied).
     * Returns 129 after findPred.
     */
    @Benchmark
    public int getTopAirY_sparseOccupied() {
        return sparse.getTopAirY(128);
    }

    /**
     * get() on an occupied Y — positive lookup.
     */
    @Benchmark
    public boolean get_hit() {
        return dense.get(128);
    }

    /**
     * get() on an unoccupied Y in a sparse column — negative lookup.
     */
    @Benchmark
    public boolean get_miss() {
        return sparse.get(127);
    }

    // ---- mixed --------------------------------------------------------------

    /**
     * Realistic mixed workload: random adds and removes over a bounded range.
     * Models a dynamic column being modified during world generation.
     */
    @Benchmark
    public int mixed_addRemove() {
        XSTR r = rng;
        for (int i = 0; i < ops.length; i++) {
            if ((i & 1) == 0) fresh.add(ops[i]);
            else fresh.remove(ops[i]);
        }
        return fresh.getTopY();
    }
}
