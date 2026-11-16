package com.cardinalstar.cubicchunks.worldgen.config;

import java.io.File;

import net.minecraft.world.World;

import org.spongepowered.configurate.ConfigurateException;

import com.cardinalstar.cubicchunks.CubicChunks;
import com.cardinalstar.cubicchunks.api.DimensionNames;
import com.cardinalstar.cubicchunks.api.IntRange;
import com.cardinalstar.cubicchunks.world.CubicChunksSavedData;

/**
 * The running server's worldgen settings. The save's file is loaded when the first dimension initializes, and dropped
 * when the server stops.
 * <p>
 * A dimension without settings gets them on first load: copied from its {@link CubicChunksSavedData} if it has any
 * (worlds created before the settings existed), otherwise the defaults. They're written to the file straight away, so
 * the dimension's values can't change if a default changes later, and the legacy data is then renamed so it's only
 * copied once.
 * <p>
 * Settings of dimensions without a {@link DimensionNames name} aren't saved, so they start from the legacy data or the
 * defaults on every server start, and their legacy data is kept until they have a name.
 */
public final class ServerWorldgenConfig {

    private static WorldgenConfig config;
    private static File worldDir;

    private ServerWorldgenConfig() {}

    /** The settings for {@code world}'s dimension. Throws if the save's file can't be loaded. */
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
        if (dim != null) return dim;

        dim = config.getOrCreate(dimensionId);
        CubicChunksSavedData legacy = CubicChunksSavedData.load(world);
        if (legacy != null) copyLegacyData(legacy, dim);
        // Only once the values are on disk: unnamed dimensions' settings aren't, so they need the legacy data again
        if (save() && legacy != null && dim.dimensionName != null) CubicChunksSavedData.markMigrated(world);
        return dim;
    }

    /**
     * Writes the settings to the save, e.g. after a generator stores an auto-detected value. Returns false if nothing
     * was written: nothing is loaded, or saving failed (which is logged).
     */
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
