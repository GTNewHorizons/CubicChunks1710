package com.cardinalstar.cubicchunks.api.worldgen.hwaccel;

import static org.lwjgl.vulkan.VK10.VK_SHADER_STAGE_COMPUTE_BIT;
import static org.lwjgl.vulkan.VK10.vkCmdPushConstants;

import java.nio.ByteOrder;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkCommandBuffer;

import com.cardinalstar.cubicchunks.api.worldgen.hwaccel.buffer.BufferAccessor;
import com.cardinalstar.cubicchunks.api.worldgen.hwaccel.buffer.BufferDataType;
import com.cardinalstar.cubicchunks.api.worldgen.hwaccel.buffer.GPUBuffer;
import com.cardinalstar.cubicchunks.api.worldgen.hwaccel.buffer.OffsetBufferAccessor;
import com.github.bsideup.jabel.Desugar;

import me.eigenraven.lwjgl3ify.api.Lwjgl3Aware;

@Lwjgl3Aware
public class PushConstantLayout {

    public interface PushConstant {

        int getWordOffset();

        int getWordSize();

        void generate(StringBuilder buffer);

        void update(IntBuffer data, Map<String, GPUBuffer> inputs, Map<String, Number> parameters);
    }

    @Desugar
    public record ArenaOffsetPushConstant(String bufferName, String pcName, int pcWordOffset) implements PushConstant {

        @Override
        public int getWordOffset() {
            return pcWordOffset;
        }

        @Override
        public int getWordSize() {
            return 1;
        }

        @Override
        public void generate(StringBuilder buffer) {
            buffer.append("    uint ")
                .append(pcName)
                .append(";\n");
        }

        @Override
        public void update(IntBuffer data, Map<String, GPUBuffer> inputs, Map<String, Number> parameters) {
            GPUBuffer buffer = inputs.get(bufferName);

            data.put(pcWordOffset, buffer.getBufferOffset() / 4);
        }
    }

    @Desugar
    public record ConstantOffsetPushConstant(int constantByteOffset, String pcName, int pcWordOffset)
        implements PushConstant {

        @Override
        public int getWordOffset() {
            return pcWordOffset;
        }

        @Override
        public int getWordSize() {
            return 1;
        }

        @Override
        public void generate(StringBuilder buffer) {
            buffer.append("    uint ")
                .append(pcName)
                .append(";\n");
        }

        @Override
        public void update(IntBuffer data, Map<String, GPUBuffer> inputs, Map<String, Number> parameters) {
            data.put(pcWordOffset, constantByteOffset / 4);
        }
    }

    @Desugar
    public record ParameterPushConstant(BufferDataType dataType, String paramName, String pcName, int pcWordOffset)
        implements PushConstant {

        @Override
        public int getWordOffset() {
            return pcWordOffset;
        }

        @Override
        public int getWordSize() {
            return dataType.width() / 4;
        }

        @Override
        public void generate(StringBuilder buffer) {
            String glslType = switch (dataType) {
                case i32 -> "int";
                case u32 -> "uint";
                case i64 -> "long";
                case u64 -> "ulong";
                case f32 -> "float";
                case f64 -> "double";
            };

            buffer.append("    ")
                .append(glslType)
                .append(" ")
                .append(pcName)
                .append(";\n");
        }

        @Override
        public void update(IntBuffer data, Map<String, GPUBuffer> inputs, Map<String, Number> parameters) {
            switch (dataType) {
                case i32, u32 -> {
                    data.put(pcWordOffset, (int) parameters.get(paramName));
                }
                case i64, u64, f64 -> {
                    long value;

                    if (dataType == BufferDataType.f64) {
                        value = Double.doubleToLongBits((double) parameters.get(paramName));
                    } else {
                        value = (long) parameters.get(paramName);
                    }

                    int lowerOffset, upperOffset;

                    if (ByteOrder.nativeOrder() == ByteOrder.BIG_ENDIAN) {
                        upperOffset = 0;
                        lowerOffset = 1;
                    } else {
                        lowerOffset = 0;
                        upperOffset = 1;
                    }

                    int lower = (int) value;
                    int upper = (int) (value >> 32);

                    data.put(pcWordOffset + lowerOffset, lower);
                    data.put(pcWordOffset + upperOffset, upper);
                }
                case f32 -> {
                    data.put(pcWordOffset, Float.floatToIntBits((float) parameters.get(paramName)));
                }
            }
        }
    }

