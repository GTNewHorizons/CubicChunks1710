package com.cardinalstar.cubicchunks.api.worldgen.hwaccel;

import static org.lwjgl.opengl.GL20.GL_COMPILE_STATUS;
import static org.lwjgl.opengl.GL20.GL_LINK_STATUS;
import static org.lwjgl.opengl.GL20.glAttachShader;
import static org.lwjgl.opengl.GL20.glCompileShader;
import static org.lwjgl.opengl.GL20.glCreateProgram;
import static org.lwjgl.opengl.GL20.glCreateShader;
import static org.lwjgl.opengl.GL20.glGetProgramInfoLog;
import static org.lwjgl.opengl.GL20.glGetProgrami;
import static org.lwjgl.opengl.GL20.glGetShaderInfoLog;
import static org.lwjgl.opengl.GL20.glGetShaderi;
import static org.lwjgl.opengl.GL20.glLinkProgram;
import static org.lwjgl.opengl.GL20.glShaderSource;
import static org.lwjgl.opengl.GL43.GL_COMPUTE_SHADER;

import java.util.Map;

import net.minecraft.util.MathHelper;

import com.cardinalstar.cubicchunks.CubicChunks;
import com.cardinalstar.cubicchunks.api.worldgen.hwaccel.buffer.BufferAllocator;
import com.cardinalstar.cubicchunks.api.worldgen.hwaccel.buffer.BufferDescriptor;

public interface KernelExecutor<Key> {

    Map<String, BufferDescriptor> getOutputs(ComputePlan plan, KernelSubmissionToken submission, Key key, Map<String, BufferDescriptor> inputs);

    KernelSubmissionResult[] submit(BufferAllocator alloc, KernelSubmission<Key>[] submissions);

    static int createProgram(String code) {
        int shader = glCreateShader(GL_COMPUTE_SHADER);
        glShaderSource(shader, code);
        glCompileShader(shader);

        if (glGetShaderi(shader, GL_COMPILE_STATUS) == 0) {
            String log = glGetShaderInfoLog(shader, 32000);

            String[] lines = code.split("\n\r?");

            int zeroes = MathHelper.ceiling_double_int(Math.log10(lines.length));

            for (int i = 0; i < lines.length; i++) {
                lines[i] = String.format("%0" + zeroes + "d:", i + 1) + lines[i];
            }

            CubicChunks.LOGGER.error("Shader code:\n{}", String.join("\n", lines));

            CubicChunks.LOGGER.error("Could not compile shader: {}", log, new Exception());
            throw new RuntimeException("Could not compile shader");
        }

        int program = glCreateProgram();

        glAttachShader(program, shader);
        glLinkProgram(program);

        if (glGetProgrami(program, GL_LINK_STATUS) == 0) {
            String log = glGetProgramInfoLog(program, 32000);

            String[] lines = code.split("\n\r?");

            int zeroes = MathHelper.ceiling_double_int(Math.log10(lines.length));

            for (int i = 0; i < lines.length; i++) {
                lines[i] = String.format("%0" + zeroes + "d:", i + 1) + lines[i];
            }

            CubicChunks.LOGGER.error("Shader code:\n{}", String.join("\n", lines));

            CubicChunks.LOGGER.error("Could not link shader: {}", log, new Exception());
            throw new RuntimeException("Could not link shader");
        }

        return program;
    }
}
