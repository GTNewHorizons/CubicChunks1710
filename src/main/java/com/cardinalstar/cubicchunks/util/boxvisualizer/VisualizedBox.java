package com.cardinalstar.cubicchunks.util.boxvisualizer;

import net.minecraft.util.AxisAlignedBB;

import com.gtnewhorizon.gtnhlib.color.RGBColor;

public class VisualizedBox {

    public final RGBColor color;
    public AxisAlignedBB bounds;

    public VisualizedBox(RGBColor color, AxisAlignedBB bounds) {
        this.color = color;
        this.bounds = bounds;
    }

    public VisualizedBox(int rgba, AxisAlignedBB bounds) {
        this.color = RGBColor.fromRGBA(rgba);
        this.bounds = bounds;
    }

    public VisualizedBox expand(double amount) {
        bounds = AxisAlignedBB.getBoundingBox(
            bounds.minX - amount,
            bounds.minY - amount,
            bounds.minZ - amount,
            bounds.maxX + amount,
            bounds.maxY + amount,
            bounds.maxZ + amount);

        return this;
    }

    @Override
    public String toString() {
        return "VisualizedBox{" + "color=" + color + ", bounds=" + bounds + '}';
    }
}
