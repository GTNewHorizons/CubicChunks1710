package com.cardinalstar.cubicchunks.mixin.early.common;

import net.minecraft.world.chunk.storage.ExtendedBlockStorage;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** The block counts that {@code removeInvalidBlocks} recounts, for storages whose counts are already known. */
@Mixin(ExtendedBlockStorage.class)
public interface AccessorExtendedBlockStorage {

    /** Blocks that are not air. */
    @Accessor("blockRefCount")
    void setBlockRefCount(int count);

    /** Blocks that tick randomly. */
    @Accessor("tickRefCount")
    void setTickRefCount(int count);
}
