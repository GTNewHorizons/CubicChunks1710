package com.cardinalstar.cubicchunks.server.chunkio;

import static com.cardinalstar.cubicchunks.server.chunkio.StorageTestData.cube;
import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.ByteBuffer;

import net.minecraft.nbt.NBTTagCompound;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.cardinalstar.cubicchunks.CubicChunksConfig;
import com.cardinalstar.cubicchunks.util.CubePos;

class CubeBundleCodecTest {

    private CCNBTUtils.TagCompression compression;

    @BeforeEach
    void setUp() {
        compression = CubicChunksConfig.chunkCompression;
        CubicChunksConfig.chunkCompression = CCNBTUtils.TagCompression.ZSTD;
    }

    @AfterEach
    void tearDown() {
        CubicChunksConfig.chunkCompression = compression;
    }

    private static NBTTagCompound[] members(long seed, int mask, int states) {
        NBTTagCompound[] out = new NBTTagCompound[8];
        for (int s = 0; s < 8; s++)
            if ((mask & 1 << s) != 0) out[s] = cube(CubeBundleCodec.member(new CubePos(0, 0, 0), s), seed + s, states);
        return out;
    }

    private static void assertRoundTrip(NBTTagCompound[] members) throws IOException {
        NBTTagCompound[] before = new NBTTagCompound[8];
        int mask = 0;
        for (int s = 0; s < 8; s++) {
            if (members[s] == null) continue;
            before[s] = (NBTTagCompound) members[s].copy();
            mask |= 1 << s;
        }
        ByteBuffer record = CubeBundleCodec.encode(members);
        assertArrayEquals(before, members, "inputs are not modified");
        assertTrue(CubeBundleCodec.isBundle(record));
        assertEquals(mask, CubeBundleCodec.memberMask(record));
        assertArrayEquals(members, CubeBundleCodec.decode(record));
    }

    @Test
    void bundlesRoundTripExactlyWithAnyMembers() throws Exception {
        assertRoundTrip(members(1, 0xff, 12));
        assertRoundTrip(members(2, 0b10010010, 3));
        // more than 256 states use 16-bit indices
        NBTTagCompound[] wide = members(3, 0xff, 60);
        assertRoundTrip(wide);
        assertTrue(CubeBundleCodec.read(CubeBundleCodec.encode(wide)).count > 256);
        // every codec
        for (CCNBTUtils.TagCompression codec : CCNBTUtils.TagCompression.values()) {
            CubicChunksConfig.chunkCompression = codec;
            assertRoundTrip(members(4, 0b1011, 8));
        }
    }

    @Test
    void membersOutsideThePaletteAndWithoutClientDataKeepTheirNbt() throws Exception {
        NBTTagCompound twoSections = cube(new CubePos(0, 0, 0), 10);
        twoSections.getCompoundTag("Level")
            .getTagList("Sections", 10)
            .appendTag(new NBTTagCompound());
        NBTTagCompound sectionless = cube(new CubePos(0, 0, 1), 11);
        sectionless.getCompoundTag("Level")
            .removeTag("Sections");
        sectionless.getCompoundTag("Level")
            .removeTag("Biomes");
        NBTTagCompound noLevel = new NBTTagCompound();
        noLevel.setString("mod:only", "root tag");
        NBTTagCompound shortArray = cube(new CubePos(0, 1, 0), 12);
        shortArray.getCompoundTag("Level")
            .getTagList("Sections", 10)
            .getCompoundTagAt(0)
            .setByteArray("Blocks", new byte[100]);
        NBTTagCompound[] members = { twoSections, sectionless, shortArray, noLevel, cube(new CubePos(1, 0, 0), 13),
            null, null, null };
        for (int s = 0; s < 4; s++) assertEquals(-1, CubeBundleCodec.palettizable(members[s]), "member " + s);
        assertRoundTrip(members);
    }

    @Test
    void rewritingOneMemberKeepsTheOthersEncodedBytes() throws Exception {
        NBTTagCompound[] members = members(20, 0xff, 10);
        CubeBundleCodec.Stored stored = CubeBundleCodec.read(CubeBundleCodec.encode(members));
        NBTTagCompound[] replace = new NBTTagCompound[8];
        replace[3] = cube(CubeBundleCodec.member(new CubePos(0, 0, 0), 3), 99, 20);
        ByteBuffer rewritten = CubeBundleCodec.rewrite(stored, replace, 1 << 5);
        CubeBundleCodec.Stored after = CubeBundleCodec.read(rewritten);
        for (int s = 0; s < 8; s++) {
            if (s == 3 || s == 5) continue;
            assertArrayEquals(stored.client[s], after.client[s], "member " + s + " keeps its client bytes");
            assertArrayEquals(stored.server[s], after.server[s], "member " + s + " keeps its server bytes");
        }
        NBTTagCompound[] expected = members.clone();
        expected[3] = replace[3];
        expected[5] = null;
        assertArrayEquals(expected, CubeBundleCodec.decode(rewritten));
        assertNull(CubeBundleCodec.rewrite(after, new NBTTagCompound[8], 0xff), "no member left, no record");
    }

    @Test
    void damagedRecordsAreRejected() throws Exception {
        ByteBuffer good = CubeBundleCodec.encode(members(30, 0b11, 4));
        byte[] bytes = new byte[good.remaining()];
        good.duplicate()
            .get(bytes);
        byte[][] damaged = { bytes.clone(), bytes.clone(), bytes.clone(),
            java.util.Arrays.copyOf(bytes, bytes.length - 3) };
        damaged[0][3] = 9; // unknown version
        damaged[1][5] = 7; // member mask that differs from the payload
        damaged[2][9]++; // client part length
        for (byte[] record : damaged)
            assertThrows(IOException.class, () -> CubeBundleCodec.read(ByteBuffer.wrap(record)));
        for (CCNBTUtils.TagCompression codec : CCNBTUtils.TagCompression.values()) {
            assertFalse(
                CubeBundleCodec.isBundle(CCNBTUtils.saveTag(cube(new CubePos(0, 0, 0), 31), codec)),
                "a per-cube " + codec + " record");
        }
    }
}
