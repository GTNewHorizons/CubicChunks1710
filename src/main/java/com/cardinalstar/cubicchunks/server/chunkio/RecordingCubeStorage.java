package com.cardinalstar.cubicchunks.server.chunkio;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Map;

import com.cardinalstar.cubicchunks.api.world.storage.ICubicStorage.NBTBatch;
import com.cardinalstar.cubicchunks.util.CubePos;

/**
 * Storage that tells its caller which record each written cube ended up in, so the cube cache can keep those bytes
 * instead of serializing the cubes again.
 */
interface RecordingCubeStorage {

    /**
     * Writes the batch like {@link com.cardinalstar.cubicchunks.api.world.storage.ICubicStorage#writeBatch} and adds,
     * for every cube whose new data reached storage, a read-only view of its record ({@link CubeBundleCodec bundle}
     * record or per-cube NBT record), or {@code null} for a cube stored in a compact table. Cubes missing from
     * {@code written} were not written, also when this throws after writing part of the batch.
     */
    void writeBatch(NBTBatch batch, Map<CubePos, ByteBuffer> written) throws IOException;

    /** Whether cubes are stored in 2x2x2 bundle records, so that writing a bundle's saves together pays off. */
    boolean writesBundles();
}
