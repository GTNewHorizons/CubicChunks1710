package com.cardinalstar.cubicchunks.api.worldgen.hwaccel.buffer;

import com.cardinalstar.cubicchunks.api.worldgen.hwaccel.KernelContext;
import com.cardinalstar.cubicchunks.util.MathUtil;

/**
 * Offset-tracking allocator over the Vulkan device-local arena buffer.
 *
 * <p>
 * Allocations are sub-regions of {@code KernelContext.arenaBuffer}. Each call to
 * {@link #alloc} advances an internal byte cursor; {@link #reset} sets it back to zero
 * for the next batch. No memory is actually allocated or freed here — the backing buffer
 * is managed by {@link KernelContext#ensureArenaCapacity}.
 */
public class VulkanArenaAllocator implements BufferAllocator {

    private int nextByteOffset = 0;

    @Override
    public GPUBuffer alloc(BufferDataType dataType, int lenX, int lenY, int lenZ) {
        int byteLen = dataType.width() * lenX * lenY * lenZ;
        int offset = nextByteOffset;
        nextByteOffset = MathUtil.alignTo(nextByteOffset + byteLen, 16);
        return new VulkanArenaSlot(dataType, offset, lenX, lenY, lenZ);
    }

    /** Resets the allocation cursor to zero. Call once at the start of each batch. */
    public void reset() {
        nextByteOffset = 0;
    }

    /** Returns the current high-water mark in bytes (useful for {@code ensureArenaCapacity}). */
    public int currentByteLen() {
        return nextByteOffset;
    }
}
