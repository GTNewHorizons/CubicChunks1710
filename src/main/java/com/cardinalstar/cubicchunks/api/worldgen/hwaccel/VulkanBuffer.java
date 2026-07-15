package com.cardinalstar.cubicchunks.api.worldgen.hwaccel;

import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.system.MemoryUtil.memByteBuffer;
import static org.lwjgl.util.vma.Vma.VMA_ALLOCATION_CREATE_HOST_ACCESS_RANDOM_BIT;
import static org.lwjgl.util.vma.Vma.VMA_ALLOCATION_CREATE_HOST_ACCESS_SEQUENTIAL_WRITE_BIT;
import static org.lwjgl.util.vma.Vma.VMA_ALLOCATION_CREATE_MAPPED_BIT;
import static org.lwjgl.util.vma.Vma.VMA_MEMORY_USAGE_AUTO;
import static org.lwjgl.util.vma.Vma.vmaCreateBuffer;
import static org.lwjgl.util.vma.Vma.vmaDestroyBuffer;
import static org.lwjgl.vulkan.VK10.VK_BUFFER_USAGE_STORAGE_BUFFER_BIT;
import static org.lwjgl.vulkan.VK10.VK_BUFFER_USAGE_TRANSFER_DST_BIT;
import static org.lwjgl.vulkan.VK10.VK_BUFFER_USAGE_TRANSFER_SRC_BIT;

import java.io.Closeable;
import java.nio.ByteBuffer;
import java.nio.LongBuffer;

import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.util.vma.VmaAllocationCreateInfo;
import org.lwjgl.util.vma.VmaAllocationInfo;
import org.lwjgl.vulkan.VkBufferCreateInfo;

import me.eigenraven.lwjgl3ify.api.Lwjgl3Aware;

/**
 * A host-visible, persistently mapped Vulkan buffer backed by VMA.
 *
 * <p>
 * Create instances via the static factory methods:
 * <ul>
 * <li>{@link #allocDeviceLocal(long, int)} — device-local SSBO (GPU only, fast for compute)</li>
 * <li>{@link #allocHostVisible(long, int)} — host-visible, coherent buffer for CPU↔GPU transfers</li>
 * </ul>
 *
 * <p>
 * The buffer is persistently mapped: {@link #mapped()} always returns the same
 * {@link ByteBuffer} view. Callers must not retain the returned buffer past {@link #close()}.
 */
@Lwjgl3Aware
public final class VulkanBuffer implements Closeable {

    /** Raw Vulkan buffer handle. */
    private final long buffer;
    /** VMA allocation handle. */
    private final long allocation;
    /** Size in bytes of this buffer. */
    private final int byteLen;
    /** Persistently mapped view, or {@code null} for device-local buffers. */
    private final ByteBuffer mappedView;

    // Stored so resize() can recreate an equivalent buffer.
    private final int usageFlags;
    private final int vmaUsage;
    private final int vmaCreateFlags;

    private boolean closed = false;

    private VulkanBuffer(long buffer, long allocation, int byteLen, ByteBuffer mappedView, int usageFlags, int vmaUsage,
        int vmaCreateFlags) {
        this.buffer = buffer;
        this.allocation = allocation;
        this.byteLen = byteLen;
        this.mappedView = mappedView;
        this.usageFlags = usageFlags;
        this.vmaUsage = vmaUsage;
        this.vmaCreateFlags = vmaCreateFlags;
    }

    // -------------------------------------------------------------------------
    // Factory methods
    // -------------------------------------------------------------------------

    /**
     * Allocates a device-local storage buffer.
     *
     * <p>
     * The buffer is not host-visible; use a staging buffer to upload data.
     * Usage flags: {@code STORAGE_BUFFER | TRANSFER_SRC | TRANSFER_DST}.
     *
     * @param allocator VMA allocator handle (from {@link KernelContext#getVmaAllocator()})
     * @param byteLen   byte capacity of the buffer
     * @return a newly allocated {@link VulkanBuffer}
     */
    public static VulkanBuffer allocDeviceLocal(long allocator, int byteLen) {
        return alloc(
            allocator,
            byteLen,
            VK_BUFFER_USAGE_STORAGE_BUFFER_BIT | VK_BUFFER_USAGE_TRANSFER_SRC_BIT | VK_BUFFER_USAGE_TRANSFER_DST_BIT,
            VMA_MEMORY_USAGE_AUTO,
            0 /* no host-access flags — device-local */);
    }

