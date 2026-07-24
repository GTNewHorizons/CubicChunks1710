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
package com.cardinalstar.cubicchunks.world.column;

import java.util.AbstractCollection;
import java.util.AbstractSet;
import java.util.Collection;
import java.util.Iterator;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;

import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.ChunkPosition;

import org.jetbrains.annotations.NotNull;

import com.cardinalstar.cubicchunks.api.IColumn;
import com.cardinalstar.cubicchunks.api.ICube;
import com.cardinalstar.cubicchunks.util.Coords;
import com.google.common.collect.AbstractIterator;

@ParametersAreNonnullByDefault
public class ColumnTileEntityMap implements Map<ChunkPosition, TileEntity> {

    private final IColumn column;

    public ColumnTileEntityMap(IColumn column) {
        this.column = column;
    }

    @Override
    public int size() {
        return column.getLoadedCubes()
            .stream()
            .map(ICube::getTileEntityMap)
            .map(Map::size)
            .reduce(Integer::sum)
            .orElse(0);
    }

    @Override
    public boolean isEmpty() {
        return column.getLoadedCubes()
            .stream()
            .map(ICube::getTileEntityMap)
            .allMatch(Map::isEmpty);
    }

    @Override
    public boolean containsKey(Object o) {
        if (!(o instanceof ChunkPosition pos)) {
            return false;
        }

        int y = Coords.blockToCube(pos.chunkPosY);

        ICube cube = column.getCube(y);

        return cube != null && cube.getTileEntityMap()
            .containsKey(o);
    }

    @Override
    public boolean containsValue(Object o) {
        if (!(o instanceof TileEntity)) {
            return false;
        }
        int y = Coords.blockToCube(((TileEntity) o).yCoord);
        ICube cube = column.getCube(y);
        assert cube != null : "Cube is null but tile entity in it exists!";
        return cube.getTileEntityMap()
            .containsValue(o);
    }

    @Nullable
    @Override
    public TileEntity get(Object o) {
        if (!(o instanceof ChunkPosition pos)) {
            return null;
        }

        int y = Coords.blockToCube(pos.chunkPosY);

        ICube cube = column.getCube(y);

        return cube == null ? null
            : cube.getTileEntityMap()
                .get(o);
    }

    @Override
    public TileEntity put(ChunkPosition chunkPos, TileEntity tileEntity) {
        int y = Coords.blockToCube(chunkPos.chunkPosY);
        ICube cube = column.getCube(y);
        return cube.getTileEntityMap()
            .put(chunkPos, tileEntity);
    }

    @Nullable
    @Override
    public TileEntity remove(Object o) {
        if (!(o instanceof ChunkPosition pos)) {
            return null;
        }

        int y = Coords.blockToCube(pos.chunkPosY);

        ICube cube = column.getCube(y);

        return cube == null ? null
            : cube.getTileEntityMap()
                .remove(o);
    }

    @Override
    public void putAll(Map<? extends ChunkPosition, ? extends TileEntity> map) {
        map.forEach(this::put);
    }

    @Override
    public void clear() {
        throw new UnsupportedOperationException();
    }

    @Override
    public @NotNull Set<ChunkPosition> keySet() {
        return new AbstractSet<>() {

            @Override
            public int size() {
                return ColumnTileEntityMap.this.size();
            }

            @Override
            public boolean isEmpty() {
                return ColumnTileEntityMap.this.isEmpty();
            }

            @Override
            public boolean contains(Object o) {
                return ColumnTileEntityMap.this.containsKey(o);
            }

            @Nonnull
            @Override
            public Iterator<ChunkPosition> iterator() {
                return new AbstractIterator<>() {

                    private final Iterator<? extends ICube> cubes = column.getLoadedCubes()
                        .iterator();
                    private Iterator<ChunkPosition> withinCube = null;

                    @Override
                    protected ChunkPosition computeNext() {
                        while (withinCube == null || !withinCube.hasNext()) {
                            if (!cubes.hasNext()) {
                                return this.endOfData();
                            }

                            withinCube = cubes.next()
                                .getTileEntityMap()
                                .keySet()
                                .iterator();
                        }

                        return withinCube.next();
                    }
                };
            }

            @Override
            public boolean remove(Object o) {
                return ColumnTileEntityMap.this.remove(o) != null;
            }

            @Override
            public void clear() {
                throw new UnsupportedOperationException();
            }
        };
    }

