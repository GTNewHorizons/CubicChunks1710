package com.cardinalstar.cubicchunks.api.worldgen.hwaccel;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import com.cardinalstar.cubicchunks.CubicChunks;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import me.eigenraven.lwjgl3ify.api.Lwjgl3Aware;

@Lwjgl3Aware
public class ShaderCache {

    private static File shadersDir;
    private static Map<String, Long> index;
    private static final Gson GSON = new Gson();
    private static final long EXPIRY_DAYS = 30;

    private ShaderCache() {}

    public static void init(File cacheDir) {
        shadersDir = new File(cacheDir, "shaders");
        if (!shadersDir.exists()) {
            shadersDir.mkdirs();
        }
        loadIndex();
        cleanup();
    }

    public static byte[] getOrCompile(String glslSource) {
        String hash = sha256Hex(glslSource);
        File spvFile = new File(shadersDir, hash + ".spv");

        if (spvFile.exists()) {
            try {
                byte[] bytes = Files.readAllBytes(spvFile.toPath());
                long today = LocalDate.now()
                    .toEpochDay();
                index.put(hash, today);
                saveIndex();
                return bytes;
            } catch (IOException e) {
                CubicChunks.LOGGER.warn("Failed to read cached SPIR-V {}, recompiling", spvFile, e);
            }
        }

        // Cache miss — compile
        byte[] bytes = SpirVCompiler.compile(glslSource);

        try {
            Files.write(spvFile.toPath(), bytes);
        } catch (IOException e) {
            CubicChunks.LOGGER.warn("Failed to write SPIR-V cache file {}", spvFile, e);
        }

        long today = LocalDate.now()
            .toEpochDay();
        index.put(hash, today);
        saveIndex();

        return bytes;
    }

    private static void loadIndex() {
        File indexFile = new File(shadersDir, "index.json");
        if (indexFile.exists()) {
            try {
                String json = new String(Files.readAllBytes(indexFile.toPath()), StandardCharsets.UTF_8);
                Map<String, Long> loaded = GSON.fromJson(json, new TypeToken<Map<String, Long>>() {}.getType());
                index = (loaded != null) ? loaded : new HashMap<>();
                return;
            } catch (Exception e) {
                CubicChunks.LOGGER.warn("Failed to read shader cache index, clearing cache", e);
            }
        } else {
            CubicChunks.LOGGER.warn("Shader cache index not found, starting fresh");
        }

        // Delete all existing .spv files since we can't trust them without the index
        File[] spvFiles = shadersDir.listFiles((dir, name) -> name.endsWith(".spv"));
        if (spvFiles != null) {
            for (File f : spvFiles) {
                f.delete();
            }
        }
        index = new HashMap<>();
    }

    private static void saveIndex() {
        File indexFile = new File(shadersDir, "index.json");
        try {
            Files.write(
                indexFile.toPath(),
                GSON.toJson(index)
                    .getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            CubicChunks.LOGGER.warn("Failed to save shader cache index", e);
        }
    }

    private static void cleanup() {
        long today = LocalDate.now()
            .toEpochDay();
        long cutoff = today - EXPIRY_DAYS;

        boolean modified = false;
        Iterator<Map.Entry<String, Long>> it = index.entrySet()
            .iterator();
        while (it.hasNext()) {
            Map.Entry<String, Long> entry = it.next();
            if (entry.getValue() < cutoff) {
                it.remove();
                new File(shadersDir, entry.getKey() + ".spv").delete();
                modified = true;
            }
        }

        // Orphan cleanup: remove .spv files whose hash isn't in the index
        File[] spvFiles = shadersDir.listFiles((dir, name) -> name.endsWith(".spv"));
        if (spvFiles != null) {
            for (File f : spvFiles) {
                String name = f.getName();
                String hashPart = name.substring(0, name.length() - 4); // strip ".spv"
                if (!index.containsKey(hashPart)) {
                    f.delete();
                }
            }
        }

        if (modified) {
            saveIndex();
        }
    }

    private static String sha256Hex(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }
}
