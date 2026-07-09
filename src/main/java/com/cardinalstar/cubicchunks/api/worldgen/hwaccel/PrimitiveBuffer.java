package com.cardinalstar.cubicchunks.api.worldgen.hwaccel;

import java.nio.ByteBuffer;
import java.util.Iterator;

import gnu.trove.procedure.TIntObjectProcedure;

/// A wrapper around some sort of non-object buffer ([ByteBuffer] or primitive array).
/// This buffer provides a 'view' object, which contains an index into the buffer.
/// Mutations to the view are immediately reflected into the buffer.
/// Accesses on the view directly operate on the data in the buffer.
/// Views are typically pooled, but this does not have to be the case.
/// Views track whether they came from the pool. Releasing an unpooled view does nothing.
/// A primitive buffer can be fixed length or variable length - this is implementation-defined.
public interface PrimitiveBuffer<View extends PrimitiveView> {

    /// Fills this buffer with default values, but does not change its size.
    void clear();

    /// Gets the number of available entries. Entries may contain invalid data.
    int size();

    /// Gets the number of bytes used by this buffer.
    int getByteLength();

    /// Allocates a new [View], points it to the backing buffer, and returns it.
    /// The [View] may come from an internal object pool and should be released by calling ([AutoCloseable#close()]).
    View get(int index);

    /// Scans over this buffer and calls the function once per stored object. Each [View] is a new object.
    void forEachSlow(TIntObjectProcedure<View> fn);

    /// Scans over this buffer and calls the function once per stored object. The [View] is one object that is suitably
    /// mutated.
    void forEachFast(TIntObjectProcedure<View> fn);

    /// Iterates over all contained objects. Allocates a new [View] for each object.
    Iterator<View> iteratorSlow();

    default Iterable<View> iterableSlow() {
        return this::iteratorSlow;
    }

    /// Iterates over all contained objects. The [View] is one object that is suitably mutated.
    Iterator<View> iteratorFast();

    default Iterable<View> iterableFast() {
        return this::iteratorFast;
    }

    /// Uploads the contents of this buffer into another buffer.
    /// @throws IllegalArgumentException If the byte lengths don't match.
    void upload(ByteBuffer dest);

    /// Downloads the contents of another buffer into this buffer.
    /// @throws IllegalArgumentException If the byte lengths don't match.
    void download(ByteBuffer source);
}
