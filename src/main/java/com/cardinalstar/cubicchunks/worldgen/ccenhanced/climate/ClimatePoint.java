package com.cardinalstar.cubicchunks.worldgen.ccenhanced.climate;

/**
 * An N-dimensional point in climate space, one float per axis.
 * Axis order matches ClimateSystem.TEMPERATURE / HUMIDITY / CONTINENTALNESS / EROSION constants.
 */
public class ClimatePoint {

    public final float[] values;

    public ClimatePoint(float[] values) {
        this.values = values;
    }

    /**
     * Returns the value for the given axis index, or 0 if the axis index is out of range
     * (backward-compatible when new axes are added).
     */
    public float get(int axis) {
        return axis < values.length ? values[axis] : 0.0f;
    }
}
