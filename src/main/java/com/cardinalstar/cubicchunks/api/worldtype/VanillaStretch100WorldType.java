package com.cardinalstar.cubicchunks.api.worldtype;

import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraft.world.WorldType;

import org.jetbrains.annotations.NotNull;

import com.cardinalstar.cubicchunks.api.IntRange;
import com.cardinalstar.cubicchunks.api.world.ICubicWorldType;
import com.cardinalstar.cubicchunks.api.worldgen.BuiltinWorldDecorators;
import com.cardinalstar.cubicchunks.api.worldgen.IWorldGenerator;
import com.cardinalstar.cubicchunks.worldgen.VanillaWorldGenerator;

/** Vanilla chunk generation with each source block layer repeated a hundred times in the overworld. */
public class VanillaStretch100WorldType extends WorldType implements ICubicWorldType {

    // world type names are limited to 16 characters
    public static final String LEVEL_NAME = "VanillaX100";
    public static final int SCALE = 100;

    public static void init() {
        new VanillaStretch100WorldType();
    }

    private VanillaStretch100WorldType() {
        super(LEVEL_NAME);
    }

    @Override
    public @NotNull IWorldGenerator createCubeGenerator(World world) {
        return new VanillaWorldGenerator(
            world.provider.createChunkGenerator(),
            world,
            BuiltinWorldDecorators.VANILLA,
            SCALE);
    }

    @Override
    public IntRange calculateGenerationHeightRange(WorldServer world) {
        return new IntRange(0, 256 * SCALE);
    }

    @Override
    public boolean hasCubicGeneratorForWorld(World world) {
        return world.provider.dimensionId == 0;
    }
}
