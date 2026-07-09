package com.cardinalstar.cubicchunks.api.worldgen.hwaccel;

import static org.lwjgl.util.shaderc.Shaderc.shaderc_compilation_status_success;
import static org.lwjgl.util.shaderc.Shaderc.shaderc_compile_into_spv;
import static org.lwjgl.util.shaderc.Shaderc.shaderc_compiler_initialize;
import static org.lwjgl.util.shaderc.Shaderc.shaderc_compiler_release;
import static org.lwjgl.util.shaderc.Shaderc.shaderc_glsl_compute_shader;
import static org.lwjgl.util.shaderc.Shaderc.shaderc_result_get_bytes;
import static org.lwjgl.util.shaderc.Shaderc.shaderc_result_get_compilation_status;
import static org.lwjgl.util.shaderc.Shaderc.shaderc_result_get_error_message;
import static org.lwjgl.util.shaderc.Shaderc.shaderc_result_release;

import java.nio.ByteBuffer;

import net.minecraft.util.MathHelper;

import com.cardinalstar.cubicchunks.CubicChunks;

import me.eigenraven.lwjgl3ify.api.Lwjgl3Aware;

@Lwjgl3Aware
public class SpirVCompiler {

    private static long compiler;

    private SpirVCompiler() {}

    public static void init() {
        compiler = shaderc_compiler_initialize();
        if (compiler == 0) {
            throw new IllegalStateException("Failed to initialize shaderc compiler");
        }
    }

    public static void destroy() {
        if (compiler != 0) {
            shaderc_compiler_release(compiler);
            compiler = 0;
        }
    }

    public static byte[] compile(String glsl) {
        long result = 0;
        try {
            result = shaderc_compile_into_spv(compiler, glsl, shaderc_glsl_compute_shader, "shader.comp", "main", 0);

            if (shaderc_result_get_compilation_status(result) != shaderc_compilation_status_success) {
                String errorMessage = shaderc_result_get_error_message(result);

                String[] lines = glsl.split("\n\r?");
                int zeroes = MathHelper.ceiling_double_int(Math.log10(lines.length));
                for (int i = 0; i < lines.length; i++) {
                    lines[i] = String.format("%0" + zeroes + "d:", i + 1) + lines[i];
                }

                CubicChunks.LOGGER.error("Shader code:\n{}", String.join("\n", lines));
                CubicChunks.LOGGER.error("Could not compile shader to SPIR-V: {}", errorMessage, new Exception());
                throw new RuntimeException("Could not compile shader to SPIR-V");
            }

            ByteBuffer spirvBytes = shaderc_result_get_bytes(result);
            if (spirvBytes == null) {
                throw new RuntimeException("shaderc returned null SPIR-V bytes despite success status");
            }

            byte[] bytes = new byte[spirvBytes.remaining()];
            spirvBytes.get(bytes);
            return bytes;
        } finally {
            if (result != 0) {
                shaderc_result_release(result);
            }
        }
    }
}
