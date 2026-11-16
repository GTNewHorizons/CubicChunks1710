package com.cardinalstar.cubicchunks.worldgen.config;

import net.minecraft.world.WorldServer;

import com.cardinalstar.cubicchunks.api.IntRange;
import com.cardinalstar.cubicchunks.api.worldgen.IWorldGeneratorType;

import cpw.mods.fml.common.eventhandler.Event;

/// Posted when the worldgen config is loaded and the world generators are configured, but before [World#chunkProvider]
/// is set. This is only posted on the server. This is useful for marshalling your config items into fields on the
/// [World] so that worldgen doesn't need to do map lookups constantly.
public class WorldgenConfigLoadedEvent extends Event {

    public final WorldServer world;
    public final WGDimensionConfig config;
    public final IWorldGeneratorType generator;
    public final IntRange generationRange, heightRange;

    public WorldgenConfigLoadedEvent(WorldServer world, WGDimensionConfig config, IWorldGeneratorType generator,
        IntRange generationRange, IntRange heightRange) {
        this.world = world;
        this.config = config;
        this.generator = generator;
        this.generationRange = generationRange;
        this.heightRange = heightRange;
    }
}
