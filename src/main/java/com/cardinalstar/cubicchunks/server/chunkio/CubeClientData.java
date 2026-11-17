package com.cardinalstar.cubicchunks.server.chunkio;

import java.io.IOException;
import java.nio.ByteBuffer;

import javax.annotation.Nullable;

import net.minecraft.nbt.NBTTagCompound;

import com.cardinalstar.cubicchunks.world.cube.Cube;

/**
 * What clients are sent of a cube: its blocks and biomes, as a bundle record with that cube as its only member, so it
 * has its own block palette and is compressed on its own with the configured codec.
 */
public final class CubeClientData {

    private static final byte[] EMPTY = new byte[0];

    private CubeClientData() {}

    /** Encodes a cube's client data; no bytes for an empty cube without biomes. */
    public static byte[] encode(Cube cube) throws IOException {
        NBTTagCompound part = IONbtWriter.writeClientPart(cube);
        if (part == null) return EMPTY;
        NBTTagCompound[] members = new NBTTagCompound[8];
        members[0] = part;
        ByteBuffer record = CubeBundleCodec.encode(members);
        byte[] bytes = new byte[record.remaining()];
        record.get(bytes);
        return bytes;
    }

    /** Decodes what {@link #encode} wrote; {@code null} for an empty cube without biomes. */
    @Nullable
    public static NBTTagCompound decode(byte[] bytes) throws IOException {
        if (bytes.length == 0) return null;
        return CubeBundleCodec.read(ByteBuffer.wrap(bytes))
            .clientMember(0);
    }

    /** Applies decoded client data to a client cube; {@code null} makes it an empty cube. */
    public static void apply(Cube cube, @Nullable NBTTagCompound clientData) {
        IONbtReader.readClientPart(cube, clientData);
    }
}
