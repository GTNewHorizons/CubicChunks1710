package com.cardinalstar.cubicchunks.server.chunkio;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.zip.GZIPInputStream;

import net.jpountz.lz4.LZ4Factory;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTSizeTracker;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.world.ChunkCoordIntPair;

import com.cardinalstar.cubicchunks.CubicChunksConfig;
import com.cardinalstar.cubicchunks.api.world.storage.ICubicStorage;
import com.cardinalstar.cubicchunks.util.CubePos;

/** Opt-in Anvil3D storage with lossless, per-region templates for sectionless cubes. */
public final class CompactCubeStorage implements ICubicStorage {

    public static final String DIRECTORY = "region3d-empty";
    private static final long MAGIC = 0x4343454D50545901L; // CCEMPTY, version 1
    private static final int ENTRIES = 4096;
    private static final int MAX_TEMPLATE_BYTES = 8192;
    private static final int CACHE_BYTES = 4 * 1024 * 1024;

    private final RegionCubeStorage regular;
    private final Path directory;
    private final LinkedHashMap<CubePos, Table> cache = new LinkedHashMap<>(16, 0.75f, true);
    private boolean closed;

    public CompactCubeStorage(Path path) throws IOException {
        this.directory = path.resolve(DIRECTORY);
        this.regular = new RegionCubeStorage(path, true);
        try {
            Files.createDirectories(directory);
        } catch (IOException | RuntimeException | Error e) {
            try {
                regular.close();
            } catch (IOException close) {
                e.addSuppressed(close);
            }
            throw e;
        }
    }

    private static CubePos region(CubePos pos) {
        return new CubePos(pos.getX() >> 4, pos.getY() >> 4, pos.getZ() >> 4);
    }

    private static int slot(CubePos pos) {
        return (pos.getX() & 15) << 8 | (pos.getY() & 15) << 4 | (pos.getZ() & 15);
    }

    private static CubePos position(CubePos region, int slot) {
        return new CubePos(
            region.getX() * 16 + (slot >> 8),
            region.getY() * 16 + ((slot >> 4) & 15),
            region.getZ() * 16 + (slot & 15));
    }

    private Path file(CubePos region) {
        return directory.resolve(region.getX() + "." + region.getY() + "." + region.getZ() + ".cce");
    }

    private void checkOpen() throws IOException {
        if (closed) throw new IOException("Compact cube storage is closed");
    }

    private void remember(CubePos key, Table table) {
        cache.put(key, table);
        long bytes = 0;
        for (Table value : cache.values()) bytes += value.memoryEstimate();
        var entries = cache.entrySet()
            .iterator();
        // Keep a single large table rather than rereading it for every cube in that region.
        while (entries.hasNext() && cache.size() > 1 && (bytes > CACHE_BYTES || cache.size() > 32)) {
            Table removed = entries.next()
                .getValue();
            bytes -= removed.memoryEstimate();
            entries.remove();
        }
    }

