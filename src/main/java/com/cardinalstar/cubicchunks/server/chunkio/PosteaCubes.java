package com.cardinalstar.cubicchunks.server.chunkio;

import java.util.HashSet;
import java.util.Set;

import javax.annotation.Nullable;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;
import net.minecraftforge.common.util.Constants.NBT;

import com.cardinalstar.cubicchunks.CubicChunks;
import com.cardinalstar.cubicchunks.api.MetaKey;
import com.cardinalstar.cubicchunks.util.Coords;
import com.cardinalstar.cubicchunks.world.cube.Cube;
import com.gtnewhorizons.postea.api.TriFunction;
import com.gtnewhorizons.postea.compat.Compat;
import com.gtnewhorizons.postea.compat.SubChunkAccess;
import com.gtnewhorizons.postea.mixins.interfaces.IMixinNBTTagList;
import com.gtnewhorizons.postea.utility.BlockConversionInfo;
import com.gtnewhorizons.postea.utility.BlockInfo;
import com.gtnewhorizons.postea.utility.ChunkFixerUtility;
import com.gtnewhorizons.postea.utility.TransformerRegistry;

/**
 * Runs Postea's migrations on a cube when it is read from disk. Postea migrates a vanilla chunk when the chunk is
 * read: mods register transformers that turn old tile entities into plain blocks and replace old blocks. A column has
 * no blocks or tile entities of its own, so under CubicChunks Postea's own hook finds nothing to do; this does for a
 * cube what {@code ChunkFixerUtility.onChunkRead} does for a chunk.
 * <p>
 * Like Postea, a cube remembers the mod list it was last migrated with (tag {@value #TAG}) and is skipped while that
 * has not changed. Only loaded when Postea is installed.
 */
final class PosteaCubes {

    /** In a cube's Level: {@code ChunkFixerUtility.POSTEA_UPDATE_CODE} of the game that last migrated it. */
    static final String TAG = "POSTEA";

    private static final Set<String> FAILED = new HashSet<>();
    /** On a cube with a tile entity whose transformer failed. */
    private static final MetaKey<Boolean> INCOMPLETE = new MetaKey<>() {};

    private PosteaCubes() {}

    /**
     * Migrates the cube's blocks (already in {@code cube}) and its saved tile entities (still in {@code level}), before
     * the tile entities are created.
     */
    static void onCubeRead(Cube cube, NBTTagCompound level, World world) {
        ExtendedBlockStorage storage = cube.getStorage();
        NBTTagList tileEntities = level.getTagList("TileEntities", NBT.TAG_COMPOUND);
        // Nothing to migrate, and a cube without content must stay one: it is stored as two state bytes.
        if (storage == null && tileEntities.tagCount() == 0) return;
        long code = level.hasKey(TAG, NBT.TAG_LONG) ? level.getLong(TAG) : -1;
        if (ChunkFixerUtility.hasChunkBeenUpdated(code)) return;

        boolean changed = migrateTileEntities(cube, storage, tileEntities, world);
        if (storage != null) changed |= migrateBlocks(cube, storage, world);

        if (changed) {
            // block IDs were written into the arrays directly
            if (storage != null) storage.removeInvalidBlocks();
            // the blocks are not what the column's height maps were built from; saved with the new blocks
            cube.setInHeightMap(false);
            cube.markDirty();
        }
    }

    /**
     * Marks a cube that is being saved as migrated: whatever is loaded has been through {@link #onCubeRead}. A cube
     * with a tile entity whose transformer failed is not marked, so that it is tried again.
     */
    static void onCubeWrite(Cube cube, NBTTagCompound level) {
        if (cube.getStorage() == null && level.getTagList("TileEntities", NBT.TAG_COMPOUND)
            .tagCount() == 0) return;
        if (cube.getMeta(INCOMPLETE) != null) return;
        level.setLong(TAG, ChunkFixerUtility.POSTEA_UPDATE_CODE);
    }

