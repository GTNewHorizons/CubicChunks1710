package com.cardinalstar.cubicchunks.server.chunkio;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.minecraft.world.WorldServer;
import net.minecraft.world.chunk.Chunk;

import org.junit.jupiter.api.Test;

import com.cardinalstar.cubicchunks.api.XYZMap;
import com.cardinalstar.cubicchunks.api.XZMap;
import com.cardinalstar.cubicchunks.server.CubicPlayerManager;
import com.cardinalstar.cubicchunks.util.CubePos;
import com.cardinalstar.cubicchunks.world.column.EmptyColumn;
import com.cardinalstar.cubicchunks.world.cube.Cube;

/** Actual loader code with inert world/IO fixtures: no game saves or network. */
class CubeLoaderGcRegressionTest {

    static Object allocate(Class<?> c) throws Exception {
        Class<?> u = Class.forName("sun.misc.Unsafe");
        Field f = u.getDeclaredField("theUnsafe");
        f.setAccessible(true);
        return u.getMethod("allocateInstance", Class.class)
            .invoke(f.get(null), c);
    }

    static Field field(Class<?> c, String name) throws Exception {
        while (c != null) {
            try {
                Field f = c.getDeclaredField(name);
                f.setAccessible(true);
                return f;
            } catch (NoSuchFieldException e) {
                c = c.getSuperclass();
            }
        }
        throw new NoSuchFieldException(name);
    }

    static void set(Object o, String n, Object v) throws Exception {
        field(o.getClass(), n).set(o, v);
    }

    static Object get(Object o, String n) throws Exception {
        return field(o.getClass(), n).get(o);
    }

    private static class Manager extends CubicPlayerManager {

        Set<Integer> watched;

        Manager() {
            super(null);
        }

        @Override
        public boolean func_152621_a(int x, int z) {
            return watched.contains(x);
        }

        @Override
        public boolean isCubeWatched(int x, int y, int z) {
            return watched.contains(x);
        }
    }

    private static class Column extends EmptyColumn {

        Map<Integer, Cube> resident;
        Runnable unloading;
        int unloads, loads;

        Column() {
            super(null, 0, 0);
        }

        @Override
        public boolean hasLoadedCubes() {
            return !resident.isEmpty();
        }

        @Override
        public Collection<? extends Cube> getLoadedCubes() {
            return resident.values();
        }

        @Override
        public Cube getLoadedCube(int y) {
            return resident.get(y);
        }

        @Override
        public void addCube(Cube c) {
            resident.put(c.getY(), c);
        }

        @Override
        public Cube removeCube(int y) {
            return resident.remove(y);
        }

        @Override
        public void onChunkLoad() {
            isChunkLoaded = true;
            loads++;
        }

        @Override
        public void onChunkUnload() {
            isChunkLoaded = false;
            unloads++;
            if (unloading != null) unloading.run();
        }
    }

    private static class TestCube extends Cube {

        int unloads;

        TestCube(Column column) {
            super(
                null,
                null,
                column,
                new CubePos(column.xPosition, 3, 0),
                null,
                Collections.emptyList(),
                Collections.emptyMap(),
                null);
        }

        @Override
        public void onCubeUnload() {
            unloads++;
        }
    }

    private static class Fixture {

        CubeLoaderServer loader;
        Manager manager;
        XZMap columns;
        Class<?> ci = Class.forName(CubeLoaderServer.class.getName() + "$ColumnInfo");
        Class<?> qi = Class.forName(CubeLoaderServer.class.getName() + "$CubeInfo");
        int saves, callbacks;
        Runnable onSave;
        boolean failSave;

        Fixture() throws Exception {
            loader = (CubeLoaderServer) allocate(CubeLoaderServer.class);
            manager = (Manager) allocate(Manager.class);
            manager.watched = new HashSet<>();
            WorldServer world = (WorldServer) allocate(WorldServer.class);
            set(world, "thePlayerManager", manager);
            set(loader, "world", world);
            columns = new XZMap();
            set(loader, "columns", columns);
            set(loader, "cubes", new XYZMap<>());
            set(loader, "pendingCubeLoads", new ArrayList<>());
            set(loader, "pendingColumnLoads", new ArrayList<>());
            set(loader, "pendingCubeGenerates", new ArrayList<>());
            set(
                loader,
                "cubeIO",
                Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[] { ICubeIO.class }, (p, m, a) -> {
                    if (m.getName()
                        .equals("saveColumn")) {
                        saves++;
                        if (onSave != null) onSave.run();
                        if (failSave) throw new IllegalStateException("fixture save failure");
                    }
                    return null;
                }));
            set(loader, "callback", new CubeLoaderCallback() {

                @Override
                public void onColumnUnloaded(Chunk c) {
                    callbacks++;
                }
            });
        }

        Object column(int x) throws Exception {
            Column c = (Column) allocate(Column.class);
            set(c, "xPosition", x);
            set(c, "zPosition", 0);
            c.resident = new HashMap<>();
            c.isChunkLoaded = true;
            Constructor<?> ctor = ci.getDeclaredConstructor(CubeLoaderServer.class, int.class, int.class);
            ctor.setAccessible(true);
            Object info = ctor.newInstance(loader, x, 0);
            set(info, "column", c);
            columns.put((com.cardinalstar.cubicchunks.util.XZAddressable) info);
            return info;
        }

        Column body(Object info) {
            try {
                return (Column) get(info, "column");
            } catch (Exception e) {
                throw new AssertionError(e);
            }
        }

