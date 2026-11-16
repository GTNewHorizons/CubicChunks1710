package com.cardinalstar.cubicchunks.worldgen.config;

import java.util.function.Function;

import net.minecraft.util.ResourceLocation;

import org.jetbrains.annotations.Nullable;

public interface WGConfigItem<T> {

    /** Unique id; the domain is the owning mod's id. Also the item's path in the config file. */
    ResourceLocation getId();

    /** Defaults to {@code <domain>.wgconfig.<path>}, with '/' in the path replaced by '.'. */
    default String getLangKey() {
        return getId().getResourceDomain() + ".wgconfig."
            + getId().getResourcePath()
                .replace('/', '.');
    }

    /**
     * @param dimensionName null for dimensions without a name. Ids aren't passed: they can change between modpacks,
     *                      and a mod that wants a default for its own dimension can name it.
     */
    T getDefaultValue(@Nullable ResourceLocation dimensionName);

    WGConfigItemType<T> getConfigItemType();

    static <T> WGConfigItem<T> of(ResourceLocation id, T defaultValue, WGConfigItemType<T> type) {
        return perDimension(id, dimensionName -> defaultValue, type);
    }

    /** {@code defaultValue} gets the dimension's name, or null if it has none. */
    static <T> WGConfigItem<T> perDimension(ResourceLocation id, Function<ResourceLocation, T> defaultValue,
        WGConfigItemType<T> type) {
        return new WGConfigItem<T>() {

            @Override
            public ResourceLocation getId() {
                return id;
            }

            @Override
            public T getDefaultValue(@Nullable ResourceLocation dimensionName) {
                return defaultValue.apply(dimensionName);
            }

            @Override
            public WGConfigItemType<T> getConfigItemType() {
                return type;
            }
        };
    }
}
