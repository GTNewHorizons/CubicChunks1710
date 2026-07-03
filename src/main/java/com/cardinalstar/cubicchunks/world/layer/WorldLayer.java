package com.cardinalstar.cubicchunks.world.layer;

import javax.annotation.Nullable;

import net.minecraft.block.Block;
import net.minecraft.tileentity.TileEntity;

import org.apache.commons.lang3.mutable.MutableInt;

import com.cardinalstar.cubicchunks.util.BooleanArray2D;
import com.cardinalstar.cubicchunks.util.BooleanArray3D;
import com.cardinalstar.cubicchunks.util.HashMap2D;
import com.cardinalstar.cubicchunks.util.HashMap3D;
import it.unimi.dsi.fastutil.ints.Int2ObjectRBTreeMap;

public class WorldLayer {

    private final HashMap2D<PseudoChunk> chunks = new HashMap2D<>();
    private final HashMap3D<PseudoEBS> data = new HashMap3D<>();
    private final HashMap3D<TileEntity> tiles = new HashMap3D<>();

    @Nullable
    public Block getBlock(int x, int y, int z) {
        var ebs = data.get(x >> 4, y >> 4, z >> 4);

        if (ebs == null) return null;
        if (!ebs.presence.get(x & 0xF, y & 0xF, z & 0xF)) return null;

        return ebs.blocks[((x & 0xF) << 8) | ((y & 0xF) << 4) | (z & 0xF)];
    }

    public boolean getBlockMeta(int x, int y, int z, MutableInt meta) {
        var ebs = data.get(x >> 4, y >> 4, z >> 4);

        if (ebs == null) return false;
        if (!ebs.presence.get(x & 0xF, y & 0xF, z & 0xF)) return false;

        meta.setValue(ebs.meta[((x & 0xF) << 8) | ((y & 0xF) << 4) | (z & 0xF)]);
        return true;
    }

    private static class PseudoChunk {
        public final int[] heightmap = new int[16 * 16];
        public final BooleanArray2D presencce = new BooleanArray2D(16, 16);

        public final Int2ObjectRBTreeMap<PseudoEBS> ebs = new Int2ObjectRBTreeMap<>();
    }

    private static class PseudoEBS {
        public final Block[] blocks = new Block[16 * 16 * 16];
        public final int[] meta = new int[16 * 16 * 16];
        public final BooleanArray3D presence = new BooleanArray3D(16, 16, 16);
    }
}
