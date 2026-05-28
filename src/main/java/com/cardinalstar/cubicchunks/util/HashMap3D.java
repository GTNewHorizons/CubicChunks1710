package com.cardinalstar.cubicchunks.util;

import static com.gtnewhorizon.gtnhlib.util.CoordinatePacker.pack;
import static com.gtnewhorizon.gtnhlib.util.CoordinatePacker.unpackX;
import static com.gtnewhorizon.gtnhlib.util.CoordinatePacker.unpackY;
import static com.gtnewhorizon.gtnhlib.util.CoordinatePacker.unpackZ;

import java.util.Spliterator;
import java.util.Spliterators;
import java.util.function.Consumer;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import org.jetbrains.annotations.NotNull;

import com.cardinalstar.cubicchunks.api.XYZAddressable;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.AbstractObjectSet;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import it.unimi.dsi.fastutil.objects.ObjectSet;

@SuppressWarnings("unused")
public class HashMap3D<V> extends Long2ObjectOpenHashMap<V> {

    public V get(int posX, int posY, int posZ) {
        return super.get(pack(posX, posY, posZ));
    }

    public V get(XYZAddressable xyz) {
        return get(xyz.getX(), xyz.getY(), xyz.getZ());
    }

    public V remove(int posX, int posY, int posZ) {
        return super.remove(pack(posX, posY, posZ));
    }

    public V remove(XYZAddressable xyz) {
        return remove(xyz.getX(), xyz.getY(), xyz.getZ());
    }

    public boolean containsKey(int posX, int posY, int posZ) {
        return super.containsKey(pack(posX, posY, posZ));
    }

    public boolean containsKey(XYZAddressable xyz) {
        return containsKey(xyz.getX(), xyz.getY(), xyz.getZ());
    }

    public V put(int posX, int posY, int posZ, V v) {
        return super.put(pack(posX, posY, posZ), v);
    }

    public V put(XYZAddressable xyz, V v) {
        return put(xyz.getX(), xyz.getY(), xyz.getZ(), v);
    }

    public interface ComputeFn3D<V> {

        V apply(int posX, int posY, int posZ);
    }

    public V computeIfAbsent(int posX, int posY, int posZ, @NotNull HashMap3D.ComputeFn3D<V> mappingFunction) {
        V v;

        long key = pack(posX, posY, posZ);

        if ((v = get(key)) == null) {
            V newValue;
            if ((newValue = mappingFunction.apply(posX, posY, posZ)) != null) {
                put(key, newValue);
                return newValue;
            }
        }

        return v;
    }

    public interface Consumer3D<T> {

        void accept(int posX, int posY, int posZ, T value);
    }

    public void forEach(Consumer3D<V> consumer) {
        for (var e : this.fastEntryIterable()) {
            consumer.accept(e.getX(), e.getY(), e.getZ(), e.getValue());
        }
    }

    public interface FastEntrySet3D<V> extends ObjectSet<Entry3D<V>> {

        /**
         * Returns a fast iterator over this entry set; the iterator might return always the same entry
         * instance, suitably mutated.
         *
         * @return a fast iterator over this entry set; the iterator might return always the same
         *         {@link java.util.Map.Entry} instance, suitably mutated.
         */
        ObjectIterator<Entry3D<V>> fastIterator();

        /**
         * Iterates quickly over this entry set; the iteration might happen always on the same entry
         * instance, suitably mutated.
         *
         * <p>
         *
         * This default implementation just delegates to {@link #forEach(Consumer)}.
         *
         * @param consumer a consumer that will by applied to the entries of this set; the entries might be
         *                 represented by the same entry instance, suitably mutated.
         * @since 8.1.0
         */
        default void fastForEach(final Consumer<? super Entry3D<V>> consumer) {
            forEach(consumer);
        }
    }

    private FastEntrySet3DImpl entrySet;

    public FastEntrySet3D<V> fastEntrySet() {
        if (entrySet == null) entrySet = new FastEntrySet3DImpl();

        return entrySet;
    }

    public Iterable<Entry3D<V>> fastEntryIterable() {
        return () -> fastEntrySet().fastIterator();
    }

    public Stream<Entry3D<V>> fastEntryStream() {
        return StreamSupport.stream(
            Spliterators.spliterator(
                fastEntryIterable().iterator(),
                size(),
                Spliterator.SIZED | Spliterator.NONNULL | Spliterator.DISTINCT),
            false);
    }

    private class FastEntrySet3DImpl extends AbstractObjectSet<Entry3D<V>> implements FastEntrySet3D<V> {

        @Override
        public ObjectIterator<Entry3D<V>> fastIterator() {
            Entry3D<V> entry = new Entry3D<>();

            var iter = HashMap3D.this.long2ObjectEntrySet()
                .fastIterator();

            return new ObjectIterator<>() {

                @Override
                public boolean hasNext() {
                    return iter.hasNext();
                }

                @Override
                public Entry3D<V> next() {
                    var e = iter.next();

                    entry.setKey(e.getLongKey());
                    entry.setValue(e.getValue());

                    return entry;
                }
            };
        }

        @Override
        public @NotNull ObjectIterator<Entry3D<V>> iterator() {
            var iter = HashMap3D.this.long2ObjectEntrySet()
                .fastIterator();

            return new ObjectIterator<>() {

                @Override
                public boolean hasNext() {
                    return iter.hasNext();
                }

                @Override
                public Entry3D<V> next() {
                    var e = iter.next();

                    return new Entry3D<>(e.getLongKey(), e.getValue());
                }
            };
        }

        @Override
        public int size() {
            return HashMap3D.this.size();
        }
    }

    public static class Entry3D<T> extends BasicEntry<T> implements XYZAddressable {

        public Entry3D() {}

        /// @deprecated Use [#Entry3D(long, Object)] to avoid the long boxing.
        @Deprecated
        public Entry3D(Long key, T value) {
            super(key, value);
        }

        public Entry3D(long key, T value) {
            super(key, value);
        }

        public Entry3D(int posX, int posY, int posZ, T value) {
            super(pack(posX, posY, posZ), value);
        }

        void setKey(long key) {
            super.key = key;
        }

        @Override
        public T setValue(T value) {
            T old = super.value;
            super.value = value;
            return old;
        }

        @Override
        public final int getX() {
            return unpackX(getLongKey());
        }

        @Override
        public final int getY() {
            return unpackY(getLongKey());
        }

        @Override
        public final int getZ() {
            return unpackZ(getLongKey());
        }
    }
}
