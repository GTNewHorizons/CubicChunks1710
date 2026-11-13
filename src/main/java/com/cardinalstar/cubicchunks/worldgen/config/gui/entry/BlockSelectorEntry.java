package com.cardinalstar.cubicchunks.worldgen.config.gui.entry;

import java.util.Objects;

import net.minecraft.client.renderer.Tessellator;

import com.cardinalstar.cubicchunks.worldgen.config.BlockRef;
import com.cardinalstar.cubicchunks.worldgen.config.gui.BlockVariants;
import com.cardinalstar.cubicchunks.worldgen.config.gui.GuiBlockSearch;
import com.cardinalstar.cubicchunks.worldgen.config.gui.element.WGConfigElement;

import cpw.mods.fml.client.config.GuiConfig;
import cpw.mods.fml.client.config.GuiConfigEntries;
import cpw.mods.fml.client.config.GuiConfigEntries.ButtonEntry;
import cpw.mods.fml.client.config.IConfigElement;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Config entry for a {@link BlockRef}. The value button shows the block's icon and name, and opens
 * {@link GuiBlockSearch}. For items that allow auto-detection the value may be null; resetting to the default gets
 * back to it.
 */
@SideOnly(Side.CLIENT)
public class BlockSelectorEntry extends ButtonEntry {

    private final WGConfigElement<BlockRef> element;
    private final BlockRef beforeValue;
    private BlockRef currentValue;

    @SuppressWarnings({ "rawtypes", "unchecked" })
    public BlockSelectorEntry(GuiConfig owningScreen, GuiConfigEntries owningEntryList, IConfigElement configElement) {
        super(owningScreen, owningEntryList, configElement);
        this.element = (WGConfigElement<BlockRef>) configElement;
        this.beforeValue = element.getValue();
        this.currentValue = beforeValue;
        updateValueButtonText();
    }

    @Override
    public void updateValueButtonText() {
        btnValue.displayString = BlockVariants.label(currentValue);
    }

    @Override
    public void valueButtonPressed(int slotIndex) {
        mc.displayGuiScreen(new GuiBlockSearch(owningScreen, currentValue, this::setBlockFromSearch));
    }

    private void setBlockFromSearch(BlockRef block) {
        if (enabled()) {
            currentValue = block;
            updateValueButtonText();
        }
    }

    @Override
    public void drawEntry(int slotIndex, int x, int y, int listWidth, int slotHeight, Tessellator tessellator,
        int mouseX, int mouseY, boolean isSelected) {
        super.drawEntry(slotIndex, x, y, listWidth, slotHeight, tessellator, mouseX, mouseY, isSelected);
        BlockVariants.renderIcon(currentValue, btnValue.xPosition + 2, y + 1);
    }

    @Override
    public boolean isDefault() {
        return Objects.equals(currentValue, element.getDefaultValue());
    }

    @Override
    public void setToDefault() {
        if (enabled()) {
            currentValue = element.getDefaultValue();
            updateValueButtonText();
        }
    }

    @Override
    public boolean isChanged() {
        return !Objects.equals(currentValue, beforeValue);
    }

    @Override
    public void undoChanges() {
        if (enabled()) {
            currentValue = beforeValue;
            updateValueButtonText();
        }
    }

    @Override
    public boolean saveConfigElement() {
        if (enabled() && isChanged()) {
            element.set(currentValue);
            return element.requiresMcRestart();
        }
        return false;
    }

    @Override
    public Object getCurrentValue() {
        return currentValue;
    }

    @Override
    public Object[] getCurrentValues() {
        return new Object[] { currentValue };
    }
}
