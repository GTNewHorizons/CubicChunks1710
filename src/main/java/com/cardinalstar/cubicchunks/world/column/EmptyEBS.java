package com.cardinalstar.cubicchunks.world.column;

import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.world.chunk.NibbleArray;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;

public class EmptyEBS extends ExtendedBlockStorage {

    public static final EmptyEBS INSTANCE = new EmptyEBS(0);

    public EmptyEBS(int yLevel) {
        super(yLevel, false);
    }

    @Override
    public Block getBlockByExtId(int x, int y, int z) {
        return Blocks.air;
    }

    @Override
    public void func_150818_a(int p_150818_1_, int p_150818_2_, int p_150818_3_, Block p_150818_4_) {

    }

    @Override
    public int getExtBlockMetadata(int p_76665_1_, int p_76665_2_, int p_76665_3_) {
        return 0;
    }

    @Override
    public void setExtBlockMetadata(int p_76654_1_, int p_76654_2_, int p_76654_3_, int p_76654_4_) {

    }

    @Override
    public boolean isEmpty() {
        return true;
    }

    @Override
    public boolean getNeedsRandomTick() {
        return false;
    }

    @Override
    public void setExtSkylightValue(int p_76657_1_, int p_76657_2_, int p_76657_3_, int p_76657_4_) {

    }

    @Override
    public int getExtSkylightValue(int p_76670_1_, int p_76670_2_, int p_76670_3_) {
        return 15;
    }

    @Override
    public void setExtBlocklightValue(int p_76677_1_, int p_76677_2_, int p_76677_3_, int p_76677_4_) {

    }

    @Override
    public int getExtBlocklightValue(int p_76674_1_, int p_76674_2_, int p_76674_3_) {
        return 0;
    }

    @Override
    public void removeInvalidBlocks() {

    }

    @Override
    public NibbleArray getBlockMSBArray() {
        throw new UnsupportedOperationException();
    }

    @Override
    public NibbleArray getMetadataArray() {
        throw new UnsupportedOperationException();
    }

    @Override
    public NibbleArray getBlocklightArray() {
        throw new UnsupportedOperationException();
    }

    @Override
    public NibbleArray getSkylightArray() {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setBlockLSBArray(byte[] p_76664_1_) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setBlockMSBArray(NibbleArray p_76673_1_) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setBlockMetadataArray(NibbleArray p_76668_1_) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setBlocklightArray(NibbleArray p_76659_1_) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setSkylightArray(NibbleArray p_76666_1_) {
        throw new UnsupportedOperationException();
    }

    @Override
    public NibbleArray createBlockMSBArray() {
        throw new UnsupportedOperationException();
    }
}
