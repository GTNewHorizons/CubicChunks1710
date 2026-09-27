package com.cardinalstar.cubicchunks.world.convert;

import static org.junit.jupiter.api.Assertions.*;

import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.ChunkCoordIntPair;
import net.minecraft.world.chunk.storage.RegionFile;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.cardinalstar.cubicchunks.CubicChunksConfig;
import com.cardinalstar.cubicchunks.server.chunkio.CCNBTUtils;
import com.cardinalstar.cubicchunks.server.chunkio.RegionCubeStorage;
import com.cardinalstar.cubicchunks.world.convert.adapter.SectionAdapterDiscovery;
import com.cardinalstar.cubicchunks.world.convert.adapter.biome.BiomeAdapter;
import com.cardinalstar.cubicchunks.world.convert.adapter.biome.BiomeAdapterDiscovery;

class ConversionSafetyTest {

    @TempDir
    Path world;
    private boolean shadowPaging;
    private CCNBTUtils.TagCompression compression;

    @BeforeEach
    void configureStorage() {
        shadowPaging = CubicChunksConfig.useShadowPagingIO;
        compression = CubicChunksConfig.chunkCompression;
        CubicChunksConfig.useShadowPagingIO = false;
        CubicChunksConfig.chunkCompression = CCNBTUtils.TagCompression.GZIP;
    }

    @AfterEach
    void restoreStorage() {
        CubicChunksConfig.useShadowPagingIO = shadowPaging;
        CubicChunksConfig.chunkCompression = compression;
    }

    @Test
    void laterDimensionFailureLeavesEveryOriginalIntactAndCanRetry() throws Exception {
        Path overworld = writeRegion(world, 33, false);
        Path other = writeRegion(world.resolve("DIM1"), 1, true);
        byte[] originalOverworld = Files.readAllBytes(overworld);
        byte[] originalOther = Files.readAllBytes(other);
        assertThrows(IOException.class, () -> convert(new AtomicBoolean()));
        assertArrayEquals(originalOverworld, Files.readAllBytes(overworld));
        assertArrayEquals(originalOther, Files.readAllBytes(other));
        assertNotInstalled(world);
        assertNotInstalled(world.resolve("DIM1"));
        assertFalse(Files.exists(world.resolve(".cubicchunks-conversion")));
        assertFalse(Files.exists(world.resolve(".cubicchunks-anvil-backup")));
        assertEquals(WorldSaveFormat.VANILLA, WorldFormatDetector.detect(world.toFile()));

        writeRegion(world.resolve("DIM1"), 1, false);
        convert(new AtomicBoolean());
        assertEquals(WorldSaveFormat.CC, WorldFormatDetector.detect(world.toFile()));
        assertArrayEquals(originalOverworld, Files.readAllBytes(world.resolve(".cubicchunks-anvil-backup/region/r.0.0.mca")));
    }

    @Test
    void successConvertsAllDimensionsAndRetainsOriginals() throws Exception {
        java.util.Map<Path, byte[]> originals = new java.util.LinkedHashMap<>();
        for (Path dimension : new Path[] { world, world.resolve("DIM-1"), world.resolve("PERSONAL_DIM7") }) {
            originals.put(dimension, Files.readAllBytes(writeRegion(dimension, 1, false)));
        }
        convert(new AtomicBoolean());
        assertTrue(Files.exists(world.resolve("data/cubicchunks.world_format.dat")));
        for (Path dimension : new Path[] { world, world.resolve("DIM-1"), world.resolve("PERSONAL_DIM7") }) {
            assertFalse(Files.exists(dimension.resolve("region")));
            Path saved = world.resolve(".cubicchunks-anvil-backup").resolve(world.relativize(dimension)).resolve("region/r.0.0.mca");
            assertArrayEquals(originals.get(dimension), Files.readAllBytes(saved));
            try (RegionCubeStorage storage = new RegionCubeStorage(dimension)) {
                assertNotNull(storage.readColumn(new ChunkCoordIntPair(0, 0)));
            }
        }
        assertFalse(Files.exists(world.resolve(".cubicchunks-conversion")));
    }

    @Test
    void cancellationAfterBatchFlushPreservesSourceAndRemovesOutput() throws Exception {
        Path source = writeRegion(world, 34, false);
        byte[] original = Files.readAllBytes(source);
        AtomicBoolean cancel = new AtomicBoolean();
        AtomicInteger columns = new AtomicInteger();
        BiomeAdapter adapter = new BiomeAdapter() {
            @Override
            public void writeBiomeData(int[] biomes, NBTTagCompound level) {
                BiomeAdapterDiscovery.eid().writeBiomeData(biomes, level);
                if (columns.incrementAndGet() == 33) cancel.set(true);
            }

            @Override
            public void readBiomeData(NBTTagCompound level, int[] biomes) {
                BiomeAdapterDiscovery.eid().readBiomeData(level, biomes);
            }
        };
        ConversionProgress progress = new ConversionProgress();
        assertThrows(InterruptedIOException.class, () -> new VanillaToCCConverter(SectionAdapterDiscovery.eid(), adapter)
            .convertWorld(world.toFile(), progress, cancel));
        assertEquals(33, columns.get());
        assertFalse(progress.isDone());
        assertArrayEquals(original, Files.readAllBytes(source));
        assertNotInstalled(world);
        assertFalse(Files.exists(world.resolve(".cubicchunks-conversion")));
        convert(new AtomicBoolean());
        assertEquals(WorldSaveFormat.CC, WorldFormatDetector.detect(world.toFile()));
    }

