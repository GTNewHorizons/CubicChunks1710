package com.cardinalstar.cubicchunks.server.chunkio;

import java.io.Closeable;
import java.io.Flushable;

import net.minecraft.world.chunk.Chunk;

import com.cardinalstar.cubicchunks.api.util.Box;
import com.cardinalstar.cubicchunks.world.api.ICubeProviderServer.Requirement;
import com.cardinalstar.cubicchunks.world.cube.Cube;

public interface ICubeLoader extends Flushable, Closeable {

    void pauseLoadCalls();

    void unpauseLoadCalls();

    Chunk getColumn(int x, int z, Requirement effort);

    default boolean columnExists(int x, int z) {
        return getColumn(x, z, Requirement.GET_CACHED) != null;
    }

    Cube getLoadedCube(int x, int y, int z);

    boolean cubeExists(int x, int y, int z);

    Cube getCube(int x, int y, int z, Requirement effort);

    /// Notifies this loader that a cube was generated further, either by a populator or something else.
    void onCubeGenerated(Cube cube);

    void cacheCubes(int x, int y, int z, int spanx, int spany, int spanz);

    void setNow(long now);

    default void cacheCubes(Box box) {
        cacheCubes(
            box.getX1(),
            box.getY1(),
            box.getZ1(),
            box.getX2() - box.getX1() + 1,
            box.getY2() - box.getY1() + 1,
            box.getZ2() - box.getZ1() + 1);
    }

    void uncacheCubes();

    void unloadCube(int x, int y, int z);

    void save(boolean saveAll);

    void saveColumn(Chunk column);

    void saveCube(Cube cube);

    void doGC();
}
