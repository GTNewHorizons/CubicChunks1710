package com.cardinalstar.cubicchunks.world.heightmap;

import java.util.TreeSet;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import com.cardinalstar.cubicchunks.network.CCPacketBuffer;
import com.cardinalstar.cubicchunks.util.Coords;
import com.cardinalstar.cubicchunks.util.XSTR;

import io.netty.buffer.Unpooled;

public class YIntervalTreeTest {

    // ---- Empty state --------------------------------------------------------

    @Test
    public void emptyTree() {
        YIntervalTree tree = new YIntervalTree();
        Assertions.assertEquals(Coords.NO_HEIGHT, tree.getTopY());
        Assertions.assertEquals(Coords.NO_HEIGHT, tree.getBottomY());
        Assertions.assertFalse(tree.get(0));
        Assertions.assertFalse(tree.get(Integer.MIN_VALUE));
        Assertions.assertFalse(tree.get(Integer.MAX_VALUE));
    }

    @Test
    public void getTopAirY_emptyTree() {
        YIntervalTree tree = new YIntervalTree();
        Assertions.assertEquals(5, tree.getTopAirY(5));
        Assertions.assertEquals(-3, tree.getTopAirY(-3));
    }

    @Test
    public void getBottomAirY_emptyTree() {
        YIntervalTree tree = new YIntervalTree();
        Assertions.assertEquals(5, tree.getBottomAirY(5));
        Assertions.assertEquals(-3, tree.getBottomAirY(-3));
    }

    // ---- Single element -----------------------------------------------------

    @Test
    public void addSingle() {
        YIntervalTree tree = new YIntervalTree();
        tree.add(5);
        Assertions.assertEquals(5, tree.getTopY());
        Assertions.assertEquals(5, tree.getBottomY());
        Assertions.assertTrue(tree.get(5));
        Assertions.assertFalse(tree.get(4));
        Assertions.assertFalse(tree.get(6));
    }

    @Test
    public void addSingle_noisy() {
        YIntervalTree tree = new YIntervalTree();
        tree.add(100);
        Assertions.assertEquals(100, tree.getTopY());
        Assertions.assertEquals(100, tree.getBottomY());
        Assertions.assertEquals(101, tree.getTopAirY(100));
        Assertions.assertEquals(99, tree.getBottomAirY(100));
        Assertions.assertEquals(99, tree.getTopAirY(99));
        Assertions.assertEquals(101, tree.getBottomAirY(101));
    }

    @Test
    public void addThenRemoveSingleton() {
        YIntervalTree tree = new YIntervalTree();
        tree.add(5);
        tree.remove(5);
        Assertions.assertEquals(Coords.NO_HEIGHT, tree.getTopY());
        Assertions.assertEquals(Coords.NO_HEIGHT, tree.getBottomY());
        Assertions.assertFalse(tree.get(5));
    }

    // ---- Interval merging ---------------------------------------------------

    @Test
    public void addAdjacentRight_merges() {
        YIntervalTree tree = new YIntervalTree();
        tree.add(5);
        tree.add(6);
        Assertions.assertEquals(6, tree.getTopY());
        Assertions.assertEquals(5, tree.getBottomY());
        Assertions.assertTrue(tree.get(5));
        Assertions.assertTrue(tree.get(6));
        // should be a single interval [5,6]: first air above 5 is 7
        Assertions.assertEquals(7, tree.getTopAirY(5));
    }

    @Test
    public void addAdjacentLeft_merges() {
        YIntervalTree tree = new YIntervalTree();
        tree.add(6);
        tree.add(5);
        Assertions.assertEquals(6, tree.getTopY());
        Assertions.assertEquals(5, tree.getBottomY());
        Assertions.assertEquals(7, tree.getTopAirY(5));
    }

    @Test
    public void addBridging_mergesThree() {
        YIntervalTree tree = new YIntervalTree();
        tree.add(5);
        tree.add(7);
        // gap at 6
        Assertions.assertEquals(6, tree.getTopAirY(5));
        Assertions.assertEquals(6, tree.getBottomAirY(7));
        // bridge the gap
        tree.add(6);
        Assertions.assertEquals(7, tree.getTopY());
        Assertions.assertEquals(5, tree.getBottomY());
        Assertions.assertEquals(8, tree.getTopAirY(5));
        Assertions.assertEquals(4, tree.getBottomAirY(7));
    }

    @Test
    public void addDuplicate_noOp() {
        YIntervalTree tree = new YIntervalTree();
        tree.add(5);
        tree.add(5); // should be no-op
        Assertions.assertEquals(5, tree.getTopY());
        Assertions.assertEquals(5, tree.getBottomY());
        Assertions.assertEquals(6, tree.getTopAirY(5));
    }

    // ---- Remove / split -----------------------------------------------------

    @Test
    public void removeTop_updatesCache() {
        YIntervalTree tree = new YIntervalTree();
        tree.add(5);
        tree.add(6);
        tree.add(7);
        tree.remove(7);
        Assertions.assertEquals(6, tree.getTopY());
        Assertions.assertFalse(tree.get(7));
    }

