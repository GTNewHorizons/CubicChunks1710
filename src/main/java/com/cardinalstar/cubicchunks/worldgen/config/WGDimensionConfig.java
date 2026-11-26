package com.cardinalstar.cubicchunks.worldgen.config;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.util.ResourceLocation;

/**
 * One dimension's worldgen settings: the items gathered for it (see {@link GatherWorldgenConfigEvent}) and their
 * values. Only values that differ from the item's default are stored here, but {@link WorldgenConfig#save} writes
 * every item's value so the file pins them against future default changes.
 * <p>
 * Thread-safe: generators may store values they work out while generating (e.g. auto-detected filler).
 */
public class WGDimensionConfig {

    public final int dimensionId;
    private final List<WGConfigItem<?>> items;
    private final Set<ResourceLocation> itemIds = new HashSet<>();
    private final Map<ResourceLocation, Object> values = new ConcurrentHashMap<>();

    public WGDimensionConfig(int dimensionId, List<WGConfigItem<?>> items) {
        this.dimensionId = dimensionId;
        this.items = Collections.unmodifiableList(new ArrayList<>(items));
        for (WGConfigItem<?> item : items) {
            itemIds.add(item.getId());
        }
    }

    /** The settings that apply to this dimension, in display order. */
    public List<WGConfigItem<?>> getItems() {
        return items;
    }

    public boolean hasItem(WGConfigItem<?> item) {
        return itemIds.contains(item.getId());
    }

    @SuppressWarnings("unchecked")
    public <T> T get(WGConfigItem<T> item) {
        checkItem(item);
        Object val = values.get(item.getId());
        if (val == null) return item.getDefaultValue(dimensionId);
        return (T) val;
    }

    /** Null resets the item to its default. */
    public <T> void set(WGConfigItem<T> item, T value) {
        checkItem(item);
        if (value != null) {
            value = item.getConfigItemType()
                .normalize(value);
        }
        if (value == null || Objects.deepEquals(value, item.getDefaultValue(dimensionId))) {
            values.remove(item.getId());
        } else {
            values.put(item.getId(), value);
        }
    }

    public boolean isDefault(WGConfigItem<?> item) {
        return !values.containsKey(item.getId());
    }

    public void reset(WGConfigItem<?> item) {
        checkItem(item);
        values.remove(item.getId());
    }

    public void resetAll() {
        values.clear();
    }

    /** Catches reads and writes of settings this dimension doesn't have, which would otherwise never be saved. */
    private void checkItem(WGConfigItem<?> item) {
        if (!hasItem(item)) {
            throw new IllegalArgumentException(
                "Worldgen config item " + item.getId() + " wasn't gathered for dimension " + dimensionId);
        }
    }
}