    @Override
    public Collection<TileEntity> values() {
        return new AbstractCollection<TileEntity>() {

            @Override
            public int size() {
                return ColumnTileEntityMap.this.size();
            }

            @Override
            public boolean isEmpty() {
                return ColumnTileEntityMap.this.isEmpty();

            }

            @Override
            public boolean contains(Object o) {
                return ColumnTileEntityMap.this.containsValue(o);
            }

            @Override
            public Iterator<TileEntity> iterator() {
                return new Iterator<TileEntity>() {

                    Iterator<? extends ICube> cubes = column.getLoadedCubes()
                        .iterator();
                    Iterator<TileEntity> curIt = !cubes.hasNext() ? null
                        : cubes.next()
                            .getTileEntityMap()
                            .values()
                            .iterator();
                    TileEntity nextVal;

                    @Override
                    public boolean hasNext() {
                        if (nextVal != null) {
                            return true;
                        }
                        if (curIt == null) {
                            return false;
                        }
                        while (!curIt.hasNext() && cubes.hasNext()) {
                            curIt = cubes.next()
                                .getTileEntityMap()
                                .values()
                                .iterator();
                        }
                        if (!curIt.hasNext()) {
                            return false;
                        }
                        nextVal = curIt.next();
                        return true;
                    }

                    @Override
                    public TileEntity next() {
                        if (hasNext()) {
                            TileEntity next = nextVal;
                            nextVal = null;
                            return next;
                        }
                        throw new NoSuchElementException();
                    }
                };
            }

            @Override
            public boolean add(TileEntity te) {
                return ColumnTileEntityMap.this.put(new ChunkPosition(te.xCoord, te.yCoord, te.zCoord), te) == null;
            }

            @Override
            public boolean remove(Object o) {
                if (!(o instanceof TileEntity te)) {
                    return false;
                }
                return ColumnTileEntityMap.this.remove(new ChunkPosition(te.xCoord, te.yCoord, te.zCoord), te);
            }

            @Override
            public void clear() {
                throw new UnsupportedOperationException();
            }
        };
    }

    @Override
    public Set<Entry<ChunkPosition, TileEntity>> entrySet() {
        return new AbstractSet<Entry<ChunkPosition, TileEntity>>() {

            @Override
            public int size() {
                return ColumnTileEntityMap.this.size();
            }

            @Override
            public boolean isEmpty() {
                return ColumnTileEntityMap.this.isEmpty();
            }

            @Override
            public boolean contains(Object o) {
                return ColumnTileEntityMap.this.containsKey(o);
            }

            @Nonnull
            @Override
            public Iterator<Entry<ChunkPosition, TileEntity>> iterator() {
                return new Iterator<Entry<ChunkPosition, TileEntity>>() {

                    Iterator<? extends ICube> cubes = column.getLoadedCubes()
                        .iterator();
                    Iterator<Entry<ChunkPosition, TileEntity>> curIt = !cubes.hasNext() ? null
                        : cubes.next()
                            .getTileEntityMap()
                            .entrySet()
                            .iterator();
                    Entry<ChunkPosition, TileEntity> nextVal;

                    @Override
                    public boolean hasNext() {
                        if (nextVal != null) {
                            return true;
                        }
                        if (curIt == null) {
                            return false;
                        }
                        while (!curIt.hasNext() && cubes.hasNext()) {
                            curIt = cubes.next()
                                .getTileEntityMap()
                                .entrySet()
                                .iterator();
                        }
                        if (!curIt.hasNext()) {
                            return false;
                        }
                        nextVal = curIt.next();
                        return true;
                    }

                    @Override
                    public Entry<ChunkPosition, TileEntity> next() {
                        if (hasNext()) {
                            Entry<ChunkPosition, TileEntity> e = nextVal;
                            nextVal = null;
                            return e;
                        }
                        throw new NoSuchElementException();
                    }
                };
            }

            @Override
            public boolean remove(Object o) {
                return ColumnTileEntityMap.this.remove(o) != null;
            }

            @Override
            public void clear() {
                throw new UnsupportedOperationException();
            }
        };
    }
}
