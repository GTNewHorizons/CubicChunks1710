package com.cardinalstar.cubicchunks.server.chunkio;

import java.io.Closeable;
import java.io.Flushable;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.ChunkCoordIntPair;
import net.minecraft.world.chunk.Chunk;

import com.cardinalstar.cubicchunks.util.CubePos;
import com.cardinalstar.cubicchunks.world.cube.Cube;

public interface ICubeIO extends Flushable, Closeable {

    boolean columnExists(ChunkCoordIntPair pos);

    boolean cubeExists(CubePos pos);

    /// Saves a column. With {@code unloading}, its data stays cached after the write, for a quick reload.
    void saveColumn(ChunkCoordIntPair pos, Chunk column, boolean unloading);

    default void saveColumn(ChunkCoordIntPair pos, Chunk column) {
        saveColumn(pos, column, false);
    }

    /// Saves a cube. With {@code unloading}, its data stays cached after the write, for a quick reload.
    void saveCube(CubePos pos, Cube cube, boolean unloading);

    default void saveCube(CubePos pos, Cube cube) {
        saveCube(pos, cube, false);
    }

    NBTTagCompound loadColumn(ChunkCoordIntPair pos) throws LoadFailureException;

    NBTTagCompound loadCube(CubePos pos) throws LoadFailureException;

    void preloadColumn(ChunkCoordIntPair pos);

    void preloadCube(CubePos pos, CubeInitLevel level);

    void trimCaches();
}
