package com.cardinalstar.cubicchunks.server.chunkio;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Map;
import java.util.Optional;

import net.jpountz.lz4.LZ4Compressor;
import net.jpountz.lz4.LZ4Factory;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;

import org.jetbrains.annotations.Nullable;

import com.cardinalstar.cubicchunks.CubicChunks;

import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;

/**
 * Data that {@link CubeIO} holds for columns or cubes: saves that are still being written, data read ahead of a load,
 * recently unloaded and written data and positions known not to exist on disk. Reads always see the
 * newest save. Written data is kept compressed within a byte budget instead of as NBT objects. Thread-safe.
 */
final class IOCache<K> {

    /** Map and bookkeeping overhead counted per kept entry, roughly. */
    static final int ENTRY_OVERHEAD = 96;

    private enum State {
        /** A save that is queued or being written. Never evicted. */
        PENDING,
        /** Read ahead of a load; handed over once and dropped after {@link #preloadExpiryMillis} if not taken. */
        PRELOADED,
        /** Written to storage; kept compressed, or as the storage record itself, for reloads until evicted. */
        CLEAN,
        /** Known not to exist in storage. */
        ABSENT
    }

    private static final class Entry {

        final State state;
        /** Pending only: keep the data cached once written (the object was unloaded). */
        final boolean keep;
        @Nullable
        final NBTTagCompound tag;
        @Nullable
        final byte[] packed;
        /** Clean only, instead of {@link #packed}: the record storage wrote, possibly shared with other entries. */
        @Nullable
        final Record record;
        final long time;

        Entry(State state, boolean keep, @Nullable NBTTagCompound tag, @Nullable byte[] packed, @Nullable Record record,
            long time) {
            this.state = state;
            this.keep = keep;
            this.tag = tag;
            this.packed = packed;
            this.record = record;
            this.time = time;
        }

        /** Bytes counted for this entry; a record's own bytes are counted once for all entries pointing at it. */
        long size() {
            if (state == State.CLEAN) return (packed != null ? packed.length : 0) + ENTRY_OVERHEAD;
            return state == State.ABSENT ? ENTRY_OVERHEAD : 0;
        }
    }

    /**
     * A record as storage wrote it, which several entries can point at (e.g. a bundle of cubes). Its bytes count
     * against the budget once, while any entry points at it.
     */
    static final class Record {

        final ByteBuffer bytes;
        private int references;

        Record(ByteBuffer bytes) {
            // keep only the record itself: storage may return a view into a larger buffer, which the budget would
            // not see
            if (bytes.capacity() != bytes.remaining()) {
                ByteBuffer copy = ByteBuffer.allocate(bytes.remaining());
                copy.put(bytes.duplicate());
                copy.flip();
                bytes = copy.asReadOnlyBuffer();
            }
            this.bytes = bytes;
        }
    }

    /** Decodes every position stored in a record, keyed like the cache. The caller owns the results. */
    interface Decoder<K> {

        Map<K, NBTTagCompound> decode(K pos, ByteBuffer record) throws IOException;
    }

    private final Object2ObjectLinkedOpenHashMap<K, Entry> entries = new Object2ObjectLinkedOpenHashMap<>();
    /**
     * Sequence number and time of each position's last save, kept {@link #saveFenceMillis} after it. A read that
     * started
     * before a later save of the same position is older than that save, even when the save's entry is gone already.
     */
    private final Object2ObjectLinkedOpenHashMap<K, long[]> saves = new Object2ObjectLinkedOpenHashMap<>();
    private final long budgetBytes;
    private final long preloadExpiryMillis;
    private final long saveFenceMillis;
    private long keptBytes;
    private long saveSequence;
    private int pending;
    @Nullable
    private final Decoder<K> decoder;

    IOCache(long budgetBytes, long preloadExpiryMillis, long saveFenceMillis) {
        this(budgetBytes, preloadExpiryMillis, saveFenceMillis, null);
    }

    /** {@code decoder} reads records kept by {@link #writtenAs}. */
    IOCache(long budgetBytes, long preloadExpiryMillis, long saveFenceMillis, @Nullable Decoder<K> decoder) {
        this.budgetBytes = budgetBytes;
        this.preloadExpiryMillis = preloadExpiryMillis;
        this.saveFenceMillis = saveFenceMillis;
        this.decoder = decoder;
    }

    private void put(K pos, Entry entry) {
        Entry old = entries.putAndMoveToLast(pos, entry);
        if (old != null) remove(old);
        if (entry.state == State.PENDING) pending++;
        keptBytes += entry.size();
        if (entry.record != null && entry.record.references++ == 0) keptBytes += entry.record.bytes.remaining();
    }

    private void remove(Entry old) {
        if (old.state == State.PENDING) pending--;
        keptBytes -= old.size();
        if (old.record != null && --old.record.references == 0) keptBytes -= old.record.bytes.remaining();
    }

