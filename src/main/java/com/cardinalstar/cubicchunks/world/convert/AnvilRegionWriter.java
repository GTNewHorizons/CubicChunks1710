package com.cardinalstar.cubicchunks.world.convert;

import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.zip.DeflaterOutputStream;

import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;

/** Writes one new Anvil region file ({@code r.X.Z.mca}), each chunk once, without gaps. */
final class AnvilRegionWriter implements Closeable {

    private static final int SECTOR = 4096;
    /** A chunk's sector count is one byte of its table entry. */
    private static final int MAX_SECTORS = 255;

    private final Path file;
    private final FileChannel channel;
    private final ByteBuffer header = ByteBuffer.allocate(2 * SECTOR);
    private int nextSector = 2;

    AnvilRegionWriter(Path file) throws IOException {
        this.file = file;
        Files.createDirectories(file.getParent());
        this.channel = FileChannel.open(file, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
    }

    static Path file(Path regionFolder, int chunkX, int chunkZ) {
        return regionFolder.resolve("r." + (chunkX >> 5) + "." + (chunkZ >> 5) + ".mca");
    }

    /** A chunk as a region file stores it: length, compression type 2 (zlib), data. Can run on any thread. */
    static byte[] encode(NBTTagCompound chunk) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream(8192);
        bytes.write(new byte[5], 0, 5);
        try (DataOutputStream out = new DataOutputStream(new DeflaterOutputStream(bytes))) {
            CompressedStreamTools.write(chunk, out);
        }
        byte[] record = bytes.toByteArray();
        ByteBuffer.wrap(record)
            .putInt(record.length - 4)
            .put((byte) 2);
        return record;
    }

    void write(int chunkX, int chunkZ, byte[] record) throws IOException {
        int sectors = (record.length + SECTOR - 1) / SECTOR;
        if (sectors > MAX_SECTORS) {
            throw new IOException(
                "Chunk " + chunkX
                    + ","
                    + chunkZ
                    + " takes "
                    + record.length
                    + " bytes compressed; an Anvil region file cannot hold more than "
                    + MAX_SECTORS * SECTOR);
        }
        int slot = (chunkX & 31) + (chunkZ & 31) * 32;
        if (header.getInt(slot * 4) != 0) throw new IOException("Chunk " + chunkX + "," + chunkZ + " written twice");

        ByteBuffer padded = ByteBuffer.allocate(sectors * SECTOR);
        padded.put(record);
        padded.rewind();
        long position = (long) nextSector * SECTOR;
        while (padded.hasRemaining()) channel.write(padded, position + padded.position());

        header.putInt(slot * 4, nextSector << 8 | sectors);
        header.putInt(SECTOR + slot * 4, (int) (System.currentTimeMillis() / 1000L));
        nextSector += sectors;
    }

    @Override
    public void close() throws IOException {
        try {
            header.rewind();
            while (header.hasRemaining()) channel.write(header, header.position());
            channel.force(false);
        } finally {
            channel.close();
        }
    }

    @Override
    public String toString() {
        return file.toString();
    }
}
