package com.cardinalstar.cubicchunks.server.chunkio;

import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Random;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.world.ChunkCoordIntPair;

import com.cardinalstar.cubicchunks.util.CubePos;

/** Cube and column NBT for the storage tests, and helpers to inspect and damage region files. */
final class StorageTestData {

    private StorageTestData() {}

    /** A cube with one section of {@code states} random block states, light, an entity list and a tile entity. */
    static NBTTagCompound cube(CubePos pos, long seed, int states) {
        Random random = new Random(seed);
        int[] palette = new int[states];
        for (int i = 0; i < states; i++) palette[i] = random.nextInt(4096) | random.nextInt(16) << 12;
        byte[] blocks = new byte[4096], add = new byte[2048], data = new byte[2048];
        for (int i = 0; i < 4096; i++) {
            int state = palette[random.nextInt(states)], shift = (i & 1) << 2;
            blocks[i] = (byte) state;
            add[i >> 1] |= (byte) ((state >> 8 & 15) << shift);
            data[i >> 1] |= (byte) ((state >> 12 & 15) << shift);
        }
        byte[] sky = new byte[2048];
        random.nextBytes(sky);
        NBTTagCompound section = new NBTTagCompound();
        section.setByte("Y", (byte) 0);
        section.setByteArray("Blocks", blocks);
        section.setByteArray("Add", add);
        section.setByteArray("Data", data);
        section.setByteArray("BlockLight", new byte[2048]);
        section.setByteArray("SkyLight", sky);
        NBTTagList sections = new NBTTagList();
        sections.appendTag(section);
        NBTTagCompound tile = new NBTTagCompound();
        tile.setString("id", "Chest");
        tile.setInteger("x", pos.getMinBlockX());
        NBTTagList tiles = new NBTTagList();
        tiles.appendTag(tile);
        NBTTagCompound level = position(pos);
        level.setTag("Sections", sections);
        level.setTag("Entities", new NBTTagList());
        level.setTag("TileEntities", tiles);
        level.setByteArray("Biomes", new byte[] { 1, (byte) seed });
        NBTTagCompound root = new NBTTagCompound();
        root.setTag("Level", level);
        root.setString("mod:custom", "kept " + seed);
        return root;
    }

    static NBTTagCompound cube(CubePos pos, long seed) {
        return cube(pos, seed, 6);
    }

    /** A cube the compact tables take: no sections, entities, tile entities or ticks. */
    static NBTTagCompound empty(CubePos pos) {
        NBTTagCompound level = position(pos);
        level.setShort("population", (short) 255);
        level.setBoolean("initLightDone", true);
        level.setTag("Entities", new NBTTagList());
        level.setTag("TileEntities", new NBTTagList());
        level.setTag("TileTicks", new NBTTagList());
        NBTTagCompound lighting = new NBTTagCompound();
        int[] heights = new int[256];
        Arrays.fill(heights, 71);
        lighting.setIntArray("LastHeightMap", heights);
        level.setTag("LightingInfo", lighting);
        level.setString("LightingInfoType", "phosphor_cc");
        NBTTagCompound root = new NBTTagCompound();
        root.setTag("Level", level);
        return root;
    }

    private static NBTTagCompound position(CubePos pos) {
        NBTTagCompound level = new NBTTagCompound();
        level.setInteger("x", pos.getX());
        level.setInteger("y", pos.getY());
        level.setInteger("z", pos.getZ());
        level.setByte("v", (byte) 1);
        return level;
    }

    /** A cube with an incompressible tile entity payload of {@code bytes}. */
    static NBTTagCompound large(CubePos pos, long seed, int bytes) {
        NBTTagCompound root = cube(pos, seed);
        byte[] noise = new byte[bytes];
        new Random(seed).nextBytes(noise);
        NBTTagCompound tile = new NBTTagCompound();
        tile.setByteArray("payload", noise);
        NBTTagList tiles = new NBTTagList();
        tiles.appendTag(tile);
        root.getCompoundTag("Level")
            .setTag("TileEntities", tiles);
        return root;
    }

    /** The cubes of a bundle in {@code mask} (bits as {@link CubeBundleCodec#slot}). */
    static Map<CubePos, NBTTagCompound> bundle(CubePos origin, long seed, int mask) {
        Map<CubePos, NBTTagCompound> out = new LinkedHashMap<>();
        for (int s = 0; s < 8; s++) {
            if ((mask & 1 << s) == 0) continue;
            CubePos pos = CubeBundleCodec.member(origin, s);
            out.put(pos, cube(pos, seed * 8 + s));
        }
        return out;
    }

    /** A column NBT with a height map, inhabited time and ChunkAPI-style root tags. */
    static NBTTagCompound column(ChunkCoordIntPair pos, long seed) {
        Random random = new Random(seed);
        NBTTagCompound level = new NBTTagCompound();
        level.setInteger("x", pos.chunkXPos);
        level.setInteger("z", pos.chunkZPos);
        level.setByte("v", (byte) 1);
        level.setLong("InhabitedTime", seed);
        byte[] opacity = new byte[600];
        random.nextBytes(opacity);
        level.setByteArray("OpacityIndex", opacity);
        NBTTagCompound root = new NBTTagCompound();
        root.setTag("Level", level);
        int[] heights = new int[256];
        for (int i = 0; i < 256; i++) heights[i] = 60 + random.nextInt(8);
        root.setIntArray("HeightMap", heights);
        return root;
    }

    static Map<ChunkCoordIntPair, NBTTagCompound> columnGroup(ChunkCoordIntPair origin, long seed, int mask) {
        Map<ChunkCoordIntPair, NBTTagCompound> out = new LinkedHashMap<>();
        for (int s = 0; s < 4; s++) {
            if ((mask & 1 << s) == 0) continue;
            ChunkCoordIntPair pos = ColumnGroupCodec.member(origin, s);
            out.put(pos, column(pos, seed * 4 + s));
        }
        return out;
    }

    static Path cubeRegion(Path save, CubePos p) {
        return save.resolve("region3d/" + (p.getX() >> 4) + "." + (p.getY() >> 4) + "." + (p.getZ() >> 4) + ".3dr");
    }

    static Path columnRegion(Path save, ChunkCoordIntPair p) {
        return save.resolve("region2d/" + (p.chunkXPos >> 5) + "." + (p.chunkZPos >> 5) + ".2dr");
    }

    /** The packed header entry (sector offset and count) of a cube, 0 if it has none. */
    static int header(Path save, CubePos p) throws Exception {
        return header(cubeRegion(save, p), (p.getX() & 15) << 8 | (p.getY() & 15) << 4 | p.getZ() & 15);
    }

    static int header(Path save, ChunkCoordIntPair p) throws Exception {
        return header(columnRegion(save, p), (p.chunkXPos & 31) << 5 | p.chunkZPos & 31);
    }

    private static int header(Path region, int slot) throws Exception {
        if (!region.toFile()
            .exists()) return 0;
        ByteBuffer entry = ByteBuffer.allocate(4);
        try (FileChannel file = FileChannel.open(region)) {
            file.read(entry, slot * 4L);
        }
        return entry.getInt(0);
    }

    /** Overwrites the length prefix of the record a header entry points at. */
    static void damageLength(Path region, int packed, int length) throws Exception {
        try (FileChannel file = FileChannel.open(region, StandardOpenOption.WRITE)) {
            file.write(
                ByteBuffer.allocate(4)
                    .putInt(0, length),
                (long) (packed >>> 8) * 512);
        }
    }
}
