package com.cardinalstar.cubicchunks.worldgen.ccenhanced;

import javax.annotation.ParametersAreNonnullByDefault;

import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraft.world.WorldType;

import org.jetbrains.annotations.NotNull;

import com.cardinalstar.cubicchunks.api.IntRange;
import com.cardinalstar.cubicchunks.api.world.ICubicWorldType;
import com.cardinalstar.cubicchunks.api.worldgen.IWorldGenerator;

@ParametersAreNonnullByDefault
public class CCEnhancedWorldType extends WorldType implements ICubicWorldType {

    public static CCEnhancedWorldType INSTANCE;

    private CCEnhancedWorldType() {
        super("Cubic Chunks");
    }

    public static void init() {
        INSTANCE = new CCEnhancedWorldType();
    }

    @Override
    public @NotNull IWorldGenerator createCubeGenerator(World world) {
        return new CCEnhancedWorldGenerator(world);
    }

    @Override
    public IntRange calculateGenerationHeightRange(WorldServer world) {
        return new IntRange(-1024, 1024);
    }

    @Override
    public boolean hasCubicGeneratorForWorld(World world) {
        return true;
    }
}
