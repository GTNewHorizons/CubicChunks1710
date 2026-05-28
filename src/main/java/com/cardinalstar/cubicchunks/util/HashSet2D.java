package com.cardinalstar.cubicchunks.util;

import java.util.Iterator;

import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;

@SuppressWarnings("unused")
public class HashSet2D extends LongOpenHashSet {

    public boolean contains(int posX, int posZ) {
        return super.contains(Coords.packChunk(posX, posZ));
    }

    public boolean contains(XZAddressable xyz) {
        return contains(xyz.getX(), xyz.getZ());
    }

    public boolean remove(int posX, int posZ) {
        return super.remove(Coords.packChunk(posX, posZ));
    }

    public boolean remove(XZAddressable xyz) {
        return remove(xyz.getX(), xyz.getZ());
    }

    public boolean add(int posX, int posZ) {
        return super.add(Coords.packChunk(posX, posZ));
    }

    public boolean add(XZAddressable xyz) {
        return add(xyz.getX(), xyz.getZ());
    }

    public interface Consumer2D {
        void accept(int posX, int posZ);
    }

    public void forEach(Consumer2D consumer) {
        for (var e : this.fastEntryIterable()) {
            consumer.accept(e.getX(), e.getZ());
        }
    }

    public Iterator<XZAddressable> fastIterator() {
        LongIterator iter = super.iterator();

        MutableXZ pos = new MutableXZ();

        return new Iterator<>() {

            @Override
            public boolean hasNext() {
                return iter.hasNext();
            }

            @Override
            public XZAddressable next() {
                long l = iter.nextLong();

                pos.x = Coords.unpackChunkX(l);
                pos.z = Coords.unpackChunkZ(l);

                return pos;
            }
        };
    }

    public Iterable<XZAddressable> fastEntryIterable() {
        return this::fastIterator;
    }
}
