package com.cardinalstar.cubicchunks.world;

import net.minecraft.block.Block;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;
import net.minecraft.world.WorldSavedData;

import com.cardinalstar.cubicchunks.worldgen.config.BlockRef;
import com.cardinalstar.cubicchunks.worldgen.config.ServerWorldgenConfig;

/**
 * Per-dimension world data from before worldgen settings existed: the height range and the vanilla generator's
 * filler blocks. Only read, to seed a dimension's settings the first time they're created (see
 * {@link ServerWorldgenConfig}). It's left on disk unchanged.
 */
public class CubicChunksSavedData extends WorldSavedData {

    private static final String NAME = "cubicChunksData";

    public int minHeight, maxHeight;
    /** Null if none was saved. */
    public BlockRef bottomFiller, topFiller;

    public CubicChunksSavedData(String name) {
        super(name);
    }

    /** The world's saved data, or null if it has none (the world was created after it was replaced). */
    public static CubicChunksSavedData load(World world) {
        return (CubicChunksSavedData) world.perWorldStorage.loadData(CubicChunksSavedData.class, NAME);
    }

    @Override
    public void readFromNBT(NBTTagCompound nbt) {
        // set 4 least significant bits to zero to ensure they are always multiples of 16
        minHeight = nbt.getInteger("minHeight") & ~0xF;
        maxHeight = nbt.getInteger("maxHeight") & ~0xF;

        NBTTagCompound filler = nbt.getCompoundTag("fillerBlocks");
        bottomFiller = readFiller(filler, "bottomId", "bottomMeta");
        topFiller = readFiller(filler, "topId", "topMeta");
    }

    private static BlockRef readFiller(NBTTagCompound filler, String idKey, String metaKey) {
        if (!filler.hasKey(idKey)) return null;
        Block block = Block.getBlockById(filler.getInteger(idKey));
        String id = Block.blockRegistry.getNameForObject(block);
        int meta = filler.getShort(metaKey);
        return id == null || meta < 0 || meta > 15 ? null : new BlockRef(id, meta);
    }

    // Never marked dirty, so never written; this keeps the data unchanged if something else saves it
    @Override
    public void writeToNBT(NBTTagCompound compound) {
        compound.setInteger("minHeight", minHeight);
        compound.setInteger("maxHeight", maxHeight);

        NBTTagCompound filler = new NBTTagCompound();
        writeFiller(filler, bottomFiller, "bottomId", "bottomMeta");
        writeFiller(filler, topFiller, "topId", "topMeta");
        compound.setTag("fillerBlocks", filler);
    }

    private static void writeFiller(NBTTagCompound filler, BlockRef ref, String idKey, String metaKey) {
        Block block = ref == null ? null : ref.resolve();
        if (block == null) return;
        filler.setInteger(idKey, Block.getIdFromBlock(block));
        filler.setShort(metaKey, (short) ref.meta);
    }
}
