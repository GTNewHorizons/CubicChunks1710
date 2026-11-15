package com.cardinalstar.cubicchunks.server.chunkio;

import static com.cardinalstar.cubicchunks.server.chunkio.StorageTestData.column;
import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.ByteBuffer;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.ChunkCoordIntPair;

import org.junit.jupiter.api.Test;

class ColumnGroupCodecTest {

    @Test
    void groupsAreAlignedAndStayInsideTheirRegion() {
        for (int x = -40; x <= 40; x++) for (int z = -40; z <= 40; z++) {
            ChunkCoordIntPair pos = new ChunkCoordIntPair(x, z), origin = ColumnGroupCodec.origin(pos);
            assertEquals(pos, ColumnGroupCodec.member(origin, ColumnGroupCodec.slot(pos)));
            assertEquals(x >> 5, origin.chunkXPos >> 5);
            assertEquals(z >> 5, origin.chunkZPos >> 5);
        }
    }

    @Test
    void groupsRoundTripAndRewritesKeepTheOtherMembersBytes() throws Exception {
        ChunkCoordIntPair origin = new ChunkCoordIntPair(-4, 6);
        for (int mask = 1; mask < 16; mask++) {
            NBTTagCompound[] members = new NBTTagCompound[4];
            for (int s = 0; s < 4; s++)
                if ((mask & 1 << s) != 0) members[s] = column(ColumnGroupCodec.member(origin, s), mask * 4 + s);
            ByteBuffer record = ColumnGroupCodec.encode(members);
            assertEquals(mask, ColumnGroupCodec.memberMask(record));
            ColumnGroupCodec.Stored stored = ColumnGroupCodec.read(record);
            for (int s = 0; s < 4; s++) assertEquals(members[s], stored.member(s));
        }
        NBTTagCompound[] all = new NBTTagCompound[4];
        for (int s = 0; s < 4; s++) all[s] = column(ColumnGroupCodec.member(origin, s), 100 + s);
        ColumnGroupCodec.Stored stored = ColumnGroupCodec.read(ColumnGroupCodec.encode(all));
        NBTTagCompound[] replace = new NBTTagCompound[4];
        replace[2] = column(ColumnGroupCodec.member(origin, 2), 200);
        ColumnGroupCodec.Stored after = ColumnGroupCodec.read(ColumnGroupCodec.rewrite(stored, replace, 0b1011));
        assertArrayEquals(stored.members[0], after.members[0]);
        assertArrayEquals(stored.members[3], after.members[3]);
        assertEquals(replace[2], after.member(2));
    }

    @Test
    void damagedRecordsAreRejected() throws Exception {
        NBTTagCompound[] members = { column(new ChunkCoordIntPair(0, 0), 1), null,
            column(new ChunkCoordIntPair(1, 0), 2), null };
        ByteBuffer good = ColumnGroupCodec.encode(members);
        byte[] bytes = new byte[good.remaining()];
        good.duplicate()
            .get(bytes);
        byte[][] damaged = { bytes.clone(), bytes.clone(), bytes.clone(),
            java.util.Arrays.copyOf(bytes, bytes.length - 3) };
        damaged[0][3] = 2; // unknown version
        damaged[1][5] = 0b0111; // member mask that differs from the payload
        damaged[2][9]++; // raw length
        for (byte[] record : damaged)
            assertThrows(IOException.class, () -> ColumnGroupCodec.read(ByteBuffer.wrap(record)));
        for (CCNBTUtils.TagCompression codec : CCNBTUtils.TagCompression.values()) {
            assertFalse(
                ColumnGroupCodec.isGroup(CCNBTUtils.saveTag(column(new ChunkCoordIntPair(0, 0), 3), codec)),
                codec.toString());
        }
    }
}
