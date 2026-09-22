package com.cardinalstar.cubicchunks.world.convert.adapter;

import java.nio.ByteBuffer;
import java.nio.ShortBuffer;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.chunk.NibbleArray;

public class EIDSectionAdapter implements SectionAdapter {

    EIDSectionAdapter() { }

    @Override
    public void writeSectionData(CubeData data, NBTTagCompound section) {
        byte[] lsbOut = new byte[4096];
        NibbleArray msbOut = null;
        NibbleArray b2HighOut = null;
        byte[] b3Out = null;

        section.setByteArray("Blocks", lsbOut);

        NibbleArray m1LowOut = new NibbleArray(4096, 4);

        section.setByteArray("Data", m1LowOut.data);

        NibbleArray m1HighOut = null;
        byte[] m2Out = null;

        NibbleArray blockLightOut = new NibbleArray(4096, 4);
        NibbleArray skyLightOut = new NibbleArray(4096, 4);

        section.setByteArray("BlockLight", blockLightOut.data);
        section.setByteArray("SkyLight", skyLightOut.data);

        for (int y = 0; y < 16; y++) {
            for (int z = 0; z < 16; z++) {
                for (int x = 0; x < 16; x++) {
                    int blockId = data.blocks[CubeData.index(x, y, z)];

                    if (blockId == 0) continue;

                    lsbOut[y << 8 | z << 4 | x] = (byte) (blockId & 0xFF);

                    blockId >>= 8;

                    if (blockId == 0) continue;

                    if (msbOut == null) {
                        msbOut = new NibbleArray(4096, 4);
                        section.setByteArray("Add", msbOut.data);
                    }

                    msbOut.set(x, y, z, blockId & 0xF);

                    blockId >>= 4;

                    if (blockId == 0) continue;

                    if (b2HighOut == null) {
                        b2HighOut = new NibbleArray(4096, 4);
                        section.setByteArray("BlocksB2Hi", b2HighOut.data);
                    }

                    b2HighOut.set(x, y, z, blockId & 0xF);

                    blockId >>= 4;

                    if (blockId == 0) continue;

                    if (b3Out == null) {
                        b3Out = new byte[4096];
                        section.setByteArray("BlocksB3", b3Out);
                    }

                    b3Out[y << 8 | z << 4 | x] = (byte) (blockId & 0xFF);
                }
            }
        }

        for (int y = 0; y < 16; y++) {
            for (int z = 0; z < 16; z++) {
                for (int x = 0; x < 16; x++) {
                    int meta = data.meta[CubeData.index(x, y, z)];

                    if (meta == 0) continue;

                    m1LowOut.set(x, y, z, meta & 0xF);

                    meta >>= 4;

                    if (meta == 0) continue;

                    if (m1HighOut == null) {
                        m1HighOut = new NibbleArray(4096, 4);
                        section.setByteArray("Data1High", m1HighOut.data);
                    }

                    m1HighOut.set(x, y, z, meta & 0xF);

                    meta >>= 4;

                    if (meta == 0) continue;

                    if (m2Out == null) {
                        m2Out = new byte[4096];
                        section.setByteArray("Data2", m2Out);
                    }

                    m2Out[CubeData.index(x, y, z)] = (byte) (meta & 0xFF);
                }
            }
        }

        for (int y = 0; y < 16; y++) {
            for (int z = 0; z < 16; z++) {
                for (int x = 0; x < 16; x++) {
                    int skyLight = data.skyLight[CubeData.index(x, y, z)];

                    if (skyLight == 0) continue;

                    skyLightOut.set(x, y, z, skyLight & 0xF);
                }
            }
        }

        for (int y = 0; y < 16; y++) {
            for (int z = 0; z < 16; z++) {
                for (int x = 0; x < 16; x++) {
                    int blockLight = data.blockLight[CubeData.index(x, y, z)];

                    if (blockLight == 0) continue;

                    blockLightOut.set(x, y, z, blockLight & 0xF);
                }
            }
        }
    }

