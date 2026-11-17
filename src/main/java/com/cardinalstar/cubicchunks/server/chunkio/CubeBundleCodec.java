package com.cardinalstar.cubicchunks.server.chunkio;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.function.IntFunction;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import net.jpountz.lz4.LZ4Compressor;
import net.jpountz.lz4.LZ4Exception;
import net.jpountz.lz4.LZ4Factory;
import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTSizeTracker;
import net.minecraft.nbt.NBTTagByteArray;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

import org.jetbrains.annotations.Nullable;

import com.cardinalstar.cubicchunks.CubicChunksConfig;
import com.cardinalstar.cubicchunks.util.CubePos;

import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;

/**
 * One region record for the cubes of an aligned 2x2x2 block of cubes (a bundle). The header entries of all member
 * cubes point at the same record ({@link com.cardinalstar.cubicchunks.server.chunkio.region.ShadowPagingRegion}).
 * <p>
 * Layout: {@code 'C' 'C' 'B' version}, codec byte, member mask, the raw payload length as a big-endian int, then the
 * compressed payload as one frame. The payload holds the member mask, the mask of members using the palette, a block
 * state palette shared by all members, then each member's NBT. A member whose single section has plain block arrays
 * stores palette indices ({@value #INDICES}) and a mask of the arrays it had ({@value #ARRAYS_PRESENT}) instead of the
 * arrays; byte indices up to 256 states, else 16-bit. Other sections are kept unchanged. Decoding restores the exact
 * original NBT.
 */
final class CubeBundleCodec {

    static final int VERSION = 3;
    static final int HEADER_BYTES = 10;
    static final int MAX_RAW_BYTES = 64 * 1024 * 1024;
    /** Largest payload {@link #encode} writes; {@link #MAX_RAW_BYTES} except in tests. */
    static int maxEncodedBytes = MAX_RAW_BYTES;

    /** The members together exceed the payload limit; they can still be stored one record each. */
    static final class OversizedBundleException extends IOException {

        OversizedBundleException(long bytes) {
            super("Oversized cube bundle: " + bytes + " bytes");
        }
    }

    /** Block arrays of a cube section (vanilla and EndlessIDs), in the order of the arrays-present mask bits. */
    static final String[] ARRAYS = { "Blocks", "Add", "BlocksB2Hi", "Data", "Data1High", "Data2", "BlocksB3" };
    static final int[] LENGTHS = { 4096, 2048, 2048, 2048, 2048, 4096, 4096 };
    private static final int B3 = 6;
    static final String ARRAYS_PRESENT = "ccb:arrays", INDICES = "ccb:indices";
    /**
     * A restored client section's block counts ({@code removeInvalidBlocks}): blocks that are not air and those that
     * tick randomly, counted from the palette while restoring.
     */
    static final String BLOCK_REFS = "ccb:blockRefs", TICK_REFS = "ccb:tickRefs";

    private static final int CODEC_NONE = 0, CODEC_GZIP = 1, CODEC_LZ4 = 2, CODEC_ZSTD = 3;

    private CubeBundleCodec() {}

    static CubePos origin(CubePos pos) {
        return new CubePos(pos.getX() & ~1, pos.getY() & ~1, pos.getZ() & ~1);
    }

    static int slot(CubePos pos) {
        return (pos.getX() & 1) << 2 | (pos.getY() & 1) << 1 | pos.getZ() & 1;
    }

    static CubePos member(CubePos origin, int slot) {
        return new CubePos(origin.getX() + (slot >> 2), origin.getY() + (slot >> 1 & 1), origin.getZ() + (slot & 1));
    }

    static boolean isBundle(ByteBuffer record) {
        int p = record.position();
        return record.remaining() >= HEADER_BYTES && record.get(p) == 'C'
            && record.get(p + 1) == 'C'
            && record.get(p + 2) == 'B';
    }

    /** Members present in a bundle record, from its uncompressed header. */
    static int memberMask(ByteBuffer record) {
        return record.get(record.position() + 5) & 255;
    }

