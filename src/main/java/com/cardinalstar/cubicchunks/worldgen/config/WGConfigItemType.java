package com.cardinalstar.cubicchunks.worldgen.config;

import org.spongepowered.configurate.serialize.TypeSerializer;

public interface WGConfigItemType<T> {

    Class<T> getType();

    TypeSerializer<T> getSerializer();

    /** Adjusts a value before it's stored, e.g. rounding. Applies to GUI edits and to values loaded from file. */
    default T normalize(T value) {
        return value;
    }
}
