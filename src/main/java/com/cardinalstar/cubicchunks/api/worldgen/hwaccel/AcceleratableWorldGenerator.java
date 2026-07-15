package com.cardinalstar.cubicchunks.api.worldgen.hwaccel;

import net.minecraft.world.chunk.Chunk;

import org.jetbrains.annotations.Nullable;

import it.unimi.dsi.fastutil.ints.IntArrayList;

public interface AcceleratableWorldGenerator {

    ComputePlan plan(@Nullable Chunk column, int columnX, int columnZ, IntArrayList cubeY);

}
