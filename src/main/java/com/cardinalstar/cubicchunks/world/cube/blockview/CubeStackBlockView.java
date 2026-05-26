package com.cardinalstar.cubicchunks.world.cube.blockview;

import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.world.chunk.Chunk;

import org.jetbrains.annotations.NotNull;

import com.cardinalstar.cubicchunks.api.IColumn;
import com.cardinalstar.cubicchunks.world.cube.Cube;
import it.unimi.dsi.fastutil.ints.Int2ObjectRBTreeMap;

public class CubeStackBlockView implements IMutableBlockView {

    public final Int2ObjectRBTreeMap<Cube> cubes = new Int2ObjectRBTreeMap<>();

    public static CubeStackBlockView getExistingCubes(Chunk column, int cubeYStart, int cubeYEnd) {
        CubeStackBlockView view = new CubeStackBlockView();

        for (int i = Math.min(cubeYStart, cubeYEnd); i <= Math.max(cubeYStart, cubeYEnd); i++) {
            Cube cube = ((IColumn) column).getLoadedCube(i);

            if (cube != null) view.cubes.put(i, cube);
        }

        return view;
    }

    public static CubeStackBlockView createNewCubes(Chunk column, int cubeYStart, int cubeYEnd) {
        CubeStackBlockView view = new CubeStackBlockView();

        for (int i = Math.min(cubeYStart, cubeYEnd); i <= Math.max(cubeYStart, cubeYEnd); i++) {
            view.cubes.put(i, new Cube(column, i));
        }

        return view;
    }

    @Override
    public void setBlock(int x, int y, int z, @NotNull Block block) {
        Cube cube = cubes.get(y >> 4);

        if (cube == null) {
            throw new IllegalArgumentException("Cube does not exist for Y: " + y + " (" + (y >> 4) + ")");
        }

        cube.getOrCreateStorage()
            .func_150818_a(x & 0xF, y & 0xF, z & 0xF, block);
    }

    @Override
    public void setBlockMetadata(int x, int y, int z, int meta) {
        Cube cube = cubes.get(y >> 4);

        if (cube == null) {
            throw new IllegalArgumentException("Cube does not exist for Y: " + y + " (" + (y >> 4) + ")");
        }

        cube.getOrCreateStorage()
            .setExtBlockMetadata(x & 0xF, y & 0xF, z & 0xF, meta);
    }

    @Override
    public void setBlock(int x, int y, int z, @NotNull Block block, int meta) {
        Cube cube = cubes.get(y >> 4);

        if (cube == null) {
            throw new IllegalArgumentException("Cube does not exist for Y: " + y + " (" + (y >> 4) + ")");
        }

        cube.getOrCreateStorage()
            .func_150818_a(x & 0xF, y & 0xF, z & 0xF, block);
        cube.getOrCreateStorage()
            .setExtBlockMetadata(x & 0xF, y & 0xF, z & 0xF, meta);
    }

    @Override
    public @NotNull Block getBlock(int x, int y, int z) {
        Cube cube = cubes.get(y >> 4);

        return cube == null || cube.getStorage() == null ? Blocks.air
            : cube.getStorage()
                .getBlockByExtId(x & 0xF, y & 0xF, z & 0xF);
    }

    @Override
    public int getBlockMetadata(int x, int y, int z) {
        Cube cube = cubes.get(y >> 4);

        return cube == null || cube.getStorage() == null ? 0
            : cube.getStorage()
                .getExtBlockMetadata(x & 0xF, y & 0xF, z & 0xF);
    }
}