    /**
     * A save of {@code tag} was queued. Until {@link #written} is called for it, loads return copies of it. With
     * {@code keep} (the object was unloaded) its data stays cached after the write; otherwise the loaded object is
     * newer
     * and the entry is dropped then.
     */
    synchronized void putPending(K pos, NBTTagCompound tag, boolean keep, long now) {
        put(pos, new Entry(State.PENDING, keep, tag, null, null, now));
        saves.putAndMoveToLast(pos, new long[] { ++saveSequence, now });
    }

    /**
     * The current save sequence number; pass it to {@link #putPreloaded} or {@link #savedSince} for a read started now.
     */
    synchronized long fence() {
        return saveSequence;
    }

    /** Whether {@code pos} was saved after {@code fence} was taken. */
    synchronized boolean savedSince(K pos, long fence) {
        long[] save = saves.get(pos);
        return save != null && save[0] > fence;
    }

    /** Drops a pending save without keeping its data, e.g. when settling it failed. */
    synchronized void discardPending(K pos, NBTTagCompound tag) {
        Entry current = entries.get(pos);
        if (current != null && current.state == State.PENDING && current.tag == tag) remove(entries.remove(pos));
    }

    /**
     * The write of {@code tag} finished (or failed; the data is still kept). Unless a newer save replaced it, the entry
     * becomes compressed written data.
     */
    void written(K pos, NBTTagCompound tag, long now) {
        Entry written;
        synchronized (this) {
            // reads that started before the save stay refused for the fence time after it reached storage. If the fence
            // expired while the save waited, the current sequence number is at least the save's: it refuses those reads
            // too, and no read of this position can have started since, because the pending entry was cached
            long[] save = saves.getAndMoveToLast(pos);
            if (save != null) save[1] = now;
            else saves.put(pos, new long[] { saveSequence, now });
            written = entries.get(pos);
            if (written == null || written.state != State.PENDING || written.tag != tag) return;
        }
        byte[] packed = written.keep && budgetBytes > 0 ? pack(tag) : null;
        synchronized (this) {
            Entry current = entries.get(pos);
            if (current == null || current.state != State.PENDING || current.tag != tag) return;
            // a load while packing replaced the entry with one that is not kept
            if (packed == null || current != written) {
                remove(entries.remove(pos));
            } else {
                put(pos, new Entry(State.CLEAN, false, null, packed, null, now));
            }
            trim(now, 100);
        }
    }

    /** Whether {@link #writtenAs} would keep a record for this save now, so that callers only prepare one then. */
    synchronized boolean keepsRecord(K pos, NBTTagCompound tag) {
        Entry current = entries.get(pos);
        return current != null && current.state == State.PENDING
            && current.tag == tag
            && current.keep
            && budgetBytes > 0
            && decoder != null;
    }

    /**
     * The write of {@code tag} finished, and storage keeps it in {@code record} ({@code null}: somewhere reloading is
     * cheap, such as a compact table, or not kept). Unless a newer save replaced it, a kept save then points at the
     * record without being serialized again; other saves are dropped.
     */
    synchronized void writtenAs(K pos, NBTTagCompound tag, long now, @Nullable Record record) {
        long[] save = saves.getAndMoveToLast(pos);
        if (save != null) save[1] = now;
        else saves.put(pos, new long[] { saveSequence, now });
        Entry current = entries.get(pos);
        if (current == null || current.state != State.PENDING || current.tag != tag) return;
        if (record != null && keepsRecord(pos, tag)) {
            put(pos, new Entry(State.CLEAN, false, null, null, record, now));
        } else {
            remove(entries.remove(pos));
        }
        trim(now, 100);
    }

    /**
     * Stores data read ahead of a load, unless something is cached for it or it was saved after the read started
     * ({@code fence}, from {@link #fence()} before the read). {@code null} means absent. Returns whether it was stored.
     */
    synchronized boolean putPreloaded(K pos, @Nullable NBTTagCompound tag, long fence, long now) {
        if (entries.containsKey(pos) || savedSince(pos, fence)) return false;
        put(
            pos,
            tag == null ? new Entry(State.ABSENT, false, null, null, null, now)
                : new Entry(State.PRELOADED, false, tag, null, null, now));
        trim(now, 100);
        return true;
    }

    /** Records that storage has no data for {@code pos}, unless something is cached for it by now. */
    synchronized void putAbsent(K pos, long now) {
        if (entries.containsKey(pos)) return;
        put(pos, new Entry(State.ABSENT, false, null, null, null, now));
    }

    synchronized boolean contains(K pos) {
        return entries.containsKey(pos);
    }

    /** {@code null} if nothing is cached, otherwise whether data exists. */
    @Nullable
    synchronized Boolean exists(K pos) {
        Entry entry = entries.get(pos);
        return entry == null ? null : entry.state != State.ABSENT;
    }

    /**
     * Data for a load. {@code null} if nothing is cached; an empty Optional if known absent; otherwise a tag the caller
     * owns. Preloaded and written data is handed over and dropped from the cache: the loaded object is newer from then
     * on, and its unload saves it again.
     */
    @Nullable
    Optional<NBTTagCompound> take(K pos) {
        return take(pos, System.currentTimeMillis());
    }

