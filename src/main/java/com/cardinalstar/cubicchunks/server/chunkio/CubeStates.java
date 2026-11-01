package com.cardinalstar.cubicchunks.server.chunkio;

import java.io.IOException;
import java.util.Arrays;
import java.util.Map;
import java.util.function.IntConsumer;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.common.util.Constants;

import com.cardinalstar.cubicchunks.lighting.LightingManager;
import com.cardinalstar.cubicchunks.lighting.phosphor.LightingHooks;
import com.cardinalstar.cubicchunks.util.Coords;
import com.cardinalstar.cubicchunks.util.CubePos;
import com.cardinalstar.cubicchunks.world.cube.Cube;

/**
 * Empty cubes are not written as records of their own: each is two state bytes in its column's record, from which the
 * cube's data is rebuilt when it is read. A cube is stored this way only when rebuilding gives back exactly its data:
 * no blocks, entities, tile entities, scheduled ticks, 3D biomes or other tags, and either the lighting info the game
 * writes, with a saved height map entirely below or above the cube and no pending neighbor light checks, or no
 * lighting info at all (cubes converted from vanilla worlds). Every other cube keeps a full record.
 * <p>
 * A state is a flags byte and the cube's population status (0..255).
 */
final class CubeStates {

    /** Root tag of a column's NBT: {@code {MinY: int, States: byte[]}}, two bytes per cube Y from {@code MinY}. */
    static final String TAG = "CubeStates";
    /** Most cube Ys one column's states span; a cube that would widen them further keeps a full record. */
    static final int MAX_SPAN = 4096;

    private static final int PRESENT = 1, CONVERTED = 2, LIGHT_DONE = 4, SURFACE_TRACKED = 8, NO_SURFACE_TAG = 16,
        HEIGHTS_ABOVE = 32;

    private CubeStates() {}

    /**
     * The state of a cube, or -1 if it needs a full record. The saved height map may be clipped or not, as clipping
     * (IONbtWriter.clipLastHeightMap) does not change what it means on load.
     */
    static int encode(CubePos pos, NBTTagCompound root) {
        if (root.func_150296_c()
            .size() != 1 || !root.hasKey("Level", Constants.NBT.TAG_COMPOUND)) return -1;
        NBTTagCompound level = root.getCompoundTag("Level");
        int population = level.getShort("population");
        if (population < 0 || population > 255) return -1;

        int flags = PRESENT;
        if (level.getBoolean("initLightDone")) flags |= LIGHT_DONE;
        if (!level.hasKey("isSurfaceTracked")) flags |= NO_SURFACE_TAG;
        else if (level.getBoolean("isSurfaceTracked")) flags |= SURFACE_TRACKED;

        int[] heights = null;
        if (!level.hasKey("LightingInfo")) flags |= CONVERTED;
        else {
            heights = level.getCompoundTag("LightingInfo")
                .getIntArray("LastHeightMap");
            if (heights.length != 256) return -1;
            // as the clipped values: at most the cube's bottom Y is below it, at least its bottom Y + 17 is above
            int below = Coords.cubeToMinBlock(pos.getY());
            long above = (long) below + Cube.SIZE + 1;
            boolean allBelow = true, allAbove = true;
            for (int height : heights) {
                allBelow &= height <= below;
                allAbove &= height >= above;
            }
            if (allAbove) flags |= HEIGHTS_ABOVE;
            else if (!allBelow) return -1;
        }
        int state = flags | population << 8;

        NBTTagCompound rebuilt = decode(pos, state);
        // the height map was checked above; everything else has to be identical
        if (heights != null) rebuilt.getCompoundTag("Level")
            .getCompoundTag("LightingInfo")
            .setIntArray("LastHeightMap", heights);
        return rebuilt.equals(root) ? state : -1;
    }

    /** The cube data a state stands for, as IONbtWriter writes it (with a clipped height map). */
    static NBTTagCompound decode(CubePos pos, int state) {
        NBTTagCompound level = new NBTTagCompound();
        level.setByte("v", (byte) 1);
        level.setInteger("x", pos.getX());
        level.setInteger("y", pos.getY());
        level.setInteger("z", pos.getZ());
        level.setShort("population", (short) (state >>> 8 & 255));
        if ((state & NO_SURFACE_TAG) == 0) level.setBoolean("isSurfaceTracked", (state & SURFACE_TRACKED) != 0);
        level.setBoolean("initLightDone", (state & LIGHT_DONE) != 0);
        level.setTag("Entities", new NBTTagList());
        level.setTag("TileEntities", new NBTTagList());
        level.setTag("TileTicks", new NBTTagList());
        if ((state & CONVERTED) == 0) {
            level.setString("LightingInfoType", LightingManager.ID);
            NBTTagCompound lighting = new NBTTagCompound();
            int[] heights = new int[256];
            int below = Coords.cubeToMinBlock(pos.getY());
            Arrays.fill(heights, (state & HEIGHTS_ABOVE) != 0 ? below + Cube.SIZE + 1 : below);
            lighting.setIntArray("LastHeightMap", heights);
            lighting.setLong(LightingHooks.neighborLightChecksSkyKey, 0);
            lighting.setLong(LightingHooks.neighborLightChecksBlockKey, 0);
            level.setTag("LightingInfo", lighting);
            // Cube.writeBiomeArray of a cube without 3D biomes
            level.setByteArray("Biomes", new byte[] { 0 });
        }
        NBTTagCompound root = new NBTTagCompound();
        root.setTag("Level", level);
        return root;
    }