    @Test
    void installFailureRollsBackBothDimensions() throws Exception {
        Path overworld = writeRegion(world, 1, false);
        Path nether = writeRegion(world.resolve("DIM-1"), 1, false);
        byte[] original = Files.readAllBytes(overworld);
        byte[] originalNether = Files.readAllBytes(nether);
        Files.write(world.resolve("data"), new byte[] { 42 });
        assertThrows(IOException.class, () -> convert(new AtomicBoolean()));
        assertArrayEquals(original, Files.readAllBytes(overworld));
        assertArrayEquals(originalNether, Files.readAllBytes(nether));
        assertArrayEquals(new byte[] { 42 }, Files.readAllBytes(world.resolve("data")));
        assertNotInstalled(world);
        assertNotInstalled(world.resolve("DIM-1"));
        assertFalse(Files.exists(world.resolve(".cubicchunks-anvil-backup")));
        assertFalse(Files.exists(world.resolve(".cubicchunks-conversion")));
    }

    @Test
    void existingDestinationIsNeverOverwritten() throws Exception {
        Path source = writeRegion(world, 1, false);
        byte[] original = Files.readAllBytes(source);
        Files.createDirectory(world.resolve("region3d"));
        Path sentinel = world.resolve("region3d/keep");
        Files.write(sentinel, new byte[] { 42 });
        assertThrows(IOException.class, () -> convert(new AtomicBoolean()));
        assertArrayEquals(original, Files.readAllBytes(source));
        assertArrayEquals(new byte[] { 42 }, Files.readAllBytes(sentinel));
        assertFalse(Files.exists(world.resolve(".cubicchunks-conversion")));
    }

    @Test
    void truncatedRegionIsNotPaddedOrSilentlySkipped() throws Exception {
        Files.createDirectory(world.resolve("region"));
        Path source = world.resolve("region/r.0.0.mca");
        byte[] original = new byte[] { 1, 2, 3 };
        Files.write(source, original);
        assertThrows(IOException.class, () -> convert(new AtomicBoolean()));
        assertArrayEquals(original, Files.readAllBytes(source));
        assertNotInstalled(world);
        assertFalse(Files.exists(world.resolve(".cubicchunks-conversion")));
    }

    @Test
    void preCancelledConversionDoesNotCreateOutput() throws Exception {
        writeRegion(world, 1, false);
        assertThrows(InterruptedIOException.class, () -> convert(new AtomicBoolean(true)));
        assertNotInstalled(world);
        assertFalse(Files.exists(world.resolve(".cubicchunks-conversion")));
    }

    @Test
    void abandonedStagingIsNotDeletedOrReused() throws Exception {
        writeRegion(world, 1, false);
        Files.createDirectory(world.resolve(".cubicchunks-conversion"));
        Path sentinel = world.resolve(".cubicchunks-conversion/keep");
        Files.write(sentinel, new byte[] { 42 });
        assertThrows(IOException.class, () -> convert(new AtomicBoolean()));
        assertArrayEquals(new byte[] { 42 }, Files.readAllBytes(sentinel));
        assertNotInstalled(world);
    }

    private void convert(AtomicBoolean cancel) throws IOException {
        new VanillaToCCConverter(SectionAdapterDiscovery.eid(), BiomeAdapterDiscovery.eid())
            .convertWorld(world.toFile(), new ConversionProgress(), cancel);
    }

    private static void assertNotInstalled(Path dimension) {
        assertFalse(Files.exists(dimension.resolve("region2d")));
        assertFalse(Files.exists(dimension.resolve("region3d")));
        assertFalse(Files.exists(dimension.resolve("data/cubicchunks.world_format.dat")));
    }

    private static Path writeRegion(Path dimension, int chunks, boolean wrongCoordinates) throws IOException {
        Files.createDirectories(dimension.resolve("region"));
        Path file = dimension.resolve("region/r.0.0.mca");
        RegionFile region = new RegionFile(file.toFile());
        try {
            for (int i = 0; i < chunks; i++) {
                int x = i / 32, z = i % 32;
                NBTTagCompound root = new NBTTagCompound(), level = new NBTTagCompound();
                level.setInteger("xPos", wrongCoordinates ? 123 : x);
                level.setInteger("zPos", z);
                level.setByteArray("Biomes", new byte[256]);
                root.setTag("Level", level);
                try (DataOutputStream out = region.getChunkDataOutputStream(x, z)) {
                    CompressedStreamTools.write(root, out);
                }
            }
        } finally {
            region.close();
        }
        return file;
    }
}
