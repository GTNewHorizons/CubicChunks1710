package com.cardinalstar.cubicchunks.mixin.early.mod;

import org.embeddedt.embeddium.impl.util.PositionUtil;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

import com.cardinalstar.cubicchunks.util.Coords;

@Mixin(value = PositionUtil.class, remap = false)
public class MixinPositionUtil {

    /**
     * @author Recursive Pineapple
     * @reason Performance
     */
    @Overwrite
    public static long packBlock(int x, int y, int z) {
        return Coords.key(x, y, z);
    }

    /**
     * @author Recursive Pineapple
     * @reason Performance
     */
    @Overwrite
    public static int unpackBlockX(long packed) {
        return Coords.x(packed);
    }

    /**
     * @author Recursive Pineapple
     * @reason Performance
     */
    @Overwrite
    public static int unpackBlockY(long packed) {
        return Coords.y(packed);
    }

    /**
     * @author Recursive Pineapple
     * @reason Performance
     */
    @Overwrite
    public static int unpackBlockZ(long packed) {
        return Coords.z(packed);
    }

    /**
     * @author Recursive Pineapple
     * @reason Performance
     */
    @Overwrite
    public static long packSection(int x, int y, int z) {
        return Coords.key(x, y, z);
    }
}
