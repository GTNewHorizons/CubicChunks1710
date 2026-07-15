package com.cardinalstar.cubicchunks.api.worldgen.hwaccel;

import com.cardinalstar.cubicchunks.CubicChunks;

public class RenderdocAPI {

    private static boolean available = false;

    static {
        try {
            System.loadLibrary("renderdoc");
            available = true;
        } catch (UnsatisfiedLinkError e) {
            CubicChunks.LOGGER.warn("Failed to load renderdoc native library", e);
        }
    }

}
