package com.cardinalstar.cubicchunks.client.gui.worldselect;

import java.io.File;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.resources.I18n;
import net.minecraft.world.storage.ISaveHandler;

import com.cardinalstar.cubicchunks.world.convert.WorldFormatDetector;
import com.cardinalstar.cubicchunks.world.convert.WorldSaveFormat;

/**
 * Draws and caches the format badge ([CC] / [Vanilla] / [Mixed]) rendered in
 * each world-select list slot.
 *
 * <p>Detection results are cached by world folder name and cleared whenever the
 * world list is refreshed (see {@link #invalidate()}).
 */
public final class WorldFormatBadgeRenderer {

    private static final Map<String, WorldSaveFormat> CACHE = new ConcurrentHashMap<>();

    private static final int COLOR_CC      = 0x55FFFF;
    private static final int COLOR_VANILLA = 0xFFFF55;
    private static final int COLOR_MIXED   = 0xFF5500;

    private WorldFormatBadgeRenderer() {}

    /** Clears cached detection results. Call when the world list changes. */
    public static void invalidate() {
        CACHE.clear();
    }

    /**
     * Returns the cached format for {@code worldFileName}, detecting it on first call.
     *
     * @param mc           Minecraft instance (used to resolve the saves directory)
     * @param worldFileName folder name of the world save (not the display name)
     */
    public static WorldSaveFormat getCachedFormat(Minecraft mc, String worldFileName) {
        return CACHE.computeIfAbsent(worldFileName, name -> {
            ISaveHandler handler = mc.getSaveLoader().getSaveLoader(name, false);
            File worldDir = handler.getWorldDirectory();
            return WorldFormatDetector.detect(worldDir);
        });
    }

    /**
     * Draws the format badge at the right edge of a world-list slot.
     *
     * @param fontRenderer renderer to measure and draw the badge text
     * @param format       detected format for the slot
     * @param slotX        left edge of the slot
     * @param slotY        top edge of the slot
     * @param slotWidth    width of the slot
     */
    public static void drawBadge(FontRenderer fontRenderer, WorldSaveFormat format,
                                  int slotX, int slotY, int slotWidth) {
        String badge = getBadgeText(format);
        if (badge == null) return;

        int color = getBadgeColor(format);
        int badgeWidth = fontRenderer.getStringWidth(badge);
        fontRenderer.drawStringWithShadow(badge, slotX + slotWidth - badgeWidth - 4, slotY + 1, color);
    }

    private static String getBadgeText(WorldSaveFormat format) {
        switch (format) {
            case CC:      return I18n.format("cubicchunks.gui.worldselect.badge.cc");
            case VANILLA: return I18n.format("cubicchunks.gui.worldselect.badge.vanilla");
            case MIXED:   return I18n.format("cubicchunks.gui.worldselect.badge.mixed");
            default:      return null;
        }
    }

    private static int getBadgeColor(WorldSaveFormat format) {
        switch (format) {
            case CC:      return COLOR_CC;
            case VANILLA: return COLOR_VANILLA;
            case MIXED:   return COLOR_MIXED;
            default:      return 0xFFFFFF;
        }
    }
}
