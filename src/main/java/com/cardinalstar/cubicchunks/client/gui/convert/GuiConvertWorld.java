package com.cardinalstar.cubicchunks.client.gui.convert;

import java.io.File;

import javax.annotation.ParametersAreNonnullByDefault;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiYesNo;
import net.minecraft.client.gui.GuiYesNoCallback;
import net.minecraft.client.resources.I18n;

import com.cardinalstar.cubicchunks.world.convert.WorldSaveFormat;

/**
 * Format picker screen. The user chooses a target container format and block
 * encoding format, then proceeds to {@link GuiConvertProgress} after confirming
 * a backup prompt.
 */
@ParametersAreNonnullByDefault
public class GuiConvertWorld extends GuiScreen implements GuiYesNoCallback {

    /** Identifies which container format the conversion will produce. */
    public enum ConversionTarget {
        CC(      "cubicchunks.format.cc"),
        VANILLA( "cubicchunks.format.vanilla");

        final String langKey;

        ConversionTarget(String langKey) { this.langKey = langKey; }

        public String getDisplayName() { return I18n.format(langKey); }
    }

    /** Identifies which block encoding the output sections will use. */
    public enum BlockFormat {
        VANILLA("cubicchunks.format.block.vanilla"),
        EID(    "cubicchunks.format.block.eid");

        final String langKey;

        BlockFormat(String langKey) { this.langKey = langKey; }

        public String getDisplayName() { return I18n.format(langKey); }
    }

    /**
     * A button that renders with the highlighted (hover) texture when selected,
     * giving a "pressed in" appearance for radio-style groups.
     *
     * <p>{@code getHoverState} is final in {@link GuiButton}, so we force the hover
     * appearance by faking a mouse position inside the button bounds when selected.
     */
    private static class ToggleButton extends GuiButton {

        private boolean selected;

        ToggleButton(int id, int x, int y, int w, int h, String text) {
            super(id, x, y, w, h, text);
        }

        void setSelected(boolean selected) { this.selected = selected; }

        @Override
        public void drawButton(net.minecraft.client.Minecraft mc, int mouseX, int mouseY) {
            super.drawButton(mc, selected ? xPosition : mouseX, selected ? yPosition : mouseY);
        }
    }

    private static final int BTN_CONVERT       = 1;
    private static final int BTN_BACK          = 0;
    private static final int BTN_CC            = 10;
    private static final int BTN_VANILLA       = 11;
    private static final int BTN_BLOCK_VANILLA = 12;
    private static final int BTN_BLOCK_EID     = 13;

    private final GuiScreen parent;
    private final String worldFileName;
    private final File worldDir;
    private final WorldSaveFormat currentFormat;

    private ConversionTarget selectedTarget = null;
    private BlockFormat selectedBlockFormat = BlockFormat.VANILLA;

    private GuiButton convertButton;
    private ToggleButton ccButton;
    private ToggleButton vanillaButton;
    private ToggleButton blockVanillaButton;
    private ToggleButton blockEidButton;

    public GuiConvertWorld(GuiScreen parent, String worldFileName, File worldDir,
                            WorldSaveFormat currentFormat) {
        this.parent = parent;
        this.worldFileName = worldFileName;
        this.worldDir = worldDir;
        this.currentFormat = currentFormat;
    }

