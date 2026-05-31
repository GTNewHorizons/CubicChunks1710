package com.cardinalstar.cubicchunks.api.compat;

import com.cardinalstar.cubicchunks.CubicChunksConfig;
import com.cardinalstar.cubicchunks.util.Mods;

public class CubicChunksVideoSettings {

    public static int getMinVerticalViewDistance() {
        return 2;
    }

    public static int getMaxVerticalViewDistance() {
        return Mods.Angelica.isModLoaded() ? 64 : 32;
    }

    public static int getVerticalViewDistance() {
        return CubicChunksConfig.verticalCubeLoadDistance;
    }

    public static void setVerticalViewDistance(int distance) {
        CubicChunksConfig.setVerticalViewDistance(distance);
    }
}