    @Test
    public void removeBottom_updatesCache() {
        YIntervalTree tree = new YIntervalTree();
        tree.add(5);
        tree.add(6);
        tree.add(7);
        tree.remove(5);
        Assertions.assertEquals(6, tree.getBottomY());
        Assertions.assertFalse(tree.get(5));
    }

    @Test
    public void removeMiddle_splitsInterval() {
        YIntervalTree tree = new YIntervalTree();
        tree.add(5);
        tree.add(6);
        tree.add(7);
        tree.remove(6);
        Assertions.assertEquals(7, tree.getTopY());
        Assertions.assertEquals(5, tree.getBottomY());
        Assertions.assertTrue(tree.get(5));
        Assertions.assertFalse(tree.get(6));
        Assertions.assertTrue(tree.get(7));
        // two disjoint intervals: first air above 5 is 6
        Assertions.assertEquals(6, tree.getTopAirY(5));
        // first air below 7 is 6
        Assertions.assertEquals(6, tree.getBottomAirY(7));
    }

    @Test
    public void removeAbsent_noOp() {
        YIntervalTree tree = new YIntervalTree();
        tree.add(5);
        tree.remove(99); // absent
        Assertions.assertEquals(5, tree.getTopY());
    }

    // ---- Directional air queries --------------------------------------------

    @Test
    public void getTopAirY_insideInterval() {
        YIntervalTree tree = new YIntervalTree();
        for (int y = 10; y <= 20; y++) tree.add(y);
        Assertions.assertEquals(21, tree.getTopAirY(10));
        Assertions.assertEquals(21, tree.getTopAirY(15));
        Assertions.assertEquals(21, tree.getTopAirY(20));
    }

    @Test
    public void getTopAirY_aboveAllIntervals() {
        YIntervalTree tree = new YIntervalTree();
        for (int y = 10; y <= 20; y++) tree.add(y);
        Assertions.assertEquals(25, tree.getTopAirY(25));
    }

    @Test
    public void getTopAirY_inGap() {
        YIntervalTree tree = new YIntervalTree();
        for (int y = 10; y <= 15; y++) tree.add(y);
        for (int y = 20; y <= 25; y++) tree.add(y);
        // gap is 16..19
        Assertions.assertEquals(17, tree.getTopAirY(17));
    }

    @Test
    public void getBottomAirY_insideInterval() {
        YIntervalTree tree = new YIntervalTree();
        for (int y = 10; y <= 20; y++) tree.add(y);
        Assertions.assertEquals(9, tree.getBottomAirY(10));
        Assertions.assertEquals(9, tree.getBottomAirY(15));
        Assertions.assertEquals(9, tree.getBottomAirY(20));
    }

    @Test
    public void getBottomAirY_belowAllIntervals() {
        YIntervalTree tree = new YIntervalTree();
        for (int y = 10; y <= 20; y++) tree.add(y);
        Assertions.assertEquals(5, tree.getBottomAirY(5));
    }

    @Test
    public void getBottomAirY_inGap() {
        YIntervalTree tree = new YIntervalTree();
        for (int y = 10; y <= 15; y++) tree.add(y);
        for (int y = 20; y <= 25; y++) tree.add(y);
        Assertions.assertEquals(17, tree.getBottomAirY(17));
    }

    // ---- set() --------------------------------------------------------------

    @Test
    public void set_returnsChangedFlag() {
        YIntervalTree tree = new YIntervalTree();
        Assertions.assertTrue(tree.set(5, true)); // was absent, now present
        Assertions.assertFalse(tree.set(5, true)); // already present, no change
        Assertions.assertTrue(tree.set(5, false)); // was present, now absent
        Assertions.assertFalse(tree.set(5, false)); // already absent, no change
    }

    @Test
    public void set_trueEqualsAdd() {
        YIntervalTree a = new YIntervalTree();
        YIntervalTree b = new YIntervalTree();
        a.add(5);
        a.add(6);
        a.add(7);
        b.set(5, true);
        b.set(6, true);
        b.set(7, true);
        Assertions.assertEquals(a.getTopY(), b.getTopY());
        Assertions.assertEquals(a.getBottomY(), b.getBottomY());
        Assertions.assertEquals(a.get(6), b.get(6));
    }

    // ---- Negative Y ---------------------------------------------------------

    @Test
    public void negativeY() {
        YIntervalTree tree = new YIntervalTree();
        tree.add(-100);
        tree.add(-99);
        tree.add(-98);
        Assertions.assertEquals(-98, tree.getTopY());
        Assertions.assertEquals(-100, tree.getBottomY());
        Assertions.assertEquals(-97, tree.getTopAirY(-100));
        Assertions.assertEquals(-101, tree.getBottomAirY(-100));
        tree.remove(-99);
        Assertions.assertFalse(tree.get(-99));
        Assertions.assertEquals(-99, tree.getTopAirY(-100));
    }

    // ---- Pool growth --------------------------------------------------------

