/*
 * This file is part of Cubic Chunks Mod, licensed under the MIT License (MIT).
 * Copyright (c) 2015-2021 OpenCubicChunks
 * Copyright (c) 2015-2021 contributors
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package com.cardinalstar.cubicchunks.util;

import static java.lang.Math.exp;
import static java.lang.Math.sqrt;

import javax.annotation.ParametersAreNonnullByDefault;

import net.minecraft.util.ChunkCoordinates;
import net.minecraft.util.MathHelper;

@ParametersAreNonnullByDefault
public class MathUtil {

    public static int ceilDiv(int a, int b) {
        return -Math.floorDiv(-a, b);
    }

    public static long ceilDiv(long a, long b) {
        return -Math.floorDiv(-a, b);
    }

    public static boolean isPowerOfN(int toTest, int n) { // works only for positive numbers
        while (toTest > n - 1 && toTest % n == 0) {
            toTest /= n;
        }
        return toTest == 1;
    }

    public static double lerp(final double a, final double min, final double max) {
        return min + a * (max - min);
    }

    // reverse linear interpolation - unlerp(lerp(a, min, max), min, max) == a
    public static double unlerp(final double v, final double min, final double max) {
        return (v - min) / (max - min);
    }

    public static float unlerp(final float v, final float min, final float max) {
        return (v - min) / (max - min);
    }

    public static float unlerp(final long v, final long min, final long max) {
        return (v - min) / (float) (max - min);
    }

    public static float lerp(final float a, final float min, final float max) {
        return min + a * (max - min);
    }

    public static float map(float x, float in_min, float in_max, float out_min, float out_max) {
        return (x - in_min) * (out_max - out_min) / (in_max - in_min) + out_min;
    }

    public static double map(double x, double in_min, double in_max, double out_min, double out_max) {
        return (x - in_min) * (out_max - out_min) / (in_max - in_min) + out_min;
    }

    public static double linearCurve(double x, double x1, double y1, double x2, double y2) {
        if (x < x1) return y1;
        if (x > x2) return y1;

        return map(x, x1, x2, y1, y2);
    }

    public static int min(int a, int b) {
        return Math.min(a, b);
    }

    public static int min(int a, int b, int c) {
        return Math.min(Math.min(a, b), c);
    }

    public static int min(int a, int b, int c, int d) {
        return Math.min(Math.min(a, b), Math.min(c, d));
    }

    public static int min(int... a) {
        int min = a[0];
        for (int i = 1; i < a.length; i++) {
            if (a[i] < min) {
                min = a[i];
            }
        }
        return min;
    }

    public static int max(int a, int b) {
        return Math.max(a, b);
    }

    public static int max(int a, int b, int c) {
        return Math.max(Math.max(a, b), c);
    }

    public static int max(int a, int b, int c, int d) {
        return Math.max(Math.max(a, b), Math.max(c, d));
    }

    public static int max(int... a) {
        int max = a[0];
        for (int i = 1; i < a.length; i++) {
            if (a[i] > max) {
                max = a[i];
            }
        }
        return max;
    }

    public static float clamp(float val, float lo, float hi) {
        return val < lo ? lo : val > hi ? hi : val;
    }

    public static double clamp(double val, double lo, double hi) {
        return val < lo ? lo : val > hi ? hi : val;
    }

    public static int clamp(int val, int lo, int hi) {
        return MathHelper.clamp_int(val, lo, hi);
    }

    public static long clamp(long val, long lo, long hi) {
        return val < lo ? lo : val > hi ? hi : val;
    }

    public static float maxIgnoreNan(float... a) {
        float max = a[0];
        for (int i = 1; i < a.length; i++) {
            if (a[i] > max || Float.isNaN(max)) {
                max = a[i];
            }
        }
        if (Float.isNaN(max)) {
            throw new IllegalArgumentException("All values are NaN");
        }
        return max;
    }

    public static double gaussianProbabilityDensity(double x, double mean, double stdDev) {
        return exp(-(x - mean) * (x - mean) / (2 * stdDev * stdDev)) / (sqrt(2 * Math.PI) * stdDev);
    }

    /**
     * Generates a gaussian probability with the curves repeatedly positioned in a set distance to each other's center.
     * How it works:
     * Modulo usually works from 0 to limit-1.
     * Since the curve is located centered on 0, it has to be moved by limit/2 to the right.
     * This is done by substracting "halfspace" from the factor.
     * To have the ore still generated in the area it's supposed to, the location inside the modulo gets shifted
     * by both the mean location of the first curve and back the "halfspace".
     *
     * @param x       Value to be evaluated
     * @param mean    Center of the first curve
     * @param stdDev  Standard deviation
     * @param spacing Distance between the centers of the curves
     * @return gaussian probability
     */
    public static double bellCurveProbabilityCyclic(int x, int mean, double stdDev, int spacing) {
        // Using vars for better overview/easier debugging.
        double halfSpace = (double) spacing / 2.0;
        double shiftedLoc = (double) x - halfSpace - (double) mean;
        double factor = Math.abs(shiftedLoc % (double) spacing) - halfSpace;
        double divisorExp = 2.0 * stdDev * stdDev;
        double exponent = (-1.0 * factor) * factor / divisorExp;
        double result = exp(exponent);
        return result;
    }

    public static boolean rangesIntersect(int min1, int max1, int min2, int max2) {
        return min1 <= max2 && min2 <= max1;
    }

    public static int packColorARGB(int r, int g, int b, int a) {
        return a << 24 | r << 16 | g << 8 | b;
    }

    /**
     * Converts normalized 0-1 color component to 8-bit integer
     *
     * @param value 0-1 color component
     * @return 8-bit (0-255) integer color component
     */
    public static int to8bitComponent(float value) {
        return MathHelper.clamp_int(Math.round(value * 255), 0, 255);
    }

    public static double distanceSq(double toX, double toY, double toZ, ChunkCoordinates coords) {
        double d0 = coords.posX - toX;
        double d1 = coords.posY - toY;
        double d2 = coords.posZ - toZ;
        return d0 * d0 + d1 * d1 + d2 * d2;
    }

    public static int alignTo(int value, int alignment) {
        if (Integer.bitCount(alignment) == 1) {
            int log2L = Long.numberOfTrailingZeros(alignment);

            int mask = (1 << log2L) - 1;

            int result = value & ~mask;

            if ((value & mask) != 0) result += (1 << log2L);

            return result;
        } else {
            return ceilDiv(value, alignment) * alignment;
        }
    }

    public static long alignTo(long value, long alignment) {
        if (Long.bitCount(alignment) == 1) {
            int log2L = Long.numberOfTrailingZeros(alignment);

            long mask = (1L << log2L) - 1;

            long result = value & ~mask;

            if ((value & mask) != 0) result += (1L << log2L);

            return result;
        } else {
            return ceilDiv(value, alignment) * alignment;
        }
    }
}
