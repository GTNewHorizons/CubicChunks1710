package com.cardinalstar.cubicchunks.worldgen.config.gui.element;

import java.util.Arrays;

import com.cardinalstar.cubicchunks.worldgen.config.WGConfigItem;
import com.cardinalstar.cubicchunks.worldgen.config.WGDimensionConfig;

import cpw.mods.fml.client.config.ConfigGuiType;
import cpw.mods.fml.client.config.DummyConfigElement.DummyListElement;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * List element whose values are read from and written to a {@link WGDimensionConfig}.
 */
@SideOnly(Side.CLIENT)
public class WGListConfigElement<T> extends DummyListElement<T> {

    private final WGConfigItem<T[]> item;
    private final WGDimensionConfig sandbox;

    public WGListConfigElement(WGConfigItem<T[]> item, WGDimensionConfig sandbox, ConfigGuiType type) {
        super(
            item.getId()
                .toString(),
            item.getDefaultValue(sandbox.dimensionId),
            type,
            item.getLangKey());
        this.item = item;
        this.sandbox = sandbox;
        this.values = sandbox.get(item);
    }

    // DummyListElement.set() overwrites defaultValues instead of values. The array edit screen also passes an
    // Object[], so copy it into an array of the item's real component type.
    @Override
    @SuppressWarnings("unchecked")
    public void set(T[] aVal) {
        this.values = Arrays.copyOf(aVal, aVal.length, (Class<T[]>) defaultValues.getClass());
        sandbox.set(item, values);
    }

    @Override
    public void setToDefault() {
        this.values = Arrays.copyOf(defaultValues, defaultValues.length);
        sandbox.reset(item);
    }
}
