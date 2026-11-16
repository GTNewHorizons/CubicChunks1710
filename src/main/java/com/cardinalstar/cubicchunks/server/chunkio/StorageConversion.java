package com.cardinalstar.cubicchunks.server.chunkio;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.function.IntFunction;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.ChunkCoordIntPair;

import com.cardinalstar.cubicchunks.CubicChunks;
import com.cardinalstar.cubicchunks.api.world.storage.ICubicStorage.NBTBatch;
import com.cardinalstar.cubicchunks.api.world.storage.ICubicStorage.PosBatch;
import com.cardinalstar.cubicchunks.util.CubePos;

import cubicchunks.regionlib.impl.EntryLocation2D;
import cubicchunks.regionlib.impl.EntryLocation3D;
import cubicchunks.regionlib.util.CorruptedDataException;

/**
 * Part of the conversion of old worlds ({@link LegacyWorldConversion}): converts a dimension's stored cubes and columns
 * to bundle storage at once: every bundle and column group that still has a member outside one shared bundle or group
 * record, or in a bundle record of an older version, is read and written again, which also stores empty cubes as states
 * in their columns. A group too large for one record gets one current record per member. A record that is damaged
 * stays as it is and the rest of its group is converted; a failed write or an I/O error ends the conversion, so that
 * it runs again on the next start.
 */
final class StorageConversion {

    /** Bundles or column groups read and written together, to bound memory. */
    private static final int GROUPS_PER_BATCH = 64;
    /** Region file names as RegionLib and the compactor accept them. */
    private static final Pattern CUBE_REGION = Pattern.compile("(-?\\d+)\\.(-?\\d+)\\.(-?\\d+)\\.3dr"),
        COLUMN_REGION = Pattern.compile("(-?\\d+)\\.(-?\\d+)\\.2dr");

    @FunctionalInterface
    interface Listener {

        void progress(int completed, int total);
    }

    static final class Summary {

        int regions;
        long cubes, columns, failed;
    }

    /** One bundle or column group to convert. */
    private static final class Group<P> {

        final List<P> members = new ArrayList<>();
    }

    private StorageConversion() {}

    static Summary convert(Path dimension, Listener listener) throws IOException {
        if (regionFiles(dimension).isEmpty()) return new Summary();
        try (RegionCubeStorage storage = new RegionCubeStorage(dimension)) {
            return convert(storage, dimension, listener);
        }
    }

    /** Converts through {@code storage}, which is open on {@code dimension}. */
    static Summary convert(RegionCubeStorage storage, Path dimension, Listener listener) throws IOException {
        Summary summary = new Summary();
        List<Path> regions = regionFiles(dimension);
        for (Path region : regions) {
            listener.progress(summary.regions, regions.size());
            Matcher cube = CUBE_REGION.matcher(
                region.getFileName()
                    .toString());
            if (cube.matches()) convertCubes(storage, region, cube, summary);
            else convertColumns(storage, region, summary);
            summary.regions++;
        }
        listener.progress(summary.regions, regions.size());
        return summary;
    }

    private static List<Path> regionFiles(Path dimension) throws IOException {
        List<Path> regions = new ArrayList<>();
        regions.addAll(regionFiles(dimension.resolve("region2d"), COLUMN_REGION));
        regions.addAll(regionFiles(dimension.resolve("region3d"), CUBE_REGION));
        return regions;
    }

    private static List<Path> regionFiles(Path directory, Pattern names) throws IOException {
        List<Path> out = new ArrayList<>();
        if (!Files.isDirectory(directory)) return out;
        try (DirectoryStream<Path> files = Files.newDirectoryStream(directory)) {
            for (Path file : files) {
                Matcher name = names.matcher(
                    file.getFileName()
                        .toString());
                if (!name.matches() || !Files.isRegularFile(file)) continue;
                try {
                    for (int i = 1; i <= name.groupCount(); i++) Integer.parseInt(name.group(i));
                } catch (NumberFormatException e) {
                    CubicChunks.LOGGER.warn("Skipping region file with an invalid position: {}", file);
                    continue;
                }
                out.add(file);
            }
        }
        Collections.sort(out);
        return out;
    }

