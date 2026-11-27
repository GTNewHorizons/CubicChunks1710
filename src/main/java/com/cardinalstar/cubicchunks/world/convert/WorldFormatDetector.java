package com.cardinalstar.cubicchunks.world.convert;

import java.io.File;
import java.io.IOException;

import javax.annotation.Nonnull;
import javax.annotation.ParametersAreNonnullByDefault;

import com.cardinalstar.cubicchunks.CubicChunks;

/**
 * Detects the on-disk save format of a world folder without loading the world.
 */
@ParametersAreNonnullByDefault
public final class WorldFormatDetector {

    private WorldFormatDetector() {}

    @Nonnull
    public static WorldSaveFormat detect(File worldSaveDir) {
        try {
            // an interrupted conversion is finished or discarded first; what is on disk is not a format until then
            ConversionInstall.recover(worldSaveDir.toPath());
        } catch (IOException | RuntimeException e) {
            CubicChunks.LOGGER.error("Could not finish the interrupted conversion of {}", worldSaveDir, e);
            return WorldSaveFormat.MIXED;
        }
        boolean cubes = holdsFiles(worldSaveDir, "region2d") || holdsFiles(worldSaveDir, "region3d");
        boolean marker = new File(worldSaveDir, "data/cubicchunks.world_format.dat").exists();
        boolean vanilla = hasVanillaRegions(worldSaveDir);

        if (cubes && vanilla) return WorldSaveFormat.MIXED;
        // A marker next to vanilla regions and no cube storage was left by opening the vanilla world with CubicChunks.
        if (cubes || marker && !vanilla) return WorldSaveFormat.CC;
        if (vanilla) return WorldSaveFormat.VANILLA;
        return WorldSaveFormat.UNKNOWN;
    }

    private static boolean holdsFiles(File dir, String folder) {
        try {
            return WorldConverter.holdsFiles(new File(dir, folder).toPath());
        } catch (IOException e) {
            return true;
        }
    }

    private static boolean hasVanillaRegions(File dir) {
        File regionDir = new File(dir, "region");
        if (!regionDir.isDirectory()) return false;
        String[] mcaFiles = regionDir.list((d, name) -> name.endsWith(".mca"));
        return mcaFiles != null && mcaFiles.length > 0;
    }
}
