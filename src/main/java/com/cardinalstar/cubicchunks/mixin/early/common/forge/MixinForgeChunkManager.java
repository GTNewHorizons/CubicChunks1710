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
package com.cardinalstar.cubicchunks.mixin.early.common.forge;

import java.util.Map;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;
import net.minecraftforge.common.ForgeChunkManager;
import net.minecraftforge.common.ForgeChunkManager.Ticket;
import net.minecraftforge.common.ForgeChunkManager.Type;

import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.Slice;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.cardinalstar.cubicchunks.world.chunkloader.CubicChunkManager;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;

@Mixin(ForgeChunkManager.class)
public abstract class MixinForgeChunkManager {

    @Shadow(remap = false)
    private static int dormantChunkCacheSize;

    // Only insert into dormantChunkCache when dormant chunk caching is actually enabled.
    // Forge does this unconditionally, which creates a cache entry even when the feature is off.
    @WrapOperation(
        method = "loadWorld",
        at = @At(
            value = "INVOKE",
            target = "Ljava/util/Map;put(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;"),
        slice = @Slice(
            from = @At(
                value = "FIELD",
                target = "Lnet/minecraftforge/common/ForgeChunkManager;dormantChunkCacheSize:I",
                remap = false,
                opcode = Opcodes.GETSTATIC)),
        remap = false)
    private static Object guardDormantCachePut(Map<Object, Object> map, Object world, Object cache,
        Operation<Object> op) {
        if (dormantChunkCacheSize != 0) {
            return op.call(map, world, cache);
        }
        return null;
    }

    // Route ticket construction through CubicChunkManager.makeTicket() because the Ticket
    // constructor is package-private and unreachable from outside net.minecraftforge.common.
    @Redirect(
        method = "loadWorld",
        at = @At(value = "NEW", target = "net/minecraftforge/common/ForgeChunkManager$Ticket"),
        remap = false)
    private static ForgeChunkManager.Ticket redirectTicketNew(String modId, ForgeChunkManager.Type type, World world) {
        return CubicChunkManager.makeTicket(modId, type, world);
    }

    // After each ticket is created, read back the cubic chunks NBT data (cube Y coordinates).
    @WrapOperation(
        method = "loadWorld",
        at = @At(value = "NEW", target = "net/minecraftforge/common/ForgeChunkManager$Ticket"),
        remap = false)
    private static Ticket injectDeserializeTicket(String modId, Type type, World world, Operation<Ticket> original,
        @Local(name = "ticket") NBTTagCompound tag) {
        Ticket ticket = original.call(modId, type, world);

        CubicChunkManager.onDeserializeTicket(tag, ticket);
        return ticket;
    }

    // After Forge loads the entity's 2D chunk column, also force-load the entity's cube in Y.
    @Inject(
        method = "loadWorld",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/World;getChunkFromChunkCoords(II)Lnet/minecraft/world/chunk/Chunk;",
            shift = At.Shift.AFTER),
        remap = false)
    private static void injectLoadEntityCube(World world, CallbackInfo ci,
        @Local(name = "tick") ForgeChunkManager.Ticket tick) {
        CubicChunkManager.onLoadEntityTicketChunk(world, tick);
    }

    @Inject(method = "saveWorld", at = @At(value = "CONSTANT", args = "stringValue=ChunkListDepth"), remap = false)
    private static void onSaveTicket(World world, CallbackInfo ci, @Local(name = "tick") ForgeChunkManager.Ticket tick,
        @Local(name = "ticket") NBTTagCompound ticket) {
        CubicChunkManager.onSerializeTicket(ticket, tick);
    }
}