    /** A decompressed bundle: its palette and the encoded NBT of each member, which is only parsed on demand. */
    static final class Stored {

        int[] states = new int[0];
        int count;
        final byte[][] members = new byte[8][];
        /** Members whose section stores palette indices instead of block arrays. */
        int palettized;

        int mask() {
            int mask = 0;
            for (int s = 0; s < 8; s++) if (members[s] != null) mask |= 1 << s;
            return mask;
        }

        int bits() {
            return count <= 256 ? 8 : 16;
        }

        /** The member's original NBT, or {@code null} if absent. The caller owns the result. */
        @Nullable
        NBTTagCompound member(int slot) throws IOException {
            if (members[slot] == null) return null;
            NBTTagCompound root = parse(members[slot]);
            if ((palettized & 1 << slot) != 0) restore(section(root), this);
            return root;
        }

        /** As {@link #member}, for a client: a palettized section also gets its block counts. */
        @Nullable
        NBTTagCompound clientMember(int slot) throws IOException {
            if (members[slot] == null) return null;
            NBTTagCompound root = parse(members[slot]);
            if ((palettized & 1 << slot) != 0) {
                int[] uses = restore(section(root), this);
                setBlockCounts(section(root), this, uses, Block::getBlockById, Blocks.air);
            }
            return root;
        }
    }

    /** Encodes the given members (indexed by {@link #slot}, {@code null} = absent). Inputs are not modified. */
    static ByteBuffer encode(NBTTagCompound[] members) throws IOException {
        Palette palette = new Palette();
        Stored stored = new Stored();
        int[][] keys = new int[8][];
        int[] arrays = new int[8];
        for (int s = 0; s < 8; s++) {
            if (members[s] == null) continue;
            arrays[s] = palettizable(members[s]);
            if (arrays[s] >= 0) keys[s] = palette.add(keys(section(members[s]), arrays[s]));
        }
        stored.states = palette.states;
        stored.count = palette.count;
        for (int s = 0; s < 8; s++) {
            if (members[s] == null) continue;
            stored.members[s] = write(members[s], keys[s], arrays[s], stored.bits());
            if (keys[s] != null) stored.palettized |= 1 << s;
        }
        return pack(stored);
    }

    /**
     * Replaces and removes members of a stored bundle. Untouched members keep their encoded bytes while new block
     * states still fit the index width; otherwise the whole bundle is encoded again, which also drops unused states.
     * Returns {@code null} when no member is left.
     */
    @Nullable
    static ByteBuffer rewrite(Stored stored, NBTTagCompound[] replace, int removeMask) throws IOException {
        int mask = (stored.mask() & ~removeMask);
        for (int s = 0; s < 8; s++) if (replace[s] != null) mask |= 1 << s;
        if (mask == 0) return null;

        Palette palette = new Palette(stored.states, stored.count);
        int bits = stored.bits();
        Stored out = new Stored();
        int[][] keys = new int[8][];
        int[] arrays = new int[8];
        for (int s = 0; s < 8; s++) {
            if (replace[s] == null) continue;
            arrays[s] = palettizable(replace[s]);
            if (arrays[s] >= 0) keys[s] = palette.add(keys(section(replace[s]), arrays[s]));
        }
        boolean widthChanges = bits == 8 && palette.count > 256;
        if (widthChanges || palette.count > 4096) {
            // encode everything again: indices of untouched members would no longer fit, or the palette is bloated
            NBTTagCompound[] all = new NBTTagCompound[8];
            for (int s = 0; s < 8; s++) {
                if ((mask & 1 << s) == 0) continue;
                all[s] = replace[s] != null ? replace[s] : stored.member(s);
            }
            return encode(all);
        }
        out.states = palette.states;
        out.count = palette.count;
        for (int s = 0; s < 8; s++) {
            if ((mask & 1 << s) == 0) continue;
            if (replace[s] != null) {
                out.members[s] = write(replace[s], keys[s], arrays[s], bits);
                if (keys[s] != null) out.palettized |= 1 << s;
            } else {
                out.members[s] = stored.members[s];
                out.palettized |= stored.palettized & 1 << s;
            }
        }
        return pack(out);
    }

