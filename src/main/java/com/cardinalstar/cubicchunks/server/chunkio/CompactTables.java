package com.cardinalstar.cubicchunks.server.chunkio;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;

import net.jpountz.lz4.LZ4Factory;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTSizeTracker;
import net.minecraft.nbt.NBTTagCompound;

import com.cardinalstar.cubicchunks.CubicChunks;
import com.cardinalstar.cubicchunks.api.world.storage.ICubicStorage.NBTBatch;
import com.cardinalstar.cubicchunks.util.CubePos;

/**
 * Part of the conversion of old worlds ({@link LegacyWorldConversion}): converts the compact empty-cube tables of
 * earlier versions ({@code region3d-empty}, format
 * {@code cubicchunks:anvil3d-compact-empty}, v0.1.27-alpha-pre) to cube states. Every table's cubes are written
 * through bundle storage, as states or as full records; the tables are removed only once all of them are written, so
 * an interrupted conversion is repeated on the next start and other versions keep refusing the world until then.
 */
final class CompactTables {

    /** Directory of the compact tables in a dimension. */
    static final String DIRECTORY = "region3d-empty";
    private static final long MAGIC = 0x4343454D50545901L; // CCEMPTY, version 1
    private static final int ENTRIES = 4096;
    private static final int MAX_TEMPLATE_BYTES = 8192;

    private CompactTables() {}

    /** Converts a dimension's compact tables, if it has any. */
    static void convert(Path dimension) throws IOException {
        Path directory = dimension.resolve(DIRECTORY);
        if (!Files.isDirectory(directory)) return;
        List<Path> tables = new ArrayList<>();
        try (DirectoryStream<Path> files = Files.newDirectoryStream(directory, "*.cce")) {
            for (Path file : files) tables.add(file);
        }
        Collections.sort(tables);
        long start = System.nanoTime(), cubes = 0;
        CubicChunks.LOGGER
            .info("Converting {} compact empty-cube tables of {} to cube states", tables.size(), dimension);
        try (RegionCubeStorage storage = new RegionCubeStorage(dimension)) {
            for (Path table : tables) {
                Map<CubePos, NBTTagCompound> read = read(table);
                storage.writeBatch(new NBTBatch(new HashMap<>(), read));
                cubes += read.size();
            }
            storage.flush();
        }
        // every table's cubes are stored: only now the tables go
        try (Stream<Path> files = Files.walk(directory)) {
            for (Path file : (Iterable<Path>) files.sorted(Comparator.reverseOrder())::iterator) Files.delete(file);
        }
        CubicChunks.LOGGER.info(
            "Converted {} cubes of {} compact tables of {} in {} ms",
            cubes,
            tables.size(),
            dimension,
            (System.nanoTime() - start) / 1_000_000);
    }

