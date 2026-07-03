package com.cardinalstar.cubicchunks.world.worldgen;

import static com.cardinalstar.cubicchunks.api.worldgen.BuiltinWorldDecorators.CUBIC_VANILLA;

import java.util.Random;

import net.minecraft.init.Blocks;

import com.cardinalstar.cubicchunks.util.DoubleInterval;
import com.cardinalstar.cubicchunks.util.Mods;
import com.cardinalstar.cubicchunks.world.worldgen.compat.DeepslateCubePopulator;
import com.cardinalstar.cubicchunks.world.worldgen.noise.OctavesSampler;
import com.cardinalstar.cubicchunks.world.worldgen.noise.ScaledSampler;
import com.gtnewhorizon.gtnhlib.util.data.LazyBlock;

import cpw.mods.fml.common.Optional;

public class WorldGenerators {

    private static final LazyBlock WATER_STILL = new LazyBlock(Mods.Minecraft, () -> Blocks.water);
    private static final LazyBlock LAVA_STILL = new LazyBlock(Mods.Minecraft, () -> Blocks.lava);

    public static void init() {
        initVanillaTerrain();
        initVanillaPopulation();

        if (Mods.EtFuturumRequiem.isModLoaded()) {
            initEFRPopulation();
        }
    }

    private static void initVanillaTerrain() {
        // CUBIC_VANILLA.terrain()
        // .addObject("noodle-caves", new NoodleCaveGenerator(), "required-by:caves-all");

        // CUBIC_VANILLA.terrain()
        // .addObject("spaghetti-caves", new SpaghettiCaveGenerator(), "required-by:caves-all");

        CUBIC_VANILLA.terrain()
            .addTarget("caves-all");

        // TODO: block carver
        // TODO: pillar caves
        // TODO: aquifers

        // CubeGeneratorsRegistry.registerVanillaPopulator(
        // "water-spouts",
        // new MapGenCaveFluids(WATER_STILL));
        //
        // CubeGeneratorsRegistry.registerVanillaPopulator(
        // "lava-spouts",
        // new MapGenCaveFluids(LAVA_STILL));
    }

    private static void initVanillaPopulation() {
        // CUBIC_VANILLA.population().addObject("biomes", new CaveBiomePopulator());
    }

    @Optional.Method(modid = Mods.ModIDs.ET_FUTURUM_REQUIEM)
    private static void initEFRPopulation() {
        CUBIC_VANILLA.population()
            .addObject("low-deepslate", new DeepslateCubePopulator() /* , "requires:biomes" */ );
    }

    private static final double CHOOSER_SCALE = 0.01;

    public static final DoubleInterval NOODLE_CAVES = new DoubleInterval(0.7, 1);
    public static final DoubleInterval PILLAR_CAVES = new DoubleInterval(0, 0.3);

    public static ScaledSampler caveChooser(Random rng) {
        return new ScaledSampler(new OctavesSampler(rng, 2), CHOOSER_SCALE);
    }
}
