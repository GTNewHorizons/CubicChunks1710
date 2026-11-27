package com.cardinalstar.cubicchunks.world.convert;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import net.minecraft.block.Block;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

import com.cardinalstar.cubicchunks.CubicChunks;
import com.cardinalstar.cubicchunks.world.heightmap.PrecipitationHeightMap;

import cpw.mods.fml.common.registry.GameData;

/**
 * What a column's height maps need to know about each block ID of one world: whether the block blocks light and
 * whether it stops rain. A world's numeric IDs are its own (the ID map in level.dat), so the table is built from that
 * map and the blocks registered in the running game, not from the game's current IDs.
 */
public final class BlockTable {

    public static final int OPAQUE = 1, STOPS_RAIN = 2;
    /**
     * The block is not known here: its name is not registered in the running game. The game may still give it a block
     * when it loads the world (mods remap the names of blocks they renamed), so nothing can be said about it.
     */
    public static final int UNKNOWN = 4;

    private final byte[] flags;

    public BlockTable(byte[] flags) {
        this.flags = flags;
    }

    /** {@link #OPAQUE} and {@link #STOPS_RAIN} of a block ID, or {@link #UNKNOWN}. */
    public int flags(int id) {
        return id >= 0 && id < flags.length ? flags[id] : UNKNOWN;
    }

    /**
     * The table of the world in {@code worldRoot}. A world without an ID map (never opened with Forge) has vanilla's
     * IDs.
     */
    public static BlockTable forWorld(Path worldRoot) throws IOException {
        NBTTagCompound fml = null;
        Path levelDat = worldRoot.resolve("level.dat");
        if (Files.isRegularFile(levelDat)) {
            try (InputStream in = Files.newInputStream(levelDat)) {
                fml = CompressedStreamTools.readCompressed(in)
                    .getCompoundTag("FML");
            }
        }
        if (fml == null || !fml.hasKey("ItemData", 9)) return fromRegisteredIds();

        Map<String, String> aliases = new HashMap<>();
        NBTTagList aliasList = fml.getTagList("BlockAliases", 10);
        for (int i = 0; i < aliasList.tagCount(); i++) {
            NBTTagCompound alias = aliasList.getCompoundTagAt(i);
            aliases.put(alias.getString("K"), alias.getString("V"));
        }

        byte[] flags = unknown(4096);
        int missing = 0;
        NBTTagList items = fml.getTagList("ItemData", 10);
        for (int i = 0; i < items.tagCount(); i++) {
            NBTTagCompound entry = items.getCompoundTagAt(i);
            String key = entry.getString("K");
            // FML prefixes block names with \u0001 and item names with \u0002
            if (key.isEmpty() || key.charAt(0) != '\u0001') continue;
            String name = key.substring(1);
            int id = entry.getInteger("V");
            if (id < 0) continue;
            Block block = registered(name);
            if (block == null && aliases.containsKey(name)) block = registered(aliases.get(name));
            if (block == null) {
                missing++;
                continue;
            }
            flags = grow(flags, id);
            flags[id] = flagsOf(block);
        }
        if (missing > 0) {
            CubicChunks.LOGGER.info(
                "{} block IDs of {} have names that are not registered; cubes with such blocks are added to the height maps by the game",
                missing,
                worldRoot);
        }
        return new BlockTable(flags);
    }

    private static Block registered(String name) {
        return GameData.getBlockRegistry()
            .containsKey(name)
                ? GameData.getBlockRegistry()
                    .getObject(name)
                : null;
    }

    private static BlockTable fromRegisteredIds() {
        byte[] flags = unknown(4096);
        for (Object object : GameData.getBlockRegistry()) {
            Block block = (Block) object;
            int id = Block.getIdFromBlock(block);
            if (id < 0) continue;
            flags = grow(flags, id);
            flags[id] = flagsOf(block);
        }
        return new BlockTable(flags);
    }

    private static byte[] unknown(int length) {
        byte[] flags = new byte[length];
        Arrays.fill(flags, (byte) UNKNOWN);
        return flags;
    }

    private static byte[] grow(byte[] flags, int id) {
        if (id < flags.length) return flags;
        byte[] grown = unknown(Math.max(id + 1, flags.length * 2));
        System.arraycopy(flags, 0, grown, 0, flags.length);
        return grown;
    }

    /** As Cube.collectHeightMapBits decides both when a cube enters its column's height maps. */
    private static byte flagsOf(Block block) {
        int flags = 0;
        if (block.getLightOpacity() > 0) flags |= OPAQUE;
        if (PrecipitationHeightMap.blocksPrecipitation(block)) flags |= STOPS_RAIN;
        return (byte) flags;
    }
}
