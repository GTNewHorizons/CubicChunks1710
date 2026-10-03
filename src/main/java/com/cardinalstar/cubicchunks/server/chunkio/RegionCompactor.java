package com.cardinalstar.cubicchunks.server.chunkio;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.AclFileAttributeView;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.PosixFileAttributeView;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/** Packs Anvil3D region files at dimension load; also provides an optional offline maintenance command. */
public final class RegionCompactor {

    private static final int SECTOR_BYTES = 512;

    private RegionCompactor() {}

    public static void main(String[] args) throws IOException {
        run(args, System.out);
    }

    static void run(String[] args, PrintStream out) throws IOException {
        if (args.length == 0 || (args.length == 1 && args[0].equals("--help"))) {
            out.println(
                "Usage: java -cp cubicchunks.jar " + RegionCompactor.class.getName()
                    + " <world-or-dimension-directory> [--apply --world-stopped]");
            out.println("Default: dry run. Includes root storage and direct DIM<number> directories only.");
            out.println("Stop Minecraft first, including for dry runs. Older jars do not honor the maintenance lock.");
            out.println("Run as the same OS user on the same machine as Minecraft (same Java user.home).");
            return;
        }
        boolean apply = false, stopped = false;
        for (int i = 1; i < args.length; i++) {
            if (args[i].equals("--apply")) apply = true;
            else if (args[i].equals("--world-stopped")) stopped = true;
            else throw new IllegalArgumentException("Unknown option: " + args[i]);
        }
        if (apply && !stopped) {
            throw new IllegalArgumentException("Stop the world and pass --apply --world-stopped. Back it up first.");
        }
        Path root = Paths.get(args[0])
            .toAbsolutePath()
            .normalize();
        requireDirectory(root);
        List<Path> dimensions = new ArrayList<>();
        dimensions.add(root);
        try (DirectoryStream<Path> children = Files.newDirectoryStream(root)) {
            for (Path child : children) {
                if (child.getFileName()
                    .toString()
                    .matches("DIM-?\\d+")) {
                    requireDirectory(child);
                    dimensions.add(child);
                }
            }
        }
        Collections.sort(dimensions);
        List<StorageMaintenanceLock> locks = new ArrayList<>();
        try {
            for (Path dimension : dimensions) locks.add(StorageMaintenanceLock.openMaintenance(dimension));
            List<Path> files = new ArrayList<>();
            for (Path dimension : dimensions) {
                findRegions(dimension.resolve("region2d"), "-?\\d+\\.-?\\d+\\.2dr", files);
                findRegions(dimension.resolve("region3d"), "-?\\d+\\.-?\\d+\\.-?\\d+\\.3dr", files);
            }
            if (files.isEmpty()) throw new IOException("No .2dr/.3dr region files found under " + root);
            Collections.sort(files);
            // Preflight every selected region before replacing any; later I/O failures can still stop a partial run.
            for (Path file : files) {
                try (FileChannel input = openRead(file)) {
                    inspect(file, input);
                }
            }
            long before = 0, after = 0;
            int changed = 0;
            for (Path file : files) {
                Result result = compactLocked(file, apply);
                before += result.before;
                after += result.after;
                if (result.before > result.after) changed++;
                out.printf(
                    "%s: %d -> %d bytes (%d records)%n",
                    root.relativize(file),
                    result.before,
                    result.after,
                    result.records);
            }
            out.printf(
                "%s: %d regions, %d %s; %d -> %d bytes; %d bytes %s.%n",
                apply ? "Applied" : "Dry run",
                files.size(),
                changed,
                apply ? "replaced" : "would shrink",
                before,
                after,
                before - after,
                apply ? "reclaimed" : "reclaimable");
            out.println("Oversized .ext records and compact-empty .cce tables were not modified.");
        } finally {
            IOException failure = null;
            for (int i = locks.size() - 1; i >= 0; i--) {
                try {
                    locks.get(i)
                        .close();
                } catch (IOException e) {
                    if (failure == null) failure = e;
                    else failure.addSuppressed(e);
                }
            }
            if (failure != null) throw failure;
        }
    }

