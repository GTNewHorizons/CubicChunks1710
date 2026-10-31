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
package com.cardinalstar.cubicchunks.world.chunkloader;

import java.util.HashMap;
import java.util.Map;

import javax.annotation.ParametersAreNonnullByDefault;

import net.minecraft.entity.Entity;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.world.ChunkCoordIntPair;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.ForgeChunkManager;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.Constants;

import com.cardinalstar.cubicchunks.CubicChunksConfig;
import com.cardinalstar.cubicchunks.mixin.early.common.forge.IForgeChunkManager;
import com.cardinalstar.cubicchunks.util.Coords;
import com.cardinalstar.cubicchunks.util.CubePos;
import com.cardinalstar.cubicchunks.util.ITicket;
import com.cardinalstar.cubicchunks.world.ICubicWorld;
import com.cardinalstar.cubicchunks.world.core.ICubicTicketInternal;
import com.cardinalstar.cubicchunks.world.cube.Cube;
import com.gtnewhorizon.gtnhlib.eventbus.EventBusSubscriber;

import cpw.mods.fml.common.FMLLog;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import it.unimi.dsi.fastutil.ints.IntArraySet;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;

/**
 * CubicChunks extension of {@link ForgeChunkManager}
 */
// TODO: figure out a way to put the right parts of it into API
@ParametersAreNonnullByDefault
@EventBusSubscriber
public class CubicChunkManager {

    /** The cube Y range a vanilla column ticket loads: the vanilla world height, Y 0 to 255. */
    private static final int COLUMN_TICKET_MIN_CUBE_Y = 0;
    private static final int COLUMN_TICKET_MAX_CUBE_Y = 15;

    /**
     * ForgeChunkManager doesn't give any way to work with Y coordinates for forced chunks.
     *
     * A column forced through ForgeChunkManager keeps the cubes of the vanilla world height loaded (see
     * onForgeChunkManagerForceChunk), so chunk loaders written for vanilla keep working. The cubes a ticket holds are
     * stored with the ticket, in the same map as the cubes forced through the cubic API.
     */

    /**
     * Force the supplied chunk coordinate to be loaded by the supplied ticket. If the ticket's maxDepth is
     * exceeded, the least
     * recently registered chunk is unforced and may be unloaded.
     * It is safe to force the chunk several times for a ticket, it will not generate duplication or change the
     * ordering.
     *
     * @param ticket The ticket registering the chunk
     * @param chunk  The chunk to force
     */
    public static void forceChunk(ForgeChunkManager.Ticket ticket, CubePos chunk) {
        if (ticket == null || chunk == null) {
            return;
        }
        if (ticket.getType() == ForgeChunkManager.Type.ENTITY && ticket.getEntity() == null) {
            throw new RuntimeException("Attempted to use an entity ticket to force a chunk, without an entity");
        }
        if (ticket.isPlayerTicket() ? !IForgeChunkManager.getPlayerTickets()
            .containsValue(ticket)
            : !IForgeChunkManager.getTickets()
                .get(ticket.world)
                .containsEntry(ticket.getModId(), ticket)) {
            FMLLog.getLogger()
                .fatal(
                    "The mod {} attempted to force load a chunk with an invalid ticket. This is not permitted.",
                    ticket.getModId());
            return;
        }
        ((ICubicTicketInternal) ticket).addRequestedCube(chunk);
        Cube cube = (Cube) ((ICubicWorld) ticket.world).getCubeFromCubeCoords(chunk);
        cube.getTickets()
            .add((ICubicTicketInternal) ticket);
        MinecraftForge.EVENT_BUS.post(new ForceCubeEvent(ticket, chunk));

        if (((ICubicTicketInternal) ticket).getMaxCubeDepth() > 0 && ((ICubicTicketInternal) ticket).requestedCubes()
            .size() > ((ICubicTicketInternal) ticket).getMaxCubeDepth()) {
            CubePos removed = ((ICubicTicketInternal) ticket).requestedCubes()
                .iterator()
                .next();
            unforceChunk(ticket, removed);
        }
    }

