package com.cardinalstar.cubicchunks.worldgen.config;

import java.util.Objects;

/**
 * One strata layer: a block that fades in with depth. It's fully present at {@link #full} and absent at
 * {@link #none}, blending linearly in between.
 */
public final class Stratum {

    public final BlockRef block;
    /** Y at which the block is 100% present. */
    public final int full;
    /** Y at which the block is 0% present. */
    public final int none;

    public Stratum(BlockRef block, int full, int none) {
        this.block = block;
        this.full = full;
        this.none = none;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Stratum)) return false;
        Stratum other = (Stratum) o;
        return full == other.full && none == other.none && block.equals(other.block);
    }

    @Override
    public int hashCode() {
        return Objects.hash(block, full, none);
    }

    @Override
    public String toString() {
        return block + " (" + full + " .. " + none + ")";
    }
}