    // Only this dimension: other dimensions may already be open, or use a different storage backend.
    // Caller holds its maintenance lease until storage has opened or the operation has failed.
    static Summary compactDimensionLocked(Path dimension) throws IOException {
        List<Path> files = new ArrayList<>();
        for (String directory : new String[] { "region2d", "region3d" }) {
            Path path = dimension.resolve(directory);
            if (Files.isSymbolicLink(path)) return new Summary(0, 0, 0, 0, path);
        }
        findRegions(dimension.resolve("region2d"), "-?\\d+\\.-?\\d+\\.2dr", files);
        findRegions(dimension.resolve("region3d"), "-?\\d+\\.-?\\d+\\.-?\\d+\\.3dr", files);
        Collections.sort(files);
        for (Path file : files) {
            if (Files.isSymbolicLink(file)) return new Summary(0, 0, 0, 0, file);
        }
        List<Path> initialized = new ArrayList<>();
        for (Path file : files) {
            try (FileChannel input = openRead(file)) {
                // RegionLib initializes files shorter than a header as empty when it first opens them.
                int headerBytes = file.toString()
                    .endsWith(".3dr") ? 16384 : 4096;
                if (input.size() < headerBytes) continue;
                inspect(file, input);
                initialized.add(file);
            }
        }
        int changed = 0;
        long reclaimed = 0;
        for (Path file : initialized) {
            Result result = compactLocked(file, true);
            if (result.before > result.after) changed++;
            reclaimed += result.before - result.after;
        }
        return new Summary(files.size(), changed, reclaimed, files.size() - initialized.size(), null);
    }

    static final class Summary {

        final int regions, changed, uninitialized;
        final long reclaimed;
        final Path skippedLink;

        Summary(int regions, int changed, long reclaimed, int uninitialized, Path skippedLink) {
            this.regions = regions;
            this.changed = changed;
            this.reclaimed = reclaimed;
            this.uninitialized = uninitialized;
            this.skippedLink = skippedLink;
        }
    }

    private static void requireDirectory(Path path) throws IOException {
        if (!Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Expected a real directory, not a symlink: " + path);
        }
    }

    private static void findRegions(Path directory, String pattern, List<Path> files) throws IOException {
        if (!Files.exists(directory, LinkOption.NOFOLLOW_LINKS)) return;
        requireDirectory(directory);
        try (DirectoryStream<Path> children = Files.newDirectoryStream(directory)) {
            for (Path child : children) {
                if (child.getFileName()
                    .toString()
                    .matches(pattern)) files.add(child);
            }
        }
    }

