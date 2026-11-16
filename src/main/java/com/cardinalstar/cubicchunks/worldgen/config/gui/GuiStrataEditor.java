package com.cardinalstar.cubicchunks.worldgen.config.gui;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiListExtended;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.resources.I18n;

import org.lwjgl.input.Keyboard;

import com.cardinalstar.cubicchunks.worldgen.config.BlockRef;
import com.cardinalstar.cubicchunks.worldgen.config.Stratum;

import cpw.mods.fml.client.config.GuiButtonExt;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Edits a list of strata, one row per layer: {@code [block] [100% at Y] [0% at Y] [-]}.
 * <p>
 * Rows keep their own state, so it survives the trip to the block picker and adding or removing rows. Nothing is
 * passed back until Done.
 */
@SideOnly(Side.CLIENT)
public class GuiStrataEditor extends GuiScreen {

    private static final int ROW_WIDTH = 360;
    private static final int Y_FIELD_WIDTH = 60;
    private static final int BUTTON_HEIGHT = 18;
    private static final int GAP = 4;
    private static final BlockRef NEW_LAYER_BLOCK = new BlockRef("minecraft:stone", 0);

    private final GuiScreen parentScreen;
    private final boolean enabled;
    private final Consumer<List<Stratum>> onDone;
    private final List<Row> rows = new ArrayList<>();

    private RowList rowList;
    private GuiButtonExt btnDone;
    /** Counts drawn frames, so clicks only reach rows that are currently on screen. */
    private int frame;

    public GuiStrataEditor(GuiScreen parentScreen, List<Stratum> strata, boolean enabled,
        Consumer<List<Stratum>> onDone) {
        this.parentScreen = parentScreen;
        this.enabled = enabled;
        this.onDone = onDone;
        for (Stratum stratum : strata) {
            rows.add(new Row(stratum.block, stratum.full, stratum.none));
        }
    }

    @Override
    public void initGui() {
        // Rows are kept: this runs again when returning from the block picker
        rowList = new RowList(mc, width, height, 44, height - 36, 20);

        GuiButtonExt btnAdd = new GuiButtonExt(
            0,
            width / 2 - 155,
            height - 28,
            100,
            20,
            I18n.format("cubicchunks.wgconfig.strata.add"));
        btnAdd.enabled = enabled;
        buttonList.add(btnAdd);
        btnDone = new GuiButtonExt(1, width / 2 - 50, height - 28, 100, 20, I18n.format("gui.done"));
        buttonList.add(btnDone);
        buttonList.add(new GuiButtonExt(2, width / 2 + 55, height - 28, 100, 20, I18n.format("gui.cancel")));
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        frame++;
        drawDefaultBackground();
        rowList.drawScreen(mouseX, mouseY, partialTicks);

        drawCenteredString(fontRendererObj, I18n.format("cubicchunks.wgconfig.strata.title"), width / 2, 8, 0xFFFFFF);

        int left = rowLeft();
        int headerY = 32;
        int gray = 0xA0A0A0;
        drawString(fontRendererObj, I18n.format("cubicchunks.wgconfig.strata.block"), left, headerY, gray);
        drawString(fontRendererObj, I18n.format("cubicchunks.wgconfig.strata.full"), fullFieldX(), headerY, gray);
        drawString(fontRendererObj, I18n.format("cubicchunks.wgconfig.strata.none"), noneFieldX(), headerY, gray);

        if (rows.isEmpty()) {
            drawCenteredString(
                fontRendererObj,
                I18n.format("cubicchunks.wgconfig.strata.empty"),
                width / 2,
                (rowList.top + rowList.bottom) / 2,
                gray);
        }

        boolean allValid = true;
        for (Row row : rows) {
            allValid &= row.isValid();
        }
        btnDone.enabled = allValid;

        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (button.id == 0) {
            rows.add(new Row(NEW_LAYER_BLOCK, 0, 0));
        } else if (button.id == 1) {
            List<Stratum> strata = new ArrayList<>(rows.size());
            for (Row row : rows) {
                strata.add(row.toStratum());
            }
            onDone.accept(Collections.unmodifiableList(strata));
            mc.displayGuiScreen(parentScreen);
        } else if (button.id == 2) {
            mc.displayGuiScreen(parentScreen);
        }
    }

    @Override
    protected void keyTyped(char eventChar, int eventKey) {
        if (eventKey == Keyboard.KEY_ESCAPE) {
            mc.displayGuiScreen(parentScreen);
            return;
        }
        if (!enabled) return;
        for (Row row : rows) {
            row.keyTyped(eventChar, eventKey);
        }
    }

    // Mirrors GuiConfig: rows get the click first; the release must reach the list to re-enable scrollbar dragging
    @Override
    protected void mouseClicked(int x, int y, int mouseEvent) {
        if (mouseEvent != 0 || !rowList.func_148179_a(x, y, mouseEvent)) {
            for (Row row : rows) {
                row.mouseClicked(x, y, mouseEvent);
            }
            super.mouseClicked(x, y, mouseEvent);
        }
    }

    @Override
    protected void mouseMovedOrUp(int x, int y, int mouseEvent) {
        if (mouseEvent != 0 || !rowList.func_148181_b(x, y, mouseEvent)) {
            super.mouseMovedOrUp(x, y, mouseEvent);
        }
    }

