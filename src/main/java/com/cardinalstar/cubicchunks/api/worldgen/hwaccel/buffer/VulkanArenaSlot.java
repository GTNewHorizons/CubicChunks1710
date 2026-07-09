package com.cardinalstar.cubicchunks.api.worldgen.hwaccel.buffer;

/**
 * An arena slot in the Vulkan device-local arena buffer.
 *
 * <p>
 * Carries the byte offset into the arena buffer and the logical dimensions of the
 * allocation. The actual memory is owned by {@code KernelContext.arenaBuffer} — closing
 * this slot is a no-op.
 */
public final class VulkanArenaSlot implements GPUBuffer {

    private final BufferDataType dataType;
    private final int byteOffset;
    private final int lenX;
    private final int lenY;
    private final int lenZ;

    VulkanArenaSlot(BufferDataType dataType, int byteOffset, int lenX, int lenY, int lenZ) {
        this.dataType = dataType;
        this.byteOffset = byteOffset;
        this.lenX = lenX;
        this.lenY = lenY;
        this.lenZ = lenZ;
    }

    @Override
    public BufferDataType getDataType() {
        return dataType;
    }

    /** Returns the byte offset of this slot within the arena VkBuffer. */
    @Override
    public int getBufferOffset() {
        return byteOffset;
    }

    @Override
    public int getLenX() {
        return lenX;
    }

    @Override
    public int getLenY() {
        return lenY;
    }

    @Override
    public int getLenZ() {
        return lenZ;
    }
}
