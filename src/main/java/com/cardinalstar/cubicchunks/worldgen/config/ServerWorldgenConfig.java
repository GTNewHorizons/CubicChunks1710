package com.cardinalstar.cubicchunks.worldgen.config;

import java.io.File;

import net.minecraft.world.World;

import org.spongepowered.configurate.ConfigurateException;

import com.cardinalstar.cubicchunks.CubicChunks;
import com.cardinalstar.cubicchunks.api.DimensionNames;
import com.cardinalstar.cubicchunks.api.IntRange;
import com.cardinalstar.cubicchunks.api.worldgen.IWorldGeneratorType;
import com.cardinalstar.cubicchunks.api.worldgen.WorldGeneratorTypes;
import com.cardinalstar.cubicchunks.world.CubicChunksSavedData;
import com.cardinalstar.cubicchunks.worldgen.BuiltinGeneratorTypes;

/// The running server's worldgen settings. The save's file is loaded when the first dimension initializes, and dropped
/// when the server stops.
///
/// A dimension without settings gets them on first load: copied from its [CubicChunksSavedData] if it has any
/// (worlds created before the settings existed), otherwise the defaults. They're written to the file straight away, so
/// the dimension's values can't change if a default changes later, and the legacy data is then renamed so it's only
/// copied once. A dimension without a generator uses the default generator, [BuiltinGeneratorTypes#DEFAULT]. This
/// should automatically migrate all existing worlds, because it uses [ICubicWorldProvider] when it's available for a
/// given dimension and falls back to [VanillaWorldGenerator] when [ICubicWorldProvider] is not available.
///
/// Settings of dimensions without a [`name`][DimensionNames] aren't saved, so they start from the legacy data or the
/// defaults on every server start, and their legacy data is kept until they have a name.
public final class ServerWorldgenConfig {

    private static WorldgenConfig config;
    private static File worldDir;

    private ServerWorldgenConfig() {}

    /// The settings for `world`'s dimension, with its generator resolved. Throws if the save's file can't be
    /// loaded.
    public static synchronized WGDimensionConfig forWorld(World world) {
        File dir = world.getSaveHandler()
            .getWorldDirectory();
        if (config == null || !dir.equals(worldDir)) {
            try {
                config = WorldgenConfig.load(dir, WGConfigItemSource.EVENT, DimensionNames.INSTANCE);
            } catch (ConfigurateException e) {
                throw new IllegalStateException(
                    "Could not load the worldgen settings in " + WorldgenConfig.getFile(dir) + ": " + e.getMessage(),
                    e);
            }
            worldDir = dir;
        }

        int dimensionId = world.provider.dimensionId;
        WGDimensionConfig dim = config.get(dimensionId);
        if (dim != null && dim.generator != null) return dim;

        CubicChunksSavedData legacy = null;
        if (dim == null) {
            dim = config.getOrCreate(dimensionId);
            legacy = CubicChunksSavedData.load(world);
            if (legacy != null) copyLegacyData(legacy, dim);
        }
        if (dim.generator == null) {
            IWorldGeneratorType generator = BuiltinGeneratorTypes.DEFAULT;
            CubicChunks.LOGGER.info("Dimension {} did not have a generator, using {}", dim, generator.getId());
            dim = config.setGenerator(dimensionId, generator.getId());
        }

        // Don't create the config on initial world load because the world will think that folder is already taken
        if (worldDir.exists()) {
            // Only once the values are on disk: unnamed dimensions' settings aren't, so they need the legacy data again
            if (save() && legacy != null && dim.dimensionName != null) CubicChunksSavedData.markMigrated(world);
        }

        return dim;
    }

    /// The generator selected for `world`'s dimension. Throws if it isn't registered, e.g. because its mod was
    /// removed, or if it can't generate this dimension: generating with a different one would corrupt the terrain.
    /// Exceptions thrown by this method should avoid corrupting the world data.
    public static synchronized IWorldGeneratorType getGenerator(World world) {
        WGDimensionConfig dim = forWorld(world);
        IWorldGeneratorType generator = WorldGeneratorTypes.INSTANCE.get(dim.generator);
        if (generator == null) {
            throw new IllegalStateException(
                "Dimension " + dim
                    + " uses world generator "
                    + dim.generator
                    + ", which isn't registered. Was the mod that adds it removed? It's set in "
                    + WorldgenConfig.getFile(worldDir));
        }

        if (!WorldGeneratorTypes.INSTANCE.isDimensionUnlocked(dim.dimensionName)
            && generator != BuiltinGeneratorTypes.DEFAULT) {
            throw new IllegalStateException(
                "Dimension " + dim
                    + " uses world generator "
                    + dim.generator
                    + ", which is illegal because the dimension only works with its original generator. Set the dimension's generator to 'cubicchunks:default' to fix this. It's set in "
                    + WorldgenConfig.getFile(worldDir));
        }

        if (!generator.appliesTo(world)) {
            throw new IllegalStateException(
                "Dimension " + dim
                    + " uses world generator "
                    + dim.generator
                    + ", which can't generate it. It's set in "
                    + WorldgenConfig.getFile(worldDir));
        }

        return generator;
    }

    /// Writes the settings to the save, e.g. after a generator stores an auto-detected value. Returns false if nothing
    /// was written: nothing is loaded, or saving failed (which is logged).
    public static synchronized boolean save() {
        if (config == null) return false;
        try {
            config.save(worldDir);
            return true;
        } catch (ConfigurateException e) {
            CubicChunks.LOGGER.error("Could not save the worldgen settings in {}", WorldgenConfig.getFile(worldDir), e);
            return false;
        }
    }

    public static synchronized void unload() {
        config = null;
        worldDir = null;
    }

    private static void copyLegacyData(CubicChunksSavedData legacy, WGDimensionConfig dim) {
        CubicChunks.LOGGER.info("Copying the height and filler blocks of dimension {} into its worldgen settings", dim);
        dim.set(WGConfigItems.HEIGHT, new IntRange(legacy.minHeight, legacy.maxHeight));
        dim.set(WGConfigItems.VANILLA_BOTTOM_FILLER, legacy.bottomFiller);
        dim.set(WGConfigItems.VANILLA_TOP_FILLER, legacy.topFiller);
    }
}
