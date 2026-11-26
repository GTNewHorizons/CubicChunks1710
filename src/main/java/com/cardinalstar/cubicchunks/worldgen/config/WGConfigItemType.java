package com.cardinalstar.cubicchunks.worldgen.config;

import org.spongepowered.configurate.serialize.TypeSerializer;

import cpw.mods.fml.client.config.IConfigElement;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

public interface WGConfigItemType<T> {

    Class<T> getType();

    TypeSerializer<T> getSerializer();

    /** Adjusts a value before it's stored, e.g. rounding. Applies to GUI edits and to values loaded from file. */
    default T normalize(T value) {
        return value;
    }

    /**
     * Produces an IConfigElement whose get/set are wired to {@code sandbox}.
     * Called fresh each time the per-dimension config screen is opened.
     */
    @SideOnly(Side.CLIENT)
    IConfigElement<?> createConfigElement(WGConfigItem<T> item, WGDimensionConfig sandbox);
}
