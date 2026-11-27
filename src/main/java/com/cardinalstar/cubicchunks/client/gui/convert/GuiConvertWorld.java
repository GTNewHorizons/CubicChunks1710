package com.cardinalstar.cubicchunks.client.gui.convert;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.resources.I18n;

import com.cardinalstar.cubicchunks.world.convert.WorldSaveFormat;

/**
 * Explains what converting the selected world does and starts it. There is nothing to choose: a vanilla world is
 * converted to cube storage and a cube storage world to vanilla, and block data is carried over as it is.
 */
@ParametersAreNonnullByDefault
public class GuiConvertWorld extends GuiScreen {

    /** Identifies which format the conversion will produce. */
    public enum ConversionTarget {

        CC("cubicchunks.format.cc"),
        VANILLA("cubicchunks.format.vanilla");

        final String langKey;

        ConversionTarget(String langKey) {
            this.langKey = langKey;
        }

        public String getDisplayName() {
            return I18n.format(langKey);
        }

        /** The format a world in {@code format} is converted to, or null when it cannot be converted. */
        @Nullable
        public static ConversionTarget of(WorldSaveFormat format) {
            switch (format) {
                case VANILLA:
                    return CC;
                case CC:
                    return VANILLA;
                default:
                    return null;
            }
        }
    }

    private static final int BTN_BACK = 0;
    private static final int BTN_CONVERT = 1;

    private final GuiScreen parent;
    private final String worldFileName;
    private final File worldDir;
    private final WorldSaveFormat currentFormat;
    @Nullable
    private final ConversionTarget target;

    public GuiConvertWorld(GuiScreen parent, String worldFileName, File worldDir, WorldSaveFormat currentFormat) {
        this.parent = parent;
        this.worldFileName = worldFileName;
        this.worldDir = worldDir;
        this.currentFormat = currentFormat;
        this.target = ConversionTarget.of(currentFormat);
    }

    @Override
    public void initGui() {
        buttonList.clear();
        GuiButton convert = new GuiButton(
            BTN_CONVERT,
            width / 2 - 100,
            height - 52,
            200,
            20,
            I18n.format("cubicchunks.gui.convert.convert"));
        convert.enabled = target != null;
        buttonList.add(convert);
        buttonList.add(
            new GuiButton(
                BTN_BACK,
                width / 2 - 100,
                height - 28,
                200,
                20,
                I18n.format("cubicchunks.gui.convert.back")));
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (button.id == BTN_BACK) {
            mc.displayGuiScreen(parent);
        } else if (button.id == BTN_CONVERT && target != null) {
            mc.displayGuiScreen(new GuiConvertProgress(parent, worldDir, target));
        }
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        drawCenteredString(
            fontRendererObj,
            I18n.format("cubicchunks.gui.convert.title", worldFileName),
            width / 2,
            20,
            0xFFFFFF);

        List<String> paragraphs = new ArrayList<>();
        if (target == null) {
            paragraphs.add(I18n.format("cubicchunks.gui.convert.source", formatName(currentFormat)));
            paragraphs.add(I18n.format("cubicchunks.gui.convert.impossible"));
        } else {
            paragraphs.add(
                I18n.format("cubicchunks.gui.convert.direction", formatName(currentFormat), target.getDisplayName()));
            paragraphs.add(I18n.format("cubicchunks.gui.convert.kept"));
            if (target == ConversionTarget.VANILLA) paragraphs.add(I18n.format("cubicchunks.gui.convert.height"));
            paragraphs.add(I18n.format("cubicchunks.gui.convert.backup"));
        }
        drawParagraphs(this, paragraphs, 44);
        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    /** Draws word-wrapped, centered paragraphs from {@code y} down. */
    static void drawParagraphs(GuiScreen screen, List<String> paragraphs, int y) {
        int wrap = Math.min(screen.width - 40, 360);
        for (String paragraph : paragraphs) {
            for (Object line : screen.mc.fontRenderer.listFormattedStringToWidth(paragraph, wrap)) {
                screen.drawCenteredString(screen.mc.fontRenderer, (String) line, screen.width / 2, y, 0xAAAAAA);
                y += 10;
            }
            y += 6;
        }
    }

    private static String formatName(WorldSaveFormat format) {
        switch (format) {
            case CC:
                return I18n.format("cubicchunks.format.cc");
            case VANILLA:
                return I18n.format("cubicchunks.format.vanilla");
            case MIXED:
                return I18n.format("cubicchunks.format.mixed");
            default:
                return I18n.format("cubicchunks.format.unknown");
        }
    }
}