    /** Decompresses and validates a bundle record. */
    static Stored read(ByteBuffer record) throws IOException {
        if (!isBundle(record)) throw new IOException("Not a cube bundle record");
        int p = record.position();
        if (record.get(p + 3) != VERSION) throw new IOException("Unsupported cube bundle version " + record.get(p + 3));
        int codec = record.get(p + 4) & 255, headerMask = record.get(p + 5) & 255;
        int rawLength = record.getInt(p + 6);
        if (rawLength < 1 || rawLength > MAX_RAW_BYTES)
            throw new IOException("Invalid cube bundle length " + rawLength);
        ByteBuffer payload = record.duplicate();
        payload.position(p + HEADER_BYTES);
        Stored stored = new Stored();
        try (DataInputStream in = stream(decompress(codec, payload, rawLength))) {
            int mask = in.readUnsignedByte();
            if (mask != headerMask || mask == 0) throw new IOException("Cube bundle member mask mismatch");
            stored.palettized = in.readUnsignedByte();
            if ((stored.palettized & ~mask) != 0) throw new IOException("Cube bundle palette mask mismatch");
            int count = in.readInt();
            if (count < 0 || count > 8 * 4096) throw new IOException("Invalid cube bundle palette size " + count);
            stored.count = count;
            stored.states = new int[count];
            for (int i = 0; i < count; i++) stored.states[i] = in.readInt();
            readMembers(in, mask, stored.members);
        }
        return stored;
    }

    static DataInputStream stream(byte[] raw) {
        return new DataInputStream(new ByteArrayInputStream(raw));
    }

    /** Reads the length-prefixed members in {@code mask}, which must end the payload. */
    private static void readMembers(DataInputStream in, int mask, byte[][] members) throws IOException {
        for (int s = 0; s < 8; s++) {
            if ((mask & 1 << s) == 0) continue;
            int length = in.readInt();
            if (length < 1 || length > in.available()) throw new IOException("Invalid cube bundle member length");
            members[s] = new byte[length];
            in.readFully(members[s]);
        }
        if (in.available() != 0) throw new IOException("Trailing data in cube bundle");
    }

    static NBTTagCompound parse(byte[] member) throws IOException {
        try (DataInputStream in = stream(member)) {
            return CompressedStreamTools.func_152456_a(in, NBTSizeTracker.field_152451_a);
        }
    }

    /** Decodes every member of a bundle record ({@code null} where absent). */
    static NBTTagCompound[] decode(ByteBuffer record) throws IOException {
        Stored stored = read(record);
        NBTTagCompound[] members = new NBTTagCompound[8];
        for (int s = 0; s < 8; s++) members[s] = stored.member(s);
        return members;
    }

    // --- encoding

    private static NBTTagCompound section(NBTTagCompound root) {
        return root.getCompoundTag("Level")
            .getTagList("Sections", 10)
            .getCompoundTagAt(0);
    }

    /**
     * The mask of block arrays if the cube's block data can use the palette, else -1: one section with arrays of the
     * expected lengths, no reserved keys, and no full 24-bit block IDs ({@code BlocksB3} must be absent or zero).
     */
    static int palettizable(NBTTagCompound root) {
        if (!root.hasKey("Level", 10)) return -1;
        NBTTagCompound level = root.getCompoundTag("Level");
        if (!level.hasKey("Sections", 9)) return -1;
        NBTTagList sections = level.getTagList("Sections", 10);
        if (sections.tagCount() != 1) return -1;
        NBTTagCompound section = sections.getCompoundTagAt(0);
        if (section.hasKey(ARRAYS_PRESENT) || section.hasKey(INDICES)) return -1;
        int mask = 0;
        for (int k = 0; k < ARRAYS.length; k++) {
            if (!section.hasKey(ARRAYS[k])) continue;
            if (!section.hasKey(ARRAYS[k], 7) || section.getByteArray(ARRAYS[k]).length != LENGTHS[k]) return -1;
            mask |= 1 << k;
        }
        if ((mask & 1 << B3) != 0) {
            for (byte b : section.getByteArray(ARRAYS[B3])) if (b != 0) return -1;
        }
        return mask;
    }

