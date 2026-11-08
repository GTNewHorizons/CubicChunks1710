package com.cardinalstar.cubicchunks.network;

import java.io.IOException;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import javax.annotation.Nullable;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.NetworkManager;

import com.cardinalstar.cubicchunks.CubicChunks;
import com.cardinalstar.cubicchunks.network.PacketEncoderCube.PacketCube;
import com.cardinalstar.cubicchunks.server.chunkio.CubeClientData;

import cpw.mods.fml.common.network.internal.FMLProxyPacket;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelPipeline;

/**
 * Decodes the client data of cube packets (decompression and NBT) on other threads as soon as they arrive on the
 * network thread. Minecraft queues packets and handles them on the client thread in order, which then only applies the
 * already decoded data ({@link #take}). Sits in the connection's pipeline between FML's dispatcher, which turns custom
 * payloads into {@link FMLProxyPacket}s, and the queue ({@code packet_handler}). Without it, or for a cube it did not
 * start, the client thread decodes as before.
 */
public final class CubePredecoder extends ChannelInboundHandlerAdapter {

    private static final String NAME = "cubicchunks:cube_predecoder";

    private static final AtomicInteger THREADS = new AtomicInteger();
    private static final ExecutorService DECODERS = Executors.newFixedThreadPool(2, task -> {
        Thread thread = new Thread(task, "CubicChunks cube decoder #" + THREADS.incrementAndGet());
        thread.setDaemon(true);
        return thread;
    });

    /** Decoding started for packets the client thread has not handled yet; weak, so dropped packets do not stay. */
    private static final Map<FMLProxyPacket, Future<NBTTagCompound>> DECODING = Collections
        .synchronizedMap(new WeakHashMap<>());

    private static final PacketEncoderCube CUBES = new PacketEncoderCube();

    private final String channel = CubicChunks.MODID + NetworkChannel.PROTOCOL_VERSION;

    /** Adds the decoder to a client connection, once. */
    public static void install(NetworkManager manager) {
        ChannelPipeline pipeline = manager.channel()
            .pipeline();
        if (pipeline.get(NAME) == null && pipeline.get("packet_handler") != null) {
            pipeline.addBefore("packet_handler", NAME, new CubePredecoder());
        }
    }

    @Override
    public void channelRead(ChannelHandlerContext context, Object message) throws Exception {
        if (message instanceof FMLProxyPacket packet && channel.equals(packet.channel())) {
            ByteBuf payload = packet.payload();
            if (payload.readableBytes() > 1 && payload.getByte(payload.readerIndex()) == CCPacketEntry.Cube.id) {
                try {
                    ByteBuf body = payload.duplicate();
                    body.skipBytes(1);
                    PacketCube cube = CUBES.readPacket(new CCPacketBuffer(body));
                    DECODING.put(packet, DECODERS.submit(() -> CubeClientData.decode(cube.data())));
                } catch (RuntimeException e) {
                    // the client thread reads the packet again and reports it
                }
            }
        }
        context.fireChannelRead(message);
    }

    /** The decoding started for this packet, if any; only once. */
    @Nullable
    static Future<NBTTagCompound> take(FMLProxyPacket packet) {
        return DECODING.remove(packet);
    }

    /**
     * The cube's client data from a started decoding, waiting for it if needed; {@code null} for an empty cube or when
     * the wait is interrupted (the caller then decodes itself). A failed decoding throws its exception.
     */
    @Nullable
    static NBTTagCompound join(Future<NBTTagCompound> decoding) throws IOException {
        try {
            return decoding.get();
        } catch (InterruptedException e) {
            Thread.currentThread()
                .interrupt();
            return null;
        } catch (ExecutionException e) {
            if (e.getCause() instanceof IOException cause) throw cause;
            if (e.getCause() instanceof RuntimeException cause) throw cause;
            throw new IOException(e.getCause());
        }
    }
}
