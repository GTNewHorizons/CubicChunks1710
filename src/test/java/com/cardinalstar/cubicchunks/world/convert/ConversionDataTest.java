package com.cardinalstar.cubicchunks.world.convert;

import static org.junit.jupiter.api.Assertions.*;

import java.io.DataOutputStream;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicBoolean;

import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagDouble;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.world.ChunkCoordIntPair;
import net.minecraft.world.chunk.storage.RegionFile;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.cardinalstar.cubicchunks.CubicChunksConfig;
import com.cardinalstar.cubicchunks.server.chunkio.CCNBTUtils;
import com.cardinalstar.cubicchunks.server.chunkio.RegionCubeStorage;
import com.cardinalstar.cubicchunks.util.CubePos;
import com.cardinalstar.cubicchunks.world.convert.adapter.CubeData;
import com.cardinalstar.cubicchunks.world.convert.adapter.SectionAdapterDiscovery;
import com.cardinalstar.cubicchunks.world.convert.adapter.biome.BiomeAdapterDiscovery;

public class ConversionDataTest {

    @TempDir
    Path temp;

    private boolean originalShadowPaging;
    private CCNBTUtils.TagCompression originalCompression;

    @BeforeEach
    void rememberStorageConfig() {
        originalShadowPaging = CubicChunksConfig.useShadowPagingIO;
        originalCompression = CubicChunksConfig.chunkCompression;
    }

    @AfterEach
    void restoreStorageConfig() {
        CubicChunksConfig.useShadowPagingIO = originalShadowPaging;
        CubicChunksConfig.chunkCompression = originalCompression;
    }

    @Test
    void oversizedCubeFallsBackWithoutLosingBatchNeighbors() throws Exception {
        CubicChunksConfig.useShadowPagingIO = true;
        CubicChunksConfig.chunkCompression = CCNBTUtils.TagCompression.LZ4;
        CubePos largePos = new CubePos(0, 0, 0), smallPos = new CubePos(1, 0, 0);
        NBTTagCompound large = new NBTTagCompound(), small = new NBTTagCompound();
        byte[] bytes = new byte[256 * 1024];
        new java.util.Random(3).nextBytes(bytes);
        large.setByteArray("large", bytes);
        small.setInteger("retained", 123);
        java.util.Map<CubePos, NBTTagCompound> cubes = new java.util.HashMap<>();
        cubes.put(largePos, large);
        cubes.put(smallPos, small);
        try (RegionCubeStorage storage = new RegionCubeStorage(temp)) {
            storage.writeBatch(
                new com.cardinalstar.cubicchunks.api.world.storage.ICubicStorage.NBTBatch(
                    java.util.Collections.emptyMap(),
                    cubes));
            storage.flush();
        }
        try (RegionCubeStorage storage = new RegionCubeStorage(temp)) {
            assertEquals(large, storage.readCube(largePos));
            assertEquals(small, storage.readCube(smallPos));
        } finally {
            CubicChunksConfig.useShadowPagingIO = false;
        }
    }

    @Test
    void compressionRoundTripWithoutClientNatives() throws Exception {
        NBTTagCompound tag = new NBTTagCompound();
        byte[] bytes = new byte[65536];
        new java.util.Random(123).nextBytes(bytes);
        tag.setByteArray("random", bytes);
        tag.setInteger("y", 512);
        for (CCNBTUtils.TagCompression compression : CCNBTUtils.TagCompression.values()) {
            assertEquals(tag, CCNBTUtils.loadTag(CCNBTUtils.saveTag(tag, compression)));
        }
    }

    @Test
    void extendedBiomesRoundTrip() {
        int[] input = new int[256];
        for (int i = 0; i < 256; i++) input[i] = i * 257;
        NBTTagCompound tag = new NBTTagCompound();
        BiomeAdapterDiscovery.eid()
            .writeBiomeData(input, tag);
        int[] output = new int[256];
        BiomeAdapterDiscovery.detect(tag)
            .readBiomeData(tag, output);
        assertArrayEquals(input, output);
    }

