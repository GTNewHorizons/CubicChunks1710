package com.cardinalstar.cubicchunks.network;

import javax.annotation.ParametersAreNonnullByDefault;

import net.minecraft.network.INetHandler;
import net.minecraft.network.NetHandlerPlayServer;
import net.minecraft.world.World;

import com.cardinalstar.cubicchunks.network.PacketEncoderProtocolHello.PacketProtocolHello;
import com.github.bsideup.jabel.Desugar;

/** The network protocol version, sent by the server when a player joins and answered by the client. */
@ParametersAreNonnullByDefault
public class PacketEncoderProtocolHello extends CCPacketEncoder<PacketProtocolHello> {

    @Desugar
    public record PacketProtocolHello(int version) implements CCPacket {

        @Override
        public byte getPacketID() {
            return CCPacketEntry.ProtocolHello.id;
        }
    }

    public PacketEncoderProtocolHello() {}

    public static PacketProtocolHello createPacket(int version) {
        return new PacketProtocolHello(version);
    }

    @Override
    public byte getPacketID() {
        return CCPacketEntry.ProtocolHello.id;
    }

    @Override
    public void writePacket(CCPacketBuffer buffer, PacketProtocolHello packet) {
        buffer.writeVarIntToBuffer(packet.version);
    }

    @Override
    public PacketProtocolHello readPacket(CCPacketBuffer buffer) {
        return new PacketProtocolHello(buffer.readVarIntFromBuffer());
    }

    @Override
    public void setINetHandler(INetHandler handler, PacketProtocolHello packet) {
        if (handler instanceof NetHandlerPlayServer server) {
            ProtocolCheck.clientHello(server.playerEntity, packet.version);
        }
    }

    @Override
    public void process(World world, PacketProtocolHello packet) {
        // the server handled it in setINetHandler
        if (world != null) ProtocolCheck.serverHello(packet.version);
    }
}