    /** Header entries of a region file, or {@code null} for a file too short to hold a header. */
    private static int[] header(Path region, int entries) throws IOException {
        try (FileChannel file = FileChannel.open(region)) {
            if (file.size() < entries * 4L) return null;
            ByteBuffer header = ByteBuffer.allocate(entries * 4);
            while (header.hasRemaining()) if (file.read(header, header.position()) < 0) return null;
            int[] out = new int[entries];
            header.flip();
            header.asIntBuffer()
                .get(out);
            return out;
        }
    }

    /** Whether the record at a packed header entry is a current record of the kind {@code 'C' 'C' kind}. */
    private static boolean shared(FileChannel file, int packed, char kind) throws IOException {
        ByteBuffer start = ByteBuffer.allocate(4);
        long offset = (long) (packed >>> 8) * 512 + Integer.BYTES;
        while (start.hasRemaining()) if (file.read(start, offset + start.position()) < 0) return false;
        return start.get(0) == 'C' && start.get(1) == 'C'
            && start.get(2) == kind
            && (kind != 'B' || start.get(3) == CubeBundleCodec.VERSION);
    }

    /**
     * The groups that still need converting, in slot order: a group is converted unless all its members share one
     * current record of the given kind. A member in an external file ({@code <region>.ext/<slot>}) has no header entry
     * here, so it is not part of the batch; the write handles it with the rest of its group.
     */
    private static <P> List<Group<P>> pending(Path region, int[] header, Function<Integer, Integer> groupOf,
        IntFunction<P> position, char kind) throws IOException {
        Map<Integer, List<Integer>> members = new TreeMap<>();
        for (int slot = 0; slot < header.length; slot++) {
            if (header[slot] != 0) members.computeIfAbsent(groupOf.apply(slot), k -> new ArrayList<>())
                .add(slot);
        }
        List<Group<P>> out = new ArrayList<>();
        try (FileChannel file = FileChannel.open(region)) {
            for (Map.Entry<Integer, List<Integer>> e : members.entrySet()) {
                Set<Integer> records = new HashSet<>();
                for (int slot : e.getValue()) records.add(header[slot]);
                if (records.size() == 1 && shared(
                    file,
                    records.iterator()
                        .next(),
                    kind)) continue;
                Group<P> group = new Group<>();
                for (int slot : e.getValue()) group.members.add(position.apply(slot));
                out.add(group);
            }
        }
        return out;
    }

    private static void convertCubes(RegionCubeStorage storage, Path region, Matcher name, Summary summary)
        throws IOException {
        int rx = Integer.parseInt(name.group(1)), ry = Integer.parseInt(name.group(2)),
            rz = Integer.parseInt(name.group(3));
        int[] header = header(region, 4096);
        if (header == null) return;
        List<Group<CubePos>> pending = pending(
            region,
            header,
            slot -> (slot >> 9) << 6 | (slot >> 5 & 7) << 3 | (slot & 15) >> 1,
            slot -> new CubePos(rx << 4 | slot >> 8, ry << 4 | (slot >> 4 & 15), rz << 4 | (slot & 15)),
            'B');
        for (int i = 0; i < pending.size(); i += GROUPS_PER_BATCH) {
            summary.cubes += rewrite(
                storage,
                pending.subList(i, Math.min(pending.size(), i + GROUPS_PER_BATCH)),
                false,
                region,
                summary);
        }
    }

    private static void convertColumns(RegionCubeStorage storage, Path region, Summary summary) throws IOException {
        Matcher name = COLUMN_REGION.matcher(
            region.getFileName()
                .toString());
        if (!name.matches()) return;
        int rx = Integer.parseInt(name.group(1)), rz = Integer.parseInt(name.group(2));
        int[] header = header(region, 1024);
        if (header == null) return;
        List<Group<ChunkCoordIntPair>> pending = pending(
            region,
            header,
            slot -> (slot >> 6) << 4 | (slot & 31) >> 1,
            slot -> new ChunkCoordIntPair(rx << 5 | slot >> 5, rz << 5 | (slot & 31)),
            'G');
        for (int i = 0; i < pending.size(); i += GROUPS_PER_BATCH) {
            summary.columns += rewrite(
                storage,
                pending.subList(i, Math.min(pending.size(), i + GROUPS_PER_BATCH)),
                true,
                region,
                summary);
        }
    }

