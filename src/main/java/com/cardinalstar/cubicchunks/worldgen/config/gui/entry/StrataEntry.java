package com.cardinalstar.cubicchunks.worldgen.config.gui.entry;

import java.util.List;

import net.minecraft.client.resources.I18n;

import com.cardinalstar.cubicchunks.worldgen.config.Stratum;
import com.cardinalstar.cubicchunks.worldgen.config.gui.GuiStrataEditor;
import com.cardinalstar.cubicchunks.worldgen.config.gui.element.WGConfigElement;

import cpw.mods.fml.client.config.GuiConfig;
import cpw.mods.fml.client.config.GuiConfigEntries;
import cpw.mods.fml.client.config.GuiConfigEntries.ButtonEntry;
import cpw.mods.fml.client.config.IConfigElement;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Config entry for strata. The value button shows the layer count and opens {@link GuiStrataEditor}.
 */
@SideOnly(Side.CLIENT)
public class StrataEntry extends ButtonEntry {

    private final WGConfigElement<List<Stratum>> element;
    private final List<Stratum> beforeValue;
    private List<Stratum> currentValue;

    @SuppressWarnings({ "rawtypes", "unchecked" })
    public StrataEntry(GuiConfig owningScreen, GuiConfigEntries owningEntryList, IConfigElement configElement) {
        super(owningScreen, owningEntryList, configElement);
        this.element = (WGConfigElement<List<Stratum>>) configElement;
        this.beforeValue = element.getValue();
        this.currentValue = beforeValue;
        updateValueButtonText();
    }

    @Override
    public void updateValueButtonText() {
        btnValue.displayString = I18n.format("cubicchunks.wgconfig.strata.count", currentValue.size());
    }

    @Override
    public void valueButtonPressed(int slotIndex) {
        mc.displayGuiScreen(new GuiStrataEditor(owningScreen, currentValue, enabled(), this::setFromEditor));
    }

    private void setFromEditor(List<Stratum> strata) {
        if (enabled()) {
            currentValue = strata;
            updateValueButtonText();
        }
    }

    @Override
    public boolean isDefault() {
        return currentValue.equals(element.getDefaultValue());
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
        return !currentValue.equals(beforeValue);
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
        return currentValue.toArray();
    }
}
