package com.cardinalstar.cubicchunks.worldgen.config;

import java.util.List;

import net.minecraft.util.ResourceLocation;

import org.jetbrains.annotations.Nullable;

/**
 * Supplies the settings that apply to a dimension. In game this is {@link GatherWorldgenConfigEvent#gather}.
 */
@FunctionalInterface
public interface WGConfigItemSource {

    WGConfigItemSource EVENT = GatherWorldgenConfigEvent::gather;

    /** @param dimensionName null if the dimension has no name */
    List<WGConfigItem<?>> getItems(int dimensionId, @Nullable ResourceLocation dimensionName);
}
