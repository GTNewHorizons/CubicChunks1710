package com.cardinalstar.cubicchunks.worldgen.config.gui.element;

import com.cardinalstar.cubicchunks.worldgen.config.WGConfigItem;
import com.cardinalstar.cubicchunks.worldgen.config.WGDimensionConfig;
import com.cardinalstar.cubicchunks.worldgen.config.gui.entry.ToggleEntry;

import cpw.mods.fml.client.config.ConfigGuiType;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import lombok.Getter;

@SideOnly(Side.CLIENT)
public class ToggleConfigElement extends WGConfigElement<Boolean> {

    @Getter
    private final ToggleType toggleType;

    public ToggleConfigElement(WGConfigItem<Boolean> item, WGDimensionConfig sandbox, ToggleType toggleType) {
        super(item, sandbox, ConfigGuiType.BOOLEAN);
        this.toggleType = toggleType;
        setCustomListEntryClass(ToggleEntry.class);
    }

    @Override
    protected String formatValue(Boolean value) {
        return ToggleType.stringify(toggleType, value);
    }
}
