package com.cardinalstar.cubicchunks.api;

import java.util.Collection;
import java.util.Iterator;

import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;

import org.jetbrains.annotations.Nullable;

import com.cardinalstar.cubicchunks.world.ICubicWorld;
import com.cardinalstar.cubicchunks.world.api.ICubeProviderServer;
import com.cardinalstar.cubicchunks.world.api.ICubeProviderServer.Requirement;
import com.cardinalstar.cubicchunks.world.column.EmptyEBS;
import com.cardinalstar.cubicchunks.world.cube.Cube;
import com.google.common.collect.AbstractIterator;

@SuppressWarnings("unused")
public final class CCAPI {

    private CCAPI() {}

    public static ExtendedBlockStorage getBlockStorage(Chunk chunk, int yLevel) {
        ICube cube = ((IColumn) chunk).getCube(yLevel);

        if (cube == null) return new EmptyEBS(yLevel);

        return cube.getStorage();
    }

    /// Gets a loaded cube. Does not load or generate the cube. Will return null if the cube is not loaded.
    @Nullable
    public static ICube getLoadedCube(World world, int cubeX, int cubeY, int cubeZ) {
        return ((ICubicWorld) world).getCubeCache()
            .getLoadedCube(cubeX, cubeY, cubeZ);
    }

    /// Gets a cube. Does terrain generation if the cube is not in memory and could not be loaded from disk.
    public static ICube getCube(World world, int cubeX, int cubeY, int cubeZ) {
        return ((ICubicWorld) world).getCubeFromCubeCoords(cubeX, cubeY, cubeZ);
    }

    /// Gets a cube. The returned cube's status will match the given [Requirement].
    public static ICube getCube(World world, int cubeX, int cubeY, int cubeZ, Requirement effort) {
        return ((ICubeProviderServer) ((ICubicWorld) world).getCubeCache()).getCube(cubeX, cubeY, cubeZ, effort);
    }

    /// Gets all loaded cubes in a column.
    public static Collection<Cube> getLoadedCubes(Chunk chunk) {
        // noinspection unchecked
        return (Collection<Cube>) ((IColumn) chunk).getLoadedCubes();
    }

    /// Gets all loaded block storages in a column.
    public static Iterable<ExtendedBlockStorage> getLoadedBlockStorages(Chunk chunk) {
        return () -> new AbstractIterator<>() {

            private final Iterator<? extends Cube> iter = ((IColumn) chunk).getLoadedCubes()
                .iterator();

            @Override
            protected ExtendedBlockStorage computeNext() {
                while (iter.hasNext()) {
                    Cube cube = iter.next();

                    if (cube == null) continue;

                    ExtendedBlockStorage ebs = cube.getStorage();

                    if (ebs == null) continue;

                    return ebs;
                }

                endOfData();
                return null;
            }
        };
    }
}
