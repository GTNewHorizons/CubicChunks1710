package com.cardinalstar.cubicchunks.worldgen.config.gui.entry;

import java.util.Objects;

import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.util.EnumChatFormatting;

import com.cardinalstar.cubicchunks.api.IntRange;
import com.cardinalstar.cubicchunks.worldgen.config.gui.IntTextFields;
import com.cardinalstar.cubicchunks.worldgen.config.gui.element.IntRangeConfigElement;

import cpw.mods.fml.client.config.GuiButtonExt;
import cpw.mods.fml.client.config.GuiConfig;
import cpw.mods.fml.client.config.GuiConfigEntries;
import cpw.mods.fml.client.config.GuiConfigEntries.ListEntryBase;
import cpw.mods.fml.client.config.GuiUtils;
import cpw.mods.fml.client.config.IConfigElement;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Entry for an {@link IntRangeConfigElement}: {@code [∞][min] .. [∞][max]}.
 * The ∞ buttons toggle an end between a number and unbounded (the limit), and are hidden when the element doesn't
 * allow it. An unbounded end shows as ∞ followed by the limit in gray.
 */
@SideOnly(Side.CLIENT)
public class IntRangeEntry extends ListEntryBase {

    private static final int TOGGLE_WIDTH = 18;
    private static final String SEPARATOR = "..";

    private final IntRangeConfigElement rangeElement;
    private final IntRange beforeValue;
    private final Bound lower;
    private final Bound upper;

    @SuppressWarnings("rawtypes")
    public IntRangeEntry(GuiConfig owningScreen, GuiConfigEntries owningEntryList, IConfigElement configElement) {
        super(owningScreen, owningEntryList, configElement);
        this.rangeElement = (IntRangeConfigElement) configElement;
        this.lower = new Bound(rangeElement.limits.getMin(), "-\u221E");
        this.upper = new Bound(rangeElement.limits.getMax(), "+\u221E");
        this.beforeValue = rangeElement.getValue();
        applyRange(beforeValue);
    }

    @Override
    public void drawEntry(int slotIndex, int x, int y, int listWidth, int slotHeight, Tessellator tessellator,
        int mouseX, int mouseY, boolean isSelected) {
        super.drawEntry(slotIndex, x, y, listWidth, slotHeight, tessellator, mouseX, mouseY, isSelected);

        int sepWidth = mc.fontRenderer.getStringWidth(SEPARATOR) + 8;
        int half = (owningEntryList.controlWidth - sepWidth) / 2;
        int left = owningEntryList.controlX;

        lower.draw(left, y, half, mouseX, mouseY);
        mc.fontRenderer.drawString(
            EnumChatFormatting.GRAY + SEPARATOR,
            left + half + 4,
            y + slotHeight / 2 - mc.fontRenderer.FONT_HEIGHT / 2,
            0xFFFFFF);
        upper.draw(left + half + sepWidth, y, half, mouseX, mouseY);
    }

    @Override
    public boolean mousePressed(int index, int x, int y, int mouseEvent, int relativeX, int relativeY) {
        if (lower.toggleClicked(x, y) || upper.toggleClicked(x, y)) {
            validate();
            return true;
        }
        return super.mousePressed(index, x, y, mouseEvent, relativeX, relativeY);
    }

    @Override
    public void mouseReleased(int index, int x, int y, int mouseEvent, int relativeX, int relativeY) {
        super.mouseReleased(index, x, y, mouseEvent, relativeX, relativeY);
        lower.btnToggle.mouseReleased(x, y);
        upper.btnToggle.mouseReleased(x, y);
    }

    @Override
    public void mouseClicked(int x, int y, int mouseEvent) {
        lower.txtValue.mouseClicked(x, y, mouseEvent);
        upper.txtValue.mouseClicked(x, y, mouseEvent);
    }

    @Override
    public void keyTyped(char eventChar, int eventKey) {
        if (!enabled()) return;
        lower.keyTyped(eventChar, eventKey);
        upper.keyTyped(eventChar, eventKey);
        validate();
    }

    @Override
    public void updateCursorCounter() {
        lower.txtValue.updateCursorCounter();
        upper.txtValue.updateCursorCounter();
    }

    @Override
    public boolean isDefault() {
        return Objects.equals(currentRange(), rangeElement.getDefaultValue());
    }

    @Override
    public void setToDefault() {
        if (enabled()) applyRange(rangeElement.getDefaultValue());
    }

