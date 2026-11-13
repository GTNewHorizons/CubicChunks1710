package com.cardinalstar.cubicchunks.worldgen.config.gui;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.resources.I18n;
import net.minecraftforge.common.DimensionManager;

import com.cardinalstar.cubicchunks.CubicChunks;
import com.cardinalstar.cubicchunks.worldgen.config.WorldgenConfig;

import cpw.mods.fml.client.config.DummyConfigElement.DummyCategoryElement;
import cpw.mods.fml.client.config.GuiConfig;
import cpw.mods.fml.client.config.GuiConfigEntries;
import cpw.mods.fml.client.config.GuiConfigEntries.CategoryEntry;
import cpw.mods.fml.client.config.IConfigElement;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Top-level screen for a world's worldgen settings, with one category button per dimension.
 * The caller owns {@code config} and is responsible for saving it.
 */
@SideOnly(Side.CLIENT)
public class WGDimensionListScreen extends GuiConfig {

    public WGDimensionListScreen(GuiScreen parent, WorldgenConfig config) {
        super(
            parent,
            buildDimensionElements(config),
            CubicChunks.MODID,
            false,
            false,
            I18n.format("cubicchunks.wgconfig.list_title"));
    }

    @SuppressWarnings("rawtypes")
    private static List<IConfigElement> buildDimensionElements(WorldgenConfig config) {
        // Registered dimensions, plus any the file has settings for (e.g. from a mod that isn't installed)
        Set<Integer> ids = new TreeSet<>(Arrays.asList(DimensionManager.getStaticDimensionIDs()));
        ids.addAll(config.getDimensionIds());

        List<IConfigElement> out = new ArrayList<>(ids.size());
        for (int id : ids) {
            out.add(new DimensionElement(config, id));
        }
        return out;
    }

    private static String dimensionLabel(int id) {
        try {
            String name = DimensionManager.createProviderFor(id)
                .getDimensionName();
            if (name != null && !name.isEmpty()) return String.format("%s (dim %d)", name, id);
        } catch (Exception ignored) {
            // not registered, or the provider can't be created outside a world
        }
        return String.format("Dimension %d", id);
    }

    @SuppressWarnings("rawtypes")
    private static class DimensionElement extends DummyCategoryElement {

        final WorldgenConfig config;
        final int dimensionId;

        DimensionElement(WorldgenConfig config, int dimensionId) {
            super(String.valueOf(dimensionId), dimensionLabel(dimensionId), DimensionEntry.class);
            this.config = config;
            this.dimensionId = dimensionId;
        }

        // The lang key is the dimension's label, so the default "<key>.tooltip" lookup would show the raw key
        @Override
        public String getComment() {
            return I18n.format("cubicchunks.wgconfig.dimension.tooltip");
        }
    }

    /**
     * Category button that only creates the dimension's settings once it is opened, so browsing the list doesn't
     * add every dimension to the file.
     */
    public static class DimensionEntry extends CategoryEntry {

        @SuppressWarnings("rawtypes")
        public DimensionEntry(GuiConfig owningScreen, GuiConfigEntries owningEntryList, IConfigElement configElement) {
            super(owningScreen, owningEntryList, configElement);
        }

        @Override
        protected GuiScreen buildChildScreen() {
            return null;
        }

        @Override
        public boolean mousePressed(int index, int x, int y, int mouseEvent, int relativeX, int relativeY) {
            if (btnSelectCategory.mousePressed(mc, x, y)) {
                btnSelectCategory.func_146113_a(mc.getSoundHandler());
                if (childScreen == null) {
                    DimensionElement element = (DimensionElement) configElement;
                    childScreen = new WGConfigGuiScreen(owningScreen, element.config.getOrCreate(element.dimensionId));
                }
                mc.displayGuiScreen(childScreen);
                return true;
            }
            return super.mousePressed(index, x, y, mouseEvent, relativeX, relativeY);
        }
    }
}
