package com.cardinalstar.cubicchunks.world.heightmap;

import java.util.Arrays;

import com.cardinalstar.cubicchunks.network.CCPacketBuffer;
import com.cardinalstar.cubicchunks.util.Coords;

/**
 * An interval set that tracks which integer Y coordinates are occupied (have a solid block).
 * <p>
 * Contiguous runs of occupied Y values are merged into disjoint, non-adjacent intervals [lo, hi], stored in ascending
 * order as {@code lo, hi} pairs in a single exact-length {@code int[]}. A height map holds one of these per block
 * column, and a block column rarely has more than a handful of runs, so a flat array is both the smallest
 * representation and faster than a tree: lookups are a binary search and changes that keep the run count only
 * rewrite a bound in place. Adding or removing a run copies the array.
 * <p>
 * Not thread-safe. Readers on other threads (JourneyMap reads precipitation heights on its own thread) may see a stale
 * or briefly inconsistent state, but never an exception: every reader reads {@link #runs} once, an array is only edited
 * in place without changing its length, and a replaced array is never written again.
 */
public final class YIntervalTree {

    private static final int[] EMPTY = new int[0];

    /** {@code lo0, hi0, lo1, hi1, ...}, ascending, with at least one free Y between neighbouring runs. */
    private int[] runs = EMPTY;

    // ---- Public API -------------------------------------------------------

    /**
     * Marks Y as occupied. No-op if already occupied.
     * Adjacent or bridging intervals are merged automatically.
     */
    public void add(int y) {
        set(y, true);
    }

    /**
     * Marks Y as unoccupied. No-op if not occupied.
     * Splits the containing interval if Y is in its interior.
     */
    public void remove(int y) {
        set(y, false);
    }

    /**
     * Returns the maximum occupied Y coordinate, or {@link Coords#NO_HEIGHT} if the tree is empty.
     */
    public int getTopY() {
        int[] r = runs;
        return r.length == 0 ? Coords.NO_HEIGHT : r[r.length - 1];
    }

    /**
     * Returns the minimum occupied Y coordinate, or {@link Coords#NO_HEIGHT} if the tree is empty.
     */
    public int getBottomY() {
        int[] r = runs;
        return r.length == 0 ? Coords.NO_HEIGHT : r[0];
    }

    /**
     * Returns the first air Y just above the solid interval at or below {@code startY},
     * i.e. {@code hi + 1} of the highest interval with lo &lt;= startY.
     * Returns {@link Coords#NO_HEIGHT} if no solid exists at or below {@code startY}.
     */
    public int getTopAirY(int startY) {
        int[] r = runs;
        int pred = findPred(r, startY);
        if (pred < 0) return Coords.NO_HEIGHT;
        return r[pred + 1] + 1;
    }

    /**
     * Returns the first air Y just below the solid interval at or above {@code startY},
     * i.e. {@code lo - 1} of the lowest interval with lo &gt; startY
     * (or of the interval containing {@code startY} if it is solid).
     * Returns {@link Coords#NO_HEIGHT} if no solid exists at or above {@code startY}.
     */
    public int getBottomAirY(int startY) {
        int[] r = runs;
        int pred = findPred(r, startY);
        if (pred >= 0 && r[pred + 1] >= startY) return r[pred] - 1; // startY is solid
        int succ = pred + 2;
        if (succ < r.length) return r[succ] - 1; // startY is air; anchor on solid above
        return Coords.NO_HEIGHT;
    }

    /**
     * Returns the first Y' &gt;= {@code startY} that IS occupied (first solid at or above startY).
     * If {@code startY} is already occupied, returns {@code startY}.
     * Returns {@link Coords#NO_HEIGHT} if no solid exists at or above {@code startY}.
     */
    public int getTopSolidY(int startY) {
        int[] r = runs;
        int pred = findPred(r, startY);
        if (pred >= 0 && r[pred + 1] >= startY) return startY;
        int succ = pred + 2;
        if (succ < r.length) return r[succ];
        return Coords.NO_HEIGHT;
    }

    /**
     * Returns the first Y' &lt;= {@code startY} that IS occupied (first solid at or below startY).
     * If {@code startY} is already occupied, returns {@code startY}.
     * Returns {@link Coords#NO_HEIGHT} if no solid exists at or below {@code startY}.
     */
    public int getBottomSolidY(int startY) {
        int[] r = runs;
        int pred = findPred(r, startY);
        if (pred < 0) return Coords.NO_HEIGHT;
        if (r[pred + 1] >= startY) return startY;
        return r[pred + 1];
    }

