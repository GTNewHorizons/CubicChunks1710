package com.cardinalstar.cubicchunks.network;

import java.util.List;

import net.minecraft.util.AxisAlignedBB;
import net.minecraft.world.World;

import com.cardinalstar.cubicchunks.network.PacketEncoderUpdateVisualizedBoxes.PacketUpdateVisualizedBoxes;
import com.cardinalstar.cubicchunks.util.boxvisualizer.VisualizedBox;
import com.cardinalstar.cubicchunks.util.boxvisualizer.VisualizedBoxRenderer;
import com.github.bsideup.jabel.Desugar;
import com.gtnewhorizon.gtnhlib.color.RGBColor;

public class PacketEncoderUpdateVisualizedBoxes extends CCPacketEncoder<PacketUpdateVisualizedBoxes> {

    @Desugar
    public record PacketUpdateVisualizedBoxes(long timeout, boolean append, boolean disableDepth,
        List<VisualizedBox> boxes) implements CCPacket {

        @Override
        public byte getPacketID() {
            return CCPacketEntry.UpdateVisualizedBoxes.id;
        }
    }

    @Override
    public byte getPacketID() {
        return CCPacketEntry.UpdateVisualizedBoxes.id;
    }

    @Override
    public void writePacket(CCPacketBuffer buffer, PacketUpdateVisualizedBoxes packet) {
        buffer.writeLong(packet.timeout);
        buffer.writeBoolean(packet.append);
        buffer.writeBoolean(packet.disableDepth);

        buffer.writeList(packet.boxes, ($, value) -> {
            buffer.writeInt(value.color.toIntRGBA());

            buffer.writeDouble(value.bounds.minX);
            buffer.writeDouble(value.bounds.minY);
            buffer.writeDouble(value.bounds.minZ);
            buffer.writeDouble(value.bounds.maxX);
            buffer.writeDouble(value.bounds.maxY);
            buffer.writeDouble(value.bounds.maxZ);
        });
    }

    @Override
    public PacketUpdateVisualizedBoxes readPacket(CCPacketBuffer buffer) {
        long timeout = buffer.readLong();
        boolean append = buffer.readBoolean();
        boolean disableDepth = buffer.readBoolean();

        var boxes = buffer.readList($ -> {
            RGBColor color = RGBColor.fromRGBA(buffer.readInt());

            double minX = buffer.readDouble();
            double minY = buffer.readDouble();
            double minZ = buffer.readDouble();
            double maxX = buffer.readDouble();
            double maxY = buffer.readDouble();
            double maxZ = buffer.readDouble();

            return new VisualizedBox(color, AxisAlignedBB.getBoundingBox(minX, minY, minZ, maxX, maxY, maxZ));
        });

        return new PacketUpdateVisualizedBoxes(timeout, append, disableDepth, boxes);
    }

    @Override
    public void process(World world, PacketUpdateVisualizedBoxes packet) {
        VisualizedBoxRenderer.receiveBoxes(packet.timeout, packet.append, packet.boxes, packet.disableDepth);
    }
}
