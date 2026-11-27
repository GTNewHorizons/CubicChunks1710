package com.cardinalstar.cubicchunks.world.convert;

import java.io.ByteArrayInputStream;
import java.io.Closeable;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.zip.GZIPInputStream;
import java.util.zip.InflaterInputStream;

import javax.annotation.Nullable;

import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;

/**
 * Reads one Anvil region file ({@code r.X.Z.mca}) without ever writing to it. Vanilla's {@code RegionFile} opens the
 * file for writing and pads it, which a converter must not do to its source. Safe to use from several threads.
 */
final class AnvilRegionReader implements Closeable {

    /** A chunk that vanilla would treat as missing: its slot is used but its data cannot be read. */
    static final class UnreadableChunkException extends IOException {

        private static final long serialVersionUID = 1L;

        UnreadableChunkException(String message, @Nullable Throwable cause) {
            super(message, cause);
        }
    }

    private static final int SECTOR = 4096;

    private final Path file;
    private final FileChannel channel;
    private final long size;
    private final int[] offsets = new int[1024];

    AnvilRegionReader(Path file) throws IOException {
        this.file = file;
        this.channel = FileChannel.open(file, StandardOpenOption.READ);
        try {
            this.size = channel.size();
            // A file shorter than its offset table holds no chunks, as for vanilla.
            if (size >= SECTOR) {
                ByteBuffer header = ByteBuffer.allocate(SECTOR);
                readFully(header, 0);
                header.flip();
                for (int i = 0; i < offsets.length; i++) offsets[i] = header.getInt();
            }
        } catch (IOException | RuntimeException e) {
            channel.close();
            throw e;
        }
    }

    /** Parses {@code r.X.Z.mca}; null for any other name. */
    @Nullable
    static int[] regionCoords(String name) {
        if (!name.startsWith("r.") || !name.endsWith(".mca")) return null;
        String[] parts = name.substring(2, name.length() - 4)
            .split("\\.", -1);
        if (parts.length != 2) return null;
        try {
            return new int[] { Integer.parseInt(parts[0]), Integer.parseInt(parts[1]) };
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** Whether the slot of the chunk at these region-local coordinates (0..31) is used. */
    boolean isPresent(int localX, int localZ) {
        return offsets[localX + localZ * 32] != 0;
    }

    int chunkCount() {
        int count = 0;
        for (int offset : offsets) if (offset != 0) count++;
        return count;
    }

    /**
     * The chunk's NBT, or null when its slot is unused.
     *
     * @throws UnreadableChunkException when the slot is used but its data is damaged
     * @throws IOException              when the file cannot be read
     */
    @Nullable
    NBTTagCompound read(int localX, int localZ) throws IOException {
        int offset = offsets[localX + localZ * 32];
        if (offset == 0) return null;

        long position = (long) (offset >>> 8) * SECTOR;
        int sectors = offset & 255;
        if (position < 2 * SECTOR || sectors == 0 || position + 5 > size) throw unreadable(localX, localZ, null);

        ByteBuffer head = ByteBuffer.allocate(5);
        readFully(head, position);
        head.flip();
        int length = head.getInt();
        int version = head.get();
        if (length < 1 || length > sectors * SECTOR || position + 4 + length > size)
            throw unreadable(localX, localZ, null);

        ByteBuffer data = ByteBuffer.allocate(length - 1);
        readFully(data, position + 5);
        try {
            InputStream raw = new ByteArrayInputStream(data.array());
            InputStream in;
            if (version == 1) in = new GZIPInputStream(raw);
            else if (version == 2) in = new InflaterInputStream(raw);
            else throw unreadable(localX, localZ, null);
            try (DataInputStream stream = new DataInputStream(in)) {
                return CompressedStreamTools.read(stream);
            }
        } catch (UnreadableChunkException e) {
            throw e;
        } catch (IOException | RuntimeException e) {
            // the bytes were read; what they hold is damaged
            throw unreadable(localX, localZ, e);
        }
    }

    private UnreadableChunkException unreadable(int localX, int localZ, @Nullable Throwable cause) {
        return new UnreadableChunkException("Unreadable chunk at slot " + localX + "," + localZ + " of " + file, cause);
    }

    private void readFully(ByteBuffer buffer, long position) throws IOException {
        while (buffer.hasRemaining()) {
            int read = channel.read(buffer, position + buffer.position());
            if (read < 0) throw new IOException("Unexpected end of " + file);
        }
    }

    @Override
    public void close() throws IOException {
        channel.close();
    }
}
