package com.cardinalstar.cubicchunks.worldgen.ccenhanced.biome.defaults;

import net.minecraft.init.Blocks;

import com.cardinalstar.cubicchunks.worldgen.ccenhanced.biome.CCBiomeGenBase;
import com.cardinalstar.cubicchunks.worldgen.ccenhanced.biome.CCBiomeRegistry;
import com.gtnewhorizon.gtnhlib.util.data.BlockMeta;

/**
 * Default CC Enhanced biome set. 12 biomes covering the full climate space.
 * Climate point axes: [temperature, humidity, continentalness, erosion].
 *
 * <p>
 * Biome IDs 200–211 are reserved for this mod. rootHeight and heightVariation
 * follow the formula in SDD §4.3:
 * {@code surfaceY = SEA_LEVEL + rootHeight * 64 + heightVariation * hvNoise * 64}.
 */
public final class CCBiomes {

    // Biome ID range: 200–211
    private static final int ID_DEEP_OCEAN = 200;
    private static final int ID_OCEAN = 201;
    private static final int ID_BEACH = 202;
    private static final int ID_PLAINS = 203;
    private static final int ID_ROLLING_HILLS = 204;
    private static final int ID_FOREST = 205;
    private static final int ID_LARGE_HILLS = 206;
    private static final int ID_MOUNTAINS = 207;
    private static final int ID_DESERT = 208;
    private static final int ID_TUNDRA = 209;
    private static final int ID_DEEP_CANYON = 210;
    private static final int ID_OCEAN_TRENCH = 211;

    public static CCBiomeGenBase DEEP_OCEAN;
    public static CCBiomeGenBase OCEAN;
    public static CCBiomeGenBase BEACH;
    public static CCBiomeGenBase PLAINS;
    public static CCBiomeGenBase ROLLING_HILLS;
    public static CCBiomeGenBase FOREST;
    public static CCBiomeGenBase LARGE_HILLS;
    public static CCBiomeGenBase MOUNTAINS;
    public static CCBiomeGenBase DESERT;
    public static CCBiomeGenBase TUNDRA;
    public static CCBiomeGenBase DEEP_CANYON;
    public static CCBiomeGenBase OCEAN_TRENCH;

    private CCBiomes() {}

    /** Create and register all default biomes. Must be called during FML preInit. */
    public static void init() {
        // Climate points: [temperature, humidity, continentalness, erosion]
        DEEP_OCEAN = make(ID_DEEP_OCEAN, "Deep Ocean", new float[] { -0.2f, 0.5f, -0.9f, 0.5f }, -1.8f, 0.1f);
        DEEP_OCEAN.ccTopBlock = new BlockMeta(Blocks.sand, 0);
        DEEP_OCEAN.ccFillerBlock = new BlockMeta(Blocks.sand, 0);

        OCEAN = make(ID_OCEAN, "Ocean", new float[] { 0.0f, 0.5f, -0.6f, 0.4f }, -1.0f, 0.1f);
        OCEAN.ccTopBlock = new BlockMeta(Blocks.sand, 0);
        OCEAN.ccFillerBlock = new BlockMeta(Blocks.sand, 0);

        BEACH = make(ID_BEACH, "Beach", new float[] { 0.3f, 0.3f, -0.1f, 0.7f }, -0.1f, 0.05f);
        BEACH.ccTopBlock = new BlockMeta(Blocks.sand, 0);
        BEACH.ccFillerBlock = new BlockMeta(Blocks.sand, 0);

        PLAINS = make(ID_PLAINS, "Plains", new float[] { 0.4f, 0.2f, 0.3f, 0.8f }, 0.1f, 0.05f);
        PLAINS.allowVillage = true;

        ROLLING_HILLS = make(ID_ROLLING_HILLS, "Rolling Hills", new float[] { 0.3f, 0.4f, 0.4f, 0.4f }, 0.4f, 0.3f);

        FOREST = make(ID_FOREST, "Forest", new float[] { 0.1f, 0.6f, 0.5f, 0.5f }, 0.3f, 0.2f);

        LARGE_HILLS = make(ID_LARGE_HILLS, "Large Hills", new float[] { 0.1f, 0.3f, 0.6f, 0.2f }, 0.7f, 0.5f);

        MOUNTAINS = make(ID_MOUNTAINS, "Mountains", new float[] { -0.1f, 0.2f, 0.7f, -0.5f }, 1.0f, 0.8f);
        MOUNTAINS.ccTopBlock = new BlockMeta(Blocks.stone, 0);
        MOUNTAINS.ccFillerBlock = new BlockMeta(Blocks.stone, 0);
        MOUNTAINS.ccStoneBlock = new BlockMeta(Blocks.stone, 0);
        MOUNTAINS.perturbationScale = 2.0f;

        DESERT = make(ID_DESERT, "Desert", new float[] { 0.9f, -0.7f, 0.2f, 0.6f }, 0.1f, 0.1f);
        DESERT.ccTopBlock = new BlockMeta(Blocks.sand, 0);
        DESERT.ccFillerBlock = new BlockMeta(Blocks.sand, 0);
        DESERT.allowVillage = true;

        TUNDRA = make(ID_TUNDRA, "Tundra", new float[] { -0.8f, -0.2f, 0.3f, 0.5f }, 0.1f, 0.1f);

        DEEP_CANYON = make(ID_DEEP_CANYON, "Deep Canyon", new float[] { 0.5f, -0.4f, 0.5f, -0.8f }, 0.2f, 0.1f);
        DEEP_CANYON.ccTopBlock = new BlockMeta(Blocks.stone, 0);
        DEEP_CANYON.ccFillerBlock = new BlockMeta(Blocks.stone, 0);
        DEEP_CANYON.ccStoneBlock = new BlockMeta(Blocks.stone, 0);
        DEEP_CANYON.allowCanyons = true;

        OCEAN_TRENCH = make(ID_OCEAN_TRENCH, "Ocean Trench", new float[] { -0.3f, 0.5f, -0.8f, -0.9f }, -2.5f, 0.2f);
        OCEAN_TRENCH.ccTopBlock = new BlockMeta(Blocks.sand, 0);
        OCEAN_TRENCH.ccFillerBlock = new BlockMeta(Blocks.sand, 0);
        OCEAN_TRENCH.allowTrenches = true;
    }

    private static CCBiomeGenBase make(int id, String name, float[] climate, float rootHeight, float heightVariation) {
        CCBiomeGenBase b = new CCBiomeGenBase(id, name, climate);
        b.rootHeight = rootHeight;
        b.heightVariation = heightVariation;
        CCBiomeRegistry.register(b);
        return b;
    }
}