    /**
     * Reads the given groups and writes them again. A batch that cannot be read is retried group by group, and a single
     * group member by member ({@link #salvage}). A failed write is not tolerated: it throws, the world keeps its old
     * format marker and the conversion runs again on the next start. Returns how many cubes or columns were written.
     */
    @SuppressWarnings("unchecked")
    private static <P> long rewrite(RegionCubeStorage storage, List<Group<P>> groups, boolean columns, Path region,
        Summary summary) throws IOException {
        List<P> positions = new ArrayList<>();
        for (Group<P> group : groups) positions.addAll(group.members);
        NBTBatch read;
        try {
            read = storage.readBatch(
                columns ? new PosBatch((List<ChunkCoordIntPair>) positions, Collections.emptyList())
                    : new PosBatch(Collections.emptyList(), (List<CubePos>) positions));
        } catch (IOException | RuntimeException e) {
            if (groups.size() == 1) return salvage(storage, groups.get(0), columns, region, summary);
            long written = 0;
            for (Group<P> group : groups)
                written += rewrite(storage, Collections.singletonList(group), columns, region, summary);
            return written;
        }
        Map<P, NBTTagCompound> tags = new HashMap<>((Map<P, NBTTagCompound>) (columns ? read.columns : read.cubes));
        write(storage, tags, columns);
        return tags.size();
    }

    @SuppressWarnings("unchecked")
    private static <P> void write(RegionCubeStorage storage, Map<P, NBTTagCompound> tags, boolean columns)
        throws IOException {
        storage.writeBatch(
            columns ? new NBTBatch((Map<ChunkCoordIntPair, NBTTagCompound>) tags, new HashMap<>())
                : new NBTBatch(new HashMap<>(), (Map<CubePos, NBTTagCompound>) tags));
    }

    /**
     * A group whose read failed, member by member: an I/O error ends the conversion, a damaged member stays as it is
     * and counts as failed, and the readable members are written, so that none of them stays in an old record.
     */
    private static <P> long salvage(RegionCubeStorage storage, Group<P> group, boolean columns, Path region,
        Summary summary) throws IOException {
        Map<P, NBTTagCompound> readable = new HashMap<>();
        for (P pos : group.members) {
            failOnIOError(storage, pos);
            try {
                NBTTagCompound tag = columns ? storage.readColumn((ChunkCoordIntPair) pos)
                    : storage.readCube((CubePos) pos);
                if (tag != null) readable.put(pos, tag);
            } catch (IOException | RuntimeException e) {
                CubicChunks.LOGGER.error("Could not read stored {} in {}; leaving it as it is", pos, region, e);
                summary.failed++;
            }
        }
        write(storage, readable, columns);
        return readable.size();
    }

    /**
     * Throws an I/O error reading this record's bytes; a damaged record, which RegionLib reports differently, passes.
     */
    static void failOnIOError(RegionCubeStorage storage, Object pos) throws IOException {
        IOException error;
        try {
            if (pos instanceof CubePos cube) {
                EntryLocation3D key = new EntryLocation3D(cube.getX(), cube.getY(), cube.getZ());
                error = storage.save.load3D(Collections.singletonList(key), false).errored.get(key);
            } else {
                ChunkCoordIntPair column = (ChunkCoordIntPair) pos;
                EntryLocation2D key = new EntryLocation2D(column.chunkXPos, column.chunkZPos);
                error = storage.save.load2D(Collections.singletonList(key), false).errored.get(key);
            }
        } catch (IOException e) {
            error = e;
        } catch (RuntimeException e) {
            // a damaged record can fail the read without naming its key
            return;
        }
        if (error != null && !isCorrupted(error)) throw error;
    }

    /** Whether RegionLib reported corruption, directly or wrapped: as a cause or as a suppressed exception. */
    private static boolean isCorrupted(Throwable error) {
        if (error instanceof CorruptedDataException) return true;
        if (error.getCause() != null && isCorrupted(error.getCause())) return true;
        for (Throwable suppressed : error.getSuppressed()) if (isCorrupted(suppressed)) return true;
        return false;
    }
}
