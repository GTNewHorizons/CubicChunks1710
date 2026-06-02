package com.cardinalstar.cubicchunks.worldgen.ccenhanced;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;

import net.minecraft.entity.EnumCreatureType;
import net.minecraft.init.Blocks;
import net.minecraft.world.ChunkPosition;
import net.minecraft.world.World;
import net.minecraft.world.biome.BiomeGenBase;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;

import com.cardinalstar.cubicchunks.api.ICube;
import com.cardinalstar.cubicchunks.api.worldgen.GenerationResult;
import com.cardinalstar.cubicchunks.api.worldgen.IWorldGenerator;
import com.cardinalstar.cubicchunks.world.core.IColumnInternal;
import com.cardinalstar.cubicchunks.world.cube.Cube;

@ParametersAreNonnullByDefault
public class CCEnhancedWorldGenerator implements IWorldGenerator {

    private static final int SEA_LEVEL = 64;

    // Flat world layout:
    // cubeY 0-2 (blocks 0-47): all stone
    // cubeY 3 (blocks 48-63): stone, top layer (local y=15) = dirt
    // cubeY 4 (blocks 64-79): grass at local y=0, air above
    // cubeY 5+ (blocks 80+ ): air
    private static final int SURFACE_CUBE_Y = SEA_LEVEL / 16; // 4
    private static final int DIRT_CUBE_Y = SURFACE_CUBE_Y - 1; // 3

    private final World world;

    public CCEnhancedWorldGenerator(World world) {
        this.world = world;
    }

    // -------------------------------------------------------------------------
    // Column
    // -------------------------------------------------------------------------

    @Override
    public GenerationResult<Chunk> provideColumn(World world, int columnX, int columnZ) {
        Chunk chunk = new Chunk(world, columnX, columnZ);
        ((IColumnInternal) chunk).setColumn(true);

        List<Cube> cubes = new ArrayList<>(16);
        for (int cy = 0; cy < 16; cy++) {
            cubes.add(new Cube(chunk, cy, createFlatEbs(cy)));
        }

        return new GenerationResult<>(chunk, null, cubes);
    }

    // -------------------------------------------------------------------------
    // Cube
    // -------------------------------------------------------------------------

    @Override
    public GenerationResult<Cube> provideCube(@Nullable Chunk chunk, int cubeX, int cubeY, int cubeZ) {
        List<Chunk> generatedColumns = new ArrayList<>();
        List<Cube> generatedCubes = new ArrayList<>();

        // Generate column + all vanilla-range cubes if chunk is missing,
        // or if the requested cube falls in the vanilla range.
        if ((cubeY >= 0 && cubeY < 16) || chunk == null) {
            if (chunk == null) {
                chunk = new Chunk(world, cubeX, cubeZ);
                ((IColumnInternal) chunk).setColumn(true);
                generatedColumns.add(chunk);
            }
            for (int cy = 0; cy < 16; cy++) {
                generatedCubes.add(new Cube(chunk, cy, createFlatEbs(cy)));
            }
        }

        // Cubes outside [0,16) are always air.
        if (cubeY < 0 || cubeY >= 16) {
            generatedCubes.add(new Cube(chunk, cubeY, (ExtendedBlockStorage) null));
        }

        // Extract the requested cube as the primary result.
        Cube primary = null;
        for (int i = 0; i < generatedCubes.size(); i++) {
            Cube c = generatedCubes.get(i);
            if (c.getY() == cubeY) {
                primary = c;
                generatedCubes.remove(i);
                break;
            }
        }

        return new GenerationResult<>(primary, generatedColumns, generatedCubes);
    }

    // -------------------------------------------------------------------------
    // Population
    // -------------------------------------------------------------------------

    @Override
    public void populate(Cube cube) {
        cube.markPopulated(Cube.POP_ALL);
    }

    // -------------------------------------------------------------------------
    // Structure stubs
    // -------------------------------------------------------------------------

    @Override
    public void recreateStructures(ICube cube) {}

    @Override
    public void recreateStructures(Chunk column) {}

    @Override
    public List<BiomeGenBase.SpawnListEntry> getPossibleCreatures(EnumCreatureType type, int x, int y, int z) {
        return BiomeGenBase.plains.getSpawnableList(type);
    }

    @Override
    @Nullable
    public ChunkPosition getNearestStructure(String name, int x, int y, int z) {
        return null;
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /**
     * Build an ExtendedBlockStorage for the flat world at the given cube Y.
     * Returns null for fully-air cubes (cubeY outside the filled range).
     */
    @Nullable
    private ExtendedBlockStorage createFlatEbs(int cubeY) {
        if (cubeY < 0 || cubeY > SURFACE_CUBE_Y) {
            return null; // air
        }

        int yBase = cubeY * 16;
        boolean storeSkylight = !world.provider.hasNoSky;
        ExtendedBlockStorage ebs = new ExtendedBlockStorage(yBase, storeSkylight);

        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                if (cubeY < DIRT_CUBE_Y) {
                    // All stone
                    for (int ly = 0; ly < 16; ly++) {
                        ebs.func_150818_a(x, ly, z, Blocks.stone);
                    }
                } else if (cubeY == DIRT_CUBE_Y) {
                    // Stone with dirt cap
                    for (int ly = 0; ly < 15; ly++) {
                        ebs.func_150818_a(x, ly, z, Blocks.stone);
                    }
                    ebs.func_150818_a(x, 15, z, Blocks.dirt);
                } else {
                    // cubeY == SURFACE_CUBE_Y: grass at local y=0, air above
                    ebs.func_150818_a(x, 0, z, Blocks.grass);
                }
            }
        }

        return ebs;
    }
}
