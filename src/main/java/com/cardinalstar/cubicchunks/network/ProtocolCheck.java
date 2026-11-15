package com.cardinalstar.cubicchunks.network;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.client.Minecraft;
import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.network.INetHandler;
import net.minecraft.network.NetHandlerPlayServer;
import net.minecraft.util.ChatComponentText;

import com.cardinalstar.cubicchunks.CubicChunks;

import cpw.mods.fml.common.network.NetworkRegistry;
import cpw.mods.fml.common.network.internal.FMLProxyPacket;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;

/**
 * Makes sure client and server use the same network protocol. {@link NetworkChannel#PROTOCOL_VERSION} is part of the
 * channel name, so peers with a different protocol never read each other's packets. The server sends a hello when a
 * player enters a cubic world and disconnects a client that does not answer in time. Packets on the channels of
 * earlier protocols come from an older CubicChunks on the other side, which is told so at once.
 */
public final class ProtocolCheck {

    /** Server ticks a client has to answer; ticks, so that a stalled server does not count. */
    private static final int TIMEOUT_TICKS = 20 * 60;

    private static final String CLIENT_TOO_OLD = "This server uses CubicChunks network protocol "
        + NetworkChannel.PROTOCOL_VERSION
        + ", which your CubicChunks does not. Install the same CubicChunks version as the server.";

    private static final String SERVER_TOO_OLD = "The server's CubicChunks uses an older network protocol than yours ("
        + NetworkChannel.PROTOCOL_VERSION
        + "). Install the same CubicChunks version as the server.";

    /** Players whose client sent a matching hello; written on network threads. */
    private static final Set<UUID> confirmed = ConcurrentHashMap.newKeySet();
    /** Players sent a hello, with the server ticks left to answer; server thread only. */
    private static final Map<UUID, Integer> awaiting = new HashMap<>();

    private ProtocolCheck() {}

    /** Listens on the channels of earlier protocols. */
    static void init() {
        NetworkRegistry.INSTANCE.newChannel(CubicChunks.MODID + "1", new EarlierProtocolHandler());
    }

    /** A player entered a cubic world: once per connection, send the protocol and wait for the client's. */
    public static void onCubicJoin(EntityPlayerMP player) {
        UUID id = player.getUniqueID();
        if (confirmed.contains(id) || awaiting.containsKey(id)) return;

        awaiting.put(id, TIMEOUT_TICKS);
        PacketEncoderProtocolHello.createPacket(NetworkChannel.PROTOCOL_VERSION)
            .sendToPlayer(player);
    }

    static void clientHello(EntityPlayerMP player, int version) {
        if (version == NetworkChannel.PROTOCOL_VERSION) confirmed.add(player.getUniqueID());
    }

    /** Disconnects players whose client did not confirm the protocol in time. Server thread, once per tick. */
    public static void serverTick(Iterable<EntityPlayerMP> players) {
        if (awaiting.isEmpty()) return;

        for (EntityPlayerMP player : players) {
            UUID id = player.getUniqueID();
            Integer left = awaiting.get(id);
            if (left == null) continue;

            if (confirmed.contains(id)) {
                awaiting.remove(id);
            } else if (left > 0) {
                awaiting.put(id, left - 1);
            } else {
                awaiting.remove(id);
                CubicChunks.LOGGER.warn(
                    "{} did not confirm CubicChunks network protocol {}; disconnecting",
                    player.getCommandSenderName(),
                    NetworkChannel.PROTOCOL_VERSION);
                player.playerNetServerHandler.kickPlayerFromServer(CLIENT_TOO_OLD);
            }
        }
    }

    public static void onLogout(EntityPlayerMP player) {
        confirmed.remove(player.getUniqueID());
        awaiting.remove(player.getUniqueID());
    }

    static void serverHello(int version) {
        if (version != NetworkChannel.PROTOCOL_VERSION) return;

        NetworkChannel.CHANNEL.sendToServer(PacketEncoderProtocolHello.createPacket(NetworkChannel.PROTOCOL_VERSION));
    }

    @SideOnly(Side.CLIENT)
    private static void leaveServer() {
        NetHandlerPlayClient handler = Minecraft.getMinecraft()
            .getNetHandler();
        if (handler != null) {
            handler.getNetworkManager()
                .closeChannel(new ChatComponentText(SERVER_TOO_OLD));
        }
    }

    /**
     * Packets of an earlier protocol: the other side runs an older CubicChunks. Packets are handled on main threads.
     */
    @ChannelHandler.Sharable
    private static final class EarlierProtocolHandler extends SimpleChannelInboundHandler<FMLProxyPacket> {

        @Override
        protected void channelRead0(ChannelHandlerContext context, FMLProxyPacket packet) {
            INetHandler handler = context.channel()
                .attr(NetworkRegistry.NET_HANDLER)
                .get();

            if (handler instanceof NetHandlerPlayServer server) {
                CubicChunks.LOGGER.warn(
                    "{} uses an earlier CubicChunks network protocol; disconnecting",
                    server.playerEntity.getCommandSenderName());
                server.kickPlayerFromServer(CLIENT_TOO_OLD);
            } else {
                CubicChunks.LOGGER.warn("The server uses an earlier CubicChunks network protocol; disconnecting");
                leaveServer();
            }
        }
    }
}
