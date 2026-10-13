package com.cardinalstar.cubicchunks.world.convert.adapter;

import java.util.HashMap;
import java.util.Map;

import com.cardinalstar.cubicchunks.api.MetaContainer;
import com.cardinalstar.cubicchunks.api.MetaKey;

public class CubeData implements MetaContainer {

    public final int[] blocks = new int[4096];
    public final int[] meta = new int[4096];
    public final int[] blockLight = new int[4096];
    public final int[] skyLight = new int[4096];

    @SuppressWarnings("rawtypes")
    private final Map<MetaKey, Object> metadata = new HashMap<>();

    @Override
    public <T> T getMeta(MetaKey<T> key) {
        //noinspection unchecked
        return (T) metadata.get(key);
    }

    @Override
    public <T> void setMeta(MetaKey<T> key, T value) {
        metadata.put(key, value);
    }

    public static int index(int x, int y, int z) {
        return y << 8 | z << 4 | x;
    }
}
