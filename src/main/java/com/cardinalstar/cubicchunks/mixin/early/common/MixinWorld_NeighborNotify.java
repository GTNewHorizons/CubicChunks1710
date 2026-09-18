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
package com.cardinalstar.cubicchunks.mixin.early.common;

import net.minecraft.block.Block;
import net.minecraft.world.World;

import org.apache.commons.lang3.mutable.MutableBoolean;
import org.apache.commons.lang3.mutable.MutableInt;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

import com.gtnewhorizon.gtnhlib.util.CoordinatePacker;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

import it.unimi.dsi.fastutil.longs.LongArrayFIFOQueue;

/**
 * Flattens the synchronous neighbour-notify cascade that overflows the stack
 * during cubic world generation (issue #61).
 *
 * <p>
 * Vanilla propagates support-dependency chains (snow, reeds, leaves, cacti,
 * falling blocks, ...) through {@code setBlockToAir -> markAndNotifyBlock ->
 * notifyBlockOfNeighborChange -> onNeighborBlockChange -> ...} as a synchronous
 * recursion. Vanilla keeps that chain shallow via the hard height bounds; a
 * cubic world removes those bounds, so a large patch of support-dependent
 * blocks losing support at once can recurse until the stack overflows.
 *
 * <p>
 * This mixin wraps the actual {@code onNeighborBlockChange} call: while the
 * depth is under the cap it runs normally, and past the cap the coordinate is
 * parked in a thread-local FIFO instead. The outermost frame drains the FIFO
 * iteratively, so notify semantics are preserved (no floating snow, no dropped
 * updates) — only the recursion is flattened.
 *
 * <p>
 * Depth accounting lives in a {@code try/finally} so it can never leak, and
 * a separate "draining" flag keeps the drain iterative (nested drains only
 * enqueue and let the already-running drain pick the entries up), which is what
 * keeps redstone/mechanical blocks working: they only ever see the normal,
 * synchronous path unless a genuinely pathological cascade is in progress.
 *
 * <p>
 * Known limitation: for very tall vertical chains (e.g. a reed stack taller
 * than the depth cap), the cascade is flattened in batches and the remainder
 * falls back to vanilla random ticks over a few seconds. This does not affect
 * vanilla-sized features (reeds are capped at 3 blocks) and only trades a stack
 * overflow for a short delay in that extreme case.
 */
@Mixin(World.class)
public class MixinWorld_NeighborNotify {

    private static final Logger LOGGER = LogManager.getLogger("CubicChunks.MixinWorld_NeighborNotify");

    @Unique
    private static final int CC_MAX_NOTIFY_DEPTH = 64;

    @Unique
    private static final ThreadLocal<MutableInt> cc$notifyDepth = ThreadLocal.withInitial(MutableInt::new);

    @Unique
    private static final ThreadLocal<LongArrayFIFOQueue> cc$pendingNotifies = ThreadLocal
        .withInitial(LongArrayFIFOQueue::new);

    @Unique
    private static final ThreadLocal<MutableBoolean> cc$draining = ThreadLocal.withInitial(MutableBoolean::new);

    @WrapOperation(
        method = "notifyBlockOfNeighborChange",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/block/Block;onNeighborBlockChange(Lnet/minecraft/world/World;IIILnet/minecraft/block/Block;)V"))
    private void cc$guardNeighborNotify(Block block, World world, int x, int y, int z, Block neighbor,
        Operation<Void> original) {
        MutableInt depth = cc$notifyDepth.get();
        depth.increment();
        try {
            if (depth.intValue() <= CC_MAX_NOTIFY_DEPTH) {
                original.call(block, world, x, y, z, neighbor);
            } else {
                cc$pendingNotifies.get()
                    .enqueue(CoordinatePacker.pack(x, y, z));
            }
        } finally {
            depth.decrement();
            if (depth.intValue() == 0 && !cc$draining.get()
                .booleanValue()) {
                cc$draining.get()
                    .setTrue();
                try {
                    cc$drain((World) (Object) this, neighbor);
                } finally {
                    cc$draining.get()
                        .setFalse();
                }
            }
        }
    }

    @Unique
    private void cc$drain(World world, Block neighbor) {
        LongArrayFIFOQueue pending = cc$pendingNotifies.get();
        while (!pending.isEmpty()) {
            long packed = pending.dequeueLong();
            int px = CoordinatePacker.unpackX(packed);
            int py = CoordinatePacker.unpackY(packed);
            int pz = CoordinatePacker.unpackZ(packed);
            Block block = world.getBlock(px, py, pz);
            if (block == null) {
                continue;
            }
            try {
                block.onNeighborBlockChange(world, px, py, pz, neighbor);
            } catch (Throwable t) {
                // Never let a single bad neighbour abort the iterative drain, but
                // keep it visible for debugging instead of failing silently.
                LOGGER.warn("Failed to notify neighbour at ({}, {}, {})", px, py, pz, t);
            }
        }
    }
}