    /**
     * As {@link #take(Object)}. Data kept as a shared record is decoded once: the other positions of the record that
     * are still kept from it become read-ahead data (handed over once, dropped after the read-ahead expiry).
     */
    @Nullable
    Optional<NBTTagCompound> take(K pos, long now) {
        byte[] packed;
        Record record = null;
        synchronized (this) {
            Entry entry = entries.get(pos);
            if (entry == null) return null;
            switch (entry.state) {
                case PENDING:
                    // loaded again before the write: the loaded object is newer, so do not keep the data afterwards
                    if (entry.keep)
                        entries.put(pos, new Entry(State.PENDING, false, entry.tag, null, null, entry.time));
                    return Optional.of((NBTTagCompound) entry.tag.copy());
                case ABSENT:
                    return Optional.empty();
                case PRELOADED:
                    remove(entries.remove(pos));
                    return Optional.of(entry.tag);
                default:
                    remove(entries.remove(pos));
                    packed = entry.packed;
                    record = entry.record;
            }
        }
        if (record == null) {
            try {
                return Optional.of(unpack(packed));
            } catch (IOException | RuntimeException e) {
                CubicChunks.LOGGER.error("Could not unpack cached data for {}, reading it from storage", pos, e);
                return null;
            }
        }
        Map<K, NBTTagCompound> decoded;
        try {
            decoded = decoder.decode(pos, record.bytes.duplicate());
        } catch (IOException | RuntimeException e) {
            CubicChunks.LOGGER.error("Could not decode cached data for {}, reading it from storage", pos, e);
            return null;
        }
        NBTTagCompound result = decoded.remove(pos);
        synchronized (this) {
            for (Map.Entry<K, NBTTagCompound> other : decoded.entrySet()) {
                Entry entry = entries.get(other.getKey());
                if (entry == null || entry.state != State.CLEAN || entry.record != record) continue;
                put(other.getKey(), new Entry(State.PRELOADED, false, other.getValue(), null, null, now));
            }
        }
        return result == null ? null : Optional.of(result);
    }

    synchronized int pendingCount() {
        return pending;
    }

    synchronized long keptBytes() {
        return keptBytes;
    }

    synchronized int size() {
        return entries.size();
    }

    /**
     * Drops expired read-ahead data and, oldest first, written data and absent markers while over the byte budget.
     * Checks at most {@code maxChecks} entries. Pending saves are never dropped.
     */
    synchronized void trim(long now, int maxChecks) {
        var fences = saves.object2ObjectEntrySet()
            .fastIterator();
        for (int i = 0; i < maxChecks && fences.hasNext(); i++) {
            if (now - fences.next()
                .getValue()[1] < saveFenceMillis) break;
            fences.remove();
        }
        var iter = entries.object2ObjectEntrySet()
            .fastIterator();
        int checked = 0;
        while (iter.hasNext() && checked++ < maxChecks) {
            Entry entry = iter.next()
                .getValue();
            boolean drop = switch (entry.state) {
                case PENDING -> false;
                case PRELOADED -> now - entry.time >= preloadExpiryMillis;
                default -> keptBytes > budgetBytes;
            };
            if (drop) {
                remove(entry);
                iter.remove();
            }
        }
    }

    /** Uncompressed NBT length (4 bytes, big-endian), then the NBT as one LZ4 block. Internal to this cache. */
    @Nullable
    static byte[] pack(NBTTagCompound tag) {
        try {
            ByteArrayOutputStream raw = new ByteArrayOutputStream(4096);
            try (DataOutputStream out = new DataOutputStream(raw)) {
                CompressedStreamTools.write(tag, out);
            }
            byte[] data = raw.toByteArray();
            LZ4Compressor compressor = LZ4Factory.fastestInstance()
                .fastCompressor();
            byte[] packed = new byte[4 + compressor.maxCompressedLength(data.length)];
            int length = compressor.compress(data, 0, data.length, packed, 4, packed.length - 4);
            packed[0] = (byte) (data.length >>> 24);
            packed[1] = (byte) (data.length >>> 16);
            packed[2] = (byte) (data.length >>> 8);
            packed[3] = (byte) data.length;
            return Arrays.copyOf(packed, 4 + length);
        } catch (Throwable e) {
            // also errors such as running out of memory: the data is written, it is just not cached
            CubicChunks.LOGGER.error("Could not compress written data for the save cache", e);
            return null;
        }
    }

    static NBTTagCompound unpack(byte[] packed) throws IOException {
        int length = (packed[0] & 255) << 24 | (packed[1] & 255) << 16 | (packed[2] & 255) << 8 | packed[3] & 255;
        byte[] data = LZ4Factory.fastestInstance()
            .fastDecompressor()
            .decompress(packed, 4, length);
        return CompressedStreamTools.read(new DataInputStream(new ByteArrayInputStream(data)));
    }
}