    @Override
    public void updateScreen() {
        for (Row row : rows) {
            row.txtFull.updateCursorCounter();
            row.txtNone.updateCursorCounter();
        }
    }

    private int rowLeft() {
        return width / 2 - ROW_WIDTH / 2;
    }

    private int blockButtonWidth() {
        return ROW_WIDTH - 2 * (Y_FIELD_WIDTH + GAP) - (BUTTON_HEIGHT + GAP);
    }

    private int fullFieldX() {
        return rowLeft() + blockButtonWidth() + GAP;
    }

    private int noneFieldX() {
        return fullFieldX() + Y_FIELD_WIDTH + GAP;
    }

    private class RowList extends GuiListExtended {

        RowList(Minecraft mc, int width, int height, int top, int bottom, int slotHeight) {
            super(mc, width, height, top, bottom, slotHeight);
        }

        @Override
        public IGuiListEntry getListEntry(int index) {
            return rows.get(index);
        }

        @Override
        protected int getSize() {
            return rows.size();
        }

        @Override
        public int getListWidth() {
            return ROW_WIDTH;
        }

        @Override
        protected int getScrollBarX() {
            return width / 2 + ROW_WIDTH / 2 + 6;
        }
    }

    private class Row implements GuiListExtended.IGuiListEntry {

        BlockRef block;
        final GuiButtonExt btnBlock = new GuiButtonExt(0, 0, 0, 0, BUTTON_HEIGHT, "");
        final GuiTextField txtFull = new GuiTextField(Minecraft.getMinecraft().fontRenderer, 0, 0, 0, 16);
        final GuiTextField txtNone = new GuiTextField(Minecraft.getMinecraft().fontRenderer, 0, 0, 0, 16);
        final GuiButtonExt btnRemove = new GuiButtonExt(0, 0, 0, BUTTON_HEIGHT, BUTTON_HEIGHT, "-");
        int drawnFrame = -1;

        Row(BlockRef block, int full, int none) {
            this.block = block;
            txtFull.setMaxStringLength(11);
            txtNone.setMaxStringLength(11);
            IntTextFields.setText(txtFull, String.valueOf(full));
            IntTextFields.setText(txtNone, String.valueOf(none));
        }

        boolean isValid() {
            return IntTextFields.parse(txtFull) != null && IntTextFields.parse(txtNone) != null;
        }

        Stratum toStratum() {
            return new Stratum(block, IntTextFields.parse(txtFull), IntTextFields.parse(txtNone));
        }

        @Override
        public void drawEntry(int slotIndex, int x, int y, int listWidth, int slotHeight, Tessellator tessellator,
            int mouseX, int mouseY, boolean isHovered) {
            drawnFrame = frame;
            int left = rowLeft();

            btnBlock.xPosition = left;
            btnBlock.yPosition = y;
            btnBlock.width = blockButtonWidth();
            btnBlock.displayString = BlockVariants.label(block);
            btnBlock.enabled = enabled;
            btnBlock.drawButton(mc, mouseX, mouseY);
            BlockVariants.renderIcon(block, left + 2, y + 1);

            drawField(txtFull, fullFieldX(), y);
            drawField(txtNone, noneFieldX(), y);

            btnRemove.xPosition = left + ROW_WIDTH - BUTTON_HEIGHT;
            btnRemove.yPosition = y;
            btnRemove.enabled = enabled;
            btnRemove.drawButton(mc, mouseX, mouseY);
        }

        private void drawField(GuiTextField field, int x, int y) {
            field.xPosition = x + 1;
            field.yPosition = y + 1;
            field.width = Y_FIELD_WIDTH - 2;
            field.setEnabled(enabled);
            field.setTextColor(
                IntTextFields.parse(field) != null ? IntTextFields.VALID_COLOR : IntTextFields.INVALID_COLOR);
            field.drawTextBox();
        }

        @Override
        public boolean mousePressed(int index, int x, int y, int mouseEvent, int relativeX, int relativeY) {
            if (btnBlock.mousePressed(mc, x, y)) {
                btnBlock.func_146113_a(mc.getSoundHandler());
                mc.displayGuiScreen(new GuiBlockSearch(GuiStrataEditor.this, block, picked -> block = picked));
                return true;
            }
            if (btnRemove.mousePressed(mc, x, y)) {
                btnRemove.func_146113_a(mc.getSoundHandler());
                rows.remove(this);
                return true;
            }
            return false;
        }

        @Override
        public void mouseReleased(int index, int x, int y, int mouseEvent, int relativeX, int relativeY) {
            btnBlock.mouseReleased(x, y);
            btnRemove.mouseReleased(x, y);
        }

        void mouseClicked(int x, int y, int mouseEvent) {
            // Fields of rows scrolled out of view keep their last position, which may now be under another row
            if (drawnFrame != frame) {
                txtFull.setFocused(false);
                txtNone.setFocused(false);
                return;
            }
            txtFull.mouseClicked(x, y, mouseEvent);
            txtNone.mouseClicked(x, y, mouseEvent);
        }

        void keyTyped(char eventChar, int eventKey) {
            if (txtFull.isFocused()) IntTextFields.keyTyped(txtFull, eventChar, eventKey);
            if (txtNone.isFocused()) IntTextFields.keyTyped(txtNone, eventChar, eventKey);
        }
    }
}
