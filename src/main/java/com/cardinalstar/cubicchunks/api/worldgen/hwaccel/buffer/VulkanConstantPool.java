package com.cardinalstar.cubicchunks.api.worldgen.hwaccel.buffer;

import static org.lwjgl.util.vma.Vma.vmaFlushAllocation;
import static org.lwjgl.vulkan.VK10.VK_ACCESS_SHADER_READ_BIT;
import static org.lwjgl.vulkan.VK10.VK_ACCESS_TRANSFER_WRITE_BIT;
import static org.lwjgl.vulkan.VK10.VK_QUEUE_FAMILY_IGNORED;

import java.nio.ByteBuffer;

import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.vulkan.VK10;
import org.lwjgl.vulkan.VkBufferCopy;
import org.lwjgl.vulkan.VkBufferMemoryBarrier;
import org.lwjgl.vulkan.VkCommandBuffer;

import com.cardinalstar.cubicchunks.api.worldgen.hwaccel.KernelContext;
import com.cardinalstar.cubicchunks.api.worldgen.hwaccel.VulkanBuffer;
import com.cardinalstar.cubicchunks.util.MathUtil;

import lombok.Getter;
import me.eigenraven.lwjgl3ify.api.Lwjgl3Aware;

@Lwjgl3Aware
public class VulkanConstantPool implements ConstantBuffer {

    private final long allocator;

    private long dataPointer;
    private int dataCapacity, dataLength;
    private boolean dirty;

    private VulkanBuffer hostBuffer;
    @Getter
    private VulkanBuffer deviceBuffer;

    public VulkanConstantPool(long allocator) {
        this.allocator = allocator;
        // Placeholder so bindDescriptorBuffers() can always bind a valid buffer,
        // even before any executor has added constants.
        deviceBuffer = VulkanBuffer.allocDeviceLocal(allocator, 4);
    }

    @Override
    public GPUBuffer addConstant(BufferDataType dataType, ByteBuffer data) {
        int offset = dataLength;
        int newEnd = MathUtil.alignTo(dataLength + data.remaining(), 16);

        if (newEnd > dataCapacity) {
            dataCapacity += KernelContext.CHUNK_SIZE;

            if (dataPointer != 0) {
                dataPointer = MemoryUtil.nmemRealloc(dataPointer, dataCapacity);
            } else {
                dataPointer = MemoryUtil.nmemAlloc(dataCapacity);
            }
        }

        ByteBuffer dst = MemoryUtil.memByteBuffer(dataPointer + offset, dataCapacity - offset);

        if (data.isDirect()) {
            MemoryUtil.memCopy(data, dst);
        } else {
            dst.put(data);
        }

        dataLength = newEnd;
        dirty = true;

        return new VulkanArenaSlot(dataType, offset, data.remaining() / dataType.width(), 1, 1);
    }

    public void update(VkCommandBuffer commands) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            if (!dirty) return;
            dirty = false;

            if (hostBuffer == null) {
                hostBuffer = VulkanBuffer.allocHostVisible(allocator, dataCapacity);
            } else if (hostBuffer.byteLen() < dataCapacity) {
                hostBuffer.destroy(allocator);
                hostBuffer = VulkanBuffer.allocHostVisible(allocator, dataCapacity);
            }

            if (deviceBuffer == null) {
                deviceBuffer = VulkanBuffer.allocDeviceLocal(allocator, dataCapacity);
            } else if (deviceBuffer.byteLen() < dataCapacity) {
                deviceBuffer.destroy(allocator);
                deviceBuffer = VulkanBuffer.allocDeviceLocal(allocator, dataCapacity);
            }

            MemoryUtil.memCopy(MemoryUtil.memByteBuffer(dataPointer, dataLength), hostBuffer.mapped());
            vmaFlushAllocation(allocator, hostBuffer.allocation(), 0, dataLength);

            VK10.vkCmdCopyBuffer(
                commands,
                hostBuffer.buffer(),
                deviceBuffer.buffer(),
                VkBufferCopy.calloc(1, stack)
                    .srcOffset(0)
                    .dstOffset(0)
                    .size(dataLength));

            VK10.vkCmdPipelineBarrier(
                commands,
                VK10.VK_PIPELINE_STAGE_TRANSFER_BIT,
                VK10.VK_PIPELINE_STAGE_COMPUTE_SHADER_BIT,
                0,
                null,
                VkBufferMemoryBarrier.calloc(1, stack)
                    .sType$Default()
                    .buffer(deviceBuffer.buffer())
                    .srcAccessMask(VK_ACCESS_TRANSFER_WRITE_BIT)
                    .dstAccessMask(VK_ACCESS_SHADER_READ_BIT)
                    .srcQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
                    .dstQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
                    .offset(0)
                    .size(dataLength),
                null);
        }
    }
}
