package com.cardinalstar.cubicchunks.api.world;

import net.minecraft.world.World;
import net.minecraft.world.chunk.IChunkProvider;

import org.jetbrains.annotations.NotNull;

import com.cardinalstar.cubicchunks.api.IntRange;
import com.cardinalstar.cubicchunks.api.worldgen.IWorldGenerator;
import com.cardinalstar.cubicchunks.worldgen.VanillaWorldGenerator;

/// Implemented on a [WorldProvider]. This is primarily used by mods to make their dimensions cubic. [ICubicWorldType]
/// takes priority over this interface. When a [WorldProvider] does not implement this interface,
/// [WorldProvider#createChunkGenerator()] is called and the result is wrapped by a [VanillaWorldGenerator].
public interface ICubicWorldProvider {

    /// @deprecated New parameter: implement [#createCubeGenerator(IChunkProvider)] instead.
    @Deprecated
    @NotNull
    default IWorldGenerator createCubeGenerator() {
        throw new UnsupportedOperationException();
    }

    @NotNull
    default IWorldGenerator createCubeGenerator(IChunkProvider chunkGenerator) {
        return createCubeGenerator();
    }

    /// @deprecated Renamed: implement [#getGenerationRange()] instead.
    @Deprecated
    default int getOriginalActualHeight() {
        throw new UnsupportedOperationException();
    }

    default IntRange getGenerationRange() {
        return new IntRange(0, getOriginalActualHeight());
    }

    /// @deprecated Pointless due to public field: safe to remove
    @Deprecated
    default World getWorld() {
        return null;
    }
}