        void refill(Object info) {
            try {
                Column c = body(info);
                TestCube cube = new TestCube(c);
                c.addCube(cube);
                Constructor<?> ctor = qi
                    .getDeclaredConstructor(CubeLoaderServer.class, int.class, int.class, int.class);
                ctor.setAccessible(true);
                Object cubeInfo = ctor.newInstance(loader, c.xPosition, 3, 0);
                set(cubeInfo, "cube", cube);
                set(cubeInfo, "column", info);
                ((Set) get(info, "containedCubes")).add(cubeInfo);
                ((XYZMap) get(loader, "cubes")).put((com.cardinalstar.cubicchunks.api.XYZAddressable) cubeInfo);
            } catch (Exception e) {
                throw new AssertionError(e);
            }
        }

        List<Object> order() {
            List<Object> out = new ArrayList<>();
            for (Object c : columns) out.add(c);
            return out;
        }
    }

    @Test
    void queuedColumnRefilledByEarlierUnloadIsDeferred() throws Exception {
        Fixture f = new Fixture();
        f.column(201);
        f.column(202);
        List<Object> order = f.order();
        Object first = order.get(0), later = order.get(1);
        f.body(first).unloading = () -> f.refill(later);
        assertDoesNotThrow(f.loader::doGC);
        assertSame(later, f.columns.get(f.body(later).xPosition, 0));
        assertTrue(f.body(later).isChunkLoaded);
        assertEquals(0, f.body(later).unloads);
        assertEquals(1, f.callbacks);
    }

    @Test
    void sameColumnRefilledDuringUnloadKeepsCubesAndLoadedState() throws Exception {
        Fixture f = new Fixture();
        Object c = f.column(210);
        f.body(c).unloading = () -> f.refill(c);
        assertDoesNotThrow(f.loader::doGC);
        assertSame(c, f.columns.get(210, 0));
        assertTrue(f.body(c).isChunkLoaded);
        assertEquals(1, f.body(c).loads);
        assertEquals(1, f.body(c).resident.size());
        assertEquals(
            0,
            ((TestCube) f.body(c)
                .getLoadedCube(3)).unloads);
        assertEquals(0, f.callbacks);
    }

    @Test
    void columnWatchedAfterSelectionIsDeferred() throws Exception {
        Fixture f = new Fixture();
        f.column(211);
        f.column(212);
        List<Object> order = f.order();
        Object later = order.get(1);
        f.body(order.get(0)).unloading = () -> f.manager.watched.add(f.body(later).xPosition);
        f.loader.doGC();
        assertSame(later, f.columns.get(f.body(later).xPosition, 0));
        assertEquals(0, f.body(later).unloads);
    }

    @Test
    void pendingLoadCallbacksPreventCollection() throws Exception {
        Fixture f = new Fixture();
        Object c = f.column(220);
        f.loader.pauseLoadCalls();
        f.loader.doGC();
        assertSame(c, f.columns.get(220, 0));
        assertEquals(0, f.callbacks);
        f.loader.unpauseLoadCalls();
        f.loader.doGC();
        assertNull(f.columns.get(220, 0));
    }

    @Test
    void nestedCollectionDoesNotRepeatUnloadEvents() throws Exception {
        Fixture f = new Fixture();
        Object c = f.column(230);
        f.body(c).unloading = f.loader::doGC;
        assertDoesNotThrow(f.loader::doGC);
        assertEquals(1, f.body(c).unloads);
        assertEquals(1, f.callbacks);
    }

    @Test
    void savingRefillKeepsColumnIndexed() throws Exception {
        Fixture f = new Fixture();
        Object c = f.column(240);
        f.onSave = () -> f.refill(c);
        f.loader.doGC();
        assertSame(c, f.columns.get(240, 0));
        assertTrue(f.body(c).isChunkLoaded);
        assertEquals(0, f.callbacks);
        assertTrue(f.body(c).isModified);
    }

    @Test
    void activeInitializationDefersGcAndThenReleasesIt() throws Exception {
        Fixture f = new Fixture();
        Object c = f.column(280);
        set(f.loader, "activeLoadCalls", 1);
        f.loader.doGC();
        assertSame(c, f.columns.get(280, 0));
        set(f.loader, "activeLoadCalls", 0);
        f.loader.doGC();
        assertNull(f.columns.get(280, 0));
    }

    @Test
    void saveFailurePropagatesWithoutDetachingColumn() throws Exception {
        Fixture f = new Fixture();
        Object c = f.column(250);
        f.failSave = true;
        IllegalStateException ex = assertThrows(IllegalStateException.class, f.loader::doGC);
        assertEquals("fixture save failure", ex.getMessage());
        assertSame(c, f.columns.get(250, 0));
        assertTrue(f.body(c).isChunkLoaded);
        assertEquals(0, f.callbacks);
        f.failSave = false;
        f.loader.doGC();
        assertNull(f.columns.get(250, 0));
    }

    @Test
    void ordinaryEmptyColumnIsSavedAndRemovedOnce() throws Exception {
        Fixture f = new Fixture();
        Object c = f.column(260);
        f.loader.doGC();
        f.loader.doGC();
        assertNull(f.columns.get(260, 0));
        assertEquals(1, f.saves);
        assertEquals(1, f.callbacks);
        assertEquals(1, f.body(c).unloads);
    }

    @Test
    void physicalResidentCubeProtectsColumnEvenWithoutLoaderEntry() throws Exception {
        Fixture f = new Fixture();
        Object c = f.column(270);
        f.body(c)
            .addCube(new TestCube(f.body(c)));
        f.loader.doGC();
        assertSame(c, f.columns.get(270, 0));
        assertTrue(f.body(c).isChunkLoaded);
        assertEquals(0, f.callbacks);
    }
}
