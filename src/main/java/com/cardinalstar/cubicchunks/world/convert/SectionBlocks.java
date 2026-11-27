package com.cardinalstar.cubicchunks.world.convert;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.common.util.Constants.NBT;

/**
 * Reads the block IDs of a saved section, only to look up what the height maps need. The section itself is never
 * rewritten, so a layout this class does not know costs nothing but the precomputed height map.
 */
final class SectionBlocks {

    static final int EXACT = 0, PARTIAL = 1, UNREADABLE = -1;

    private SectionBlocks() {}

    /**
     * Sets bit y of {@code opaque[z << 4 | x]} for each block that blocks light and of {@code stopsRain[z << 4 | x]}
     * for each that stops rain.
     *
     * @return {@link #EXACT}; {@link #PARTIAL} when the section holds blocks of unknown kind, which are left out; or
     *         {@link #UNREADABLE} when its block IDs are in a layout this class does not know
     */
    static int collect(NBTTagCompound section, BlockTable table, int[] opaque, int[] stopsRain) {
        if (section.hasKey("Blocks", NBT.TAG_BYTE_ARRAY)) {
            // vanilla (Blocks, Add) and EndlessIDs (BlocksB2Hi, BlocksB3 on top)
            byte[] low = section.getByteArray("Blocks");
            byte[] add = nibbles(section, "Add"), high = nibbles(section, "BlocksB2Hi");
            byte[] top = section.hasKey("BlocksB3") ? section.getByteArray("BlocksB3") : null;
            if (low.length != 4096 || add == INVALID || high == INVALID || top != null && top.length != 4096)
                return UNREADABLE;
            int seen = 0;
            for (int i = 0; i < 4096; i++) {
                int id = low[i] & 255;
                if (add != null) id |= nibble(add, i) << 8;
                if (high != null) id |= nibble(high, i) << 12;
                if (top != null) id |= (top[i] & 255) << 16;
                seen |= mark(table.flags(id), i, opaque, stopsRain);
            }
            return (seen & BlockTable.UNKNOWN) != 0 ? PARTIAL : EXACT;
        }
        if (section.hasKey("Blocks16", NBT.TAG_BYTE_ARRAY)) {
            // EndlessIDs before its current layout: one big-endian short per block
            byte[] ids = section.getByteArray("Blocks16");
            if (ids.length != 8192) return UNREADABLE;
            int seen = 0;
            for (int i = 0; i < 4096; i++) {
                seen |= mark(table.flags((ids[i * 2] & 255) << 8 | ids[i * 2 + 1] & 255), i, opaque, stopsRain);
            }
            return (seen & BlockTable.UNKNOWN) != 0 ? PARTIAL : EXACT;
        }
        return UNREADABLE;
    }

    private static final byte[] INVALID = new byte[0];

    /** A nibble array of a section: null when absent, {@link #INVALID} when it is not one. */
    private static byte[] nibbles(NBTTagCompound section, String key) {
        if (!section.hasKey(key)) return null;
        byte[] array = section.hasKey(key, NBT.TAG_BYTE_ARRAY) ? section.getByteArray(key) : INVALID;
        return array.length == 2048 ? array : INVALID;
    }

    private static int nibble(byte[] array, int i) {
        return array[i >> 1] >> ((i & 1) << 2) & 15;
    }

    /** Block index: y << 8 | z << 4 | x. Returns {@code flags}. */
    private static int mark(int flags, int i, int[] opaque, int[] stopsRain) {
        if (flags == 0) return flags;
        int column = i & 255, bit = 1 << (i >> 8);
        if ((flags & BlockTable.OPAQUE) != 0) opaque[column] |= bit;
        if ((flags & BlockTable.STOPS_RAIN) != 0) stopsRain[column] |= bit;
        return flags;
    }
}