    @Test
    public void poolGrowth() {
        // Force pool to grow by creating many disjoint intervals (add every other Y)
        YIntervalTree tree = new YIntervalTree();
        for (int y = 0; y < 200; y += 2) tree.add(y);
        for (int y = 0; y < 200; y += 2) Assertions.assertTrue(tree.get(y), "missing y=" + y);
        for (int y = 1; y < 200; y += 2) Assertions.assertFalse(tree.get(y), "unexpected y=" + y);
        Assertions.assertEquals(198, tree.getTopY());
        Assertions.assertEquals(0, tree.getBottomY());
    }

    // ---- Serialization ------------------------------------------------------

    @Test
    public void serialization_roundTrip() {
        YIntervalTree original = new YIntervalTree();
        // Create a mix of intervals
        for (int y = 0; y < 10; y++) original.add(y);
        for (int y = 20; y < 30; y++) original.add(y);
        original.add(50);

        CCPacketBuffer buf = new CCPacketBuffer(Unpooled.buffer());
        original.writeData(buf);

        YIntervalTree restored = new YIntervalTree();
        restored.readData(buf);

        Assertions.assertEquals(original.getTopY(), restored.getTopY());
        Assertions.assertEquals(original.getBottomY(), restored.getBottomY());

        for (int y = -5; y <= 60; y++) {
            Assertions.assertEquals(original.get(y), restored.get(y), "mismatch at y=" + y);
        }
    }

    @Test
    public void serialization_emptyTree() {
        YIntervalTree original = new YIntervalTree();
        CCPacketBuffer buf = new CCPacketBuffer(Unpooled.buffer());
        original.writeData(buf);

        YIntervalTree restored = new YIntervalTree();
        restored.readData(buf);

        Assertions.assertEquals(Coords.NO_HEIGHT, restored.getTopY());
        Assertions.assertEquals(Coords.NO_HEIGHT, restored.getBottomY());
    }

    @Test
    public void readData_clearsExistingState() {
        YIntervalTree tree = new YIntervalTree();
        for (int y = 100; y < 200; y++) tree.add(y); // populate

        // Serialize an empty tree into it
        YIntervalTree empty = new YIntervalTree();
        CCPacketBuffer buf = new CCPacketBuffer(Unpooled.buffer());
        empty.writeData(buf);
        tree.readData(buf);

        Assertions.assertEquals(Coords.NO_HEIGHT, tree.getTopY());
        Assertions.assertEquals(Coords.NO_HEIGHT, tree.getBottomY());
        Assertions.assertFalse(tree.get(150));
    }

    // ---- Fuzz test ----------------------------------------------------------

    @Test
    public void fuzz_addRemove() {
        XSTR rng = new XSTR(42L);
        YIntervalTree tree = new YIntervalTree();
        TreeSet<Integer> ref = new TreeSet<>();

        final int RANGE = 200;
        final int OPS = 5000;

        for (int i = 0; i < OPS; i++) {
            int y = rng.nextInt(RANGE) - RANGE / 2; // range: [-100, 100)
            if (rng.nextBoolean()) {
                tree.add(y);
                ref.add(y);
            } else {
                tree.remove(y);
                ref.remove(y);
            }

            // Spot-check get() for several values each iteration
            for (int check = -5; check <= 5; check++) {
                Assertions
                    .assertEquals(ref.contains(check), tree.get(check), "op=" + i + " y=" + y + " check=" + check);
            }
        }

        // Full check at end
        int expectedTop = ref.isEmpty() ? Coords.NO_HEIGHT : ref.last();
        int expectedBottom = ref.isEmpty() ? Coords.NO_HEIGHT : ref.first();
        Assertions.assertEquals(expectedTop, tree.getTopY(), "topY mismatch");
        Assertions.assertEquals(expectedBottom, tree.getBottomY(), "bottomY mismatch");

        for (int y = -RANGE / 2; y < RANGE / 2; y++) {
            Assertions.assertEquals(ref.contains(y), tree.get(y), "get mismatch at y=" + y);
        }
    }

    @Test
    public void fuzz_airQueries() {
        XSTR rng = new XSTR(99L);
        YIntervalTree tree = new YIntervalTree();
        TreeSet<Integer> ref = new TreeSet<>();

        // Populate
        for (int i = 0; i < 2000; i++) {
            int y = rng.nextInt(100) - 50;
            if (rng.nextBoolean()) {
                tree.add(y);
                ref.add(y);
            } else {
                tree.remove(y);
                ref.remove(y);
            }
        }

        // Check air queries against naive reference scan
        for (int startY = -60; startY <= 60; startY++) {
            // getTopAirY: first y' >= startY not in ref
            int refTopAir = startY;
            while (ref.contains(refTopAir)) refTopAir++;
            Assertions.assertEquals(refTopAir, tree.getTopAirY(startY), "getTopAirY mismatch at startY=" + startY);

            // getBottomAirY: first y' <= startY not in ref
            int refBottomAir = startY;
            while (ref.contains(refBottomAir)) refBottomAir--;
            Assertions
                .assertEquals(refBottomAir, tree.getBottomAirY(startY), "getBottomAirY mismatch at startY=" + startY);
        }
    }
}
