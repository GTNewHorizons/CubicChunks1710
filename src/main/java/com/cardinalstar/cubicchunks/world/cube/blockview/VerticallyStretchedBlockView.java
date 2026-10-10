package com.cardinalstar.cubicchunks.world.cube.blockview;

import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;

import com.cardinalstar.cubicchunks.world.cube.Cube;

/** A cube-local view of a vanilla chunk with each source layer repeated vertically. */
public class VerticallyStretchedBlockView implements IBlockView {

    private final ExtendedBlockStorage[] source;
    private final int cubeY;
    private final int scale;

    public VerticallyStretchedBlockView(ExtendedBlockStorage[] source, int cubeY, int scale) {
        if (scale < 1) throw new IllegalArgumentException("scale must be positive");
        this.source = source;
        this.cubeY = cubeY;
        this.scale = scale;
    }

    private int sourceY(int localY) {
        return Math.floorDiv(cubeY * Cube.SIZE + localY, scale);
    }

    private ExtendedBlockStorage storage(int sourceY) {
        int index = sourceY >> 4;
        return index < 0 || index >= source.length ? null : source[index];
    }

    @Override
    public Block getBlock(int x, int y, int z) {
        int sourceY = sourceY(y);
        ExtendedBlockStorage ebs = storage(sourceY);
        return ebs == null ? Blocks.air : ebs.getBlockByExtId(x, sourceY & 15, z);
    }

    @Override
    public int getBlockMetadata(int x, int y, int z) {
        int sourceY = sourceY(y);
        ExtendedBlockStorage ebs = storage(sourceY);
        return ebs == null ? 0 : ebs.getExtBlockMetadata(x, sourceY & 15, z);
    }
}
