package com.cardinalstar.cubicchunks.api.worldgen;

import net.minecraft.util.IChatComponent;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraft.world.chunk.IChunkProvider;

import com.cardinalstar.cubicchunks.api.IntRange;

/// This is a factory that produces [IWorldGenerator]s. It is the Cubic Chunks equivalent of [WorldType].
/// Register it with [CCAPI#registerWorldGenerator] during init. A world saves each dimension's choice by
/// [`id`][#getId()], so a dimension keeps its generator even if mods later change which one it would get by default.
public interface IWorldGeneratorType {

    /// Saved in the world's worldgen settings, so it must never change.
    ResourceLocation getId();

    /// The display name, shown in various menus.
    IChatComponent getDisplayName();

    /// Whether this generator can generate `world`'s dimension. A world that selects it anyway fails to load.
    boolean appliesTo(World world);

    /// The Y range this generator produces terrain in. Players can always reach all of it, whatever the world height.
    /// This is unioned with the configured world height bounds, so the generator never generates less than it should.
    /// If a generator supports smaller world heights, that metric should be exposed as a [WGConfigItem].
    IntRange getGenerationRange(World world);

    /// Creates the generator for a world.
    ///
    /// @param original the dimension's own chunk generator, from [WorldProvider#createChunkGenerator()]. It's
    /// created regardless of whichever generator is selected, since mods keep references to it.
    IWorldGenerator create(WorldServer world, IChunkProvider original);
}