    private static int nibble(byte[] array, int i) {
        return array[i >> 1] >> ((i & 1) << 2) & 15;
    }

    /** One key per block: 16-bit ID (Blocks, Add, BlocksB2Hi) and 16-bit metadata (Data, Data1High, Data2). */
    static int[] keys(NBTTagCompound section, int mask) {
        byte[] blocks = (mask & 1) != 0 ? section.getByteArray(ARRAYS[0]) : null;
        byte[] add = (mask & 2) != 0 ? section.getByteArray(ARRAYS[1]) : null;
        byte[] b2hi = (mask & 4) != 0 ? section.getByteArray(ARRAYS[2]) : null;
        byte[] data = (mask & 8) != 0 ? section.getByteArray(ARRAYS[3]) : null;
        byte[] data1 = (mask & 16) != 0 ? section.getByteArray(ARRAYS[4]) : null;
        byte[] data2 = (mask & 32) != 0 ? section.getByteArray(ARRAYS[5]) : null;
        int[] keys = new int[4096];
        for (int i = 0; i < 4096; i++) {
            int id = blocks != null ? blocks[i] & 255 : 0;
            if (add != null) id |= nibble(add, i) << 8;
            if (b2hi != null) id |= nibble(b2hi, i) << 12;
            int meta = data != null ? nibble(data, i) : 0;
            if (data1 != null) meta |= nibble(data1, i) << 4;
            if (data2 != null) meta |= (data2[i] & 255) << 8;
            keys[i] = id | meta << 16;
        }
        return keys;
    }

    /**
     * Restores the block arrays of a palettized section in place and removes the bundle keys; returns how many blocks
     * use each palette entry.
     */
    private static int[] restore(NBTTagCompound section, Stored stored) throws IOException {
        int mask = section.getInteger(ARRAYS_PRESENT);
        byte[] indices = section.getByteArray(INDICES);
        int bits = stored.bits();
        if ((mask & ~127) != 0 || indices.length != 4096 * bits / 8) throw new IOException("Invalid bundle indices");
        byte[][] arrays = new byte[ARRAYS.length][];
        for (int k = 0; k < ARRAYS.length; k++) if ((mask & 1 << k) != 0) arrays[k] = new byte[LENGTHS[k]];
        int[] uses = new int[stored.count];
        for (int i = 0; i < 4096; i++) {
            int index = bits == 8 ? indices[i] & 255 : (indices[2 * i] & 255) << 8 | indices[2 * i + 1] & 255;
            if (index >= stored.count) throw new IOException("Bundle palette index out of range");
            uses[index]++;
            int key = stored.states[index], id = key & 65535, meta = key >>> 16, shift = (i & 1) << 2;
            if (arrays[0] != null) arrays[0][i] = (byte) id;
            if (arrays[1] != null) arrays[1][i >> 1] |= (byte) ((id >> 8 & 15) << shift);
            if (arrays[2] != null) arrays[2][i >> 1] |= (byte) ((id >> 12 & 15) << shift);
            if (arrays[3] != null) arrays[3][i >> 1] |= (byte) ((meta & 15) << shift);
            if (arrays[4] != null) arrays[4][i >> 1] |= (byte) ((meta >> 4 & 15) << shift);
            if (arrays[5] != null) arrays[5][i] = (byte) (meta >> 8);
        }
        section.removeTag(ARRAYS_PRESENT);
        section.removeTag(INDICES);
        for (int k = 0; k < ARRAYS.length; k++) if (arrays[k] != null) section.setByteArray(ARRAYS[k], arrays[k]);
        return uses;
    }

