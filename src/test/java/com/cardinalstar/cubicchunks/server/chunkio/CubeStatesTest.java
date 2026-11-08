package com.cardinalstar.cubicchunks.server.chunkio;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

import org.junit.jupiter.api.Test;

import com.cardinalstar.cubicchunks.util.CubePos;

/** Empty cubes as state bytes: which cubes become states, and that their data comes back exactly. */
class CubeStatesTest {

    private static final CubePos POS = new CubePos(-3, 5, 7);

    private static NBTTagCompound level(NBTTagCompound root) {
        return root.getCompoundTag("Level");
    }

    private static void assertRoundTrip(NBTTagCompound cube) {
        int state = CubeStates.encode(POS, cube);
        assertTrue(state >= 0, "a state: " + cube);
        assertEquals(cube, CubeStates.decode(POS, state));
    }

    @Test
    void theEmptyCubesTheGameSavesComeBackExactly() {
        for (int population : new int[] { 0, 1, 255 }) {
            for (boolean above : new boolean[] { false, true }) {
                for (String surface : new String[] { "true", "false", "absent" }) {
                    for (boolean lightDone : new boolean[] { false, true }) {
                        NBTTagCompound cube = StorageTestData.empty(POS);
                        NBTTagCompound level = level(cube);
                        level.setShort("population", (short) population);
                        level.setBoolean("initLightDone", lightDone);
                        if (surface.equals("absent")) level.removeTag("isSurfaceTracked");
                        else level.setBoolean("isSurfaceTracked", Boolean.parseBoolean(surface));
                        int[] heights = new int[256];
                        Arrays.fill(heights, above ? POS.getY() * 16 + 17 : POS.getY() * 16);
                        level.getCompoundTag("LightingInfo")
                            .setIntArray("LastHeightMap", heights);
                        assertRoundTrip(cube);
                    }
                }
            }
        }
    }

    @Test
    void cubesConvertedFromVanillaWorldsComeBackExactly() {
        NBTTagCompound cube = StorageTestData.empty(POS);
        NBTTagCompound level = level(cube);
        level.removeTag("LightingInfo");
        level.removeTag("LightingInfoType");
        level.removeTag("Biomes");
        level.setBoolean("initLightDone", false);
        level.setBoolean("isSurfaceTracked", false);
        assertRoundTrip(cube);
    }

    @Test
    void anUnclippedHeightMapComesBackClipped() {
        NBTTagCompound cube = StorageTestData.empty(POS);
        int[] heights = new int[256];
        for (int i = 0; i < 256; i++) heights[i] = POS.getY() * 16 - 1 - i;
        level(cube).getCompoundTag("LightingInfo")
            .setIntArray("LastHeightMap", heights);
        int state = CubeStates.encode(POS, cube);
        assertTrue(state >= 0);
        assertEquals(StorageTestData.empty(POS), CubeStates.decode(POS, state), "the clipped values, the same on load");
    }

    @Test
    void cubesWithAnythingElseKeepFullRecords() {
        Map<String, NBTTagCompound> cubes = new HashMap<>();
        cubes.put("blocks", StorageTestData.cube(POS, 1));
        NBTTagCompound entity = StorageTestData.empty(POS);
        NBTTagList entities = new NBTTagList();
        entities.appendTag(new NBTTagCompound());
        level(entity).setTag("Entities", entities);
        cubes.put("an entity", entity);
        NBTTagCompound checks = StorageTestData.empty(POS);
        level(checks).getCompoundTag("LightingInfo")
            .setLong("NeighborLightChecksSky", 5);
        cubes.put("pending light checks", checks);
        NBTTagCompound crossing = StorageTestData.empty(POS);
        int[] heights = new int[256];
        Arrays.fill(heights, POS.getY() * 16);
        heights[17] = POS.getY() * 16 + 5;
        level(crossing).getCompoundTag("LightingInfo")
            .setIntArray("LastHeightMap", heights);
        cubes.put("a height map inside the cube", crossing);
        NBTTagCompound biomes = StorageTestData.empty(POS);
        level(biomes).setByteArray("Biomes", new byte[] { 1, 0, 0, 0, 3 });
        cubes.put("3D biomes", biomes);
        NBTTagCompound levelTag = StorageTestData.empty(POS);
        level(levelTag).setString("mod:data", "x");
        cubes.put("another Level tag", levelTag);
        NBTTagCompound rootTag = StorageTestData.empty(POS);
        rootTag.setString("mod:data", "x");
        cubes.put("another root tag", rootTag);
        NBTTagCompound population = StorageTestData.empty(POS);
        level(population).setShort("population", (short) 256);
        cubes.put("a population above 255", population);
        NBTTagCompound lighting = StorageTestData.empty(POS);
        level(lighting).setString("LightingInfoType", "other");
        cubes.put("another lighting engine", lighting);
        NBTTagCompound position = StorageTestData.empty(new CubePos(0, 0, 0));
        cubes.put("another position", position);
        for (Map.Entry<String, NBTTagCompound> e : cubes.entrySet())
            assertEquals(-1, CubeStates.encode(POS, e.getValue()), e.getKey());
    }

    @Test
    void columnStatesChangeAndStayWithinTheirSpan() throws Exception {
        Map<Integer, Integer> set = new HashMap<>();
        set.put(-2, CubeStates.encode(new CubePos(0, -2, 0), StorageTestData.empty(new CubePos(0, -2, 0))));
        set.put(3, CubeStates.encode(new CubePos(0, 3, 0), StorageTestData.empty(new CubePos(0, 3, 0))));
        Set<Integer> rejected = new HashSet<>();
        CubeStates.Column column = CubeStates.Column.EMPTY.with(set, rejected::add);
        assertEquals(set.get(-2), column.get(-2));
        assertEquals(set.get(3), column.get(3));
        assertEquals(-1, column.get(0));
        assertEquals(-1, column.get(4));

        NBTTagCompound tag = new NBTTagCompound();
        column.write(tag);
        assertEquals(column, CubeStates.Column.read(tag));

        Map<Integer, Integer> far = new HashMap<>();
        far.put(-2 + CubeStates.MAX_SPAN, set.get(3));
        far.put(-2, -1);
        CubeStates.Column moved = column.with(far, rejected::add);
        assertTrue(rejected.isEmpty(), "within the span once -2 is removed");
        assertEquals(-1, moved.get(-2));
        far.clear();
        far.put(3 - CubeStates.MAX_SPAN, set.get(3));
        CubeStates.Column wider = moved.with(far, rejected::add);
        assertEquals(
            java.util.Collections.singleton(3 - CubeStates.MAX_SPAN),
            rejected,
            "would widen the states beyond their span");
        assertEquals(moved, wider);

        Map<Integer, Integer> none = new HashMap<>();
        none.put(3, -1);
        none.put(-2 + CubeStates.MAX_SPAN, -1);
        CubeStates.Column empty = moved.with(none, rejected::add);
        assertTrue(empty.isEmpty());
        empty.write(tag);
        assertFalse(tag.hasKey(CubeStates.TAG), "no states, no tag");
    }

    @Test
    void invalidStoredStatesFailTheRead() {
        for (byte[] states : new byte[][] { {}, { 1 }, { 2, 0 } }) {
            NBTTagCompound column = new NBTTagCompound(), tag = new NBTTagCompound();
            tag.setInteger("MinY", 0);
            tag.setByteArray("States", states);
            column.setTag(CubeStates.TAG, tag);
            assertThrows(java.io.IOException.class, () -> CubeStates.Column.read(column), Arrays.toString(states));
        }
    }
}
