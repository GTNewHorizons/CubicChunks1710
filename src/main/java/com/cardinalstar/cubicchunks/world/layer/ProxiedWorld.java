package com.cardinalstar.cubicchunks.world.layer;

import java.io.File;

import net.minecraft.block.Block;
import net.minecraft.entity.Entity;
import net.minecraft.init.Blocks;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.profiler.Profiler;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import net.minecraft.world.WorldProvider;
import net.minecraft.world.WorldSettings;
import net.minecraft.world.biome.BiomeGenBase;
import net.minecraft.world.chunk.IChunkProvider;
import net.minecraft.world.chunk.storage.IChunkLoader;
import net.minecraft.world.storage.IPlayerFileData;
import net.minecraft.world.storage.ISaveHandler;
import net.minecraft.world.storage.WorldInfo;

import com.cardinalstar.cubicchunks.util.XSTR;

public class ProxiedWorld extends World {

    private final World world;

    public int airX, airY, airZ;

    public ProxiedWorld(World world) {
        super(new ISaveHandler() {

            @Override
            public void saveWorldInfoWithPlayer(WorldInfo worldInfo, NBTTagCompound nbtTagCompound) {}

            @Override
            public void saveWorldInfo(WorldInfo worldInfo) {}

            @Override
            public WorldInfo loadWorldInfo() {
                return null;
            }

            @Override
            public IPlayerFileData getSaveHandler() {
                return null;
            }

            @Override
            public File getMapFileFromName(String mapName) {
                return null;
            }

            @Override
            public IChunkLoader getChunkLoader(WorldProvider worldProvider) {
                return null;
            }

            @Override
            public void flush() {}

            @Override
            public void checkSessionLock() {}

            @Override
            public String getWorldDirectoryName() {
                return null;
            }

            @Override
            public File getWorldDirectory() {
                return null;
            }
        }, "DUMMY_DIMENSION", null, new WorldSettings(new WorldInfo(new NBTTagCompound())), new Profiler());

        this.rand = new XSTR();
        this.world = world;
        this.chunkProvider = world.getChunkProvider();
    }

    @Override
    protected IChunkProvider createChunkProvider() {
        return null;
    }

    @Override
    public Entity getEntityByID(int aEntityID) {
        return null;
    }

    @Override
    public boolean setBlock(int aX, int aY, int aZ, Block aBlock, int aMeta, int aFlags) {
        return true;
    }

    @Override
    public float getSunBrightnessFactor(float p_72967_1_) {
        return world.getSunBrightnessFactor(p_72967_1_);
    }

    @Override
    public BiomeGenBase getBiomeGenForCoords(int aX, int aZ) {
        return world.getBiomeGenForCoords(aX, aZ);
    }

    @Override
    public int getFullBlockLightValue(int aX, int aY, int aZ) {
        return world.getFullBlockLightValue(aX, aY, aZ);
    }

    @Override
    public Block getBlock(int aX, int aY, int aZ) {
        if (aX == airX && aY == airY && aZ == airZ) return Blocks.air;

        return world.getBlock(aX, aY, aZ);
    }

    @Override
    public int getBlockMetadata(int aX, int aY, int aZ) {
        if (aX == airX && aY == airY && aZ == airZ) return 0;

        return world.getBlockMetadata(aX, aY, aZ);
    }

    @Override
    public TileEntity getTileEntity(int aX, int aY, int aZ) {
        if (aX == airX && aY == airY && aZ == airZ) return null;

        return world.getTileEntity(aX, aY, aZ);
    }

    @Override
    public boolean canBlockSeeTheSky(int aX, int aY, int aZ) {
        return world.canBlockSeeTheSky(aX, aY, aZ);
    }

    @Override
    protected int func_152379_p() {
        return 0;
    }
}
