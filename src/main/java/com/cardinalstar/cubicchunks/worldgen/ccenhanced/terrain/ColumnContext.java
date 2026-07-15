package com.cardinalstar.cubicchunks.worldgen.ccenhanced.terrain;

/**
 * Per-column data computed once during provideColumn and reused across all provideCube calls
 * for the same column.
 *
 * <p>
 * surfaceY is stored here (mirrored in chunk.heightMap for structure access).
 * Canyon and trench noise arrays will be added in Step 6.
 */
public class ColumnContext {

    /**
     * SurfaceY for each block column, indexed as [bx + bz * 16]. Values are "first air Y"
     * (i.e. the block at y = surfaceY is air; solid terrain exists at y &lt; surfaceY).
     */
    public final int[] surfaceY;

    /** Minimum surfaceY across the 16×16 column. Fast-path: cubes fully below this are all stone. */
    public final int minSurfaceY;

    /** Maximum surfaceY across the 16×16 column. Fast-path: cubes fully above this are all air. */
    public final int maxSurfaceY;

    public ColumnContext(int[] surfaceY, int minSurfaceY, int maxSurfaceY) {
        this.surfaceY = surfaceY;
        this.minSurfaceY = minSurfaceY;
        this.maxSurfaceY = maxSurfaceY;
    }

    public int getSurfaceY(int bx, int bz) {
        return surfaceY[bx + bz * 16];
    }
}
