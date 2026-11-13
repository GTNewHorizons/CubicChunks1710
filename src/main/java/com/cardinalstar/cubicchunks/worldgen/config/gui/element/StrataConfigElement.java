package com.cardinalstar.cubicchunks.worldgen.config.gui.element;

import java.util.List;

import net.minecraft.client.resources.I18n;

import com.cardinalstar.cubicchunks.worldgen.config.Stratum;
import com.cardinalstar.cubicchunks.worldgen.config.WGConfigItem;
import com.cardinalstar.cubicchunks.worldgen.config.WGDimensionConfig;
import com.cardinalstar.cubicchunks.worldgen.config.gui.entry.StrataEntry;

import cpw.mods.fml.client.config.ConfigGuiType;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
public class StrataConfigElement extends WGConfigElement<List<Stratum>> {

    public StrataConfigElement(WGConfigItem<List<Stratum>> item, WGDimensionConfig sandbox) {
        super(item, sandbox, ConfigGuiType.STRING);
        setCustomListEntryClass(StrataEntry.class);
    }

    @Override
    protected String formatValue(List<Stratum> value) {
        return I18n.format("cubicchunks.wgconfig.strata.count", value.size());
    }
}
