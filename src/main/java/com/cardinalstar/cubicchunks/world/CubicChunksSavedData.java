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
package com.cardinalstar.cubicchunks.world;

import net.minecraft.block.Block;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;
import net.minecraft.world.WorldSavedData;

import com.cardinalstar.cubicchunks.CubicChunksConfig;
import com.cardinalstar.cubicchunks.worldgen.FillerInfo;
import com.cardinalstar.cubicchunks.worldgen.HeightInfo;
import com.gtnewhorizon.gtnhlib.util.data.BlockMeta;
import com.gtnewhorizon.gtnhlib.util.data.ImmutableBlockMeta;

public class CubicChunksSavedData extends WorldSavedData {

    public int minHeight = 0, maxHeight = 256;
    private FillerInfo fillerInfo;
    private World world;

    public CubicChunksSavedData(String name) {
        super(name);
    }

    public CubicChunksSavedData(int dimensionId, World world) {
        this("cubicChunksData");

        HeightInfo heightInfo = CubicChunksConfig.configuredDimensionalHeightMap.get(dimensionId);
        if (heightInfo != null) {
            this.minHeight = heightInfo.minHeight;
            this.maxHeight = heightInfo.maxHeight;
        } else {
            this.minHeight = CubicChunksConfig.defaultMinHeight;
            this.maxHeight = CubicChunksConfig.defaultMaxHeight;
        }

        this.fillerInfo = CubicChunksConfig.configuredDimensionalFillerMap.get(dimensionId);
        if (this.fillerInfo == null) {
            fillerInfo = new FillerInfo();
        }
    }

    public static CubicChunksSavedData get(World world) {
        var data = (CubicChunksSavedData) world.perWorldStorage.loadData(CubicChunksSavedData.class, "cubicChunksData");
        if (data == null) {
            data = new CubicChunksSavedData(world.provider.dimensionId, world);
            data.markDirty();

            world.perWorldStorage.setData(data.mapName, data);
            world.perWorldStorage.saveAllData();
        }
        data.world = world;
        return data;
    }

    public FillerInfo getFillerInfo() {
        return fillerInfo;
    }

    public void setBottomFiller(ImmutableBlockMeta block) {
        fillerInfo.bottomFiller = block;
        this.markDirty();

        world.perWorldStorage.setData(this.mapName, this);
        world.perWorldStorage.saveAllData();
    }

    public void setTopFiller(ImmutableBlockMeta block) {
        fillerInfo.topFiller = block;
        this.markDirty();

        world.perWorldStorage.setData(this.mapName, this);
        world.perWorldStorage.saveAllData();
    }

    @Override
    public void readFromNBT(NBTTagCompound nbt) {
        // set 4 least significant bits to zero to ensure they are always multiples of 16
        minHeight = nbt.getInteger("minHeight") & ~0xF;
        maxHeight = nbt.getInteger("maxHeight") & ~0xF;

        if (fillerInfo == null) {
            fillerInfo = new FillerInfo();
        }

        NBTTagCompound fillerCompound = nbt.getCompoundTag("fillerBlocks");

        if (fillerCompound.hasKey("topId")) {
            int topBlock = fillerCompound.getInteger("topId");
            short topMeta = fillerCompound.getShort("topMeta");
            fillerInfo.topFiller = new BlockMeta(Block.getBlockById(topBlock), topMeta);
        }

        if (fillerCompound.hasKey("bottomId")) {
            int bottomBlock = fillerCompound.getInteger("bottomId");
            short bottomMeta = fillerCompound.getShort("bottomMeta");
            fillerInfo.bottomFiller = new BlockMeta(Block.getBlockById(bottomBlock), bottomMeta);
        }

    }

    @Override
    public void writeToNBT(NBTTagCompound compound) {
        compound.setInteger("minHeight", minHeight);
        compound.setInteger("maxHeight", maxHeight);

        NBTTagCompound filler = new NBTTagCompound();

        if (fillerInfo.bottomFiller != null) {
            filler.setInteger("bottomId", Block.getIdFromBlock(fillerInfo.bottomFiller.getBlock()));
            filler.setShort("bottomMeta", (short) fillerInfo.bottomFiller.getBlockMeta());
        }

        if (fillerInfo.topFiller != null) {
            filler.setInteger("topId", Block.getIdFromBlock(fillerInfo.topFiller.getBlock()));
            filler.setShort("topMeta", (short) fillerInfo.topFiller.getBlockMeta());
        }

        compound.setTag("fillerBlocks", filler);
    }
}