    /** As {@code ChunkFixerUtility.adjustTileEntities} and the block placement that follows it there. */
    private static boolean migrateTileEntities(Cube cube, @Nullable ExtendedBlockStorage storage,
        NBTTagList tileEntities, World world) {
        Chunk column = cube.getColumn();
        ExtendedBlockStorage[] columnStorage = column.getBlockStorageArray();
        // Transformers ask Postea for the block at a tile entity, which looks into the column's storage array at the
        // tile entity's Y / 16. A column only has the cubes at Y 0 to 15 there. For a cube outside, the transformer
        // sees the cube in slot 0 and the tile entity at the same place inside it.
        boolean outside = cube.getY() < 0 || cube.getY() >= columnStorage.length;
        boolean changed = false;
        for (int i = 0; i < tileEntities.tagCount(); i++) {
            NBTTagCompound tileEntity = tileEntities.getCompoundTagAt(i);
            String id = tileEntity.getString("id");
            for (TriFunction<NBTTagCompound, World, Chunk, BlockInfo> transformer : TransformerRegistry
                .getTileEntityToNormalBlockTransformerFunction(id)) {
                int x = tileEntity.getInteger("x");
                int y = tileEntity.getInteger("y");
                int z = tileEntity.getInteger("z");
                boolean moved = outside && Coords.blockToCube(y) == cube.getY();
                ExtendedBlockStorage slot = columnStorage[0];
                BlockInfo blockInfo;
                NBTTagCompound replacement = null;
                try {
                    if (moved) {
                        columnStorage[0] = storage;
                        tileEntity.setInteger("y", y & 15);
                    }
                    blockInfo = transformer.apply(tileEntity, world, column);
                    if (blockInfo != null && blockInfo.tileTransformer != null) {
                        replacement = blockInfo.tileTransformer.apply(tileEntity);
                    }
                } catch (RuntimeException e) {
                    // The tile entity stays as it is, and the cube is not saved as migrated.
                    cube.setMeta(INCOMPLETE, Boolean.TRUE);
                    synchronized (FAILED) {
                        if (FAILED.add(id)) {
                            CubicChunks.LOGGER.error("Postea transformer for tile entity {} failed on a cube", id, e);
                        }
                    }
                    continue;
                } finally {
                    if (moved) {
                        columnStorage[0] = slot;
                        tileEntity.setInteger("y", y);
                        // a tag made from the tile entity's position has the moved one
                        if (replacement != null && replacement != tileEntity && replacement.hasKey("y")) {
                            replacement.setInteger("y", y);
                        }
                    }
                }
                if (blockInfo == null) continue;
                if (replacement != null) {
                    ((IMixinNBTTagList) tileEntities).Postea$replaceCompoundTagAt(i, replacement);
                } else {
                    tileEntities.removeTag(i--);
                }
                if (storage != null && Coords.blockToCube(y) == cube.getY()) {
                    storage.func_150818_a(x & 15, y & 15, z & 15, blockInfo.block);
                    storage.setExtBlockMetadata(x & 15, y & 15, z & 15, blockInfo.metadata);
                }
                changed = true;
                // this tile entity is dealt with
                break;
            }
        }
        return changed;
    }

    /** As {@code ChunkFixerUtility.transformNormalBlocksInSubChunk}, telling whether anything was replaced. */
    private static boolean migrateBlocks(Cube cube, ExtendedBlockStorage storage, World world) {
        int minX = Coords.cubeToMinBlock(cube.getX()), minY = Coords.cubeToMinBlock(cube.getY()),
            minZ = Coords.cubeToMinBlock(cube.getZ());
        SubChunkAccess access = Compat.getSubChunkAccess(storage);
        boolean changed = false;
        for (int y = 0; y < 16; y++) {
            for (int z = 0; z < 16; z++) {
                for (int x = 0; x < 16; x++) {
                    int blockId = access.getBlockId(x, y, z);
                    if (blockId == 0) continue;
                    BlockConversionInfo output = TransformerRegistry
                        .getBlockReplacement(blockId, access.getMeta(x, y, z), world, minX + x, minY + y, minZ + z);
                    if (output != null) {
                        access.setBlockId(x, y, z, output.blockID);
                        access.setMeta(x, y, z, output.metadata);
                        changed = true;
                    }
                }
            }
        }
        return changed;
    }
}
