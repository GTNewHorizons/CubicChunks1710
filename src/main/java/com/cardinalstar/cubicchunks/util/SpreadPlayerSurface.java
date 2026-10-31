package com.cardinalstar.cubicchunks.util;

import javax.annotation.Nullable;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.init.Blocks;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;

import com.cardinalstar.cubicchunks.api.IColumn;
import com.cardinalstar.cubicchunks.world.ICubicWorld;
import com.cardinalstar.cubicchunks.world.cube.Cube;
import com.cardinalstar.cubicchunks.world.cube.ICubeProvider;

/**
 * A bounded search around known column surfaces, never a full physical-height scan. It only reads loaded cubes and
 * never loads or generates terrain; where the loaded terrain does not decide, callers fall back to vanilla's search.
 */
public final class SpreadPlayerSurface {

    /** The floor is liquid or fire, the space above it is blocked, or it is at the top of the world. */
    public static final int UNSAFE = Integer.MIN_VALUE;
    /** The column, its floor or the space above the floor is not loaded, or the search budget ran out. */
    public static final int NOT_LOADED = Integer.MIN_VALUE + 1;

    private SpreadPlayerSurface() {}

    /** The landing height at x, z, or {@link #UNSAFE} or {@link #NOT_LOADED}. */
    public static int find(World world, int x, int z) {
        ICubicWorld cubic = (ICubicWorld) world;
        int minimum = cubic.getMinHeight();
        int maximum = cubic.getMaxHeight();
        Chunk chunk = ((ICubeProvider) world.getChunkProvider()).getLoadedColumn(x >> 4, z >> 4);
        if (chunk == null) return NOT_LOADED;
        IColumn column = (IColumn) chunk;
        // The opacity index gives the known top; resident cubes also expose transparent platforms above it.
        int top = column.getOpacityIndex()
            .getTopBlockY(x & 15, z & 15);
        int budget = 256;
        scan: for (Cube cube : column.getLoadedCubes((maximum - 1) >> 4, minimum >> 4)) {
            int cubeTop = Math.min(
                cube.getCoords()
                    .getMaxBlockY(),
                maximum - 1);
            if (cubeTop <= top) break;
            if (cube.isEmpty()) continue;
            int bottom = Math.max(
                cube.getCoords()
                    .getMinBlockY(),
                minimum);
            for (int y = cubeTop; y >= bottom && y > top; y--) {
                if (--budget < 0) return NOT_LOADED;
                if (cube.getBlock(x, y, z)
                    .getMaterial() != Material.air) {
                    top = y;
                    break scan;
                }
            }
        }
        if (top < minimum) return NOT_LOADED;
        if (top >= maximum - 1) return UNSAFE;
        for (int y = top; y >= minimum && budget-- > 0; y--) {
            Block floor = loadedBlock(column, maximum, x, y, z);
            if (floor == null) return NOT_LOADED;
            Material material = floor.getMaterial();
            if (material.isLiquid() || material == Material.fire) return UNSAFE;
            if (!material.blocksMovement()) continue;
            Block feet = loadedBlock(column, maximum, x, y + 1, z);
            Block head = loadedBlock(column, maximum, x, y + 2, z);
            if (feet == null || head == null) return NOT_LOADED;
            return clear(feet.getMaterial()) && clear(head.getMaterial()) ? y + 1 : UNSAFE;
        }
        return NOT_LOADED;
    }

    @Nullable
    private static Block loadedBlock(IColumn column, int maximum, int x, int y, int z) {
        if (y >= maximum) return Blocks.air;
        Cube cube = column.getLoadedCube(y >> 4);
        return cube == null ? null : cube.getBlock(x, y, z);
    }

    private static boolean clear(Material material) {
        return !material.blocksMovement() && !material.isLiquid() && material != Material.fire;
    }
}
