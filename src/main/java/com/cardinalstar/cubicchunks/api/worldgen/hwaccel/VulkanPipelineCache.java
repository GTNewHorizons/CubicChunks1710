package com.cardinalstar.cubicchunks.api.worldgen.hwaccel;

import static org.lwjgl.system.MemoryUtil.memAlloc;
import static org.lwjgl.system.MemoryUtil.memFree;
import static org.lwjgl.vulkan.VK10.vkCreatePipelineCache;
import static org.lwjgl.vulkan.VK10.vkDestroyPipelineCache;
import static org.lwjgl.vulkan.VK10.vkGetPipelineCacheData;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.LongBuffer;
import java.nio.file.Files;

import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkPipelineCacheCreateInfo;

import com.cardinalstar.cubicchunks.CubicChunks;

import me.eigenraven.lwjgl3ify.api.Lwjgl3Aware;

@Lwjgl3Aware
public class VulkanPipelineCache {

    private static long pipelineCache;
    private static File savedCacheFile;

    private VulkanPipelineCache() {}

    public static void init(File cacheDir) {
        if (!cacheDir.exists()) {
            cacheDir.mkdirs();
        }
        savedCacheFile = new File(cacheDir, "pipeline_cache.bin");

        ByteBuffer initialData = null;
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkPipelineCacheCreateInfo createInfo = VkPipelineCacheCreateInfo.calloc(stack)
                .sType$Default();

            if (savedCacheFile.exists()) {
                try {
                    byte[] bytes = Files.readAllBytes(savedCacheFile.toPath());
                    initialData = memAlloc(bytes.length);
                    initialData.put(bytes)
                        .flip();
                    createInfo.pInitialData(initialData);
                } catch (IOException e) {
                    CubicChunks.LOGGER.warn("Failed to read pipeline cache, starting fresh", e);
                }
            }

            LongBuffer lb = stack.mallocLong(1);
            KernelContext.check(vkCreatePipelineCache(KernelContext.getDevice(), createInfo, null, lb));
            pipelineCache = lb.get(0);
        } finally {
            if (initialData != null) memFree(initialData);
        }
    }

    public static void save() {
        if (pipelineCache == 0 || savedCacheFile == null) return;
        try (MemoryStack stack = MemoryStack.stackPush()) {
            PointerBuffer pSize = stack.mallocPointer(1);
            KernelContext.check(vkGetPipelineCacheData(KernelContext.getDevice(), pipelineCache, pSize, null));
            long size = pSize.get(0);
            if (size == 0) return;
            ByteBuffer data = memAlloc((int) size);
            try {
                KernelContext.check(vkGetPipelineCacheData(KernelContext.getDevice(), pipelineCache, pSize, data));
                byte[] bytes = new byte[(int) size];
                data.get(bytes);
                Files.write(savedCacheFile.toPath(), bytes);
            } catch (IOException e) {
                CubicChunks.LOGGER.warn("Failed to save pipeline cache", e);
            } finally {
                memFree(data);
            }
        }
    }

    public static void destroy() {
        save();
        if (pipelineCache != 0) {
            vkDestroyPipelineCache(KernelContext.getDevice(), pipelineCache, null);
            pipelineCache = 0;
        }
    }

    public static long getCache() {
        return pipelineCache;
    }
}
