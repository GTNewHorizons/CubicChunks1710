package com.cardinalstar.cubicchunks.server.chunkio;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Arrays;
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
        /** Written to storage; kept compressed for reloads until evicted. */
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
        final long time;

        Entry(State state, boolean keep, @Nullable NBTTagCompound tag, @Nullable byte[] packed, long time) {
            this.state = state;
            this.keep = keep;
            this.tag = tag;
            this.packed = packed;
            this.time = time;
        }

        long size() {
            return state == State.CLEAN ? packed.length + ENTRY_OVERHEAD : state == State.ABSENT ? ENTRY_OVERHEAD : 0;
        }
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

    IOCache(long budgetBytes, long preloadExpiryMillis, long saveFenceMillis) {
        this.budgetBytes = budgetBytes;
        this.preloadExpiryMillis = preloadExpiryMillis;
        this.saveFenceMillis = saveFenceMillis;
    }

    private void put(K pos, Entry entry) {
        Entry old = entries.putAndMoveToLast(pos, entry);
        if (old != null) remove(old);
        if (entry.state == State.PENDING) pending++;
        keptBytes += entry.size();
    }

    private void remove(Entry old) {
        if (old.state == State.PENDING) pending--;
        keptBytes -= old.size();
    }

    /**
     * A save of {@code tag} was queued. Until {@link #written} is called for it, loads return copies of it. With
     * {@code keep} (the object was unloaded) its data stays cached after the write; otherwise the loaded object is
     * newer
     * and the entry is dropped then.
     */
    synchronized void putPending(K pos, NBTTagCompound tag, boolean keep, long now) {
        put(pos, new Entry(State.PENDING, keep, tag, null, now));
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
                put(pos, new Entry(State.CLEAN, false, null, packed, now));
            }
            trim(now, 100);
        }
    }

    /**
     * Stores data read ahead of a load, unless something is cached for it or it was saved after the read started
     * ({@code fence}, from {@link #fence()} before the read). {@code null} means absent. Returns whether it was stored.
     */
    synchronized boolean putPreloaded(K pos, @Nullable NBTTagCompound tag, long fence, long now) {
        if (entries.containsKey(pos) || savedSince(pos, fence)) return false;
        put(
            pos,
            tag == null ? new Entry(State.ABSENT, false, null, null, now)
                : new Entry(State.PRELOADED, false, tag, null, now));
        trim(now, 100);
        return true;
    }

    /** Records that storage has no data for {@code pos}, unless something is cached for it by now. */
    synchronized void putAbsent(K pos, long now) {
        if (entries.containsKey(pos)) return;
        put(pos, new Entry(State.ABSENT, false, null, null, now));
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
        byte[] packed;
        synchronized (this) {
            Entry entry = entries.get(pos);
            if (entry == null) return null;
            switch (entry.state) {
                case PENDING:
                    // loaded again before the write: the loaded object is newer, so do not keep the data afterwards
                    if (entry.keep) entries.put(pos, new Entry(State.PENDING, false, entry.tag, null, entry.time));
                    return Optional.of((NBTTagCompound) entry.tag.copy());
                case ABSENT:
                    return Optional.empty();
                case PRELOADED:
                    remove(entries.remove(pos));
                    return Optional.of(entry.tag);
                default:
                    remove(entries.remove(pos));
                    packed = entry.packed;
            }
        }
        try {
            return Optional.of(unpack(packed));
        } catch (IOException | RuntimeException e) {
            CubicChunks.LOGGER.error("Could not unpack cached data for {}, reading it from storage", pos, e);
            return null;
        }
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