    /** Stores a client section's block counts from how many blocks use each palette entry. */
    static void setBlockCounts(NBTTagCompound section, Stored stored, int[] uses, IntFunction<Block> blocks,
        Block air) {
        int blockRefs = 0, tickRefs = 0;
        for (int index = 0; index < uses.length; index++) {
            if (uses[index] == 0) continue;
            Block block = blocks.apply(stored.states[index] & 65535);
            if (block == null) return;
            if (block == air) continue;
            blockRefs += uses[index];
            if (block.getTickRandomly()) tickRefs += uses[index];
        }
        section.setInteger(BLOCK_REFS, blockRefs);
        section.setInteger(TICK_REFS, tickRefs);
    }

    private static NBTTagCompound shallowCopy(NBTTagCompound tag) {
        NBTTagCompound copy = new NBTTagCompound();
        for (Object key : tag.func_150296_c()) copy.setTag((String) key, tag.getTag((String) key));
        return copy;
    }

    /** Serializes a member, with palette indices instead of block arrays when {@code keys} is given. */
    private static byte[] write(NBTTagCompound root, @Nullable int[] keys, int arrays, int bits) throws IOException {
        NBTTagCompound out = root;
        if (keys != null) {
            byte[] indices = new byte[4096 * bits / 8];
            for (int i = 0; i < 4096; i++) {
                int index = keys[i];
                if (bits == 8) indices[i] = (byte) index;
                else {
                    indices[2 * i] = (byte) (index >> 8);
                    indices[2 * i + 1] = (byte) index;
                }
            }
            NBTTagCompound section = shallowCopy(section(root));
            for (String key : ARRAYS) section.removeTag(key);
            section.setInteger(ARRAYS_PRESENT, arrays);
            section.setTag(INDICES, new NBTTagByteArray(indices));
            NBTTagList sections = new NBTTagList();
            sections.appendTag(section);
            NBTTagCompound level = shallowCopy(root.getCompoundTag("Level"));
            level.setTag("Sections", sections);
            out = shallowCopy(root);
            out.setTag("Level", level);
        }
        return serialize(out);
    }

    static byte[] serialize(NBTTagCompound root) throws IOException {
        ByteArrayOutputStream raw = new ByteArrayOutputStream(8192);
        try (DataOutputStream data = new DataOutputStream(raw)) {
            CompressedStreamTools.write(root, data);
        }
        return raw.toByteArray();
    }

    /** Palette of block keys; {@link #add} turns keys into indices in place. */
    private static final class Palette {

        final Int2IntOpenHashMap index = new Int2IntOpenHashMap();
        int[] states;
        int count;

        Palette() {
            states = new int[64];
            index.defaultReturnValue(-1);
        }

        Palette(int[] states, int count) {
            this.states = java.util.Arrays.copyOf(states, Math.max(64, count));
            this.count = count;
            index.defaultReturnValue(-1);
            for (int i = 0; i < count; i++) index.putIfAbsent(states[i], i);
        }

        int[] add(int[] keys) {
            int previousKey = 0, previousIndex = -1;
            for (int i = 0; i < keys.length; i++) {
                int key = keys[i];
                if (previousIndex >= 0 && key == previousKey) {
                    keys[i] = previousIndex;
                    continue;
                }
                int at = index.get(key);
                if (at < 0) {
                    if (count == states.length) states = java.util.Arrays.copyOf(states, count * 2);
                    states[count] = key;
                    at = count++;
                    index.put(key, at);
                }
                previousKey = key;
                previousIndex = at;
                keys[i] = at;
            }
            return keys;
        }
    }

    // --- record framing

