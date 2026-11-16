package com.cardinalstar.cubicchunks.worldgen.config;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;

import net.minecraft.util.ResourceLocation;
import net.minecraftforge.common.MinecraftForge;

import com.cardinalstar.cubicchunks.CubicChunks;
import com.cardinalstar.cubicchunks.CubicChunksConfig;
import com.cardinalstar.cubicchunks.api.DimensionNames;
import com.cardinalstar.cubicchunks.api.ICube;
import com.cardinalstar.cubicchunks.api.IntRange;
import com.cardinalstar.cubicchunks.mixin.api.ICubicWorldInternal;
import com.cardinalstar.cubicchunks.util.Mods;
import com.cardinalstar.cubicchunks.worldgen.BuiltinGeneratorTypes;
import com.cardinalstar.cubicchunks.worldgen.config.gui.element.ToggleType;

import cpw.mods.fml.common.Optional;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import ganymedes01.etfuturum.configuration.configs.ConfigBlocksItems;
import ganymedes01.etfuturum.configuration.configs.ConfigWorld;

/**
 * Cubic Chunks' own worldgen config items.
 */
public final class WGConfigItems {

    private WGConfigItems() {}

    /**
     * World height. Either end may be unbounded, and bounded ends are rounded out to whole cubes. Defaults come from
     * the global config, except the overworld's bottom.
     */
    public static final WGConfigItem<IntRange> HEIGHT = WGConfigItem.of(
        id("height"),
        new IntRange(CubicChunksConfig.defaultMinHeight, CubicChunksConfig.defaultMaxHeight),
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

    private static final HashMap<ResourceLocation, BlockRef> DEFAULT_TOP_FILLER = new HashMap<>();
    private static final HashMap<ResourceLocation, BlockRef> DEFAULT_BOTTOM_FILLER = new HashMap<>();

    static {
        DEFAULT_TOP_FILLER.put(DimensionNames.OVERWORLD, new BlockRef("minecraft:air", 0));

        if (Mods.EtFuturumRequiem.isModLoaded() && isEFRDeepslateEnabled()) {
            DEFAULT_BOTTOM_FILLER.put(DimensionNames.OVERWORLD, new BlockRef("etfuturum:deepslate", 0));
        } else {
            DEFAULT_BOTTOM_FILLER.put(DimensionNames.OVERWORLD, new BlockRef("minecraft:stone", 0));
        }

        DEFAULT_TOP_FILLER.put(DimensionNames.THE_NETHER, new BlockRef("minecraft:netherrack", 0));
        DEFAULT_BOTTOM_FILLER.put(DimensionNames.THE_NETHER, new BlockRef("minecraft:netherrack", 0));

        DEFAULT_TOP_FILLER.put(DimensionNames.THE_END, new BlockRef("minecraft:air", 0));
        DEFAULT_BOTTOM_FILLER.put(DimensionNames.THE_END, new BlockRef("minecraft:air", 0));
    }

    @Optional.Method(modid = "etfuturum")
    private static boolean isEFRDeepslateEnabled() {
        return ConfigWorld.deepslateGenerationMode == 1 && ConfigBlocksItems.enableDeepslate;
    }

    /**
     * Fills cubes below the vanilla terrain, in dimensions using the vanilla-compatible generator. Null (auto) means
     * the generator works it out from the bottom of the world's first chunk, then stores the result here.
     */
    public static final WGConfigItem<BlockRef> VANILLA_BOTTOM_FILLER = WGConfigItem.perDimension(
        id("vanilla_generator/bottom_filler"),
        DEFAULT_BOTTOM_FILLER::get,
        WGConfigItemTypes.blockOrAuto());

    /** Like {@link #VANILLA_BOTTOM_FILLER}, for cubes above the vanilla terrain. */
    public static final WGConfigItem<BlockRef> VANILLA_TOP_FILLER = WGConfigItem
        .perDimension(id("vanilla_generator/top_filler"), DEFAULT_TOP_FILLER::get, WGConfigItemTypes.blockOrAuto());

    public static final WGConfigItem<Boolean> REMOVE_WORLD_FLOOR_BEDROCK = WGConfigItem
        .of(id("vanilla_generator/remove_floor_bedrock"), false, WGConfigItemTypes.toggle(ToggleType.REMOVE));

    public static final WGConfigItem<Boolean> REMOVE_NETHER_BEDROCK = WGConfigItem
        .of(id("vanilla_generator/remove_nether_bedrock"), false, WGConfigItemTypes.toggle(ToggleType.REMOVE));

    /** Adds Cubic Chunks' own settings to every dimension, through {@link GatherWorldgenConfigEvent}. */
    public static void register() {
        MinecraftForge.EVENT_BUS.register(new WGConfigItems());
    }

    public static void setDimensionTopFiller(ResourceLocation name, BlockRef block) {
        DEFAULT_TOP_FILLER.put(name, block);
    }

    public static void setDimensionBottomFiller(ResourceLocation name, BlockRef block) {
        DEFAULT_BOTTOM_FILLER.put(name, block);
    }

    @SubscribeEvent
    public void onGather(GatherWorldgenConfigEvent event) {
        event.add(HEIGHT);

        if (Objects.equals(event.dimensionName, DimensionNames.THE_NETHER)) {
            event.add(REMOVE_NETHER_BEDROCK);
        } else if (Objects.equals(event.dimensionName, DimensionNames.THE_END)) {
            // do nothing, no bedrock to remove
        } else {
            event.add(REMOVE_WORLD_FLOOR_BEDROCK);
        }

        // Only VanillaWorldGenerator uses these
        if (event.isGeneratorSelected(BuiltinGeneratorTypes.DEFAULT.getId())) {
            event.add(VANILLA_BOTTOM_FILLER);
            event.add(VANILLA_TOP_FILLER);
        }

        event.add(STRATA);
        event.add(EXAMPLE_BLOCK);
    }

    @SubscribeEvent
    public void onLoad(WorldgenConfigLoadedEvent event) {
        ((ICubicWorldInternal) event.world).cc$setRemoveFloorBedrock(
            event.config.hasItem(REMOVE_WORLD_FLOOR_BEDROCK) && event.config.get(REMOVE_WORLD_FLOOR_BEDROCK));
        ((ICubicWorldInternal) event.world).cc$setRemoveNetherBedrock(
            event.config.hasItem(REMOVE_NETHER_BEDROCK) && event.config.get(REMOVE_NETHER_BEDROCK));
    }

    private static ResourceLocation id(String path) {
        return new ResourceLocation(CubicChunks.MODID, path);
    }
}
