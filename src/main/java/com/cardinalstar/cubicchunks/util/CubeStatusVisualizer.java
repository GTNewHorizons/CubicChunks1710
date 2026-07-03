package com.cardinalstar.cubicchunks.util;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.AxisAlignedBB;

import com.cardinalstar.cubicchunks.CubicChunksConfig;
import com.cardinalstar.cubicchunks.util.boxvisualizer.BoxVisualizer;
import com.cardinalstar.cubicchunks.util.boxvisualizer.VisualizedBox;
import com.gtnewhorizon.gtnhlib.color.RGBColor;
import com.gtnewhorizon.gtnhlib.eventbus.EventBusSubscriber;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent.Phase;
import cpw.mods.fml.common.gameevent.TickEvent.ServerTickEvent;

@EventBusSubscriber
public class CubeStatusVisualizer {

    public enum CubeStatus {
        Enqueued(RGBColor.fromRGBA(0x64292832)),
        None(RGBColor.fromRGBA(0x64326432)),
        Generated(RGBColor.fromRGBA(0x32C83232)),
        Populated(RGBColor.fromRGBA(0x3232C832)),
        Lit(RGBColor.fromRGBA(0xC8C83232)),
        Dirty(RGBColor.fromRGBA(0x0EE5BB32)),
        Synced(null);

        public final RGBColor color;

        CubeStatus(RGBColor color) {
            this.color = color;
        }
    }

    private static final ConcurrentHashMap<CubePos, CubeStatus> cubeStatus = new ConcurrentHashMap<>();
    private static final AtomicBoolean dirty = new AtomicBoolean();

    private static boolean wasSent = false;

    public static void reset() {
        cubeStatus.clear();
        dirty.set(false);
    }

    @SubscribeEvent
    public static void sync(ServerTickEvent event) {
        if (event.phase != Phase.END) return;
        if (!dirty.compareAndSet(true, false)) return;

        if (!CubicChunksConfig.enableChunkStatusDebugging) {
            if (wasSent) {
                wasSent = false;
                for (EntityPlayerMP player : MinecraftServer.getServer().getConfigurationManager().playerEntityList) {
                    BoxVisualizer.sendBoxes(player, Duration.ofMinutes(0), new ArrayList<>(), true);
                }
            }

            return;
        }

        List<VisualizedBox> boxes = new ArrayList<>();

        cubeStatus.forEach((pos, status) -> {
            if (status == CubeStatus.Synced) return;

            AxisAlignedBB boundingBox = AxisAlignedBB.getBoundingBox(
                pos.getMinBlockX() - 0.5, pos.getMinBlockY() + 0.5, pos.getMinBlockZ() - 0.5, pos.getMaxBlockX() - 0.5,
                pos.getMaxBlockY()
                    - 0.5, pos.getMaxBlockZ() - 0.5
            );

            boxes.add(new VisualizedBox(status.color, boundingBox));
        });

        wasSent = true;

        for (EntityPlayerMP player : MinecraftServer.getServer().getConfigurationManager().playerEntityList) {
            BoxVisualizer.sendBoxes(player, Duration.ofMinutes(5), boxes, false);
        }
    }

    public static void put(CubePos pos, CubeStatus status) {
        cubeStatus.put(pos, status);
        dirty.set(true);
    }

    public static void cmpexc(CubePos pos, CubeStatus expected, CubeStatus desired) {
        cubeStatus.compute(pos, (key, existing) -> existing == expected ? desired : existing);
        dirty.set(true);
    }

    public static void remove(CubePos pos) {
        cubeStatus.remove(pos);
        dirty.set(true);
    }

    public static void remove(CubePos pos, CubeStatus expected) {
        cubeStatus.remove(pos, expected);
        dirty.set(true);
    }
}
