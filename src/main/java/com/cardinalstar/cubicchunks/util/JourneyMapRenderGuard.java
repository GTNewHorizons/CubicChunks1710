package com.cardinalstar.cubicchunks.util;

import java.awt.image.BufferedImage;

public final class JourneyMapRenderGuard {

    private static final int VOID_ARGB = 0xFF110C19;
    private static final ThreadLocal<RenderContext> CONTEXT = new ThreadLocal<>();

    private JourneyMapRenderGuard() {}

    public static void begin(Object mapType) {
        boolean underground = false;
        try {
            underground = (Boolean) mapType.getClass()
                .getMethod("isUnderground")
                .invoke(mapType);
        } catch (ReflectiveOperationException ignored) {}
        CONTEXT.set(new RenderContext(underground));
    }

    public static void end() {
        CONTEXT.remove();
    }

    public static boolean shouldDiscard(BufferedImage image) {
        RenderContext context = CONTEXT.get();
        if (context == null || context.underground) {
            return false;
        }

        for (int x = 0; x < image.getWidth(); x++) {
            for (int z = 0; z < image.getHeight(); z++) {
                int argb = image.getRGB(x, z);
                if (argb == VOID_ARGB || argb >>> 24 == 0) {
                    return true;
                }
            }
        }
        return false;
    }

    private static final class RenderContext {

        private final boolean underground;

        private RenderContext(boolean underground) {
            this.underground = underground;
        }
    }
}
