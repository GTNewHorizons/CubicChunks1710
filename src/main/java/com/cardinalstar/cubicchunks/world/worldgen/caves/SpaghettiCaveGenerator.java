package com.cardinalstar.cubicchunks.world.worldgen.caves;

import java.util.Random;

import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.world.World;

import com.cardinalstar.cubicchunks.api.worldgen.decoration.ICubeGenerator;
import com.cardinalstar.cubicchunks.util.CubePos;
import com.cardinalstar.cubicchunks.world.cube.Cube;
import com.cardinalstar.cubicchunks.world.cube.blockview.CubeBlockView;
import com.cardinalstar.cubicchunks.world.worldgen.data.NoisePrecalculator;
import com.cardinalstar.cubicchunks.world.worldgen.data.SamplerFactory;
import com.cardinalstar.cubicchunks.world.worldgen.noise.NoiseSampler;
import com.cardinalstar.cubicchunks.world.worldgen.noise.OctavesSampler;
import com.cardinalstar.cubicchunks.world.worldgen.noise.ScaledSampler;

public class SpaghettiCaveGenerator implements ICubeGenerator {

    private static final double CARVE_THRESHOLD = 0.01;
    private static final double SCALE = 0.01;

    private enum Layers implements SamplerFactory {
        A {

            @Override
            public NoiseSampler createSampler(Random rng) {
                return new ScaledSampler(new OctavesSampler(rng, 3), SCALE);
            }
        },
        B {

            @Override
            public NoiseSampler createSampler(Random rng) {
                return new ScaledSampler(new OctavesSampler(rng, 3), SCALE);
            }
        };
    }

    private final NoisePrecalculator<Layers> noise = new NoisePrecalculator<>(Layers.class, 5);

    @Override
    public void pregenerate(World world, CubePos pos) {
        noise.submitPrecalculate(world, pos.getX(), pos.getY(), pos.getZ());
    }

    @Override
    public void generate(World world, Cube cube) {
        var data = noise.takeSampler(world, cube.getX(), cube.getY(), cube.getZ());

        CubeBlockView view = new CubeBlockView(cube);

        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                for (int y = 0; y < 16; y++) {
                    Block existing = view.getBlock(x, y, z);

                    if (existing != Blocks.stone) continue;

                    double a = data.sample(Layers.A, x, y, z);
                    double b = data.sample(Layers.B, x, y, z);

                    double value = a * a + b * b;

                    if (value <= CARVE_THRESHOLD) {
                        view.setBlock(x, y, z, Blocks.air, 0);
                    }
                }
            }
        }

        noise.releaseData(data);
    }
}
