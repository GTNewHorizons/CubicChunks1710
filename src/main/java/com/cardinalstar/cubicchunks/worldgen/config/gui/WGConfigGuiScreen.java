package com.cardinalstar.cubicchunks.worldgen.config.gui;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.resources.I18n;

import com.cardinalstar.cubicchunks.CubicChunks;
import com.cardinalstar.cubicchunks.worldgen.config.WGConfigItem;
import com.cardinalstar.cubicchunks.worldgen.config.WGDimensionConfig;

import cpw.mods.fml.client.config.GuiConfig;
import cpw.mods.fml.client.config.IConfigElement;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Worldgen settings for one dimension: every {@link WGConfigItem} gathered for it, bound to its
 * {@link WGDimensionConfig}.
 */
@SideOnly(Side.CLIENT)
public class WGConfigGuiScreen extends GuiConfig {

    public WGConfigGuiScreen(GuiScreen parent, WGDimensionConfig dim) {
        super(
            parent,
            buildElements(dim),
            CubicChunks.MODID,
            false,
            false,
            I18n.format("cubicchunks.wgconfig.title"),
            I18n.format("cubicchunks.wgconfig.dim_subtitle", dim.dimensionId));
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    private static List<IConfigElement> buildElements(WGDimensionConfig dim) {
        List<IConfigElement> out = new ArrayList<>();
        for (WGConfigItem item : dim.getItems()) {
            out.add(
                item.getConfigItemType()
                    .createConfigElement(item, dim));
        }
        return out;
    }
}