    /** The cubes of one table, with their coordinates restored. */
    static Map<CubePos, NBTTagCompound> read(Path table) throws IOException {
        String[] parts = table.getFileName()
            .toString()
            .split("\\.");
        if (parts.length != 4) throw new IOException("Invalid compact region name: " + table);
        CubePos region;
        try {
            region = new CubePos(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]), Integer.parseInt(parts[2]));
        } catch (NumberFormatException e) {
            throw new IOException("Invalid compact region name: " + table, e);
        }
        long size = Files.size(table);
        if (size < 16 || size > 16L + ENTRIES * (MAX_TEMPLATE_BYTES + 8L)) {
            throw new IOException("Invalid compact cube table size: " + table);
        }
        Map<CubePos, NBTTagCompound> out = new HashMap<>();
        try (DataInputStream in = new DataInputStream(new BufferedInputStream(Files.newInputStream(table)))) {
            if (in.readLong() != MAGIC) throw new IOException("Unknown compact cube format: " + table);
            int count = in.readInt();
            if (count < 0 || count > ENTRIES) throw new IOException("Invalid template count: " + table);
            List<byte[]> templates = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                int length = in.readInt();
                if (length < 1 || length > MAX_TEMPLATE_BYTES) throw new IOException("Invalid template length");
                byte[] value = new byte[length];
                in.readFully(value);
                templates.add(value);
            }
            int entries = in.readInt();
            if (entries < 0 || entries > ENTRIES) throw new IOException("Invalid compact cube count");
            for (int i = 0; i < entries; i++) {
                int slot = in.readUnsignedShort(), template = in.readUnsignedShort();
                if (slot >= ENTRIES || template >= count) throw new IOException("Invalid compact cube entry");
                CubePos pos = new CubePos(
                    region.getX() * 16 + (slot >> 8),
                    region.getY() * 16 + ((slot >> 4) & 15),
                    region.getZ() * 16 + (slot & 15));
                if (out.put(pos, restore(pos, templates.get(template))) != null)
                    throw new IOException("Duplicate compact cube entry");
            }
            if (in.read() != -1) throw new IOException("Trailing compact cube data: " + table);
        } catch (IOException e) {
            throw new IOException("Could not read compact cube table " + table, e);
        }
        return out;
    }

    private static NBTTagCompound restore(CubePos pos, byte[] value) throws IOException {
        NBTTagCompound root = decode(value);
        if (!root.hasKey("Level", 10)) throw new IOException("Compact cube is missing Level");
        NBTTagCompound level = root.getCompoundTag("Level");
        if (level.hasKey("x") || level.hasKey("y") || level.hasKey("z") || level.hasKey("Sections")) {
            throw new IOException("Invalid sectionless cube template");
        }
        level.setInteger("x", pos.getX());
        level.setInteger("y", pos.getY());
        level.setInteger("z", pos.getZ());
        return root;
    }

    private static NBTTagCompound decode(byte[] value) throws IOException {
        try {
            ByteBuffer header = ByteBuffer.wrap(value)
                .order(ByteOrder.LITTLE_ENDIAN);
            byte[] raw;
            if (value.length >= 2 && (header.getShort(0) & 0xffff) == CCNBTUtils.GZIP_MAGIC_NUMBER) {
                try (GZIPInputStream in = new GZIPInputStream(new ByteArrayInputStream(value))) {
                    raw = new byte[MAX_TEMPLATE_BYTES + 1];
                    int count = 0, read;
                    while (count < raw.length && (read = in.read(raw, count, raw.length - count)) != -1) count += read;
                    if (count > MAX_TEMPLATE_BYTES) throw new IOException("Oversized compact cube template");
                    raw = Arrays.copyOf(raw, count);
                }
            } else if (value.length >= 4 && header.getInt(0) == ZstdCodec.MAGIC) {
                raw = ZstdCodec.decompress(header, MAX_TEMPLATE_BYTES);
            } else if (value.length >= 8 && header.getInt(0) == CCNBTUtils.LZ4_MAGIC_NUMBER) {
                int length = header.getInt(4);
                if (length < 1 || length > MAX_TEMPLATE_BYTES) throw new IOException("Invalid compact NBT length");
                raw = new byte[length];
                int decoded = LZ4Factory.fastestInstance()
                    .safeDecompressor()
                    .decompress(value, 8, value.length - 8, raw, 0, length);
                if (decoded != length) throw new IOException("Truncated compact NBT");
            } else if (value.length >= 16
                && header.getLong(0) == CCNBTUtils.NONE_MAGIC_NUMBER_UUID.getLeastSignificantBits()
                && header.getLong(8) == CCNBTUtils.NONE_MAGIC_NUMBER_UUID.getMostSignificantBits()) {
                    raw = Arrays.copyOfRange(value, 16, value.length);
                } else throw new IOException("Unknown compact NBT codec");
            try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(raw))) {
                NBTTagCompound result = CompressedStreamTools.func_152456_a(in, new NBTSizeTracker(2 * 1024 * 1024));
                if (in.read() != -1) throw new IOException("Trailing compact NBT data");
                return result;
            }
        } catch (RuntimeException e) {
            throw new IOException("Invalid compact cube template", e);
        }
    }
}
