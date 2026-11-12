package com.cardinalstar.cubicchunks.worldgen.config;

import java.util.function.IntFunction;

import net.minecraft.util.ResourceLocation;

public interface WGConfigItem<T> {

    /** Unique id; the domain is the owning mod's id. Also the item's path in the config file. */
    ResourceLocation getId();

    /** Defaults to {@code <domain>.wgconfig.<path>}, with '/' in the path replaced by '.'. */
    default String getLangKey() {
        return getId().getResourceDomain() + ".wgconfig."
            + getId().getResourcePath()
                .replace('/', '.');
    }

    T getDefaultValue(int dimensionId);

    WGConfigItemType<T> getConfigItemType();

    static <T> WGConfigItem<T> of(ResourceLocation id, T defaultValue, WGConfigItemType<T> type) {
        return perDimension(id, dimensionId -> defaultValue, type);
    }

    static <T> WGConfigItem<T> perDimension(ResourceLocation id, IntFunction<T> defaultValue,
        WGConfigItemType<T> type) {
        return new WGConfigItem<T>() {

            @Override
            public ResourceLocation getId() {
                return id;
            }

            @Override
            public T getDefaultValue(int dimensionId) {
                return defaultValue.apply(dimensionId);
            }

            @Override
            public WGConfigItemType<T> getConfigItemType() {
                return type;
            }
        };
    }
}
