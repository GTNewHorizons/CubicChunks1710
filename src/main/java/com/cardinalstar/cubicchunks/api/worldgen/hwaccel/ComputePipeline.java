package com.cardinalstar.cubicchunks.api.worldgen.hwaccel;

import static org.lwjgl.system.MemoryUtil.memAlloc;
import static org.lwjgl.system.MemoryUtil.memFree;
import static org.lwjgl.vulkan.VK10.VK_PIPELINE_BIND_POINT_COMPUTE;
import static org.lwjgl.vulkan.VK10.VK_SHADER_STAGE_COMPUTE_BIT;
import static org.lwjgl.vulkan.VK10.vkCmdBindPipeline;
import static org.lwjgl.vulkan.VK10.vkCreateComputePipelines;
import static org.lwjgl.vulkan.VK10.vkCreateShaderModule;
import static org.lwjgl.vulkan.VK10.vkDestroyPipeline;
import static org.lwjgl.vulkan.VK10.vkDestroyShaderModule;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.LongBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import net.minecraft.launchwrapper.Launch;

import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkCommandBuffer;
import org.lwjgl.vulkan.VkComputePipelineCreateInfo;
import org.lwjgl.vulkan.VkPipelineShaderStageCreateInfo;
import org.lwjgl.vulkan.VkShaderModuleCreateInfo;

import com.cardinalstar.cubicchunks.CubicChunksConfig;

import me.eigenraven.lwjgl3ify.api.Lwjgl3Aware;

@Lwjgl3Aware
public class ComputePipeline {

    private static Path dumpDir;

    static {
        if (CubicChunksConfig.dumpComputeShaderCode) {
            int i = 0;

            do {
                dumpDir = Launch.minecraftHome.toPath()
                    .resolve("compute-shader-dumps" + (i++));
            } while (Files.exists(dumpDir));

            try {
                Files.createDirectories(dumpDir);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
    }

    private long pipeline;

    public ComputePipeline(String executorName, String glslSource) {
        try {
            Files.write(dumpDir.resolve(executorName + ".glsl"), glslSource.getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        byte[] spirv = ShaderCache.getOrCompile(glslSource);

        ByteBuffer spirvBuffer = memAlloc(spirv.length);
        spirvBuffer.put(spirv)
            .flip();

        try (MemoryStack stack = MemoryStack.stackPush()) {
            LongBuffer lb = stack.mallocLong(1);

            VkShaderModuleCreateInfo shaderInfo = VkShaderModuleCreateInfo.calloc(stack)
                .sType$Default()
                .pCode(spirvBuffer);

            KernelContext.check(vkCreateShaderModule(KernelContext.getDevice(), shaderInfo, null, lb));
            long shaderModule = lb.get(0);

            // spirvBuffer is no longer needed once the shader module is created
            memFree(spirvBuffer);
            spirvBuffer = null;

            try {
                VkComputePipelineCreateInfo.Buffer pipelineInfo = VkComputePipelineCreateInfo.calloc(1, stack);
                pipelineInfo.get(0)
                    .sType$Default()
                    .layout(
                        KernelContext.getScheduler()
                            .getPipelineLayout())
                    .stage(
                        VkPipelineShaderStageCreateInfo.calloc(stack)
                            .sType$Default()
                            .stage(VK_SHADER_STAGE_COMPUTE_BIT)
                            .module(shaderModule)
                            .pName(stack.ASCII("main")));

                KernelContext.check(
                    vkCreateComputePipelines(
                        KernelContext.getDevice(),
                        VulkanPipelineCache.getCache(),
                        pipelineInfo,
                        null,
                        lb));

                pipeline = lb.get(0);
            } finally {
                vkDestroyShaderModule(KernelContext.getDevice(), shaderModule, null);
            }
        } finally {
            if (spirvBuffer != null) memFree(spirvBuffer);
        }
    }

    public void bind(VkCommandBuffer cmd) {
        vkCmdBindPipeline(cmd, VK_PIPELINE_BIND_POINT_COMPUTE, pipeline);
    }

    public void destroy() {
        if (pipeline != 0) {
            vkDestroyPipeline(KernelContext.getDevice(), pipeline, null);
            pipeline = 0;
        }
    }

    public long getHandle() {
        return pipeline;
    }
}