    private static ByteBuffer pack(Stored stored) throws IOException {
        int mask = stored.mask();
        ByteArrayOutputStream raw = new ByteArrayOutputStream(16384);
        try (DataOutputStream out = new DataOutputStream(raw)) {
            out.writeByte(mask);
            out.writeByte(stored.palettized);
            out.writeInt(stored.count);
            for (int i = 0; i < stored.count; i++) out.writeInt(stored.states[i]);
            writeMembers(out, stored.members);
        }
        byte[] payload = raw.toByteArray();
        if (payload.length > maxEncodedBytes) throw new OversizedBundleException(payload.length);
        int codec = codec(CubicChunksConfig.chunkCompression);
        byte[] compressed = compress(codec, payload);
        ByteBuffer record = ByteBuffer.allocate(HEADER_BYTES + compressed.length);
        record.put((byte) 'C')
            .put((byte) 'C')
            .put((byte) 'B')
            .put((byte) VERSION)
            .put((byte) codec)
            .put((byte) mask)
            .putInt(payload.length)
            .put(compressed);
        record.flip();
        return record;
    }

    private static void writeMembers(DataOutputStream out, byte[][] members) throws IOException {
        for (byte[] member : members) {
            if (member == null) continue;
            out.writeInt(member.length);
            out.write(member);
        }
    }

    static int codec(CCNBTUtils.TagCompression compression) {
        switch (compression) {
            case GZIP:
                return CODEC_GZIP;
            case LZ4:
                return CODEC_LZ4;
            case ZSTD:
                return CODEC_ZSTD;
            default:
                return CODEC_NONE;
        }
    }

    static byte[] compress(int codec, byte[] raw) throws IOException {
        switch (codec) {
            case CODEC_ZSTD: {
                ByteBuffer out = ZstdCodec.compress(ByteBuffer.wrap(raw));
                byte[] bytes = new byte[out.remaining()];
                out.get(bytes);
                return bytes;
            }
            case CODEC_LZ4: {
                LZ4Compressor compressor = LZ4Factory.fastestInstance()
                    .fastCompressor();
                byte[] out = new byte[compressor.maxCompressedLength(raw.length)];
                int length = compressor.compress(raw, 0, raw.length, out, 0, out.length);
                return java.util.Arrays.copyOf(out, length);
            }
            case CODEC_GZIP: {
                ByteArrayOutputStream out = new ByteArrayOutputStream(raw.length / 4 + 64);
                try (GZIPOutputStream gzip = new GZIPOutputStream(out)) {
                    gzip.write(raw);
                }
                return out.toByteArray();
            }
            default:
                return raw;
        }
    }

    static byte[] decompress(int codec, ByteBuffer payload, int rawLength) throws IOException {
        byte[] out;
        switch (codec) {
            case CODEC_ZSTD:
                out = ZstdCodec.decompress(payload, MAX_RAW_BYTES);
                break;
            case CODEC_LZ4: {
                byte[] in = new byte[payload.remaining()];
                payload.get(in);
                try {
                    out = LZ4Factory.fastestInstance()
                        .safeDecompressor()
                        .decompress(in, 0, in.length, rawLength);
                } catch (LZ4Exception e) {
                    throw new IOException("Invalid LZ4 cube bundle", e);
                }
                break;
            }
            case CODEC_GZIP: {
                byte[] in = new byte[payload.remaining()];
                payload.get(in);
                ByteArrayOutputStream buffer = new ByteArrayOutputStream(rawLength);
                try (GZIPInputStream gzip = new GZIPInputStream(new ByteArrayInputStream(in))) {
                    byte[] chunk = new byte[8192];
                    for (int n; (n = gzip.read(chunk)) > 0;) {
                        if (buffer.size() + n > rawLength) throw new IOException("Oversized GZIP cube bundle");
                        buffer.write(chunk, 0, n);
                    }
                }
                out = buffer.toByteArray();
                break;
            }
            case CODEC_NONE:
                out = new byte[payload.remaining()];
                payload.get(out);
                break;
            default:
                throw new IOException("Unknown cube bundle codec " + codec);
        }
        if (out.length != rawLength) throw new IOException("Cube bundle length mismatch");
        return out;
    }
}