    private static FileChannel openRead(Path file) throws IOException {
        if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Expected a regular region file, not a symlink: " + file);
        }
        return FileChannel.open(file, StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS);
    }

    // Caller holds the maintenance lease for this dimension for the whole operation.
    static Result compactLocked(Path file, boolean apply) throws IOException {
        Path temporary = null;
        try {
            BasicFileAttributes original = attributes(file);
            Layout layout;
            try (FileChannel input = openRead(file)) {
                layout = inspect(file, input);
                if (!apply || layout.size <= layout.packedSize) return layout.result();
                temporary = Files.createTempFile(file.getParent(), ".cc-compact-", ".tmp");
                try (FileChannel output = FileChannel
                    .open(temporary, StandardOpenOption.READ, StandardOpenOption.WRITE)) {
                    ByteBuffer header = ByteBuffer.allocate(layout.header.length);
                    int nextSector = layout.header.length / SECTOR_BYTES;
                    for (Entry entry : layout.entries) {
                        header.putInt(entry.slot * 4, (nextSector << 8) | entry.packedSectors());
                        ByteBuffer bytes = read(input, entry.offset, entry.length + 4);
                        write(output, bytes, (long) nextSector * SECTOR_BYTES);
                        nextSector += entry.packedSectors();
                    }
                    write(output, header, 0);
                    // FileChannel.truncate cannot extend a file; explicitly materialize its last padding byte.
                    if (output.size() < layout.packedSize) {
                        write(output, ByteBuffer.allocate(1), layout.packedSize - 1);
                    }
                    Layout packed = inspect(file, output);
                    verifyCopy(input, layout, output, packed);
                    output.force(true);
                }
                assertUnchanged(file, original);
            }
            copyPermissions(file, temporary);
            assertUnchanged(file, original);
            // Both handles are closed for Windows. No unsafe delete/rename fallback if atomic replacement is
            // unavailable.
            Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            return layout.result();
        } finally {
            if (temporary != null) Files.deleteIfExists(temporary);
        }
    }

    private static void copyPermissions(Path from, Path to) throws IOException {
        PosixFileAttributeView source = Files
            .getFileAttributeView(from, PosixFileAttributeView.class, LinkOption.NOFOLLOW_LINKS);
        if (source != null) {
            var attrs = source.readAttributes();
            PosixFileAttributeView target = Files.getFileAttributeView(to, PosixFileAttributeView.class);
            target.setOwner(attrs.owner());
            target.setGroup(attrs.group());
            target.setPermissions(attrs.permissions());
        } else {
            AclFileAttributeView acl = Files
                .getFileAttributeView(from, AclFileAttributeView.class, LinkOption.NOFOLLOW_LINKS);
            if (acl != null) {
                AclFileAttributeView target = Files.getFileAttributeView(to, AclFileAttributeView.class);
                target.setOwner(acl.getOwner());
                target.setAcl(acl.getAcl());
            }
        }
    }

    private static BasicFileAttributes attributes(Path file) throws IOException {
        return Files.readAttributes(file, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
    }

    private static void assertUnchanged(Path file, BasicFileAttributes original) throws IOException {
        BasicFileAttributes now = attributes(file);
        if (now.size() != original.size() || !now.lastModifiedTime()
            .equals(original.lastModifiedTime()) || !Objects.equals(now.fileKey(), original.fileKey())) {
            throw new IOException("Region changed during compaction; refusing replacement: " + file);
        }
    }

    private static Layout inspect(Path file, FileChannel input) throws IOException {
        String name = file.getFileName()
            .toString();
        int count;
        if (name.endsWith(".3dr")) count = 4096;
        else if (name.endsWith(".2dr")) count = 1024;
        else throw new IOException("Not a standard CC region: " + file);
        long size = input.size();
        if (size < count * 4) throw new IOException("Truncated region header: " + file);
        ByteBuffer header = read(input, 0, count * 4);
        BitSet occupied = new BitSet();
        occupied.set(0, count * 4 / SECTOR_BYTES);
        List<Entry> entries = new ArrayList<>();
        long packedSize = count * 4;
        for (int slot = 0; slot < count; slot++) {
            int packed = header.getInt(slot * 4);
            if (packed == 0) continue;
            int sector = packed >>> 8, sectors = packed & 255;
            long offset = (long) sector * SECTOR_BYTES;
            int overlap = occupied.nextSetBit(sector);
            if (sectors == 0 || sector < count * 4 / SECTOR_BYTES
                || offset + (long) sectors * SECTOR_BYTES > ((size + SECTOR_BYTES - 1) / SECTOR_BYTES) * SECTOR_BYTES
                || (overlap >= 0 && overlap < sector + sectors)) {
                throw new IOException("Invalid/overlapping sector allocation at slot " + slot + ": " + file);
            }
            int length = read(input, offset, 4).getInt();
            if (length < 1 || length > sectors * SECTOR_BYTES - 4 || offset + 4 + length > size) {
                throw new IOException("Invalid record length at slot " + slot + ": " + file);
            }
            occupied.set(sector, sector + sectors);
            Entry entry = new Entry(slot, offset, length);
            entries.add(entry);
            packedSize += (long) entry.packedSectors() * SECTOR_BYTES;
        }
        return new Layout(header.array(), entries, size, packedSize);
    }

    private static void verifyCopy(FileChannel source, Layout before, FileChannel destination, Layout after)
        throws IOException {
        if (after.size != before.packedSize || before.entries.size() != after.entries.size()
            || !Arrays.equals(before.header, read(source, 0, before.header.length).array())) {
            throw new IOException("Region/header changed or compacted layout mismatch");
        }
        for (int i = 0; i < before.entries.size(); i++) {
            Entry a = before.entries.get(i), b = after.entries.get(i);
            if (a.slot != b.slot || a.length != b.length
                || !read(source, a.offset, a.length + 4).equals(read(destination, b.offset, b.length + 4))) {
                throw new IOException("Compacted record verification failed at slot " + a.slot);
            }
        }
    }

    private static ByteBuffer read(FileChannel channel, long offset, int length) throws IOException {
        ByteBuffer result = ByteBuffer.allocate(length);
        while (result.hasRemaining()) {
            int n = channel.read(result, offset + result.position());
            if (n <= 0) throw new IOException("Short region read at " + offset);
        }
        result.flip();
        return result;
    }

    private static void write(FileChannel channel, ByteBuffer bytes, long offset) throws IOException {
        while (bytes.hasRemaining()) {
            int n = channel.write(bytes, offset + bytes.position());
            if (n <= 0) throw new IOException("Short region write at " + offset);
        }
    }

    static final class Result {

        final long before, after;
        final int records;

        Result(long before, long after, int records) {
            this.before = before;
            this.after = after;
            this.records = records;
        }
    }

    private static final class Layout {

        final byte[] header;
        final List<Entry> entries;
        final long size, packedSize;

        Layout(byte[] header, List<Entry> entries, long size, long packedSize) {
            this.header = header;
            this.entries = entries;
            this.size = size;
            this.packedSize = packedSize;
        }

        Result result() {
            return new Result(size, Math.min(size, packedSize), entries.size());
        }
    }

    private static final class Entry {

        final int slot, length;
        final long offset;

        Entry(int slot, long offset, int length) {
            this.slot = slot;
            this.offset = offset;
            this.length = length;
        }

        int packedSectors() {
            return (length + 4 + SECTOR_BYTES - 1) / SECTOR_BYTES;
        }
    }
}
