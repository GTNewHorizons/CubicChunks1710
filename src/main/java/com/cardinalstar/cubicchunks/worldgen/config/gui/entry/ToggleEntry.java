package com.cardinalstar.cubicchunks.worldgen.config.gui.entry;

import java.util.Objects;

import com.cardinalstar.cubicchunks.worldgen.config.gui.element.ToggleConfigElement;
import com.cardinalstar.cubicchunks.worldgen.config.gui.element.ToggleType;

import cpw.mods.fml.client.config.GuiConfig;
import cpw.mods.fml.client.config.GuiConfigEntries;
import cpw.mods.fml.client.config.GuiConfigEntries.ButtonEntry;
import cpw.mods.fml.client.config.IConfigElement;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/// A toggle button that represents one of several toggle types. See [ToggleType].
@SideOnly(Side.CLIENT)
public class ToggleEntry extends ButtonEntry {

    private final ToggleConfigElement element;
    private final boolean beforeValue;
    private boolean currentValue;

    private final ToggleType toggleType;

    @SuppressWarnings({ "rawtypes" })
    public ToggleEntry(GuiConfig owningScreen, GuiConfigEntries owningEntryList, IConfigElement configElement) {
        super(owningScreen, owningEntryList, configElement);
        this.element = (ToggleConfigElement) configElement;
        this.beforeValue = element.getValue();
        this.currentValue = beforeValue;
        this.toggleType = this.element.getToggleType();
        updateValueButtonText();
    }

    @Override
    public void updateValueButtonText() {
        btnValue.displayString = ToggleType.stringify(toggleType, currentValue);
    }

    @Override
    public void valueButtonPressed(int slotIndex) {
        currentValue = !currentValue;
        updateValueButtonText();
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
