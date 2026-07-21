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
package com.cardinalstar.cubicchunks.server.chunkio;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.ChunkCoordIntPair;

import org.jetbrains.annotations.NotNull;

import com.cardinalstar.cubicchunks.CubicChunksConfig;
import com.cardinalstar.cubicchunks.api.world.storage.ICubicStorage;
import com.cardinalstar.cubicchunks.server.chunkio.CCNBTUtils.TagCompression;
import com.cardinalstar.cubicchunks.server.chunkio.region.ShadowPagingRegion;
import com.cardinalstar.cubicchunks.util.CubePos;
import com.cardinalstar.cubicchunks.util.DataUtils;

import cubicchunks.regionlib.impl.EntryLocation2D;
import cubicchunks.regionlib.impl.EntryLocation3D;
import cubicchunks.regionlib.impl.SaveCubeColumns;
import cubicchunks.regionlib.impl.save.SaveSection2D;
import cubicchunks.regionlib.impl.save.SaveSection3D;
import cubicchunks.regionlib.lib.ExtRegion;
import cubicchunks.regionlib.lib.factory.SimpleRegionFactory;
import cubicchunks.regionlib.lib.provider.SharedCachedRegionProvider;
import cubicchunks.regionlib.util.Utils;
import it.unimi.dsi.fastutil.Pair;

/**
 * Implementation of {@link ICubicStorage} for the Cubic Chunks' standard Anvil3d storage format.
 */
public class RegionCubeStorage implements ICubicStorage {

    private static SaveCubeColumns saveForPath(Path path) throws IOException {
        if (CubicChunksConfig.useShadowPagingIO) {
            Utils.createDirectories(path);

            Path part2d = path.resolve("region2d");
            Utils.createDirectories(part2d);

            Path part3d = path.resolve("region3d");
            Utils.createDirectories(part3d);

            @SuppressWarnings("unchecked")
            SaveSection2D section2d = new SaveSection2D(
                new SharedCachedRegionProvider<>(
                    new SimpleRegionFactory<>(
                        new EntryLocation2D.Provider(),
                        part2d,
                        (keyProv, r) -> ShadowPagingRegion.<EntryLocation2D>builder()
                            .setDirectory(part2d)
                            .setRegionKey(r)
                            .setKeyProvider(keyProv)
                            .setSectorSize(512)
                            .build(),
                        (dir, key) -> Files.exists(part2d.resolve(key.getName())))),
                new SharedCachedRegionProvider<>(
                    new SimpleRegionFactory<>(
                        new EntryLocation2D.Provider(),
                        part2d,
                        (keyProvider,
                            regionKey) -> new ExtRegion<>(part2d, Collections.emptyList(), keyProvider, regionKey),
                        (dir, key) -> Files.exists(part2d.resolve(key.getName() + ".ext")))));
            @SuppressWarnings("unchecked")
            SaveSection3D section3d = new SaveSection3D(
                new SharedCachedRegionProvider<>(
                    new SimpleRegionFactory<>(
                        new EntryLocation3D.Provider(),
                        part3d,
                        (keyProv, r) -> ShadowPagingRegion.<EntryLocation3D>builder()
                            .setDirectory(part3d)
                            .setRegionKey(r)
                            .setKeyProvider(keyProv)
                            .setSectorSize(512)
                            .build(),
                        (dir, key) -> Files.exists(part3d.resolve(key.getName())))),
                new SharedCachedRegionProvider<>(
                    new SimpleRegionFactory<>(
                        new EntryLocation3D.Provider(),
                        part3d,
                        (keyProvider,
                            regionKey) -> new ExtRegion<>(part3d, Collections.emptyList(), keyProvider, regionKey),
                        (dir, key) -> Files.exists(part3d.resolve(key.getName() + ".ext")))));

            return new SaveCubeColumns(section2d, section3d);
        } else {
            return SaveCubeColumns.create(path);
        }
    }

    private SaveCubeColumns save;

    public RegionCubeStorage(Path path) throws IOException {
        this.save = saveForPath(path);
    }

    @Override
    public boolean columnExists(ChunkCoordIntPair pos) throws IOException {
        return this.save.getSaveSection2D()
            .hasEntry(new EntryLocation2D(pos.chunkXPos, pos.chunkZPos));
    }

    @Override
    public boolean cubeExists(CubePos pos) throws IOException {
        return this.save.getSaveSection3D()
            .hasEntry(new EntryLocation3D(pos.getX(), pos.getY(), pos.getZ()));
    }

    @Override
    public NBTTagCompound readColumn(ChunkCoordIntPair pos) throws IOException {
        // we use a true here in order to force creation and caching of the new region, thus avoiding an expensive
        // Files.exists() check for every cube/column (which
        // is really expensive on windows)
        Optional<ByteBuffer> data = this.save.load(new EntryLocation2D(pos.chunkXPos, pos.chunkZPos), true);
        if (!data.isPresent()) return null;

        return CCNBTUtils.loadTag(data.get());
    }