    @Override
    public boolean isChanged() {
        return !Objects.equals(currentRange(), beforeValue);
    }

    @Override
    public void undoChanges() {
        if (enabled()) applyRange(beforeValue);
    }

    @Override
    @SuppressWarnings("unchecked")
    public boolean saveConfigElement() {
        if (enabled() && isChanged() && isValidValue) {
            configElement.set(currentRange());
            return configElement.requiresMcRestart();
        }
        return false;
    }

    @Override
    public Object getCurrentValue() {
        IntRange range = currentRange();
        return range == null ? "" : rangeElement.format(range);
    }

    @Override
    public Object[] getCurrentValues() {
        return new Object[] { getCurrentValue() };
    }

    /** The edited range, or null if either end doesn't parse or is out of limits. */
    private IntRange currentRange() {
        Integer min = lower.value();
        Integer max = upper.value();
        if (min == null || max == null) return null;
        return new IntRange(min, max);
    }

    private void applyRange(IntRange range) {
        lower.setValue(range.getMin());
        upper.setValue(range.getMax());
        validate();
    }

    private void validate() {
        IntRange range = currentRange();
        isValidValue = range != null && range.getMin() <= range.getMax();
    }

    /** One end of the range: an optional unbounded toggle plus a number field. */
    private class Bound {

        final int unboundedValue;
        final String unboundedText;
        final GuiButtonExt btnToggle = new GuiButtonExt(0, 0, 0, TOGGLE_WIDTH, 18, "∞");
        final GuiTextField txtValue = new GuiTextField(mc.fontRenderer, 0, 0, 0, 16);

        boolean unbounded;
        /** Number text, kept while unbounded so toggling back restores it. */
        String numberText = "0";

        Bound(int unboundedValue, String unboundedText) {
            this.unboundedValue = unboundedValue;
            this.unboundedText = unboundedText;
            txtValue.setMaxStringLength(11);
        }

        void setValue(int value) {
            unbounded = rangeElement.allowUnbounded && value == unboundedValue;
            if (!unbounded) numberText = String.valueOf(value);
            refreshField();
        }

        Integer value() {
            if (unbounded) return unboundedValue;
            try {
                int value = Integer.parseInt(
                    txtValue.getText()
                        .trim());
                IntRange limits = rangeElement.limits;
                return value >= limits.getMin() && value <= limits.getMax() ? value : null;
            } catch (NumberFormatException e) {
                return null;
            }
        }

        boolean toggleClicked(int x, int y) {
            if (!rangeElement.allowUnbounded || !btnToggle.mousePressed(mc, x, y)) return false;
            btnToggle.func_146113_a(mc.getSoundHandler());
            if (!unbounded) numberText = txtValue.getText();
            unbounded = !unbounded;
            refreshField();
            return true;
        }

        void keyTyped(char eventChar, int eventKey) {
            if (!unbounded) IntTextFields.keyTyped(txtValue, eventChar, eventKey);
        }

        void refreshField() {
            // While unbounded, draw() shows the infinity sign and limit over the empty field
            IntTextFields.setText(txtValue, unbounded ? "" : numberText);
            txtValue.setEnabled(!unbounded);
            btnToggle.packedFGColour = unbounded ? GuiUtils.getColorCode('2', true) : 0;
        }

        void draw(int x, int y, int width, int mouseX, int mouseY) {
            int fieldX = x;
            int fieldWidth = width;
            if (rangeElement.allowUnbounded) {
                btnToggle.xPosition = x;
                btnToggle.yPosition = y;
                btnToggle.enabled = enabled();
                btnToggle.drawButton(mc, mouseX, mouseY);
                fieldX += TOGGLE_WIDTH + 2;
                fieldWidth -= TOGGLE_WIDTH + 2;
            }
            txtValue.xPosition = fieldX + 1;
            txtValue.yPosition = y + 1;
            txtValue.width = fieldWidth - 2;
            txtValue.setEnabled(enabled() && !unbounded);
            txtValue.drawTextBox();

            if (unbounded) {
                String text = unboundedText + EnumChatFormatting.GRAY + " (" + unboundedValue + ")";
                mc.fontRenderer.drawString(
                    mc.fontRenderer.trimStringToWidth(text, txtValue.width - 8),
                    txtValue.xPosition + 4,
                    txtValue.yPosition + (txtValue.height - 8) / 2,
                    IntTextFields.VALID_COLOR);
            }
        }
    }
}
