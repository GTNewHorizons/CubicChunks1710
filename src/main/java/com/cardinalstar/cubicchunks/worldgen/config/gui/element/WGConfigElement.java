package com.cardinalstar.cubicchunks.worldgen.config.gui.element;

import com.cardinalstar.cubicchunks.worldgen.config.WGConfigItem;
import com.cardinalstar.cubicchunks.worldgen.config.WGDimensionConfig;

import cpw.mods.fml.client.config.ConfigGuiType;
import cpw.mods.fml.client.config.DummyConfigElement;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Property element whose value is read from and written to a {@link WGDimensionConfig}.
 */
@SideOnly(Side.CLIENT)
public class WGConfigElement<T> extends DummyConfigElement<T> {

    protected final WGConfigItem<T> item;
    protected final WGDimensionConfig sandbox;

    public WGConfigElement(WGConfigItem<T> item, WGDimensionConfig sandbox, ConfigGuiType type, T minValue,
        T maxValue) {
        super(
            item.getId()
                .toString(),
            item.getDefaultValue(sandbox.dimensionId),
            type,
            item.getLangKey(),
            minValue,
            maxValue);
        this.item = item;
        this.sandbox = sandbox;
        // DummyConfigElement starts the value at the default; load the dimension's value instead
        this.value = sandbox.get(item);
    }

    public WGConfigElement(WGConfigItem<T> item, WGDimensionConfig sandbox, ConfigGuiType type) {
        this(item, sandbox, type, null, null);
    }

    @SuppressWarnings("unchecked")
    public T getValue() {
        return (T) value;
    }

    @SuppressWarnings("unchecked")
    public T getDefaultValue() {
        return (T) defaultValue;
    }

    /** How a value is shown in the "Default: ..." tooltip line. Entries compare values via the typed getters. */
    protected String formatValue(T value) {
        return String.valueOf(value);
    }

    @Override
    public Object getDefault() {
        return formatValue(getDefaultValue());
    }

    // DummyConfigElement.set() overwrites defaultValue instead of value
    @Override
    public void set(T value) {
        sandbox.set(item, value);
        // Pick up any normalization (e.g. rounding) the item type applied
        this.value = sandbox.get(item);
    }

    @Override
    public void setToDefault() {
        this.value = defaultValue;
        sandbox.reset(item);
    }
}
