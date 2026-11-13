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
    public static final WGConfigItem<IntRange> HEIGHT = WGConfigItem.perDimension(id("height"), dimensionId -> {
        HeightInfo info = CubicChunksConfig.configuredDimensionalHeightMap.get(dimensionId);
        if (info != null) return new IntRange(info.minHeight, info.maxHeight);
        return new IntRange(CubicChunksConfig.defaultMinHeight, CubicChunksConfig.defaultMaxHeight);
    },
        WGConfigItemTypes.intRange(
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

    /**
     * Fills cubes below the vanilla terrain, in dimensions using the vanilla-compatible generator. Null (auto) means
     * the generator works it out from the bottom of the world's first chunk, then stores the result here.
     */
    public static final WGConfigItem<BlockRef> VANILLA_BOTTOM_FILLER = WGConfigItem
        .of(id("vanilla_generator/bottom_filler"), null, WGConfigItemTypes.blockOrAuto());

    /** Like {@link #VANILLA_BOTTOM_FILLER}, for cubes above the vanilla terrain. */
    public static final WGConfigItem<BlockRef> VANILLA_TOP_FILLER = WGConfigItem
        .of(id("vanilla_generator/top_filler"), null, WGConfigItemTypes.blockOrAuto());

    /** Adds Cubic Chunks' own settings to every dimension, through {@link GatherWorldgenConfigEvent}. */
    public static void register() {
        MinecraftForge.EVENT_BUS.register(new WGConfigItems());
    }

    @SubscribeEvent
    public void onGather(GatherWorldgenConfigEvent event) {
        event.add(HEIGHT);
        // Only VanillaWorldGenerator uses these; limit them to its dimensions once generators are chosen per dimension
        event.add(VANILLA_BOTTOM_FILLER);
        event.add(VANILLA_TOP_FILLER);
        event.add(STRATA);
        event.add(EXAMPLE_BLOCK);
    }

    private static ResourceLocation id(String path) {
        return new ResourceLocation(CubicChunks.MODID, path);
    }
}