    @Override
    public void readSectionData(NBTTagCompound section, CubeData data) {
        if (section.hasKey("Block16")) {
            var blocks16 = section.getByteArray("Blocks16");
            var blocksShort = new short[blocks16.length >>> 1];
            ByteBuffer.wrap(blocks16).asShortBuffer().get(blocksShort);

            for (int i = 0; i < blocksShort.length; i++) {
                data.blocks[i] = blocksShort[i];
            }
        } else {
            readEIDBlockIds(section, data);
        }

        if (section.hasKey("Data16")) {
            var data16 = section.getByteArray("Data16");
            var dataShort = new short[data16.length >>> 1];
            ByteBuffer.wrap(data16).asShortBuffer().get(dataShort);

            for (int i = 0; i < dataShort.length; i++) {
                data.meta[i] = dataShort[i];
            }
        } else {
            readMeta(section, data);
        }

        NibbleArray blockLightIn = new NibbleArray(section.getByteArray("BlockLight"), 4);
        NibbleArray skyLightIn = new NibbleArray(section.getByteArray("SkyLight"), 4);

        for (int y = 0; y < 16; y++) {
            for (int z = 0; z < 16; z++) {
                for (int x = 0; x < 16; x++) {
                    data.skyLight[CubeData.index(x, y, z)] = skyLightIn.get(x, y, z);
                }
            }
        }

        for (int y = 0; y < 16; y++) {
            for (int z = 0; z < 16; z++) {
                for (int x = 0; x < 16; x++) {
                    data.blockLight[CubeData.index(x, y, z)] = blockLightIn.get(x, y, z);
                }
            }
        }
    }

    private static void readMeta(NBTTagCompound section, CubeData data) {
        if (section.hasKey("Data16")) {
            ShortBuffer blocks = ByteBuffer.wrap(section.getByteArray("Data16")).asShortBuffer();

            for (int y = 0; y < 16; y++) {
                for (int z = 0; z < 16; z++) {
                    for (int x = 0; x < 16; x++) {
                        int index = CubeData.index(x, y, z);

                        data.meta[index] = blocks.get(index);
                    }
                }
            }
        } else {
            NibbleArray m1LowIn = new NibbleArray(section.getByteArray("Data"), 4);
            NibbleArray m1HighIn = section.hasKey("Data1High", 7)
                ? new NibbleArray(section.getByteArray("Data1High"), 4)
                : null;
            byte[] m2In = section.hasKey("Data2", 7) ? section.getByteArray("Data2") : null;

            for (int y = 0; y < 16; y++) {
                for (int z = 0; z < 16; z++) {
                    for (int x = 0; x < 16; x++) {
                        int index = CubeData.index(x, y, z);

                        int meta = m1LowIn.get(x, y, z);

                        if (m1HighIn != null) {
                            meta |= m1HighIn.get(x, y, z) << 4;
                        }

                        if (m2In != null) {
                            meta |= (m2In[index] & 0xFF) << 8;
                        }

                        data.meta[index] = meta;
                    }
                }
            }
    }
    }

    private static void readEIDBlockIds(NBTTagCompound section, CubeData data) {
        if (section.hasKey("Blocks16")) {
            ShortBuffer blocks = ByteBuffer.wrap(section.getByteArray("Blocks16")).asShortBuffer();

            for (int y = 0; y < 16; y++) {
                for (int z = 0; z < 16; z++) {
                    for (int x = 0; x < 16; x++) {
                        int index = CubeData.index(x, y, z);

                        data.blocks[index] = blocks.get(index);
                    }
                }
            }
        } else {
            byte[] lsbIn = section.getByteArray("Blocks");
            NibbleArray msbIn = section.hasKey("Add", 7) ? new NibbleArray(section.getByteArray("Add"), 4) : null;
            NibbleArray b2HighIn = section.hasKey("BlocksB2Hi", 7) ? new NibbleArray(section.getByteArray("BlocksB2Hi"), 4) : null;
            byte[] b3In = section.hasKey("BlocksB3", 7) ? section.getByteArray("BlocksB3") : null;

            for (int y = 0; y < 16; y++) {
                for (int z = 0; z < 16; z++) {
                    for (int x = 0; x < 16; x++) {
                        int index = CubeData.index(x, y, z);

                        int blockId = lsbIn[index] & 0xFF;

                        if (msbIn != null) {
                            blockId |= msbIn.get(x, y, z) << 8;
                        }

                        if (b2HighIn != null) {
                            blockId |= b2HighIn.get(x, y, z) << 12;
                        }

                        if (b3In != null) {
                            blockId |= (b3In[index] & 0xFF) << 16;
                        }

                        data.blocks[index] = blockId;
                    }
                }
            }
        }
    }
}