    @Override
    public NBTTagCompound readCube(CubePos pos) throws IOException {
        // see comment in readColumn
        Optional<ByteBuffer> data = this.save.load(new EntryLocation3D(pos.getX(), pos.getY(), pos.getZ()), true);
        if (!data.isPresent()) return null;

        return CCNBTUtils.loadTag(data.get());
    }

    @Override
    public @NotNull NBTBatch readBatch(PosBatch positions) throws IOException {
        var columns = this.save
            .load2D(DataUtils.mapToList(positions.columns, c -> new EntryLocation2D(c.chunkXPos, c.chunkZPos)), false);
        var cubes = this.save.load3D(
            DataUtils.mapToList(positions.cubes, c -> new EntryLocation3D(c.getX(), c.getY(), c.getZ())),
            false);

        var columnTags = columns.read.entrySet()
            .parallelStream()
            .map(e -> {
                try {
                    return Pair.of(
                        new ChunkCoordIntPair(
                            e.getKey()
                                .getEntryX(),
                            e.getKey()
                                .getEntryZ()),
                        CCNBTUtils.loadTag(e.getValue()));
                } catch (IOException ex) {
                    throw new UncheckedIOException(ex);
                }
            })
            .collect(Collectors.toMap(Pair::left, Pair::right));

        var cubeTags = cubes.read.entrySet()
            .parallelStream()
            .map(e -> {
                try {
                    return Pair.of(
                        new CubePos(
                            e.getKey()
                                .getEntryX(),
                            e.getKey()
                                .getEntryY(),
                            e.getKey()
                                .getEntryZ()),
                        CCNBTUtils.loadTag(e.getValue()));
                } catch (IOException ex) {
                    throw new UncheckedIOException(ex);
                }
            })
            .collect(Collectors.toMap(Pair::left, Pair::right));

        return new NBTBatch(columnTags, cubeTags);
    }

    @Override
    public void writeColumn(ChunkCoordIntPair pos, NBTTagCompound nbt) throws IOException {
        ByteBuffer compressed = CCNBTUtils.saveTag(nbt, TagCompression.FastLZ4);

        // write compressed data to disk
        this.save.save2d(new EntryLocation2D(pos.chunkXPos, pos.chunkZPos), compressed);
    }

    @Override
    public void writeCube(CubePos pos, NBTTagCompound nbt) throws IOException {
        ByteBuffer compressed = CCNBTUtils.saveTag(nbt, TagCompression.FastLZ4);

        // write compressed data to disk
        this.save.save3d(new EntryLocation3D(pos.getX(), pos.getY(), pos.getZ()), compressed);
    }

    @Override
    public void writeBatch(NBTBatch batch) throws IOException {
        // compress NBT data
        var compressedColumns = this
            .compressNBTForBatchWrite(batch.columns, pos -> new EntryLocation2D(pos.chunkXPos, pos.chunkZPos));
        var compressedCubes = this
            .compressNBTForBatchWrite(batch.cubes, pos -> new EntryLocation3D(pos.getX(), pos.getY(), pos.getZ()));

        // write compressed data to disk
        if (!compressedColumns.isEmpty()) {
            this.save.save2d(compressedColumns);
        }
        if (!compressedCubes.isEmpty()) {
            this.save.save3d(compressedCubes);
        }
    }

    private <KI, KO> Map<KO, ByteBuffer> compressNBTForBatchWrite(Map<KI, NBTTagCompound> nbt,
        Function<KI, KO> keyMappingFunction) throws IOException {
        if (nbt.isEmpty()) { // avoid somewhat expensive stream creation if there are no entries
            return Collections.emptyMap();
        }

        try {
            return nbt.entrySet()
                .parallelStream()
                .collect(Collectors.toMap(entry -> keyMappingFunction.apply(entry.getKey()), entry -> {
                    try {
                        return CCNBTUtils.saveTag(entry.getValue(), TagCompression.FastLZ4);
                    } catch (IOException e) {
                        // wrap exception so that we can throw it from inside the lambda
                        throw new UncheckedIOException(e);
                    }
                }));
        } catch (UncheckedIOException e) {
            // rethrow original exception
            throw e.getCause();
        }
    }

    @Override
    public void forEachColumn(Consumer<ChunkCoordIntPair> callback) throws IOException {
        this.save.getSaveSection2D()
            .forAllKeys(pos -> callback.accept(new ChunkCoordIntPair(pos.getEntryX(), pos.getEntryZ())));
    }

    @Override
    public void forEachCube(Consumer<CubePos> callback) throws IOException {
        this.save.getSaveSection3D()
            .forAllKeys(pos -> callback.accept(new CubePos(pos.getEntryX(), pos.getEntryY(), pos.getEntryZ())));
    }

    @Override
    public void flush() throws IOException {
        this.save.flush();
    }

    @Override
    public void close() throws IOException {
        this.save.close();
        this.save = null;
    }
}