    /**
     * Returns true if Y is occupied.
     */
    public boolean get(int y) {
        int[] r = runs;
        int pred = findPred(r, y);
        return pred >= 0 && r[pred + 1] >= y;
    }

    /**
     * Sets Y as occupied ({@code present=true}) or unoccupied ({@code present=false}).
     * Returns true when the tree was updated.
     */
    public boolean set(int y, boolean present) {
        int pred = findPred(runs, y);
        boolean was = pred >= 0 && runs[pred + 1] >= y;
        if (present == was) return false;

        if (present) {
            int succ = pred + 2;
            boolean leftAdj = pred >= 0 && runs[pred + 1] == y - 1;
            boolean rightAdj = succ < runs.length && runs[succ] == y + 1;
            if (leftAdj && rightAdj) {
                // y bridges pred and succ: pred takes succ's hi, succ goes away
                int[] merged = new int[runs.length - 2];
                System.arraycopy(runs, 0, merged, 0, pred + 1);
                merged[pred + 1] = runs[succ + 1];
                System.arraycopy(runs, succ + 2, merged, pred + 2, runs.length - succ - 2);
                runs = merged;
            } else if (leftAdj) {
                runs[pred + 1] = y;
            } else if (rightAdj) {
                runs[succ] = y;
            } else {
                runs = insert(runs, succ, y, y);
            }
        } else {
            int lo = runs[pred];
            int hi = runs[pred + 1];
            if (lo == y && hi == y) {
                runs = delete(runs, pred);
            } else if (hi == y) {
                runs[pred + 1] = y - 1;
            } else if (lo == y) {
                runs[pred] = y + 1;
            } else {
                // interior: [lo, y - 1] stays at pred, [y + 1, hi] follows it
                runs[pred + 1] = y - 1;
                runs = insert(runs, pred + 2, y + 1, hi);
            }
        }
        return true;
    }

    /**
     * Sets the {@code length} Y values from {@code minY} at once: Y {@code minY + i} becomes occupied exactly when bit
     * {@code i} of {@code bits} is set. Equivalent to calling {@link #set} for each of them, but reads and rewrites the
     * range once. Returns true when the tree was updated.
     *
     * @param length 1 to 32
     */
    public boolean setBits(int minY, int length, int bits) {
        if (length < 1 || length > 32) {
            throw new IllegalArgumentException("length must be 1 to 32: " + length);
        }
        int maxY = minY + length - 1;
        bits &= (int) ((1L << length) - 1);

        // first run that ends at or above minY; runs before it end below the range
        int first = findFirstEndingAtOrAbove(minY);
        int current = 0;
        int i = first;
        for (; i < runs.length && runs[i] <= maxY; i += 2) {
            current |= bitRange(Math.max(runs[i], minY) - minY, Math.min(runs[i + 1], maxY) - minY);
        }
        if (current == bits) return false;

        // Runs [first, end) overlap the range; the first may start below it and the last may end above it. Build
        // their replacement together with the runs just outside the range, which it joins when they touch, then copy
        // the untouched runs around it into an exact-length array.
        int end = i;
        int[] window = new int[Integer.bitCount(bits) * 2 + 8];
        int size = 0;
        int keepBefore = first;
        int keepAfter = end;
        if (first > 0) {
            size = append(window, size, runs[first - 2], runs[first - 1]);
            keepBefore = first - 2;
        }
        if (first < end && runs[first] < minY) {
            size = append(window, size, runs[first], minY - 1);
        }
        for (int rest = bits; rest != 0;) {
            int from = Integer.numberOfTrailingZeros(rest);
            int to = from + Integer.numberOfTrailingZeros(~(rest >>> from)) - 1;
            size = append(window, size, minY + from, minY + to);
            rest &= ~bitRange(from, to);
        }
        if (first < end && runs[end - 1] > maxY) {
            size = append(window, size, maxY + 1, runs[end - 1]);
        }
        if (end < runs.length) {
            size = append(window, size, runs[end], runs[end + 1]);
            keepAfter = end + 2;
        }

        int total = keepBefore + size + runs.length - keepAfter;
        if (total == 0) {
            runs = EMPTY;
            return true;
        }
        int[] updated = new int[total];
        System.arraycopy(runs, 0, updated, 0, keepBefore);
        System.arraycopy(window, 0, updated, keepBefore, size);
        System.arraycopy(runs, keepAfter, updated, keepBefore + size, runs.length - keepAfter);
        runs = updated;
        return true;
    }

