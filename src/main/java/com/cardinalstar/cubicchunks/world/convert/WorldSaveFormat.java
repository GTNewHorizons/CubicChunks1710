package com.cardinalstar.cubicchunks.world.convert;

/** Identifies the on-disk save format of a world folder. */
public enum WorldSaveFormat {

    /** CubicChunks format: has region2d/, region3d/, or cubicchunks.world_format.dat. */
    CC,

    /** Vanilla Anvil format: has region/*.mca files, no CC markers. */
    VANILLA,

    /** Both CC and vanilla data exist on disk. */
    MIXED,

    /** No recognisable data found (empty or brand-new world). */
    UNKNOWN
}
