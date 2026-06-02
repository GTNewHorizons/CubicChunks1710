package com.cardinalstar.cubicchunks.worldgen.ccenhanced.biome;

import net.minecraft.init.Blocks;
import net.minecraft.world.biome.BiomeGenBase;

import com.gtnewhorizon.gtnhlib.util.data.BlockMeta;
import com.gtnewhorizon.gtnhlib.util.data.ImmutableBlockMeta;

/**
 * Base class for CC Enhanced biomes.
 *
 * <p>
 * Extends BiomeGenBase and adds:
 * <ul>
 * <li>A 4D climate point for Voronoi biome lookup (temperature, humidity, continentalness, erosion)</li>
 * <li>ImmutableBlockMeta surface block fields (shadow the plain Block fields on BiomeGenBase)</li>
 * <li>Additional terrain-shaping parameters (perturbationScale, fillerDepth)</li>
 * <li>Carver eligibility flags (allowVillage, allowCanyons, allowTrenches)</li>
 * </ul>
 *
 * <p>
 * Vanilla fields rootHeight and heightVariation (inherited from BiomeGenBase) are reused
 * as terrain shaping parameters in the heightmap formula.
 */
public class CCBiomeGenBase extends BiomeGenBase {

    /** Position in climate space. Axis order: [temperature, humidity, continentalness, erosion]. */
    public final float[] climatePoint;

    // --- Carver / structure flags ---
    public boolean allowVillage = false;
    public boolean allowCanyons = false;
    public boolean allowTrenches = false;

    // --- Surface block fields (ImmutableBlockMeta shadows BiomeGenBase.topBlock/fillerBlock) ---
    /** Block placed at the topmost solid layer (e.g. grass, sand). */
    public ImmutableBlockMeta ccTopBlock = new BlockMeta(Blocks.grass, 0);
    /** Block placed in the filler layer directly below the top block (e.g. dirt). */
    public ImmutableBlockMeta ccFillerBlock = new BlockMeta(Blocks.dirt, 0);
    /** Block used for all solid blocks below the filler layer (e.g. stone). */
    public ImmutableBlockMeta ccStoneBlock = new BlockMeta(Blocks.stone, 0);

    // --- Extra terrain parameters ---
    /** Multiplier on the 3D perturbation noise amplitude. >1 = more overhangs/roughness. */
    public float perturbationScale = 1.0f;
    /** How many blocks below the top block receive ccFillerBlock. */
    public int fillerDepth = 4;

    /**
     * @param biomeId      Biome registry ID (0–255); registered in BiomeGenBase.biomeList
     * @param name         Human-readable biome name
     * @param climatePoint 4D climate position [temperature, humidity, continentalness, erosion]
     */
    public CCBiomeGenBase(int biomeId, String name, float[] climatePoint) {
        super(biomeId);
        setBiomeName(name);
        this.climatePoint = climatePoint;
    }
}
