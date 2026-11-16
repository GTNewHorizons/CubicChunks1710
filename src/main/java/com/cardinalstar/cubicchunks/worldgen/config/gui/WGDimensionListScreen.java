package com.cardinalstar.cubicchunks.worldgen.config.gui;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.resources.I18n;
import net.minecraft.util.IChatComponent;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.common.DimensionManager;

import com.cardinalstar.cubicchunks.CubicChunks;
import com.cardinalstar.cubicchunks.api.DimensionNames;
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
        // Registered dimensions, plus any the file has settings for. Settings of dimensions without a name aren't
        // saved.
        Set<Integer> ids = new TreeSet<>(Arrays.asList(DimensionManager.getStaticDimensionIDs()));
        ids.addAll(config.getDimensionIds());
        ids.removeIf(id -> DimensionNames.INSTANCE.getName(id) == null);

        List<IConfigElement> out = new ArrayList<>(ids.size());
        for (int id : ids) {
            out.add(new DimensionElement(config, id));
        }
        return out;
    }

    /** The dimension's display name, registry name and id, as far as they're known. */
    static String dimensionLabel(int id) {
        ResourceLocation name = DimensionNames.INSTANCE.getName(id);
        String displayName = displayName(id);
        if (displayName != null && name != null) return String.format("%s (%s, dim %d)", displayName, name, id);
        if (displayName != null) return String.format("%s (dim %d)", displayName, id);
        if (name != null) return String.format("%s (dim %d)", name, id);
        return I18n.format("cubicchunks.wgconfig.dim_subtitle", id);
    }

    /**
     * From the name registered with {@link DimensionNames}, or else the dimension's provider. Null if neither has one.
     */
    private static String displayName(int id) {
        IChatComponent registered = DimensionNames.INSTANCE.getDisplayName(id);
        if (registered != null) return registered.getFormattedText();
        try {
            String name = DimensionManager.createProviderFor(id)
                .getDimensionName();
            if (name != null && !name.isEmpty()) return name;
        } catch (Exception ignored) {
            // not registered, or the provider can't be created outside a world
        }
        return null;
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
