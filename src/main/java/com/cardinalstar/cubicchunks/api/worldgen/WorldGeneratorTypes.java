package com.cardinalstar.cubicchunks.api.worldgen;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.util.ResourceLocation;

import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.VisibleForTesting;

/// The world generators dimensions can select, by id. Mods register theirs through [CCAPI#registerWorldGenerator].
public final class WorldGeneratorTypes {

    public static final WorldGeneratorTypes INSTANCE = new WorldGeneratorTypes();

    private final Map<ResourceLocation, IWorldGeneratorType> types = new LinkedHashMap<>();

    private final HashSet<ResourceLocation> unlockedDimensions = new HashSet<>();

    @VisibleForTesting
    public WorldGeneratorTypes() {}

    /// Throws if another generator already has the same id.
    public synchronized void register(IWorldGeneratorType type) {
        IWorldGeneratorType existing = types.putIfAbsent(type.getId(), type);
        if (existing != null) {
            throw new IllegalArgumentException(
                "World generator " + type.getId() + " is already registered (" + existing + ")");
        }
    }

    /// Null if no generator has this id, e.g. because the mod that added it was removed.
    @Nullable
    public synchronized IWorldGeneratorType get(ResourceLocation id) {
        return types.get(id);
    }

    /// Every generator, in registration order.
    public synchronized List<IWorldGeneratorType> getAll() {
        return Collections.unmodifiableList(new ArrayList<>(types.values()));
    }

    /// Allows a dimension to use non-default world generators. Most modded dimensions behave poorly when their world
    /// providers or chunk providers are swapped out, but if they don't behave poorly this should be called.
    public synchronized void unlockDimension(ResourceLocation name) {
        unlockedDimensions.add(name);
    }

    /// Checks if a dimension can have a non-default world generator.
    public synchronized boolean isDimensionUnlocked(ResourceLocation name) {
        return unlockedDimensions.contains(name);
    }
}
