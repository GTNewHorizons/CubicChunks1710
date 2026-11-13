package com.cardinalstar.cubicchunks.worldgen.config;

import java.util.Objects;

import net.minecraft.block.Block;

/**
 * A block and metadata, stored by registry name so settings can be loaded without the block being registered
 * (e.g. its mod was removed). Resolve it with {@link #resolve()} when it's used.
 */
public final class BlockRef {

    /** Registry name, e.g. {@code minecraft:stone}. */
    public final String id;
    public final int meta;

    public BlockRef(String id, int meta) {
        if (meta < 0) throw new IllegalArgumentException("Block meta must not be negative, got " + meta);
        this.id = id;
        this.meta = meta;
    }

    /** The block, or null if nothing is registered under {@link #id}. */
    public Block resolve() {
        return Block.blockRegistry.containsKey(id) ? (Block) Block.blockRegistry.getObject(id) : null;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof BlockRef)) return false;
        BlockRef other = (BlockRef) o;
        return meta == other.meta && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, meta);
    }

    @Override
    public String toString() {
        return id + ":" + meta;
    }
}
