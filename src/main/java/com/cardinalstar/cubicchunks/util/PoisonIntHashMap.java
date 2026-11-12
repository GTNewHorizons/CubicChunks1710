package com.cardinalstar.cubicchunks.util;

import net.minecraft.util.IntHashMap;

public class PoisonIntHashMap extends IntHashMap {

    private final String replacement;

    public PoisonIntHashMap(String replacement) {
        this.replacement = replacement;
    }

    @Override
    public Object lookup(int p_76041_1_) {
        throw new UnsupportedOperationException(
            "This map has been replaced by " + this.replacement
                + " and cannot be used. Check the Cubic Chunks mod for more information.");
    }

    @Override
    public boolean containsItem(int p_76037_1_) {
        throw new UnsupportedOperationException(
            "This map has been replaced by " + this.replacement
                + " and cannot be used. Check the Cubic Chunks mod for more information.");
    }

    @Override
    public void addKey(int p_76038_1_, Object p_76038_2_) {
        throw new UnsupportedOperationException(
            "This map has been replaced by " + this.replacement
                + " and cannot be used. Check the Cubic Chunks mod for more information.");
    }

    @Override
    public Object removeObject(int p_76049_1_) {
        throw new UnsupportedOperationException(
            "This map has been replaced by " + this.replacement
                + " and cannot be used. Check the Cubic Chunks mod for more information.");
    }

    @Override
    public void clearMap() {
        throw new UnsupportedOperationException(
            "This map has been replaced by " + this.replacement
                + " and cannot be used. Check the Cubic Chunks mod for more information.");
    }
}
