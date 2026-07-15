/*
 * This file is part of Cubic Chunks Mod, licensed under the MIT License (MIT).
 * Copyright (c) 2015-2021 OpenCubicChunks
 * Copyright (c) 2015-2021 contributors
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package com.cardinalstar.cubicchunks;

import java.lang.management.ManagementFactory;
import java.util.HashMap;
import java.util.Map;

import javax.annotation.ParametersAreNonnullByDefault;

import net.minecraft.block.Block;

import com.cardinalstar.cubicchunks.worldgen.FillerInfo;
import com.cardinalstar.cubicchunks.worldgen.HeightInfo;
import com.gtnewhorizon.gtnhlib.config.Config;
import com.gtnewhorizon.gtnhlib.config.ConfigException;
import com.gtnewhorizon.gtnhlib.config.ConfigurationManager;
import com.gtnewhorizon.gtnhlib.util.data.BlockMeta;

import cpw.mods.fml.common.registry.GameRegistry;

@ParametersAreNonnullByDefault
@Config(modid = CubicChunks.MODID, category = "general")
public class CubicChunksConfig {

    @Config.Ignore
    public static Map<Integer, FillerInfo> configuredDimensionalFillerMap = new HashMap<>();

    @Config.LangKey("cubicchunks.config.filler_blocks")
    @Config.Comment("Determines the filler block for dimensions. Default will attempt to be auto generated based on bottom layer blocks.\n"
        + "Specifications are done as follows: 'dimensionId:direction:resourceLocation:meta'.\n"
        + "Valid directions are 'top', 'bottom', and 'both' "
        + "Meta is defaulted to 0.\n"
        + "Example:\n"
        + "     S:defaultFillerBlocks <\n"
        + "         0:bottom:minecraft:stone:3,\n"
        + "         0:top:minecraft:air,\n"
        + "         -1:both:minecraft:netherrack\n"
        + "     >\n"
        + "Spaces and tabs are not necessary. NEI is a good tool for this if you don't know the meta and resource location of blocks. (f3 + H)\n"
        + "It's highly recommend to define these, as block are chosen before population is done on chunks, meaning that if bottom layers are normally\n"
        + "populated to some block it might be missed.")
    public static String[] default_filler_blocks = { "0:bottom:minecraft:stone", "0:top:minecraft:air",
        "-1:both:minecraft:netherrack", "1:both:minecraft:air" };

    @Config.Ignore
    public static Map<Integer, HeightInfo> configuredDimensionalHeightMap = new HashMap<>();

    @Config.LangKey("cubicchunks.config.dimensional_height_limits")
    @Config.Comment("Set the height limits for dimensions specifically. Note that this will override the default max and min heights. \n"
        + "Format is dimensionId:top/bottom:height.\n"
        + "Note that these heights must be multiples of 16. If not multiples of 16 they will be rounded towards 0 on the nearest multiple of 16.\n"
        + "Example:\n"
        + "     S:dimensional_height_overrides <\n"
        + "         0:bottom:-128, \n"
        + "         0:top:1024 \n"
        + "     >\n")
    public static String[] dimensional_height_overrides = {};

    @Config.LangKey("cubicchunks.config.vert_view_distance")
    @Config.Comment("Similar to Minecraft's view distance, only for vertical chunks. Automatically adjusted by vertical view distance slider on the"
        + " client. Does not affect rendering, only what chunks are sent to client.")
    public static int verticalCubeLoadDistance = 8;

    @Config.LangKey("cubicchunks.config.enable_chunk_debugging")
    @Config.Comment("Displays coloured boxes over non-empty cubes for debugging purposes.")
    public static boolean enableChunkStatusDebugging = false;

    @Config.LangKey("cubicchunks.config.dump_compute_code")
    @Config.Comment("Dumps compute shader code.")
    public static boolean dumpComputeShaderCode = false;

    @Config.LangKey("cubicchunks.config.relight_checks_per_tick_per_column")
    @Config.Comment("In an attempt to fix lighting glitches over time, cubic chunks will keep updating light in specified amount of blocks per "
        + "column (chunk) per tick. This option shouldn't be necessary but may be useful for old worlds where lighting is broken or when "
        + "lighting bugs are encountered.")
    public static int relightChecksPerTickPerColumn = 0;

    @Config.LangKey("cubicchunks.config.do_client_light_fixes")
    @Config.Comment("By default cubic chunks will attempt to go over all the blocks over time to fix lighting only on server. Enable this to also "
        + "fix lighting on the clientside.")
    public static boolean doClientLightFixes = false;

    @Config.LangKey("cubicchunks.config.storage_format")
    @Config.Comment("The storage format. Note: this will be used for all newly created worlds. Existing worlds will continue to use the format they were created with.\n"
        + "If empty, the storage format for new worlds will be determined automatically.")
    public static String storageFormat = "";

    @Config.LangKey("cubicchunks.config.spawn_generate_distance_horizontal")
    @Config.Comment("Horizontal distance for initially generated spawn area")
    public static int spawnGenerateDistanceXZ = 12;

    @Config.LangKey("cubicchunks.config.spawn_generate_distance_vertical")
    @Config.Comment("Vertical distance for initially generated spawn area")
    public static int spawnGenerateDistanceY = 8;

    @Config.LangKey("cubicchunks.config.spawn_forceload_distance_horizontal")
    @Config.Comment("Horizontal distance for spawn chunks kept loaded in memory")
    public static int spawnLoadDistanceXZ = 8;

    @Config.LangKey("cubicchunks.config.spawn_forceload_distance_vertical")
    @Config.Comment("Vertical distance for spawn chunks kept loaded in memory")
    public static int spawnLoadDistanceY = 8;

    @Config.LangKey("cubicchunks.config.default_min_height")
    @Config.Comment("World min height. Values that are not an integer multiple of 16 may cause unintended behavior")
    @Config.RangeInt(min = CubicChunks.MIN_SUPPORTED_BLOCK_Y, max = 0)
    public static int defaultMinHeight = -(1 << 30);

    @Config.LangKey("cubicchunks.config.default_max_height")
    @Config.Comment("World max height. Values that are not an integer multiple of 16 may cause unintended behavior")
    @Config.RangeInt(min = 16, max = CubicChunks.MAX_SUPPORTED_BLOCK_Y)
    public static int defaultMaxHeight = 1 << 30;

    @Config.LangKey("cubicchunks.config.worldgen_watchdog_time_limit")
    @Config.Comment("Maximum amount of time (milliseconds) generating a single chunk can take in vanilla compatibility generator before forcing a "
        + "crash.")
    public static int worldgenWatchdogTimeLimit = 10000;

    @Config.LangKey("cubicchunks.config.use_shadow_paging_io")
    @Config.Comment("Whether cubic chunks save format IO should use shadow paging. This may be slightly slower and use "
        + "a bit more storage but should significantly improve reliability in case of improper shutdown.")
    @Config.RequiresWorldRestart
    public static boolean useShadowPagingIO = true;

    @Config.LangKey("cubicchunks.config.disable_lighting")
    @Config.Comment("Disables all light propagation")
    public static boolean disableLighting = false;

    @Config.Ignore
    public static int defaultMaxCubesPerChunkloadingTicket = 25 * 16;

    @Config.Ignore
    public static Map<String, Integer> modMaxCubesPerChunkloadingTicket = new HashMap<>();

    @Config.LangKey("cubicchunks.config.optimizations")
    @Config.Comment("Options controlling various optimizations.")
    public static Optimizations optimizations = new Optimizations();

    public static final class Optimizations {

        @Config.LangKey("cubicchunks.config.optimizations.background_threads")
        @Config.Comment("Maximum number of threads to use for background tasks (world I/O, noise generation, etc).")
        public int backgroundThreads = ManagementFactory.getOperatingSystemMXBean()
            .getAvailableProcessors() / 2;

    }

    static {
        modMaxCubesPerChunkloadingTicket.put("cubicchunks", defaultMaxCubesPerChunkloadingTicket);
    }

    public static void init() throws ConfigException {
        validateConfigValues();
        initDimensionalConfiguration();
    }

    private static void validateConfigValues() {
        if ((defaultMinHeight & 0xF) != 0) {
            CubicChunks.LOGGER.error(
                "CubicChunksConfig: defaultMinHeight not a multiple of 16, got {}, setting to {}",
                defaultMinHeight,
                defaultMinHeight & ~0xF);
            defaultMinHeight &= ~0xF;
        }
        if ((defaultMaxHeight & 0xF) != 0) {
            CubicChunks.LOGGER.error(
                "CubicChunksConfig: defaultMaxHeight not a multiple of 16, got {}, setting to {}",
                defaultMaxHeight,
                defaultMaxHeight & ~0xF);
            defaultMaxHeight &= ~0xF;
        }
    }

    private static void initDimensionalConfiguration() throws ConfigException {
        // Height Limit Configs
        int i = 1;
        for (String config : dimensional_height_overrides) {
            String[] entries = config.split(":");
            if (entries.length != 3) {
                throw new ConfigException("Dimensional Height Overrides configuration is malformed for entry: " + i);
            }

            try {
                int dimension = Integer.parseInt(entries[0]);
                int height = Integer.parseInt(entries[2]);
                if ((height & 0xF) != 0) {
                    CubicChunks.LOGGER.error(
                        "CubicChunksConfig: dimensional height configured to a non multiple of 16, got {}, setting to {}",
                        height,
                        height & ~0xF);
                    height &= ~0xF;
                }

                HeightInfo info = configuredDimensionalHeightMap.getOrDefault(dimension, new HeightInfo());

                String direction = entries[1];
                if (direction.equals("bottom")) {
                    info.minHeight = height;
                    if (info.maxHeight == null) {
                        info.maxHeight = defaultMaxHeight;
                    }
                } else if (direction.equals("top")) {
                    info.maxHeight = height;
                    if (info.minHeight == null) {
                        info.minHeight = defaultMinHeight;
                    }
                } else {
                    throw new ConfigException(
                        "Directional argument for height limits is invalid for entry: " + i
                            + ". Invalid:"
                            + direction
                            + " Valid directions bottom and top.");
                }

                configuredDimensionalHeightMap.put(dimension, info);
            } catch (NumberFormatException e) {
                throw new ConfigException("Dimensional Height Overrides configuration is malformed for entry: " + i);
            }
            i++;
        }

        i = 1;
        // Filler Configs
        for (String config : default_filler_blocks) {
            String[] entries = config.split(":");
            if (entries.length != 4 && entries.length != 5) {
                throw new ConfigException("Dimensional Filler block configuration is malformed for entry: " + i);
            }
            try {
                int dimension = Integer.parseInt(entries[0]);
                Block fillerBlock = GameRegistry.findBlock(entries[2], entries[3]);
                if (fillerBlock == null) {
                    throw new ConfigException(
                        "Dimensional Height Overrides configuration is malformed for entry: " + i
                            + ". Block not found for resource location "
                            + entries[2]
                            + ":"
                            + entries[3]);
                }
                int meta = entries.length == 4 ? 0 : Integer.parseInt(entries[4]);

                FillerInfo info = configuredDimensionalFillerMap.getOrDefault(dimension, new FillerInfo());

                String direction = entries[1];
                if (direction.equals("bottom")) {
                    info.bottomFiller = new BlockMeta(fillerBlock, meta);
                } else if (direction.equals("top")) {
                    info.topFiller = new BlockMeta(fillerBlock, meta);
                } else if (direction.equals("both")) {
                    info.bottomFiller = new BlockMeta(fillerBlock, meta);
                    info.topFiller = new BlockMeta(fillerBlock, meta);
                } else {
                    throw new ConfigException(
                        "Directional argument for filler blocks is invalid for entry: " + i
                            + ". Invalid:"
                            + direction
                            + " Valid directions are both, bottom, and top.");
                }

                configuredDimensionalFillerMap.put(dimension, info);
            } catch (NumberFormatException e) {
                throw new ConfigException("Dimensional filler default configuration is malformed for entry: " + i);
            }
        }
    }

    public static void setVerticalViewDistance(int value) {
        verticalCubeLoadDistance = value;
        ConfigurationManager.save(CubicChunksConfig.class);
    }
}