    private final List<PushConstant> pushConstants = new ArrayList<>();

    private int findSlot(int wordSize) {
        BitSet taken = new BitSet();

        for (var pc : pushConstants) {
            for (int i = 0; i < pc.getWordSize(); i++) {
                taken.set(pc.getWordOffset() + i);
            }
        }

        // Find a contiguous block of [wordSize] free words, so that the index is properly aligned
        outer: for (int i = 0; i < 32; i += wordSize) {
            for (int o = 0; o < wordSize; o++) {
                if (taken.get(i + o)) continue outer;
            }

            return i;
        }

        return -1;
    }

    public BufferAccessor addArenaOffset(BufferDataType dataType, String bufferName) {
        int offset = findSlot(1);

        if (offset == -1) {
            throw new IllegalStateException(
                "All push constant slots are full: cannot allocate another one: " + bufferName);
        }

        String pcName = "arenaOffset" + pushConstants.size();

        pushConstants.add(new ArenaOffsetPushConstant(bufferName, pcName, offset));

        return new OffsetBufferAccessor("arena", pcName, dataType);
    }

    public BufferAccessor addConstantOffset(BufferDataType dataType, int constantByteOffset) {
        int offset = findSlot(1);

        if (offset == -1) {
            throw new IllegalStateException(
                "All push constant slots are full: cannot allocate another one: " + constantByteOffset);
        }

        String pcName = "constantOffset" + pushConstants.size();

        pushConstants.add(new ConstantOffsetPushConstant(constantByteOffset, pcName, offset));

        return new OffsetBufferAccessor("constants", pcName, dataType);
    }

    public String addParameter(BufferDataType dataType, String paramName) {
        int offset = findSlot(dataType.width() / 4);

        if (offset == -1) {
            throw new IllegalStateException(
                "All push constant slots are full: cannot allocate another one: " + paramName);
        }

        String pcName = paramName + pushConstants.size();

        pushConstants.add(new ParameterPushConstant(dataType, paramName, pcName, offset));

        return "pc." + pcName;
    }

    public String getPushConstantDefinition() {
        StringBuilder text = new StringBuilder();

        text.append("layout(push_constant) uniform PC {\n");

        // Find the highest word index used so we don't iterate over empty tail.
        int highWater = 0;
        for (PushConstant pc : pushConstants) {
            highWater = Math.max(highWater, pc.getWordOffset() + pc.getWordSize());
        }

        PushConstant[] slots = new PushConstant[highWater];
        for (PushConstant pc : pushConstants) {
            for (int word = 0; word < pc.getWordSize(); word++) {
                slots[pc.getWordOffset() + word] = pc;
            }
        }

        HashSet<PushConstant> emitted = new HashSet<>();
        int padCounter = 0;

        for (int i = 0; i < slots.length; i++) {
            PushConstant pc = slots[i];

            if (pc == null) {
                // Gap between entries — pad to maintain correct offsets.
                text.append("    uint __padding")
                    .append(padCounter++)
                    .append(";\n");
                continue;
            }

            // Already emitted the declaration for this PC (multi-word entry).
            if (!emitted.add(pc)) continue;

            pc.generate(text);
        }

        text.append("} pc;\n");

        return text.toString();
    }

    public void upload(VkCommandBuffer commands, long pipelineLayout, Map<String, GPUBuffer> inputs,
        Map<String, Number> parameters) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer data = stack.callocInt(32);

            for (var pc : pushConstants) {
                pc.update(data, inputs, parameters);
            }

            vkCmdPushConstants(commands, pipelineLayout, VK_SHADER_STAGE_COMPUTE_BIT, 0, data);
        }
    }
}
