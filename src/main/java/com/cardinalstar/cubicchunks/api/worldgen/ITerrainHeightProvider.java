package com.cardinalstar.cubicchunks.api.worldgen;

/**
 * Optional for {@link IWorldGenerator}s that know their terrain height without generating cubes,
 * lets mods (IE Distant Horizons) generate only the cubes around a column's surface.
 */
public interface ITerrainHeightProvider {

    /** returned when the height can't be determined without generating the column's cubes */
    int UNKNOWN = Integer.MAX_VALUE;

    /**
     * @return the Y of the highest block the generator places in the column before population (including liquids),
     *         {@link #UNKNOWN} if it can't tell
     */
    int getMaxTerrainHeight(int columnX, int columnZ);
}
