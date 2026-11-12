package com.cardinalstar.cubicchunks.world.heightmap;

import javax.annotation.ParametersAreNonnullByDefault;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;

import com.cardinalstar.cubicchunks.util.Coords;

/**
 * Tracks the blocks that stop rain and snow. Vanilla stops precipitation at any block whose material blocks movement or
 * is a liquid, so glass and fences count even though they do not block light.
 * <p>
 * Like the opacity index, this is saved with the column and keeps the last known contents of unloaded cubes, so a roof
 * in an unloaded cube still shelters the loaded cubes below it.
 */
@ParametersAreNonnullByDefault
public class PrecipitationHeightMap extends HeightMap3D {

    public static boolean blocksPrecipitation(Block block) {
        Material material = block.getMaterial();
        return material.blocksMovement() || material.isLiquid();
    }

    public void onBlockChange(int localX, int blockY, int localZ, Block block) {
        onOpacityChange(localX, blockY, localZ, blocksPrecipitation(block));
    }

    /**
     * Returns the value of {@code Chunk.getPrecipitationHeight}: one above the highest block that stops rain, or -1
     * when there is no such block.
     */
    public int getPrecipitationHeight(int localX, int localZ) {
        int topY = getTopBlockY(localX, localZ);
        return topY == Coords.NO_HEIGHT ? -1 : topY + 1;
    }
}
