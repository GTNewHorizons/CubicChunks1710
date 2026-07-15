package com.cardinalstar.cubicchunks.network;

public enum CCPacketEntry {

    Cube(new PacketEncoderCube()),
    Column(new PacketEncoderColumn()),
    UnloadColumn(new PacketEncoderUnloadColumn()),
    UnloadCube(new PacketEncoderUnloadCube()),
    CubeBlockChange(new PacketEncoderCubeBlockChange()),
    HeightMapUpdate(new PacketEncoderHeightMapUpdate()),
    CubeSkyLightUpdates(new PacketEncoderCubeSkyLightUpdates()),
    WorldHeight(new PacketEncoderWorldHeight()),
    UpdateVisualizedBoxes(new PacketEncoderUpdateVisualizedBoxes()),
    //
    ;

    public final byte id = (byte) ordinal();
    public final CCPacketEncoder<?> encoder;

    CCPacketEntry(CCPacketEncoder<?> encoder) {
        this.encoder = encoder;
    }
}