    /** The states of one column's empty cubes. Immutable. */
    static final class Column {

        static final Column EMPTY = new Column(0, new byte[0]);

        private final int minY;
        /** Two bytes per cube Y from {@link #minY}; a flags byte of 0 means no state. */
        private final byte[] states;

        private Column(int minY, byte[] states) {
            this.minY = minY;
            this.states = states;
        }

        boolean isEmpty() {
            return states.length == 0;
        }

        /** The state of the cube at {@code y}, or -1. */
        int get(int y) {
            long i = (long) y - minY;
            if (i < 0 || i >= states.length / 2) return -1;
            int flags = states[(int) i * 2] & 255;
            return flags == 0 ? -1 : flags | (states[(int) i * 2 + 1] & 255) << 8;
        }

        void forEach(IntConsumer ys) {
            for (int i = 0; i < states.length / 2; i++) if (states[i * 2] != 0) ys.accept(minY + i);
        }

        /**
         * These states with the given changes (cube Y to state, -1 removes it). A change that would widen the states
         * beyond {@link #MAX_SPAN} cube Ys is left out and reported through {@code rejected}.
         */
        Column with(Map<Integer, Integer> changes, IntConsumer rejected) {
            java.util.TreeMap<Integer, Integer> all = new java.util.TreeMap<>();
            forEach(y -> all.put(y, get(y)));
            for (Map.Entry<Integer, Integer> change : changes.entrySet()) {
                if (change.getValue() < 0) all.remove(change.getKey());
            }
            for (Map.Entry<Integer, Integer> change : changes.entrySet()) {
                int y = change.getKey();
                if (change.getValue() < 0) continue;
                long lowest = all.isEmpty() ? y : Math.min(all.firstKey(), y);
                long highest = all.isEmpty() ? y : Math.max(all.lastKey(), y);
                if (highest - lowest >= MAX_SPAN) {
                    rejected.accept(y);
                    continue;
                }
                all.put(y, change.getValue());
            }
            if (all.isEmpty()) return EMPTY;
            int low = all.firstKey();
            byte[] out = new byte[(all.lastKey() - low + 1) * 2];
            for (Map.Entry<Integer, Integer> e : all.entrySet()) {
                out[(e.getKey() - low) * 2] = (byte) e.getValue()
                    .intValue();
                out[(e.getKey() - low) * 2 + 1] = (byte) (e.getValue() >>> 8);
            }
            return new Column(low, out);
        }

        /** The states stored in a column's NBT. */
        static Column read(NBTTagCompound column) throws IOException {
            if (!column.hasKey(TAG)) return EMPTY;
            if (!column.hasKey(TAG, Constants.NBT.TAG_COMPOUND)) throw new IOException("Invalid cube states");
            NBTTagCompound tag = column.getCompoundTag(TAG);
            byte[] states = tag.getByteArray("States");
            int minY = tag.getInteger("MinY");
            if (!tag.hasKey("MinY", Constants.NBT.TAG_INT) || !tag.hasKey("States", Constants.NBT.TAG_BYTE_ARRAY)
                || states.length == 0
                || states.length % 2 != 0
                || states.length / 2 > MAX_SPAN
                || (long) minY + states.length / 2 - 1 > Integer.MAX_VALUE)
                throw new IOException("Invalid cube states");
            for (int i = 0; i < states.length; i += 2) {
                if (states[i] != 0 && (states[i] & PRESENT) == 0) throw new IOException("Invalid cube state");
            }
            return new Column(minY, states);
        }

        /** Stores these states in a column's NBT, or removes them when there are none. */
        void write(NBTTagCompound column) {
            if (isEmpty()) {
                column.removeTag(TAG);
                return;
            }
            NBTTagCompound tag = new NBTTagCompound();
            tag.setInteger("MinY", minY);
            tag.setByteArray("States", states);
            column.setTag(TAG, tag);
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Column c && c.minY == minY && Arrays.equals(c.states, states);
        }

        @Override
        public int hashCode() {
            return 31 * minY + Arrays.hashCode(states);
        }
    }
}
