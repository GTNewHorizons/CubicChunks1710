package com.cardinalstar.cubicchunks.worldgen;

import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.IChatComponent;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraft.world.chunk.IChunkProvider;

import com.cardinalstar.cubicchunks.CubicChunks;
import com.cardinalstar.cubicchunks.api.IntRange;
import com.cardinalstar.cubicchunks.api.world.ICubicWorldProvider;
import com.cardinalstar.cubicchunks.api.worldgen.BuiltinWorldDecorators;
import com.cardinalstar.cubicchunks.api.worldgen.IWorldGenerator;
import com.cardinalstar.cubicchunks.api.worldgen.IWorldGeneratorType;
import com.cardinalstar.cubicchunks.api.worldgen.WorldGeneratorTypes;

/// Cubic Chunks' own world generators. Currently, this only contains the default generator, which either wraps a
/// non-CC-aware generator in [VanillaWorldGenerator] or which proxies to an [ICubicWorldProvider].
public final class BuiltinGeneratorTypes {

    private BuiltinGeneratorTypes() {}

    /// The dimension's own generator, extended up and down with filler blocks.
    /// CC-aware world providers are automatically handled.
    public static final IWorldGeneratorType DEFAULT = new Builtin(id("default")) {

        @Override
        public boolean appliesTo(World world) {
            return true;
        }

        @Override
        public IntRange getGenerationRange(World world) {
            if (world.provider instanceof ICubicWorldProvider cubicWorldProvider) {
                return cubicWorldProvider.getGenerationRange();
            } else {
                return new IntRange(0, world.provider.getActualHeight());
            }
        }

        @Override
        public IWorldGenerator create(WorldServer world, IChunkProvider original) {
            if (world.provider instanceof ICubicWorldProvider cubicWorldProvider) {
                return cubicWorldProvider.createCubeGenerator(original);
            } else {
                return new VanillaWorldGenerator(original, world, BuiltinWorldDecorators.VANILLA);
            }
        }
    };

    public static void register(WorldGeneratorTypes registry) {
        registry.register(DEFAULT);
    }

    private static ResourceLocation id(String path) {
        return new ResourceLocation(CubicChunks.MODID, path);
    }

    private abstract static class Builtin implements IWorldGeneratorType {

        private final ResourceLocation id;

        Builtin(ResourceLocation id) {
            this.id = id;
        }

        @Override
        public ResourceLocation getId() {
            return id;
        }

        @Override
        public IChatComponent getDisplayName() {
            return new ChatComponentTranslation("cubicchunks.generator." + id.getResourcePath());
        }

        @Override
        public String toString() {
            return id.toString();
        }
    }
}