    // ---- Serialization ----------------------------------------------------

    /**
     * Writes the tree contents to {@code buf} as a count followed by (lo, hi) pairs in ascending order.
     * All values are written as VarInts.
     */
    public void writeData(CCPacketBuffer buf) {
        int[] r = runs;
        buf.writeVarIntToBuffer(r.length >> 1);
        for (int value : r) {
            buf.writeVarIntToBuffer(value);
        }
    }

    /**
     * Reads tree contents written by {@link #writeData}. Resets all existing state first.
     */
    public void readData(CCPacketBuffer buf) {
        int count = buf.readVarIntFromBuffer();
        if (count <= 0) {
            runs = EMPTY;
            return;
        }
        // each value takes at least one byte: reject a corrupt count before allocating for it
        if (count > buf.readableBytes() / 2) {
            throw new IndexOutOfBoundsException("height map run count " + count + " exceeds the remaining data");
        }
        int[] read = new int[count * 2];
        for (int i = 0; i < read.length; i++) {
            read[i] = buf.readVarIntFromBuffer();
        }
        runs = isNormalized(read) ? read : normalize(read);
    }

    // ---- Internals --------------------------------------------------------

    /** Returns the array index of the lo of the highest run in {@code r} with {@code lo <= y}, or -2 if none. */
    private static int findPred(int[] r, int y) {
        int low = 0;
        int high = (r.length >> 1) - 1;
        int result = -1;
        while (low <= high) {
            int mid = (low + high) >>> 1;
            if (r[mid << 1] <= y) {
                result = mid;
                low = mid + 1;
            } else {
                high = mid - 1;
            }
        }
        return result << 1;
    }

    /** Returns the array index of the lo of the lowest run with {@code hi >= y}, or {@code runs.length} if none. */
    private int findFirstEndingAtOrAbove(int y) {
        int low = 0;
        int high = (runs.length >> 1) - 1;
        while (low <= high) {
            int mid = (low + high) >>> 1;
            if (runs[(mid << 1) + 1] < y) {
                low = mid + 1;
            } else {
                high = mid - 1;
            }
        }
        return low << 1;
    }

    /** Bits {@code from} to {@code to} (inclusive, 0 to 31) set. */
    private static int bitRange(int from, int to) {
        return (int) (((1L << (to + 1)) - 1) & -(1L << from));
    }

    /** Appends [lo, hi] to {@code out}, merging it into the last run when they touch. Returns the new size. */
    private static int append(int[] out, int size, int lo, int hi) {
        if (size > 0 && (long) out[size - 1] + 1 >= lo) {
            out[size - 1] = Math.max(out[size - 1], hi);
            return size;
        }
        out[size] = lo;
        out[size + 1] = hi;
        return size + 2;
    }

    private static int[] insert(int[] runs, int at, int lo, int hi) {
        int[] grown = new int[runs.length + 2];
        System.arraycopy(runs, 0, grown, 0, at);
        grown[at] = lo;
        grown[at + 1] = hi;
        System.arraycopy(runs, at, grown, at + 2, runs.length - at);
        return grown;
    }

    private static int[] delete(int[] runs, int at) {
        if (runs.length == 2) return EMPTY;
        int[] shrunk = new int[runs.length - 2];
        System.arraycopy(runs, 0, shrunk, 0, at);
        System.arraycopy(runs, at + 2, shrunk, at, runs.length - at - 2);
        return shrunk;
    }

    private static boolean isNormalized(int[] runs) {
        for (int i = 0; i < runs.length; i += 2) {
            if (runs[i] > runs[i + 1]) return false;
            if (i > 0 && (long) runs[i - 1] + 1 >= runs[i]) return false;
        }
        return true;
    }

    /** Sorts and merges runs that arrived out of order, overlapping or touching. */
    private static int[] normalize(int[] read) {
        long[] pairs = new long[read.length >> 1];
        for (int i = 0; i < pairs.length; i++) {
            int lo = Math.min(read[2 * i], read[2 * i + 1]);
            int hi = Math.max(read[2 * i], read[2 * i + 1]);
            pairs[i] = (long) lo << 32 | (hi & 0xFFFFFFFFL);
        }
        Arrays.sort(pairs);
        int[] out = new int[read.length];
        int size = 0;
        for (long pair : pairs) {
            size = append(out, size, (int) (pair >> 32), (int) pair);
        }
        return size == out.length ? out : Arrays.copyOf(out, size);
    }
}
