package com.cardinalstar.cubicchunks.worldgen.config.gui.element;

import org.jspecify.annotations.NonNull;

public enum ToggleType {

    ENABLE_DISABLE,
    GENERATE,
    ON_OFF,
    REMOVE;

    public static @NonNull String stringify(ToggleType toggleType, boolean value) {
        return switch (toggleType) {
            case ENABLE_DISABLE -> value ? "Enabled" : "Disabled";
            case GENERATE -> value ? "Will Generate" : "Won't Generate";
            case ON_OFF -> value ? "On" : "Off";
            case REMOVE -> value ? "Will Be Removed" : "Won't Be Removed";
        };
    }
}
