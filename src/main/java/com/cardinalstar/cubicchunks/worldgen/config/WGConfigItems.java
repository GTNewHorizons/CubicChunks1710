package com.cardinalstar.cubicchunks.worldgen.config;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import net.minecraft.util.ResourceLocation;
import net.minecraftforge.common.MinecraftForge;

import com.cardinalstar.cubicchunks.CubicChunks;
import com.cardinalstar.cubicchunks.CubicChunksConfig;
import com.cardinalstar.cubicchunks.api.ICube;
import com.cardinalstar.cubicchunks.api.IntRange;
import com.cardinalstar.cubicchunks.worldgen.HeightInfo;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;

/**
 * Cubic Chunks' own worldgen config items.
 */
public final class WGConfigItems {

    private WGConfigItems() {}

    /**
     * World height. Either end may be unbounded, and bounded ends are rounded out to whole cubes. Defaults come from
     * the global config, like CubicChunksSavedData.
     */
    public static final WGConfigItem<IntRange> HEIGHT = WGConfigItem.perDimension(
        id("height"),
        dimensionId -> {
            HeightInfo info = CubicChunksConfig.configuredDimensionalHeightMap.get(dimensionId);
            if (info != null) return new IntRange(info.minHeight, info.maxHeight);
            return new IntRange(CubicChunksConfig.defaultMinHeight, CubicChunksConfig.defaultMaxHeight);
        },
        WGConfigItemTypes
            .intRange(
                new IntRange(CubicChunks.MIN_SUPPORTED_BLOCK_Y, CubicChunks.MAX_SUPPORTED_BLOCK_Y),
                true,
                ICube.SIZE));

    /** Strata layers. Placeholder defaults until the generator uses them. */
    public static final WGConfigItem<List<Stratum>> STRATA = WGConfigItem.of(
        id("strata"),
        Collections.unmodifiableList(
            Arrays.asList(
                new Stratum(new BlockRef("minecraft:stone", 0), -50, -25),
                new Stratum(new BlockRef("minecraft:gravel", 0), -80, -60))),
        WGConfigItemTypes.strata());

    public static final WGConfigItem<BlockRef> EXAMPLE_BLOCK = WGConfigItem
        .of(id("example_block"), new BlockRef("minecraft:stone", 0), WGConfigItemTypes.block());

    /** Adds Cubic Chunks' own settings to every dimension, through {@link GatherWorldgenConfigEvent}. */
    public static void register() {
        MinecraftForge.EVENT_BUS.register(new WGConfigItems());
    }

    @SubscribeEvent
    public void onGather(GatherWorldgenConfigEvent event) {
        event.add(HEIGHT);
        event.add(STRATA);
        event.add(EXAMPLE_BLOCK);
    }

    private static ResourceLocation id(String path) {
        return new ResourceLocation(CubicChunks.MODID, path);
    }
}