    private Table table(CubePos key) throws IOException {
        Table result = cache.get(key);
        if (result != null) return result;
        Path path = file(key);
        result = new Table();
        if (Files.exists(path)) {
            long size = Files.size(path);
            if (size < 16 || size > 16L + ENTRIES * (MAX_TEMPLATE_BYTES + 8L)) {
                throw new IOException("Invalid compact cube table size: " + path);
            }
            try (DataInputStream in = new DataInputStream(new BufferedInputStream(Files.newInputStream(path)))) {
                if (in.readLong() != MAGIC) throw new IOException("Unknown compact cube format: " + path);
                int count = in.readInt();
                if (count < 0 || count > ENTRIES) throw new IOException("Invalid template count: " + path);
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
                    if (slot >= ENTRIES || template >= count
                        || result.values.put(slot, templates.get(template)) != null) {
                        throw new IOException("Invalid or duplicate compact cube entry");
                    }
                }
                if (in.read() != -1) throw new IOException("Trailing compact cube data: " + path);
            }
        }
        remember(key, result);
        return result;
    }

    private Table batchTable(CubePos key, Map<CubePos, Table> tables) throws IOException {
        Table result = tables.get(key);
        if (result == null) tables.put(key, result = table(key));
        return result;
    }

    private void store(CubePos key, Table table) throws IOException {
        Path target = file(key);
        if (table.values.isEmpty()) {
            Files.deleteIfExists(target);
            remember(key, table);
            return;
        }
        Path temp = directory.resolve(target.getFileName() + "." + UUID.randomUUID() + ".tmp");
        // Normal CREATE_NEW permissions follow the umask, like ordinary region files.
        Files.createFile(temp);
        try {
            Map<ByteBuffer, Integer> templates = new LinkedHashMap<>();
            List<byte[]> unique = new ArrayList<>();
            for (Map.Entry<Integer, byte[]> entry : table.values.entrySet()) {
                byte[] value = entry.getValue();
                ByteBuffer identity = ByteBuffer.wrap(value)
                    .asReadOnlyBuffer();
                Integer index = templates.get(identity);
                if (index == null) {
                    index = templates.size();
                    templates.put(identity, index);
                    unique.add(value);
                }
                entry.setValue(unique.get(index));
            }
            try (FileChannel channel = FileChannel.open(temp, StandardOpenOption.WRITE);
                DataOutputStream out = new DataOutputStream(
                    new BufferedOutputStream(Channels.newOutputStream(channel)))) {
                out.writeLong(MAGIC);
                out.writeInt(templates.size());
                for (ByteBuffer value : templates.keySet()) {
                    out.writeInt(value.remaining());
                    byte[] bytes = new byte[value.remaining()];
                    value.duplicate()
                        .get(bytes);
                    out.write(bytes);
                }
                out.writeInt(table.values.size());
                for (Map.Entry<Integer, byte[]> entry : table.values.entrySet()) {
                    out.writeShort(entry.getKey());
                    out.writeShort(templates.get(ByteBuffer.wrap(entry.getValue())));
                }
                out.flush();
                channel.force(true);
            }
            // Never remove the previous committed table before its replacement is complete.
            Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            remember(key, table);
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    private static byte[] template(CubePos pos, NBTTagCompound root) throws IOException {
        if (!root.hasKey("Level", 10)) return null;
        NBTTagCompound level = root.getCompoundTag("Level");
        if (level.hasKey("Sections")) return null;
        if (!level.hasKey("x", 3) || !level.hasKey("y", 3)
            || !level.hasKey("z", 3)
            || level.getInteger("x") != pos.getX()
            || level.getInteger("y") != pos.getY()
            || level.getInteger("z") != pos.getZ()) return null;
        for (String key : new String[] { "Entities", "TileEntities", "TileTicks" }) {
            if (level.hasKey(key) && (!(level.getTag(key) instanceof NBTTagList list) || list.tagCount() != 0))
                return null;
        }
        NBTTagCompound normalized = (NBTTagCompound) root.copy();
        NBTTagCompound state = normalized.getCompoundTag("Level");
        state.removeTag("x");
        state.removeTag("y");
        state.removeTag("z");
        ByteBuffer raw = CCNBTUtils.saveTag(normalized, CCNBTUtils.TagCompression.NONE);
        if (raw.remaining() > MAX_TEMPLATE_BYTES) return null;
        ByteBuffer compressed = CCNBTUtils.saveTag(normalized, CubicChunksConfig.chunkCompression);
        if (compressed.remaining() > MAX_TEMPLATE_BYTES) return null;
        byte[] result = new byte[compressed.remaining()];
        compressed.get(result);
        return result;
    }

    private static NBTTagCompound restore(CubePos pos, byte[] value) throws IOException {
        NBTTagCompound root = decodeTemplate(value);
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

    private static NBTTagCompound decodeTemplate(byte[] value) throws IOException {
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

    @Override
    public synchronized boolean columnExists(ChunkCoordIntPair pos) throws IOException {
        checkOpen();
        return regular.columnExists(pos);
    }

    @Override
    public synchronized boolean cubeExists(CubePos pos) throws IOException {
        checkOpen();
        return table(region(pos)).values.containsKey(slot(pos)) || regular.cubeExists(pos);
    }

    @Override
    public synchronized NBTTagCompound readColumn(ChunkCoordIntPair pos) throws IOException {
        checkOpen();
        return regular.readColumn(pos);
    }

    @Override
    public synchronized NBTTagCompound readCube(CubePos pos) throws IOException {
        checkOpen();
        byte[] value = table(region(pos)).values.get(slot(pos));
        return value == null ? regular.readCube(pos) : restore(pos, value);
    }

    @Override
    public synchronized NBTBatch readBatch(PosBatch positions) throws IOException {
        checkOpen();
        Map<CubePos, NBTTagCompound> compact = new HashMap<>();
        Map<CubePos, Table> tables = new HashMap<>();
        List<CubePos> normal = new ArrayList<>();
        for (CubePos pos : positions.cubes) {
            byte[] value = batchTable(region(pos), tables).values.get(slot(pos));
            if (value == null) normal.add(pos);
            else compact.put(pos, restore(pos, value));
        }
        NBTBatch result = regular.readBatch(new PosBatch(positions.columns, normal));
        result.cubes.putAll(compact);
        return result;
    }

    @Override
    public synchronized void writeColumn(ChunkCoordIntPair pos, NBTTagCompound nbt) throws IOException {
        checkOpen();
        regular.writeColumn(pos, nbt);
    }

    @Override
    public void writeCube(CubePos pos, NBTTagCompound nbt) throws IOException {
        writeBatch(new NBTBatch(Collections.emptyMap(), Collections.singletonMap(pos, nbt)));
    }

    @Override
    public synchronized void writeBatch(NBTBatch batch) throws IOException {
        checkOpen();
        Map<CubePos, NBTTagCompound> normal = new HashMap<>();
        Map<CubePos, Table> changed = new LinkedHashMap<>();
        Map<CubePos, Table> tables = new HashMap<>();
        List<CubePos> compact = new ArrayList<>();
        for (Map.Entry<CubePos, NBTTagCompound> entry : batch.cubes.entrySet()) {
            CubePos pos = entry.getKey(), key = region(pos);
            byte[] value = template(pos, entry.getValue());
            Table old = batchTable(key, tables);
            if (value == null) normal.put(pos, entry.getValue());
            else compact.add(pos);
            byte[] previous = old.values.get(slot(pos));
            if (!Arrays.equals(value, previous)) {
                Table next = changed.computeIfAbsent(key, k -> new Table(old));
                if (value == null) {
                    next.values.remove(slot(pos));
                } else next.values.put(slot(pos), value);
            }
        }
        // Both ordinary writers finish data/header writes before returning; shadow paging also forces them.
        // Do not globally flush RegionLib's shared cache for each compact-to-full transition.
        regular.writeBatch(new NBTBatch(batch.columns, normal));
        for (Map.Entry<CubePos, Table> entry : changed.entrySet()) store(entry.getKey(), entry.getValue());
        // Compact tables now win reads, so retiring stale full records cannot lose the new state.
        regular.removeCubes(compact);
    }

    @Override
    public synchronized void forEachColumn(Consumer<ChunkCoordIntPair> callback) throws IOException {
        checkOpen();
        regular.forEachColumn(callback);
    }

    @Override
    public synchronized void forEachCube(Consumer<CubePos> callback) throws IOException {
        checkOpen();
        try {
            regular.forEachCube(pos -> {
                try {
                    // RegionLib can enumerate absent slots from its fallback region provider.
                    if (!table(region(pos)).values.containsKey(slot(pos)) && regular.cubeExists(pos))
                        callback.accept(pos);
                } catch (IOException e) {
                    throw new java.io.UncheckedIOException(e);
                }
            });
        } catch (java.io.UncheckedIOException e) {
            throw e.getCause();
        }
        try (var paths = Files.list(directory)) {
            for (Path path : paths.filter(
                p -> p.getFileName()
                    .toString()
                    .endsWith(".cce"))
                .collect(java.util.stream.Collectors.toList())) {
                String[] parts = path.getFileName()
                    .toString()
                    .split("\\.");
                if (parts.length != 4) throw new IOException("Invalid compact region name: " + path);
                CubePos key;
                try {
                    key = new CubePos(
                        Integer.parseInt(parts[0]),
                        Integer.parseInt(parts[1]),
                        Integer.parseInt(parts[2]));
                } catch (NumberFormatException e) {
                    throw new IOException("Invalid compact region name: " + path, e);
                }
                for (int coordinate : new int[] { key.getX(), key.getY(), key.getZ() }) {
                    if (coordinate < Integer.MIN_VALUE >> 4 || coordinate > Integer.MAX_VALUE >> 4) {
                        throw new IOException("Compact region coordinate outside cube range: " + path);
                    }
                }
                for (int slot : table(key).values.keySet()) callback.accept(position(key, slot));
            }
        } catch (java.io.UncheckedIOException e) {
            throw e.getCause();
        }
    }

    @Override
    public synchronized void flush() throws IOException {
        checkOpen();
        regular.flush();
    }

    @Override
    public synchronized void close() throws IOException {
        if (closed) return;
        regular.close();
        cache.clear();
        closed = true;
    }

    private static final class Table {

        final Map<Integer, byte[]> values = new TreeMap<>();
        private long memoryBytes = -1;

        Table() {}

        Table(Table source) {
            values.putAll(source.values);
        }

        long memoryEstimate() {
            if (memoryBytes >= 0) return memoryBytes;
            long result = 128L + values.size() * 64L;
            Map<byte[], Boolean> unique = new IdentityHashMap<>();
            for (byte[] value : values.values()) {
                if (unique.put(value, Boolean.TRUE) == null) result += value.length + 16L;
            }
            return memoryBytes = result;
        }
    }
}