    @Override
    public void initGui() {
        buttonList.clear();
        ccButton     = null;
        vanillaButton = null;

        convertButton = new GuiButton(BTN_CONVERT, width / 2 - 100, height - 52, 200, 20,
            I18n.format("cubicchunks.gui.convert.convert"));
        convertButton.enabled = selectedTarget != null;
        buttonList.add(convertButton);

        buttonList.add(new GuiButton(BTN_BACK, width / 2 - 100, height - 28, 200, 20,
            I18n.format("cubicchunks.gui.convert.back")));

        int targetY = height / 2 - 32;

        ccButton = new ToggleButton(BTN_CC, width / 2 - 100, targetY, 200, 20,
            ConversionTarget.CC.getDisplayName());
        ccButton.setSelected(currentFormat == WorldSaveFormat.CC);
        buttonList.add(ccButton);
        targetY += 24;

        vanillaButton = new ToggleButton(BTN_VANILLA, width / 2 - 100, targetY, 200, 20,
            ConversionTarget.VANILLA.getDisplayName());
        vanillaButton.setSelected(currentFormat == WorldSaveFormat.VANILLA);
        buttonList.add(vanillaButton);

        int blockY = height / 2 + 36;
        blockVanillaButton = new ToggleButton(BTN_BLOCK_VANILLA, width / 2 - 100, blockY, 200, 20,
            BlockFormat.VANILLA.getDisplayName());
        blockVanillaButton.setSelected(selectedBlockFormat == BlockFormat.VANILLA);
        buttonList.add(blockVanillaButton);

        blockEidButton = new ToggleButton(BTN_BLOCK_EID, width / 2 - 100, blockY + 24, 200, 20,
            BlockFormat.EID.getDisplayName());
        blockEidButton.setSelected(selectedBlockFormat == BlockFormat.EID);
        buttonList.add(blockEidButton);
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (button.id == BTN_BACK) {
            mc.displayGuiScreen(parent);
            return;
        }

        if (button.id == BTN_CC) {
            selectedTarget = ConversionTarget.CC;
            convertButton.enabled = true;
            ccButton.setSelected(true);
            if (vanillaButton != null) vanillaButton.setSelected(false);
            return;
        }

        if (button.id == BTN_VANILLA) {
            selectedTarget = ConversionTarget.VANILLA;
            convertButton.enabled = true;
            if (ccButton != null) ccButton.setSelected(false);
            vanillaButton.setSelected(true);
            return;
        }

        if (button.id == BTN_BLOCK_VANILLA) {
            selectedBlockFormat = BlockFormat.VANILLA;
            blockVanillaButton.setSelected(true);
            blockEidButton.setSelected(false);
            return;
        }

        if (button.id == BTN_BLOCK_EID) {
            selectedBlockFormat = BlockFormat.EID;
            blockVanillaButton.setSelected(false);
            blockEidButton.setSelected(true);
            return;
        }

        if (button.id == BTN_CONVERT && selectedTarget != null) {
            String prompt = I18n.format("cubicchunks.gui.convert.backup_prompt");
            String confirm = I18n.format("cubicchunks.gui.convert.backup_yes");
            mc.displayGuiScreen(new GuiYesNo(this, prompt, "", confirm,
                I18n.format("gui.cancel"), 0));
        }
    }

    @Override
    public void confirmClicked(boolean result, int id) {
        if (result && selectedTarget != null) {
            mc.displayGuiScreen(new GuiConvertProgress(parent, worldDir, currentFormat, selectedTarget, selectedBlockFormat));
        } else {
            mc.displayGuiScreen(this);
        }
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();

        drawCenteredString(fontRendererObj,
            I18n.format("cubicchunks.gui.convert.title", worldFileName),
            width / 2, 20, 0xFFFFFF);

        String sourceLabel = I18n.format("cubicchunks.gui.convert.source", formatName(currentFormat));
        drawCenteredString(fontRendererObj, sourceLabel, width / 2, 36, 0xAAAAAA);

        drawCenteredString(fontRendererObj,
            I18n.format("cubicchunks.gui.convert.target_label"),
            width / 2, height / 2 - 48, 0xFFFFFF);

        drawCenteredString(fontRendererObj,
            I18n.format("cubicchunks.gui.convert.block_format_label"),
            width / 2, height / 2 + 20, 0xFFFFFF);

        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    private static String formatName(WorldSaveFormat format) {
        switch (format) {
            case CC:      return I18n.format("cubicchunks.format.cc");
            case VANILLA: return I18n.format("cubicchunks.format.vanilla");
            case MIXED:   return "Mixed";
            default:      return "Unknown";
        }
    }
}