    /**
     * Reorganize the internal chunk list so that the chunk supplied is at the *end* of the list
     * This helps if you wish to guarantee a certain "automatic unload ordering" for the chunks
     * in the ticket list
     *
     * @param ticket The ticket holding the chunk list
     * @param chunk  The chunk you wish to push to the end (so that it would be unloaded last)
     */
    public static void reorderChunk(ForgeChunkManager.Ticket ticket, CubePos chunk) {
        if (ticket == null || chunk == null
            || !((ICubicTicketInternal) ticket).requestedCubes()
                .contains(chunk)) {
            return;
        }
        ((ICubicTicketInternal) ticket).removeRequestedCube(chunk);
        ((ICubicTicketInternal) ticket).addRequestedCube(chunk);
    }

    /**
     * Unforce the supplied chunk, allowing it to be unloaded and stop ticking.
     *
     * @param ticket The ticket holding the chunk
     * @param chunk  The chunk to unforce
     */
    public static void unforceChunk(ForgeChunkManager.Ticket ticket, CubePos chunk) {
        if (ticket == null || chunk == null) {
            return;
        }
        ((ICubicTicketInternal) ticket).removeRequestedCube(chunk);
        MinecraftForge.EVENT_BUS.post(new UnforceCubeEvent(ticket, chunk));
        Cube cube = (Cube) ((ICubicWorld) ticket.world).getCubeFromCubeCoords(chunk);
        cube.getTickets()
            .remove((ICubicTicketInternal) ticket);
    }

    // internals

    public static void onDeserializeTicket(NBTTagCompound ticketNBT, ForgeChunkManager.Ticket ticket) {
        NBTTagCompound cubicNBT = ticketNBT.getCompoundTag("cubicchunks");
        if (cubicNBT == null) {
            return;
        }
        // the cube Y of the entity an entity ticket is bound to, so that loading the world can find it again
        ((ICubicTicketInternal) ticket).setEntityChunkY(cubicNBT.getInteger("entityCubeY"));
        Map<ChunkCoordIntPair, IntSet> coordsMap = new HashMap<>();

        NBTTagList chunkMap = cubicNBT.getTagList("chunkMap", Constants.NBT.TAG_COMPOUND);
        for (int i = 0; i < chunkMap.tagCount(); i++) {
            NBTTagCompound entry = chunkMap.getCompoundTagAt(i);
            int x = entry.getInteger("x");
            int z = entry.getInteger("z");
            IntSet cubes = new IntArraySet(entry.getIntArray("cubes"));
            coordsMap.put(new ChunkCoordIntPair(x, z), cubes);
        }
        // Only store the cube Y coords, don't load. Mods force their columns and cubes again in their loading callback:
        // a column forced again holds the vanilla world height (onForgeChunkManagerForceChunk), cubes forced through
        // the cubic API are held one by one. Stored Y values are a record only and are not loaded by themselves.
        ((ICubicTicketInternal) ticket).setAllForcedChunkCubes(coordsMap);
    }

    public static void onSerializeTicket(NBTTagCompound ticket, ForgeChunkManager.Ticket tick) {
        Entity entity = tick.getType() == ForgeChunkManager.Type.ENTITY ? tick.getEntity() : null;
        if (((ICubicTicketInternal) tick).getAllForcedChunkCubes()
            .isEmpty() && entity == null) {
            return;
        }
        NBTTagCompound cubicNBT = new NBTTagCompound();
        // Forge saves the column of the entity's chunk coordinates; its cube is the matching chunkCoordY
        cubicNBT.setInteger(
            "entityCubeY",
            entity != null ? entity.chunkCoordY : ((ICubicTicketInternal) tick).getEntityChunkY());

        NBTTagList chunkMap = new NBTTagList();
        ((ICubicTicketInternal) tick).getAllForcedChunkCubes()
            .forEach((pos, cubes) -> {
                NBTTagCompound entry = new NBTTagCompound();
                entry.setInteger("x", pos.chunkXPos);
                entry.setInteger("z", pos.chunkZPos);
                entry.setIntArray("cubes", cubes.toIntArray());
                chunkMap.appendTag(entry);
            });
        cubicNBT.setTag("chunkMap", chunkMap);

        ticket.setTag("cubicchunks", cubicNBT);
    }