    @Test
    void legacyUnsignedIdsAndMissingSkyLight() {
        NBTTagCompound tag = new NBTTagCompound();
        byte[] ids = new byte[8192];
        byte[] meta = new byte[8192];
        ByteBuffer.wrap(ids)
            .putShort(0, (short) 50000);
        ByteBuffer.wrap(meta)
            .putShort(0, (short) 60000);
        tag.setByteArray("Blocks16", ids);
        tag.setByteArray("Data16", meta);
        tag.setByteArray("BlockLight", new byte[2048]);
        CubeData data = new CubeData();
        SectionAdapterDiscovery.detect(tag)
            .readSectionData(tag, data);
        assertEquals(50000, data.blocks[0]);
        assertEquals(60000, data.meta[0]);
        assertEquals(0, data.skyLight[0]);
        tag.removeTag("Data16");
        tag.setByteArray("Data", new byte[2048]);
        assertSame(SectionAdapterDiscovery.eid(), SectionAdapterDiscovery.detect(tag));
    }

    @Test
    void modernSectionRoundTrip() {
        CubeData in = new CubeData();
        for (int i = 0; i < 4096; i++) {
            in.blocks[i] = i * 4001 & 0xFFFFFF;
            in.meta[i] = i * 257 & 0xFFFF;
            in.skyLight[i] = i & 15;
            in.blockLight[i] = (i >> 4) & 15;
        }
        NBTTagCompound tag = new NBTTagCompound();
        SectionAdapterDiscovery.eid()
            .writeSectionData(in, tag);
        CubeData out = new CubeData();
        SectionAdapterDiscovery.detect(tag)
            .readSectionData(tag, out);
        assertArrayEquals(in.blocks, out.blocks);
        assertArrayEquals(in.meta, out.meta);
        assertArrayEquals(in.skyLight, out.skyLight);
        assertArrayEquals(in.blockLight, out.blockLight);
    }

    @Test
    void preservesRootTagsAndNonnegativeEntities() throws Exception {
        CubicChunksConfig.useShadowPagingIO = false;
        CubicChunksConfig.chunkCompression = CCNBTUtils.TagCompression.GZIP;
        java.nio.file.Files.createDirectories(temp.resolve("region"));
        NBTTagCompound root = new NBTTagCompound();
        root.setString("mod-root-data", "retained");
        NBTTagCompound level = new NBTTagCompound();
        root.setTag("Level", level);
        level.setInteger("xPos", 0);
        level.setInteger("zPos", 0);
        level.setByteArray("Biomes", new byte[256]);
        level.setString("mod-level-data", "retained");
        NBTTagList entities = new NBTTagList();
        java.util.Map<Integer, NBTTagCompound> retainedEntities = new java.util.LinkedHashMap<>();
        for (double y : new double[] { -0.25, -16, -16.25, -64, 0, 255.5, 256, 300 }) {
            NBTTagCompound entity = new NBTTagCompound();
            entity.setString("id", "TestEntity");
            NBTTagList pos = new NBTTagList();
            for (double d : new double[] { 1, y, 1 }) pos.appendTag(new NBTTagDouble(d));
            entity.setTag("Pos", pos);
            entities.appendTag(entity);
            if (y >= 0) retainedEntities.put((int) Math.floor(y / 16), entity);
        }
        level.setTag("Entities", entities);
        RegionFile region = new RegionFile(
            temp.resolve("region/r.0.0.mca")
                .toFile());
        try (DataOutputStream out = region.getChunkDataOutputStream(0, 0)) {
            CompressedStreamTools.write(root, out);
        }
        region.close();
        new VanillaToCCConverter(SectionAdapterDiscovery.eid(), BiomeAdapterDiscovery.eid())
            .convert(temp.toFile(), true, new ConversionProgress(), new AtomicBoolean());
        try (RegionCubeStorage storage = new RegionCubeStorage(temp)) {
            NBTTagCompound column = storage.readColumn(new ChunkCoordIntPair(0, 0));
            assertEquals("retained", column.getString("mod-root-data"));
            assertEquals(
                "retained",
                column.getCompoundTag("Level")
                    .getString("mod-level-data"));
            for (int cubeY : new int[] { -1, -2, -4 }) {
                assertNull(storage.readCube(new CubePos(0, cubeY, 0)));
            }
            for (java.util.Map.Entry<Integer, NBTTagCompound> entry : retainedEntities.entrySet()) {
                NBTTagList converted = storage.readCube(new CubePos(0, entry.getKey(), 0))
                    .getCompoundTag("Level")
                    .getTagList("Entities", 10);
                assertEquals(1, converted.tagCount());
                assertEquals(entry.getValue(), converted.getCompoundTagAt(0));
            }
        }
    }
}
