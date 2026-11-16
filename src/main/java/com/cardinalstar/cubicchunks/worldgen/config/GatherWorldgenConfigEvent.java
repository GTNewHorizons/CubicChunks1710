package com.cardinalstar.cubicchunks.worldgen.config;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.util.ResourceLocation;
import net.minecraftforge.common.MinecraftForge;

import cpw.mods.fml.common.eventhandler.Event;

/**
 * Collects the worldgen settings that apply to a dimension. Fired on {@link MinecraftForge#EVENT_BUS} whenever a
 * dimension's settings are created: when a world's settings file is loaded, and when they're edited in the GUI.
 * <p>
 * Listeners {@link #add} the settings they use for this dimension, and skip it otherwise. Values in the file for
 * settings nobody adds are kept as they are.
 * <p>
 * There may be no world when this fires (e.g. on the Create World screen), so listeners must decide from the event's
 * fields alone.
 */
public class GatherWorldgenConfigEvent extends Event {

    public final int dimensionId;
    private final Map<ResourceLocation, WGConfigItem<?>> items = new LinkedHashMap<>();

    public GatherWorldgenConfigEvent(int dimensionId) {
        this.dimensionId = dimensionId;
    }

    /** Adds a setting to this dimension. Settings are shown in the order they're added. */
    public void add(WGConfigItem<?> item) {
        if (items.putIfAbsent(item.getId(), item) != null) {
            throw new IllegalArgumentException(
                "Worldgen config item " + item.getId() + " was added twice for dimension " + dimensionId);
        }
    }

    public List<WGConfigItem<?>> getItems() {
        return Collections.unmodifiableList(new ArrayList<>(items.values()));
    }

    /** Fires the event for a dimension and returns the settings that were added. */
    public static List<WGConfigItem<?>> gather(int dimensionId) {
        GatherWorldgenConfigEvent event = new GatherWorldgenConfigEvent(dimensionId);
        MinecraftForge.EVENT_BUS.post(event);
        return event.getItems();
    }
}