    public static void onLoadEntityTicketChunk(World world, ForgeChunkManager.Ticket tick) {
        ICubicTicketInternal ticket = (ICubicTicketInternal) tick;
        ((ICubicWorld) world)
            .getCubeFromCubeCoords(ticket.getEntityChunkX(), ticket.getEntityChunkY(), ticket.getEntityChunkZ());
    }

    /**
     * A vanilla column ticket has no Y, so it keeps the cubes of the vanilla world height (Y 0 to 255) of its column
     * loaded, within the world's height limits. Mods that need other heights use
     * {@link #forceChunk(ForgeChunkManager.Ticket, CubePos)}.
     * Forge posts this every time a column is forced, also when mods force their columns again after a restart.
     */
    @SubscribeEvent
    public static void onForgeChunkManagerForceChunk(ForgeChunkManager.ForceChunkEvent event) {
        ForgeChunkManager.Ticket ticket = event.ticket;
        World world = ticket.world;
        if (!(world instanceof WorldServer)) {
            return;
        }
        ICubicWorld cubicWorld = (ICubicWorld) world;
        ICubicTicketInternal cubicTicket = (ICubicTicketInternal) ticket;
        ChunkCoordIntPair column = event.location;

        int minCubeY = Math.max(COLUMN_TICKET_MIN_CUBE_Y, Coords.blockToCube(cubicWorld.getMinHeight()));
        int maxCubeY = Math.min(COLUMN_TICKET_MAX_CUBE_Y, Coords.blockToCube(cubicWorld.getMaxHeight() - 1));

        IntSet forcedCubes = new IntOpenHashSet();
        IntSet previous = cubicTicket.getAllForcedChunkCubes()
            .get(column);
        if (previous != null) {
            forcedCubes.addAll(previous);
        }
        for (int cubeY = minCubeY; cubeY <= maxCubeY; cubeY++) {
            Cube cube = (Cube) cubicWorld.getCubeFromCubeCoords(column.chunkXPos, cubeY, column.chunkZPos);
            cube.getTickets()
                .add(cubicTicket);
            forcedCubes.add(cubeY);
        }
        cubicTicket.setForcedChunkCubes(column, forcedCubes);
    }

    @SubscribeEvent
    public static void onForgeChunkManagerUnforceChunk(ForgeChunkManager.UnforceChunkEvent event) {
        ForgeChunkManager.Ticket ticket = event.ticket;
        World world = ticket.world;
        IntSet forcedCubes = ((ICubicTicketInternal) ticket).getAllForcedChunkCubes()
            .get(event.location);
        if (forcedCubes == null) {
            return;
        }
        for (int cubeY : forcedCubes) {
            // A cube that is not loaded holds no ticket: after a restart the saved Y values are known before the mod
            // forces its columns again. Loading it here would only load or generate it to remove nothing.
            Cube cube = (Cube) ((ICubicWorld) world).getCubeCache()
                .getLoadedCube(event.location.chunkXPos, cubeY, event.location.chunkZPos);
            if (cube != null) {
                cube.getTickets()
                    .remove((ITicket) ticket);
            }
        }
        ((ICubicTicketInternal) ticket).clearForcedChunkCubes(event.location);
    }

    public static int getCubeDepthFor(String modId) {
        return CubicChunksConfig.modMaxCubesPerChunkloadingTicket
            .getOrDefault(modId, CubicChunksConfig.defaultMaxCubesPerChunkloadingTicket);
    }
}
