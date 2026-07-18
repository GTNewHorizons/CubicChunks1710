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
package com.cardinalstar.cubicchunks.client;

import static net.minecraftforge.common.MinecraftForge.EVENT_BUS;

import java.util.function.Consumer;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;

import net.minecraft.client.multiplayer.ChunkProviderClient;
import net.minecraft.world.ChunkCoordIntPair;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraftforge.event.world.ChunkEvent;

import com.cardinalstar.cubicchunks.api.IColumn;
import com.cardinalstar.cubicchunks.api.XYZMap;
import com.cardinalstar.cubicchunks.api.event.CubeEvent;
import com.cardinalstar.cubicchunks.mixin.api.ICubicWorldInternal;
import com.cardinalstar.cubicchunks.mixin.early.client.IChunkProviderClient;
import com.cardinalstar.cubicchunks.util.CubePos;
import com.cardinalstar.cubicchunks.world.core.IColumnInternal;
import com.cardinalstar.cubicchunks.world.cube.BlankCube;
import com.cardinalstar.cubicchunks.world.cube.Cube;
import com.cardinalstar.cubicchunks.world.cube.ICubeProviderInternal;

// TODO: break off ICubeProviderInternal
@ParametersAreNonnullByDefault
public class CubeProviderClient extends ChunkProviderClient implements ICubeProviderInternal {

    @Nonnull
    private ICubicWorldInternal.Client world;
    @Nonnull
    private Cube blankCube;
    @Nonnull
    private XYZMap<Cube> cubeMap = new XYZMap<>();

    public CubeProviderClient(ICubicWorldInternal.Client world) {
        super((World) world);
        this.world = world;
        // chunk at Integer.MAX_VALUE will be blank chunk
        this.blankCube = new BlankCube(super.provideChunk(Integer.MAX_VALUE, 0));
    }

    @Nullable
    @Override
    public Chunk getLoadedColumn(int x, int z) {
        return getLoadedChunk(x, z);
    }

    @Override
    public Chunk provideColumn(int x, int z) {
        return provideChunk(x, z);
    }

    @Override
    public boolean cubeExists(int cubeX, int cubeY, int cubeZ) {
        return true;
    }

    @Override
    public Chunk provideChunk(int x, int z) {
        return super.provideChunk(x, z);
    }

    public Chunk getLoadedChunk(int x, int z) {
        return (Chunk) ((IChunkProviderClient) this).getChunkMapping()
            .getValueByKey(ChunkCoordIntPair.chunkXZ2Int(x, z));
    }

    @Override
    public Chunk loadChunk(int cubeX, int cubeZ) {
        return loadChunk(cubeX, cubeZ, null);
    }

    public Chunk loadChunk(int cubeX, int cubeZ, @Nullable Consumer<Chunk> init) {
        long packedCoords = ChunkCoordIntPair.chunkXZ2Int(cubeX, cubeZ);

        Chunk column = (Chunk) ((IChunkProviderClient) this).getChunkMapping()
            .getValueByKey(packedCoords);
        boolean isNew = column == null;
        if (isNew) {
            column = new Chunk((World) this.world, cubeX, cubeZ); // make a new one
            ((IColumnInternal) column).setColumn(true);

            ((IChunkProviderClient) this).getChunkMapping()
                .add(packedCoords, column);
        }

        if (init != null) init.accept(column);

        //noinspection ConstantValue
        if (isNew) {
            // fire a forge event... make mods happy :)
            EVENT_BUS.post(new ChunkEvent.Load(column));

            column.isChunkLoaded = true;
        }

        return column;
    }

    @Override
    public boolean unloadQueuedChunks() {
        return false;
    }

    // ===========================
    // ========Cube stuff=========
    // ===========================

    /**
     * This is like ChunkProviderClient.loadChunk(), but more useful for our use case
     * It is used when the server sends a new Cube to this client,
     * and the network handler wants us to create a new Cube.
     *
     * @param pos cube position
     * @return a newly created or cached cube
     */
    @Nullable
    public Cube loadCube(CubePos pos) {
        Cube cube = getLoadedCube(pos);
        if (cube != null) {
            return cube;
        }
        Chunk column = getLoadedColumn(pos.getX(), pos.getZ());
        if (column == null) {
            return null;
        }
        cube = new Cube(column, pos.getY()); // auto added to column
        ((IColumn) column).addCube(cube);
        this.cubeMap.put(cube);
        world.getLightingManager()
            .onCubeLoad(cube);
        cube.setCubeLoaded();
        EVENT_BUS.post(new CubeEvent.Load(column.worldObj, cube));
        return cube;
    }

    /**
     * This is like ChunkProviderClient.unloadChunk()
     * It is used when the server tells the client to unload a Cube.
     *
     * @param pos position to unload
     */
    public void unloadCube(CubePos pos) {
        Cube cube = getLoadedCube(pos);
        if (cube == null) {
            return;
        }
        cube.onCubeUnload();
        cubeMap.remove(pos.getX(), pos.getY(), pos.getZ());
        cube.getColumn()
            .removeCube(pos.getY());
    }

    @Override
    public Cube getCube(int cubeX, int cubeY, int cubeZ) {
        Cube cube = getLoadedCube(cubeX, cubeY, cubeZ);
        if (cube == null) {
            return blankCube;
        }
        return cube;
    }

    @Override
    public Cube getCube(CubePos coords) {
        return getCube(coords.getX(), coords.getY(), coords.getZ());
    }

    private volatile Cube lastCube;

    @Nullable
    @Override
    public Cube getLoadedCube(int cubeX, int cubeY, int cubeZ) {
        Cube c = lastCube;

        if (c != null && c.getX() == cubeX && c.getY() == cubeY && c.getZ() == cubeZ) {
            return c;
        }

        return lastCube = cubeMap.get(cubeX, cubeY, cubeZ);
    }

    @Nullable
    @Override
    public Cube getLoadedCube(CubePos coords) {
        return getLoadedCube(coords.getX(), coords.getY(), coords.getZ());
    }

    @Override
    public String makeString() {
        return "MultiplayerChunkCache: " + cubeMap.getSize()
            + "/"
            + ((IChunkProviderClient) this).getChunkMapping()
                .getNumHashElements();
    }
}