    /**
     * Allocates a host-visible, persistently mapped buffer suitable for staging.
     *
     * <p>
     * The returned buffer's {@link #mapped()} view is always valid for CPU read/write.
     * Usage flags: {@code STORAGE_BUFFER | TRANSFER_SRC | TRANSFER_DST}.
     *
     * @param allocator VMA allocator handle (from {@link KernelContext#getVmaAllocator()})
     * @param byteLen   byte capacity of the buffer
     * @return a newly allocated, persistently mapped {@link VulkanBuffer}
     */
    public static VulkanBuffer allocHostVisible(long allocator, int byteLen) {
        return alloc(
            allocator,
            byteLen,
            VK_BUFFER_USAGE_STORAGE_BUFFER_BIT | VK_BUFFER_USAGE_TRANSFER_SRC_BIT | VK_BUFFER_USAGE_TRANSFER_DST_BIT,
            VMA_MEMORY_USAGE_AUTO,
            VMA_ALLOCATION_CREATE_HOST_ACCESS_SEQUENTIAL_WRITE_BIT | VMA_ALLOCATION_CREATE_HOST_ACCESS_RANDOM_BIT
                | VMA_ALLOCATION_CREATE_MAPPED_BIT);
    }

    private static VulkanBuffer alloc(long allocator, int byteLen, int usageFlags, int vmaUsage, int vmaFlags) {
        try (MemoryStack stack = stackPush()) {
            VkBufferCreateInfo bufferInfo = VkBufferCreateInfo.calloc(stack)
                .sType$Default()
                .size(byteLen)
                .usage(usageFlags);
            // sharingMode defaults to VK_SHARING_MODE_EXCLUSIVE (0) via calloc

            VmaAllocationCreateInfo allocInfo = VmaAllocationCreateInfo.calloc(stack)
                .usage(vmaUsage)
                .flags(vmaFlags);

            LongBuffer pBuffer = stack.mallocLong(1);
            PointerBuffer pAlloc = stack.mallocPointer(1);
            VmaAllocationInfo info = VmaAllocationInfo.calloc(stack);

            KernelContext.check(vmaCreateBuffer(allocator, bufferInfo, allocInfo, pBuffer, pAlloc, info));

            long bufHandle = pBuffer.get(0);
            long allocHandle = pAlloc.get(0);

            ByteBuffer mapped = null;
            long pMapped = info.pMappedData();
            if (pMapped != 0L) {
                mapped = memByteBuffer(pMapped, byteLen);
            }

            return new VulkanBuffer(bufHandle, allocHandle, byteLen, mapped, usageFlags, vmaUsage, vmaFlags);
        }
    }

    // -------------------------------------------------------------------------
    // Accessors
    // -------------------------------------------------------------------------

    /** Returns the raw Vulkan buffer handle. */
    public long buffer() {
        return buffer;
    }

    /** Returns the VMA allocation handle. */
    public long allocation() {
        return allocation;
    }

    /** Returns the byte capacity of this buffer. */
    public int byteLen() {
        return byteLen;
    }

    /**
     * Returns a persistently mapped view of this buffer's memory.
     *
     * @return the mapped {@link ByteBuffer}, or {@code null} if this is a device-local buffer
     * @throws IllegalStateException if this buffer has been closed
     */
    public ByteBuffer mapped() {
        if (closed) throw new IllegalStateException("VulkanBuffer has been closed");
        return mappedView;
    }

    // -------------------------------------------------------------------------
    // Lifecycle
    // -------------------------------------------------------------------------

    /**
     * Returns a buffer with at least {@code newByteLen} bytes capacity.
     *
     * <p>
     * If this buffer is already large enough, returns {@code this} unchanged.
     * Otherwise destroys this buffer and allocates a new one with the same type and flags.
     * The caller must update any descriptor sets that reference this buffer when the
     * return value is not {@code this}.
     *
     * @param allocator  VMA allocator handle
     * @param newByteLen required minimum byte capacity
     * @return {@code this} if already sufficient, otherwise a new larger buffer
     */
    public VulkanBuffer resize(long allocator, int newByteLen) {
        if (newByteLen <= byteLen) return this;
        destroy(allocator);
        return alloc(allocator, newByteLen, usageFlags, vmaUsage, vmaCreateFlags);
    }

    /**
     * Destroys the Vulkan buffer and frees the VMA allocation.
     *
     * <p>
     * After this call, {@link #mapped()} will throw. The {@code allocator} handle
     * must be the same one used to create this buffer.
     *
     * @param allocator VMA allocator handle
     */
    public void destroy(long allocator) {
        if (!closed) {
            closed = true;
            vmaDestroyBuffer(allocator, buffer, allocation);
        }
    }

    /** Alias for {@link #destroy(long)}. */
    public void close(long allocator) {
        destroy(allocator);
    }

    /**
     * Convenience override that retrieves the allocator from {@link KernelContext}.
     * Only valid while the Vulkan context is active.
     */
    @Override
    public void close() {
        destroy(KernelContext.getVmaAllocator());
    }
}
