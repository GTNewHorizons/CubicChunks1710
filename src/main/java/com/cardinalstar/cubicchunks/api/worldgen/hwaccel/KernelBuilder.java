package com.cardinalstar.cubicchunks.api.worldgen.hwaccel;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.HashMap;
import java.util.Map;

import com.cardinalstar.cubicchunks.api.worldgen.hwaccel.buffer.BufferAccessor;
import com.cardinalstar.cubicchunks.api.worldgen.hwaccel.buffer.BufferDataType;
import com.cardinalstar.cubicchunks.api.worldgen.hwaccel.buffer.BufferLayout;
import com.cardinalstar.cubicchunks.api.worldgen.hwaccel.buffer.ConstantBuffer;
import com.cardinalstar.cubicchunks.api.worldgen.hwaccel.buffer.GPUBuffer;

public class KernelBuilder {

    private int discriminator = 0;

    public final PushConstantLayout pushConstants = new PushConstantLayout();
    public final ConstantBuffer constants;

    public final StringBuilder preamble = new StringBuilder();
    public final StringBuilder logic = new StringBuilder();

    public final Map<String, BufferLayout> inputs = new HashMap<>();
    public final Map<String, BufferLayout> outputs = new HashMap<>();

    public KernelBuilder(ConstantBuffer constants) {
        this.constants = constants;
    }

    public String createName(String human) {
        return human + "_" + (discriminator++);
    }

    public BufferAccessor addConstant(BufferDataType dataType, ByteBuffer data) {
        GPUBuffer buffer = constants.addConstant(dataType, data);

        return pushConstants.addConstantOffset(dataType, buffer.getBufferOffset());
    }

    public BufferAccessor addConstant(IntBuffer data) {
        GPUBuffer buffer = constants.addConstant(data);

        return pushConstants.addConstantOffset(BufferDataType.i32, buffer.getBufferOffset());
    }

    public BufferAccessor addConstant(int[] data) {
        GPUBuffer buffer = constants.addConstant(data);

        return pushConstants.addConstantOffset(BufferDataType.i32, buffer.getBufferOffset());
    }

    public BufferAccessor addConstant(FloatBuffer data) {
        GPUBuffer buffer = constants.addConstant(data);

        return pushConstants.addConstantOffset(BufferDataType.f32, buffer.getBufferOffset());
    }

    public BufferAccessor addConstant(float[] data) {
        GPUBuffer buffer = constants.addConstant(data);

        return pushConstants.addConstantOffset(BufferDataType.f32, buffer.getBufferOffset());
    }

    public void addBufferMacros(String macroName, BufferAccessor buffer) {
        preamble.append("#define GET_")
            .append(macroName)
            .append("(index) ")
            .append(
                buffer.getDataType()
                    .fromUint(buffer.access("index")))
            .append("\n");
        preamble.append("#define SET_")
            .append(macroName)
            .append("(index, value) ")
            .append(buffer.access("index"))
            .append(" = ")
            .append(
                buffer.getDataType()
                    .toUint("value"))
            .append("\n");
    }

    public void addInputBuffer(String name, BufferLayout layout) {
        inputs.put(name, layout);
        addBufferMacros(
            KernelBuilder.toScreamingSnakeCase(name),
            pushConstants.addArenaOffset(layout.dataType(), name));
    }

    public void addOutputBuffer(String name, BufferLayout layout) {
        outputs.put(name, layout);
        addBufferMacros(
            KernelBuilder.toScreamingSnakeCase(name),
            pushConstants.addArenaOffset(layout.dataType(), name));
    }

    public void addParameter(BufferDataType dataType, String name) {
        String pcName = pushConstants.addParameter(dataType, name);
        preamble.append("#define GET_")
            .append(toScreamingSnakeCase(name))
            .append(" ")
            .append(pcName)
            .append("\n");
    }

    public void addMacro(String name, String repl) {
        preamble.append("#define ")
            .append(name)
            .append(" ")
            .append(repl)
            .append("\n");
    }

    public void addMacro(String name, float value) {
        preamble.append("#define ")
            .append(name)
            .append(" ")
            .append(value)
            .append("f")
            .append("\n");
    }

    public void addMacro(String name, int value) {
        preamble.append("#define ")
            .append(name)
            .append(" ")
            .append(value)
            .append("\n");
    }

    /// theThing -> THE_THING and TheThing -> THE_THING
    public static String toScreamingSnakeCase(String camelCase) {
        StringBuilder out = new StringBuilder();

        for (char c : camelCase.toCharArray()) {
            if (Character.isUpperCase(c) && out.length() > 0) {
                out.append("_");
            }

            out.append(Character.toUpperCase(c));
        }

        return out.toString();
    }
}
