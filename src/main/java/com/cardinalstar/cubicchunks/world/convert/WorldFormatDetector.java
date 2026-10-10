package com.cardinalstar.cubicchunks.world.convert;

import java.io.File;

import javax.annotation.Nonnull;
import javax.annotation.ParametersAreNonnullByDefault;

/**
 * Detects the on-disk save format of a world folder without loading the world.
 * All checks are pure filesystem operations (File.exists / File.isDirectory).
 */
@ParametersAreNonnullByDefault
public final class WorldFormatDetector {

    private WorldFormatDetector() {}

    @Nonnull
    public static WorldSaveFormat detect(File worldSaveDir) {
        boolean cc = hasCCMarkers(worldSaveDir);
        boolean vanilla = hasVanillaRegions(worldSaveDir);

        if (cc && vanilla) return WorldSaveFormat.MIXED;
        if (cc)            return WorldSaveFormat.CC;
        if (vanilla)       return WorldSaveFormat.VANILLA;
        return WorldSaveFormat.UNKNOWN;
    }

    private static boolean hasCCMarkers(File dir) {
        if (new File(dir, "data/cubicchunks.world_format.dat").exists()) return true;
        if (new File(dir, "region2d").isDirectory()) return true;
        if (new File(dir, "region3d").isDirectory()) return true;
        return false;
    }

    private static boolean hasVanillaRegions(File dir) {
        File regionDir = new File(dir, "region");
        if (!regionDir.isDirectory()) return false;
        String[] mcaFiles = regionDir.list((d, name) -> name.endsWith(".mca"));
        return mcaFiles != null && mcaFiles.length > 0;
    }
}
