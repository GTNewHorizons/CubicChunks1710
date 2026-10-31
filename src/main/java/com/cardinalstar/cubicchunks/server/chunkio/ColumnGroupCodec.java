package com.cardinalstar.cubicchunks.server.chunkio;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.ChunkCoordIntPair;

import org.jetbrains.annotations.Nullable;

import com.cardinalstar.cubicchunks.CubicChunksConfig;

/**
 * One region record for the columns of an aligned 2x2 group of columns, the footprint of a stack of cube bundles
 * ({@link CubeBundleCodec}). The header entries of all member columns point at the same record.
 * <p>
 * Layout: {@code 'C' 'C' 'G' version}, codec byte, member mask, big-endian raw payload length, compressed payload. The
 * payload holds the member mask, then each member's NBT with its length. Decoding restores the exact original NBT.
 */
final class ColumnGroupCodec {

    static final int VERSION = 1;
    static final int HEADER_BYTES = 10;

    private ColumnGroupCodec() {}

    static ChunkCoordIntPair origin(ChunkCoordIntPair pos) {
        return new ChunkCoordIntPair(pos.chunkXPos & ~1, pos.chunkZPos & ~1);
    }

    static int slot(ChunkCoordIntPair pos) {
        return (pos.chunkXPos & 1) << 1 | pos.chunkZPos & 1;
    }

    static ChunkCoordIntPair member(ChunkCoordIntPair origin, int slot) {
        return new ChunkCoordIntPair(origin.chunkXPos + (slot >> 1), origin.chunkZPos + (slot & 1));
    }

    static boolean isGroup(ByteBuffer record) {
        int p = record.position();
        return record.remaining() >= HEADER_BYTES && record.get(p) == 'C'
            && record.get(p + 1) == 'C'
            && record.get(p + 2) == 'G';
    }

    /** Members present in a group record, from its uncompressed header. */
    static int memberMask(ByteBuffer record) {
        return record.get(record.position() + 5) & 15;
    }

    /** A decompressed group: the encoded NBT of each member, parsed on demand. */
    static final class Stored {

        final byte[][] members = new byte[4][];

        int mask() {
            int mask = 0;
            for (int s = 0; s < 4; s++) if (members[s] != null) mask |= 1 << s;
            return mask;
        }

        /** The member's NBT, or {@code null} if absent. The caller owns the result. */
        @Nullable
        NBTTagCompound member(int slot) throws IOException {
            return members[slot] == null ? null : CubeBundleCodec.parse(members[slot]);
        }
    }

    /** Encodes the given members (indexed by {@link #slot}, {@code null} = absent). Inputs are not modified. */
    static ByteBuffer encode(NBTTagCompound[] members) throws IOException {
        Stored stored = new Stored();
        for (int s = 0; s < 4; s++) if (members[s] != null) stored.members[s] = CubeBundleCodec.serialize(members[s]);
        return pack(stored);
    }

    /** Replaces members of a stored group; the other members keep their encoded bytes. */
    static ByteBuffer rewrite(Stored stored, NBTTagCompound[] replace, int keepMask) throws IOException {
        Stored out = new Stored();
        for (int s = 0; s < 4; s++) {
            if (replace[s] != null) out.members[s] = CubeBundleCodec.serialize(replace[s]);
            else if ((keepMask & 1 << s) != 0) out.members[s] = stored.members[s];
        }
        return pack(out);
    }

    /** Decompresses and validates a group record. */
    static Stored read(ByteBuffer record) throws IOException {
        if (!isGroup(record)) throw new IOException("Not a column group record");
        int p = record.position();
        if (record.get(p + 3) != VERSION)
            throw new IOException("Unsupported column group version " + record.get(p + 3));
        int codec = record.get(p + 4) & 255, headerMask = record.get(p + 5) & 255;
        int rawLength = record.getInt(p + 6);
        if (rawLength < 1 || rawLength > CubeBundleCodec.MAX_RAW_BYTES)
            throw new IOException("Invalid column group length " + rawLength);
        ByteBuffer payload = record.duplicate();
        payload.position(p + HEADER_BYTES);
        Stored stored = new Stored();
        try (DataInputStream in = CubeBundleCodec.stream(CubeBundleCodec.decompress(codec, payload, rawLength))) {
            int mask = in.readUnsignedByte();
            if (mask != headerMask || mask == 0 || mask > 15)
                throw new IOException("Column group member mask mismatch");
            for (int s = 0; s < 4; s++) {
                if ((mask & 1 << s) == 0) continue;
                int length = in.readInt();
                if (length < 1 || length > in.available()) throw new IOException("Invalid column group member length");
                stored.members[s] = new byte[length];
                in.readFully(stored.members[s]);
            }
            if (in.available() != 0) throw new IOException("Trailing data in column group");
        }
        return stored;
    }

    private static ByteBuffer pack(Stored stored) throws IOException {
        int mask = stored.mask();
        ByteArrayOutputStream raw = new ByteArrayOutputStream(8192);
        try (DataOutputStream out = new DataOutputStream(raw)) {
            out.writeByte(mask);
            for (byte[] member : stored.members) {
                if (member == null) continue;
                out.writeInt(member.length);
                out.write(member);
            }
        }
        byte[] payload = raw.toByteArray();
        if (payload.length > CubeBundleCodec.maxEncodedBytes)
            throw new CubeBundleCodec.OversizedBundleException(payload.length);
        int codec = CubeBundleCodec.codec(CubicChunksConfig.chunkCompression);
        byte[] compressed = CubeBundleCodec.compress(codec, payload);
        ByteBuffer record = ByteBuffer.allocate(HEADER_BYTES + compressed.length);
        record.put((byte) 'C')
            .put((byte) 'C')
            .put((byte) 'G')
            .put((byte) VERSION)
            .put((byte) codec)
            .put((byte) mask)
            .putInt(payload.length)
            .put(compressed);
        record.flip();
        return record;
    }
}
