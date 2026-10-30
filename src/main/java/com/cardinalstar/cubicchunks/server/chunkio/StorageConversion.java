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

/**
 * Converts a dimension's stored cubes and columns to bundle storage at once instead of as they are saved: every bundle
 * and column group that still has a member outside one shared bundle or group record is read and written again, which
 * also moves empty cubes into the compact tables. Left as they are: groups with a member in an oversized external file,
 * groups whose members together are too large for one record, and groups with a record that cannot be read.
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
        long cubes, columns, tooLarge, failed;
    }

    /**
     * One bundle or column group to convert; {@code large} when its stored records together come close to the size of
     * one record, so it is encoded in memory before it is written.
     */
    private static final class Group<P> {

        final List<P> members = new ArrayList<>();
        boolean large;
    }

    private StorageConversion() {}

    static Summary convert(Path dimension, Listener listener) throws IOException {
        Summary summary = new Summary();
        List<Path> regions = new ArrayList<>();
        regions.addAll(regionFiles(dimension.resolve("region2d"), COLUMN_REGION));
        regions.addAll(regionFiles(dimension.resolve("region3d"), CUBE_REGION));
        if (regions.isEmpty()) return summary;
        try (CompactCubeStorage storage = new CompactCubeStorage(dimension, true)) {
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
        }
        return summary;
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

    /** Whether the record at a packed header entry starts with {@code 'C' 'C' kind}. */
    private static boolean shared(FileChannel file, int packed, char kind) throws IOException {
        ByteBuffer start = ByteBuffer.allocate(3);
        long offset = (long) (packed >>> 8) * 512 + Integer.BYTES;
        while (start.hasRemaining()) if (file.read(start, offset + start.position()) < 0) return false;
        return start.get(0) == 'C' && start.get(1) == 'C' && start.get(2) == kind;
    }

    /**
     * The groups that still need converting, in slot order: a group is converted when all its members share one record
     * of the given kind. Groups with a member in an external file ({@code <region>.ext/<slot>}) are skipped.
     */
    private static <P> List<Group<P>> pending(Path region, int[] header, Function<Integer, Integer> groupOf,
        IntFunction<P> position, char kind) throws IOException {
        Set<Integer> external = new HashSet<>();
        Path ext = region.resolveSibling(region.getFileName() + ".ext");
        if (Files.isDirectory(ext)) {
            try (DirectoryStream<Path> files = Files.newDirectoryStream(ext)) {
                for (Path file : files) {
                    try {
                        external.add(
                            groupOf.apply(
                                Integer.parseInt(
                                    file.getFileName()
                                        .toString())));
                    } catch (NumberFormatException | IndexOutOfBoundsException ignored) {}
                }
            }
        }
        Map<Integer, List<Integer>> members = new TreeMap<>();
        for (int slot = 0; slot < header.length; slot++) {
            if (header[slot] != 0) members.computeIfAbsent(groupOf.apply(slot), k -> new ArrayList<>())
                .add(slot);
        }
        List<Group<P>> out = new ArrayList<>();
        try (FileChannel file = FileChannel.open(region)) {
            for (Map.Entry<Integer, List<Integer>> e : members.entrySet()) {
                if (external.contains(e.getKey())) continue;
                Set<Integer> records = new HashSet<>();
                for (int slot : e.getValue()) records.add(header[slot]);
                if (records.size() == 1 && shared(
                    file,
                    records.iterator()
                        .next(),
                    kind)) continue;
                Group<P> group = new Group<>();
                long bytes = 0;
                for (int packed : records) bytes += (packed & 255) * 512L;
                // half the limit leaves room for a shared encoding larger than the separate records (palette)
                group.large = bytes > RegionCubeStorage.MAX_SHARED_RECORD / 2;
                for (int slot : e.getValue()) group.members.add(position.apply(slot));
                out.add(group);
            }
        }
        return out;
    }

    private static void convertCubes(CompactCubeStorage storage, Path region, Matcher name, Summary summary)
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

    private static void convertColumns(CompactCubeStorage storage, Path region, Summary summary) throws IOException {
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
     * Reads the given groups and writes them again. A large group is first encoded in memory, and when it does not fit
     * one record only its empty cubes are written (to the compact tables), so it is not rewritten on every start. A
     * batch that cannot be read (a damaged record fails the whole read) or written is retried group by group, so one
     * damaged record only leaves its own group as it is. Returns how many cubes or columns were written.
     */
    @SuppressWarnings("unchecked")
    private static <P> long rewrite(CompactCubeStorage storage, List<Group<P>> groups, boolean columns, Path region,
        Summary summary) {
        try {
            List<P> positions = new ArrayList<>();
            for (Group<P> group : groups) positions.addAll(group.members);
            NBTBatch read = storage.readBatch(
                columns ? new PosBatch((List<ChunkCoordIntPair>) positions, Collections.emptyList())
                    : new PosBatch(Collections.emptyList(), (List<CubePos>) positions));
            Map<P, NBTTagCompound> tags = new HashMap<>((Map<P, NBTTagCompound>) (columns ? read.columns : read.cubes));
            long tooLarge = 0;
            for (Group<P> group : groups) {
                if (group.large && !fits(group, tags, columns)) {
                    for (P pos : group.members) {
                        // empty cubes still move to the compact tables; the others keep their own records
                        if (columns || CompactCubeStorage.template((CubePos) pos, tags.get(pos)) == null) {
                            tags.remove(pos);
                            tooLarge++;
                        }
                    }
                }
            }
            storage.writeBatch(
                columns ? new NBTBatch((Map<ChunkCoordIntPair, NBTTagCompound>) tags, new HashMap<>())
                    : new NBTBatch(new HashMap<>(), (Map<CubePos, NBTTagCompound>) tags));
            summary.tooLarge += tooLarge;
            return tags.size();
        } catch (IOException | RuntimeException e) {
            if (groups.size() == 1) {
                CubicChunks.LOGGER.error("Could not convert stored data in {}; leaving it as it is", region, e);
                summary.failed += groups.get(0).members.size();
                return 0;
            }
        }
        long written = 0;
        for (Group<P> group : groups)
            written += rewrite(storage, Collections.singletonList(group), columns, region, summary);
        return written;
    }

    /** Whether the group's members that would be written to it fit one shared record. */
    private static <P> boolean fits(Group<P> group, Map<P, NBTTagCompound> tags, boolean columns) throws IOException {
        try {
            ByteBuffer record;
            if (columns) {
                NBTTagCompound[] members = new NBTTagCompound[4];
                for (P pos : group.members) {
                    ChunkCoordIntPair column = (ChunkCoordIntPair) pos;
                    members[ColumnGroupCodec.slot(column)] = tags.get(pos);
                }
                record = ColumnGroupCodec.encode(members);
            } else {
                NBTTagCompound[] members = new NBTTagCompound[8];
                boolean any = false;
                for (P pos : group.members) {
                    CubePos cube = (CubePos) pos;
                    NBTTagCompound tag = tags.get(pos);
                    // empty cubes go to the compact tables, not into the bundle
                    if (tag == null || CompactCubeStorage.template(cube, tag) != null) continue;
                    members[CubeBundleCodec.slot(cube)] = tag;
                    any = true;
                }
                if (!any) return true;
                record = CubeBundleCodec.encode(members);
            }
            return record.remaining() <= RegionCubeStorage.MAX_SHARED_RECORD;
        } catch (CubeBundleCodec.OversizedBundleException e) {
            return false;
        }
    }
}
