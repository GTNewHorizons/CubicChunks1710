package com.cardinalstar.cubicchunks.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import org.junit.jupiter.api.Test;

class CoordsTest {

    @Test
    void defaultLayoutMatchesPrevious() {
        assumeTrue(System.getProperty(Coords.HEIGHT_BITS_PROPERTY) == null);
        assertEquals(21, Coords.Y_BITS);
        assertEquals(21, Coords.XZ_BITS);
        assertEquals(-1048576, Coords.MIN_PACKED_Y);
        assertEquals(1048575, Coords.MAX_PACKED_Y);
        // Used to alias to -1 / 0 instead of throwing
        assertThrows(IllegalArgumentException.class, () -> Coords.key(2097151, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> Coords.key(-2097152, 0, 0));
    }

    @Test
    void layoutFitsInALong() {
        assertTrue(Coords.Y_BITS + 2 * Coords.XZ_BITS <= 64);
    }

    @Test
    void roundTripsAtLimits() {
        int[] xzs = { Coords.MIN_PACKED_XZ, -1, 0, 1, Coords.MAX_PACKED_XZ };
        int[] ys = { Coords.MIN_PACKED_Y, -1, 0, 1, Coords.MAX_PACKED_Y };
        for (int x : xzs) {
            for (int y : ys) {
                for (int z : xzs) {
                    long key = Coords.key(x, y, z);
                    assertEquals(x, Coords.x(key));
                    assertEquals(y, Coords.y(key));
                    assertEquals(z, Coords.z(key));
                }
            }
        }
    }

    @Test
    void oppositeLimitsDontCollide() {
        assertNotEquals(Coords.key(Coords.MIN_PACKED_XZ, 0, 0), Coords.key(Coords.MAX_PACKED_XZ, 0, 0));
        assertNotEquals(Coords.key(0, Coords.MIN_PACKED_Y, 0), Coords.key(0, Coords.MAX_PACKED_Y, 0));
        assertNotEquals(Coords.key(0, 0, -1), Coords.key(0, 0, 0));
    }

    @Test
    void rejectsOutOfRange() {
        assertThrows(IllegalArgumentException.class, () -> Coords.key((long) Coords.MAX_PACKED_XZ + 1, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> Coords.key(0, (long) Coords.MIN_PACKED_Y - 1, 0));
        assertThrows(IllegalArgumentException.class, () -> Coords.key(0, 0, (long) Coords.MIN_PACKED_XZ - 1));
    }

    @Test
    void isPackableMatchesLimits() {
        assertTrue(Coords.isPackable(Coords.MAX_PACKED_XZ, Coords.MIN_PACKED_Y, Coords.MIN_PACKED_XZ));
        assertFalse(Coords.isPackable((long) Coords.MAX_PACKED_XZ + 1, 0, 0));
        assertFalse(Coords.isPackable(0, (long) Coords.MAX_PACKED_Y + 1, 0));
    }
}
