package com.cardinalstar.cubicchunks.worldgen.config.gui.element;

import com.cardinalstar.cubicchunks.api.IntRange;
import com.cardinalstar.cubicchunks.worldgen.config.WGConfigItem;
import com.cardinalstar.cubicchunks.worldgen.config.WGDimensionConfig;
import com.cardinalstar.cubicchunks.worldgen.config.gui.entry.IntRangeEntry;

import cpw.mods.fml.client.config.ConfigGuiType;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Element for an {@link IntRange} value. An end at one of {@link #limits} is unbounded (see
 * {@link com.cardinalstar.cubicchunks.worldgen.config.WGConfigItemTypes#intRange}).
 */
@SideOnly(Side.CLIENT)
public class IntRangeConfigElement extends WGConfigElement<IntRange> {

    /** Values each bounded end may take. */
    public final IntRange limits;
    /** Whether each end may be toggled to unbounded. */
    public final boolean allowUnbounded;

    public IntRangeConfigElement(WGConfigItem<IntRange> item, WGDimensionConfig sandbox, IntRange limits,
        boolean allowUnbounded) {
        super(item, sandbox, ConfigGuiType.STRING);
        this.limits = limits;
        this.allowUnbounded = allowUnbounded;
        setCustomListEntryClass(IntRangeEntry.class);
    }

    public boolean isUnboundedMin(int value) {
        return allowUnbounded && value == limits.getMin();
    }

    public boolean isUnboundedMax(int value) {
        return allowUnbounded && value == limits.getMax();
    }

    @Override
    protected String formatValue(IntRange value) {
        return format(value);
    }

    public String format(IntRange value) {
        String min = isUnboundedMin(value.getMin()) ? "-\u221E" : String.valueOf(value.getMin());
        String max = isUnboundedMax(value.getMax()) ? "+\u221E" : String.valueOf(value.getMax());
        return "[" + min + ", " + max + "]";
    }
}
