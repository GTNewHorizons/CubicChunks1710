package com.cardinalstar.cubicchunks.api.worldtype;

import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraft.world.WorldType;

import org.jetbrains.annotations.NotNull;

import com.cardinalstar.cubicchunks.api.IntRange;
import com.cardinalstar.cubicchunks.api.world.ICubicWorldType;
import com.cardinalstar.cubicchunks.api.worldgen.IWorldGenerator;
import com.cardinalstar.cubicchunks.worldgen.MountainWorldGenerator;

/** Mountains up to Y 20000 with water filled valleys in the overworld, see {@link MountainWorldGenerator}. */
public class MountainWorldType extends WorldType implements ICubicWorldType {

    // world type names are limited to 16 characters
    public static final String LEVEL_NAME = "CubicMountains";

    public static void init() {
        new MountainWorldType();
    }

    private MountainWorldType() {
        super(LEVEL_NAME);
    }

    @Override
    public @NotNull IWorldGenerator createCubeGenerator(World world) {
        return new MountainWorldGenerator(world);
    }

    @Override
    public IntRange calculateGenerationHeightRange(WorldServer world) {
        return new IntRange(0, 20000);
    }

    @Override
    public boolean hasCubicGeneratorForWorld(World world) {
        return world.provider.dimensionId == 0;
    }
}
