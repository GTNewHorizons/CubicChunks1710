package com.cardinalstar.cubicchunks.util;

import static com.gtnewhorizon.gtnhlib.util.CoordinatePacker.pack;

import java.util.Iterator;

import com.cardinalstar.cubicchunks.api.XYZAddressable;

import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;

@SuppressWarnings("unused")
public class HashSet3D extends LongOpenHashSet {

    public boolean contains(int posX, int posY, int posZ) {
        return super.contains(pack(posX, posY, posZ));
    }

    public boolean contains(XYZAddressable xyz) {
        return contains(xyz.getX(), xyz.getY(), xyz.getZ());
    }

    public boolean remove(int posX, int posY, int posZ) {
        return super.remove(pack(posX, posY, posZ));
    }

    public boolean remove(XYZAddressable xyz) {
        return remove(xyz.getX(), xyz.getY(), xyz.getZ());
    }

    public boolean add(int posX, int posY, int posZ) {
        return super.add(pack(posX, posY, posZ));
    }

    public boolean add(XYZAddressable xyz) {
        return add(xyz.getX(), xyz.getY(), xyz.getZ());
    }

    public interface Consumer3D {
        void accept(int posX, int posY, int posZ);
    }

    public void forEach(Consumer3D consumer) {
        for (var e : this.fastEntryIterable()) {
            consumer.accept(e.getX(), e.getY(), e.getZ());
        }
    }

    public Iterator<XYZAddressable> fastIterator() {
        LongIterator iter = super.iterator();

        MutableXYZ pos = new MutableXYZ();

        return new Iterator<>() {

            @Override
            public boolean hasNext() {
                return iter.hasNext();
            }

            @Override
            public XYZAddressable next() {
                long l = iter.nextLong();

                pos.x = Coords.x(l);
                pos.y = Coords.y(l);
                pos.z = Coords.z(l);

                return pos;
            }
        };
    }

    public Iterable<XYZAddressable> fastEntryIterable() {
        return this::fastIterator;
    }
}
