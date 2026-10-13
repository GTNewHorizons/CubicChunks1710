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

    void saveColumn(ChunkCoordIntPair pos, Chunk column);

    void saveCube(CubePos pos, Cube cube);

    NBTTagCompound loadColumn(ChunkCoordIntPair pos) throws LoadFailureException;

    NBTTagCompound loadCube(CubePos pos) throws LoadFailureException;

    void preloadColumn(ChunkCoordIntPair pos);

    void preloadCube(CubePos pos, CubeInitLevel level);

    void trimCaches();
}
