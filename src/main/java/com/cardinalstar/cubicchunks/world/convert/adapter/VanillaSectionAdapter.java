package com.cardinalstar.cubicchunks.world.convert.adapter;

import net.minecraft.block.Block;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.chunk.NibbleArray;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;

public class VanillaSectionAdapter implements SectionAdapter {

    @Override
    public void writeSectionData(CubeData data, NBTTagCompound section) {
        byte[] lsbOut = new byte[4096];
        NibbleArray msbOut = null;

        section.setByteArray("Blocks", lsbOut);

        NibbleArray metaOut = new NibbleArray(4096, 4);
        NibbleArray blockLightOut = new NibbleArray(4096, 4);
        NibbleArray skyLightOut = new NibbleArray(4096, 4);

        section.setByteArray("Data", metaOut.data);
        section.setByteArray("BlockLight", blockLightOut.data);
        section.setByteArray("SkyLight", skyLightOut.data);

        for (int y = 0; y < 16; y++) {
            for (int z = 0; z < 16; z++) {
                for (int x = 0; x < 16; x++) {
                    int blockId = data.blocks[CubeData.index(x, y, z)];

                    if (blockId == 0) continue;

                    lsbOut[CubeData.index(x, y, z)] = (byte) blockId;

                    blockId >>= 8;

                    if (blockId == 0) continue;

                    if (msbOut == null) {
                        msbOut = new NibbleArray(4096, 4);
                        section.setByteArray("Add", msbOut.data);
                    }

                    msbOut.set(x, y, z, blockId & 0xF);
                }
            }
        }

        for (int y = 0; y < 16; y++) {
            for (int z = 0; z < 16; z++) {
                for (int x = 0; x < 16; x++) {
                    int meta = data.meta[CubeData.index(x, y, z)];

                    if (meta == 0) continue;

                    metaOut.set(x, y, z, meta & 0xF);
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
        byte[] lsbIn = section.getByteArray("Blocks");
        NibbleArray msbIn = section.hasKey("Add", 7) ? new NibbleArray(section.getByteArray("Add"), 4) : null;
        NibbleArray metaIn = new NibbleArray(section.getByteArray("Data"), 4);
        NibbleArray blockLightIn = new NibbleArray(section.getByteArray("BlockLight"), 4);
        // SkyLight is absent in non-overworld dimensions
        NibbleArray skyLightIn = section.hasKey("SkyLight", 7) ? new NibbleArray(section.getByteArray("SkyLight"), 4) : null;

        for (int y = 0; y < 16; y++) {
            for (int z = 0; z < 16; z++) {
                for (int x = 0; x < 16; x++) {
                    int blockId = lsbIn[CubeData.index(x, y, z)] & 0xFF;

                    if (msbIn != null) {
                        blockId |= msbIn.get(x, y, z) << 8;
                    }

                    data.blocks[CubeData.index(x, y, z)] = blockId;
                }
            }
        }

        for (int y = 0; y < 16; y++) {
            for (int z = 0; z < 16; z++) {
                for (int x = 0; x < 16; x++) {
                    data.meta[CubeData.index(x, y, z)] = metaIn.get(x, y, z);
                }
            }
        }

        for (int y = 0; y < 16; y++) {
            for (int z = 0; z < 16; z++) {
                for (int x = 0; x < 16; x++) {
                    data.skyLight[CubeData.index(x, y, z)] = skyLightIn != null ? skyLightIn.get(x, y, z) : 0;
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
}
