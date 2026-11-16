package com.cardinalstar.cubicchunks.worldgen.config;

import java.util.List;

/**
 * Supplies the settings that apply to a dimension. In game this is {@link GatherWorldgenConfigEvent#gather}.
 */
@FunctionalInterface
public interface WGConfigItemSource {

    WGConfigItemSource EVENT = GatherWorldgenConfigEvent::gather;

    List<WGConfigItem<?>> getItems(int dimensionId);
}
