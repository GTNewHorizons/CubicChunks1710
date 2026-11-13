package com.cardinalstar.cubicchunks.worldgen.config.gui;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiListExtended;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.resources.I18n;
import net.minecraft.util.EnumChatFormatting;

import org.lwjgl.input.Keyboard;

import com.cardinalstar.cubicchunks.worldgen.config.BlockRef;
import com.cardinalstar.cubicchunks.worldgen.config.gui.BlockVariants.Variant;

import cpw.mods.fml.client.config.GuiButtonExt;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Searchable list of block variants (see {@link BlockVariants}), matched by name or registry id. Confirming (Done,
 * Enter or double-click) passes the selection to {@code onSelect} and returns to the parent screen.
 */
@SideOnly(Side.CLIENT)
public class GuiBlockSearch extends GuiScreen {

    private static final int LIST_WIDTH = 300;
    private static final long DOUBLE_CLICK_MS = 250;

    private final GuiScreen parentScreen;
    private final Consumer<BlockRef> onSelect;

    private BlockRef selected;
    private GuiTextField searchField;
    private BlockList blockList;
    private GuiButtonExt btnDone;

    public GuiBlockSearch(GuiScreen parentScreen, BlockRef currentValue, Consumer<BlockRef> onSelect) {
        this.parentScreen = parentScreen;
        this.selected = currentValue;
        this.onSelect = onSelect;
    }

    @Override
    public void initGui() {
        String query = searchField == null ? "" : searchField.getText();
        searchField = new GuiTextField(fontRendererObj, width / 2 - LIST_WIDTH / 2, 22, LIST_WIDTH, 18);
        searchField.setMaxStringLength(256);
        searchField.setText(query);
        searchField.setFocused(true);

        blockList = new BlockList(mc, width, height, 46, height - 36, 20);
        blockList.refreshFilter(query);

        btnDone = new GuiButtonExt(0, width / 2 - 154, height - 28, 150, 20, I18n.format("gui.done"));
        buttonList.add(btnDone);
        buttonList.add(new GuiButtonExt(1, width / 2 + 4, height - 28, 150, 20, I18n.format("gui.cancel")));
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        // also handles wheel scrolling and scrollbar dragging
        blockList.drawScreen(mouseX, mouseY, partialTicks);
        drawCenteredString(
            fontRendererObj,
            I18n.format("cubicchunks.wgconfig.block_search.title"),
            width / 2,
            8,
            0xFFFFFF);
        searchField.drawTextBox();
        btnDone.enabled = selected != null;
        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (button.id == 0) {
            confirm();
        } else if (button.id == 1) {
            mc.displayGuiScreen(parentScreen);
        }
    }

    @Override
    protected void keyTyped(char eventChar, int eventKey) {
        if (eventKey == Keyboard.KEY_ESCAPE) {
            mc.displayGuiScreen(parentScreen);
        } else if (eventKey == Keyboard.KEY_RETURN || eventKey == Keyboard.KEY_NUMPADENTER) {
            confirm();
        } else if (searchField.textboxKeyTyped(eventChar, eventKey)) {
            blockList.refreshFilter(searchField.getText());
        }
    }

    // Mirrors GuiConfig: rows get the click first; the release must reach the list to re-enable scrollbar dragging
    @Override
    protected void mouseClicked(int x, int y, int mouseEvent) {
        if (mouseEvent != 0 || !blockList.func_148179_a(x, y, mouseEvent)) {
            searchField.mouseClicked(x, y, mouseEvent);
            super.mouseClicked(x, y, mouseEvent);
        }
    }

    @Override
    protected void mouseMovedOrUp(int x, int y, int mouseEvent) {
        if (mouseEvent != 0 || !blockList.func_148181_b(x, y, mouseEvent)) {
            super.mouseMovedOrUp(x, y, mouseEvent);
        }
    }

    @Override
    public void updateScreen() {
        searchField.updateCursorCounter();
    }

    private void confirm() {
        if (selected == null) return;
        onSelect.accept(selected);
        mc.displayGuiScreen(parentScreen);
    }

    private class BlockList extends GuiListExtended {

        private final List<BlockRow> rows = new ArrayList<>();
        private long lastClickTime;

        BlockList(Minecraft mc, int width, int height, int top, int bottom, int slotHeight) {
            super(mc, width, height, top, bottom, slotHeight);
        }

        void refreshFilter(String query) {
            rows.clear();
            String q = query.toLowerCase()
                .trim();
            for (Variant variant : BlockVariants.all()) {
                if (q.isEmpty() || variant.matches(q)) rows.add(new BlockRow(variant));
            }
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
        protected boolean isSelected(int index) {
            return rows.get(index).variant.ref.equals(selected);
        }

        @Override
        public int getListWidth() {
            return LIST_WIDTH;
        }

        @Override
        protected int getScrollBarX() {
            return width / 2 + LIST_WIDTH / 2 + 4;
        }

        private class BlockRow implements IGuiListEntry {

            final Variant variant;

            BlockRow(Variant variant) {
                this.variant = variant;
            }

            @Override
            public void drawEntry(int slotIndex, int x, int y, int listWidth, int slotHeight, Tessellator tessellator,
                int mouseX, int mouseY, boolean isHovered) {
                BlockVariants.renderIcon(variant.ref, x + 2, y + 1);

                boolean highlighted = isHovered || variant.ref.equals(selected);
                int textY = y + slotHeight / 2 - fontRendererObj.FONT_HEIGHT / 2 - 1;
                fontRendererObj.drawString(
                    (highlighted ? EnumChatFormatting.WHITE : EnumChatFormatting.GRAY) + variant.name,
                    x + 22,
                    textY,
                    0xFFFFFF);

                String id = variant.ref.toString();
                fontRendererObj.drawString(
                    EnumChatFormatting.DARK_GRAY + id,
                    x + listWidth - 6 - fontRendererObj.getStringWidth(id),
                    textY,
                    0xFFFFFF);
            }

            @Override
            public boolean mousePressed(int index, int x, int y, int mouseEvent, int relativeX, int relativeY) {
                long now = Minecraft.getSystemTime();
                boolean doubleClick = variant.ref.equals(selected) && now - lastClickTime < DOUBLE_CLICK_MS;
                lastClickTime = now;
                selected = variant.ref;
                if (doubleClick) confirm();
                return true;
            }

            @Override
            public void mouseReleased(int index, int x, int y, int mouseEvent, int relativeX, int relativeY) {}
        }
    }
}
