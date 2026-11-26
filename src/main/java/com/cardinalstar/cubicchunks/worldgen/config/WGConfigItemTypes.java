package com.cardinalstar.cubicchunks.worldgen.config;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.spongepowered.configurate.serialize.TypeSerializer;
import org.spongepowered.configurate.serialize.TypeSerializerCollection;

import com.cardinalstar.cubicchunks.api.IntRange;
import com.cardinalstar.cubicchunks.worldgen.config.gui.element.BlockConfigElement;
import com.cardinalstar.cubicchunks.worldgen.config.gui.element.IntRangeConfigElement;
import com.cardinalstar.cubicchunks.worldgen.config.gui.element.StrataConfigElement;
import com.cardinalstar.cubicchunks.worldgen.config.gui.element.WGListConfigElement;

import cpw.mods.fml.client.config.ConfigGuiType;
import cpw.mods.fml.client.config.IConfigElement;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Factories for the built-in {@link WGConfigItemType}s.
 * <p>
 * These are anonymous classes rather than lambdas so that {@link SideOnly} strips the GUI code on dedicated servers.
 */
public final class WGConfigItemTypes {

    private WGConfigItemTypes() {}

    /**
     * An int range within {@code limits}. An end at a limit is unbounded, which follows the limit if it changes (the
     * file stores it as "unbounded"). Ends are rounded outward to multiples of {@code alignment} (min down, max up),
     * then clamped to {@code limits}.
     */
    public static WGConfigItemType<IntRange> intRange(IntRange limits, boolean allowUnbounded, int alignment) {
        TypeSerializer<IntRange> serializer = new IntRangeSerializer(limits, allowUnbounded);
        return new WGConfigItemType<IntRange>() {

            @Override
            public Class<IntRange> getType() {
                return IntRange.class;
            }

            @Override
            public TypeSerializer<IntRange> getSerializer() {
                return serializer;
            }

            @Override
            public IntRange normalize(IntRange value) {
                long min = Math.floorDiv((long) value.getMin(), alignment) * alignment;
                long max = -Math.floorDiv(-(long) value.getMax(), alignment) * alignment;
                return new IntRange((int) Math.max(limits.getMin(), min), (int) Math.min(limits.getMax(), max));
            }

            @Override
            @SideOnly(Side.CLIENT)
            public IConfigElement<?> createConfigElement(WGConfigItem<IntRange> item, WGDimensionConfig sandbox) {
                return new IntRangeConfigElement(item, sandbox, limits, allowUnbounded);
            }
        };
    }

    /** A block and metadata, picked from the registered blocks. */
    public static WGConfigItemType<BlockRef> block() {
        return block(false);
    }

    /** Like {@link #block()}, but the value may also be null, meaning whatever uses it picks one automatically. */
    public static WGConfigItemType<BlockRef> blockOrAuto() {
        return block(true);
    }

    private static WGConfigItemType<BlockRef> block(boolean allowAuto) {
        TypeSerializer<BlockRef> serializer = new BlockRefSerializer(allowAuto);
        return new WGConfigItemType<BlockRef>() {

            @Override
            public Class<BlockRef> getType() {
                return BlockRef.class;
            }

            @Override
            public TypeSerializer<BlockRef> getSerializer() {
                return serializer;
            }

            @Override
            @SideOnly(Side.CLIENT)
            public IConfigElement<?> createConfigElement(WGConfigItem<BlockRef> item, WGDimensionConfig sandbox) {
                return new BlockConfigElement(item, sandbox);
            }
        };
    }

    /** Strata layers, edited in their own screen. */
    public static WGConfigItemType<List<Stratum>> strata() {
        TypeSerializer<List<Stratum>> serializer = new StrataSerializer();
        return new WGConfigItemType<List<Stratum>>() {

            @Override
            @SuppressWarnings("unchecked")
            public Class<List<Stratum>> getType() {
                return (Class<List<Stratum>>) (Class<?>) List.class;
            }

            @Override
            public TypeSerializer<List<Stratum>> getSerializer() {
                return serializer;
            }

            @Override
            public List<Stratum> normalize(List<Stratum> value) {
                return Collections.unmodifiableList(new ArrayList<>(value));
            }

            @Override
            @SideOnly(Side.CLIENT)
            public IConfigElement<?> createConfigElement(WGConfigItem<List<Stratum>> item, WGDimensionConfig sandbox) {
                return new StrataConfigElement(item, sandbox);
            }
        };
    }

    public static WGConfigItemType<String[]> stringList() {
        TypeSerializer<String[]> serializer = TypeSerializerCollection.defaults()
            .get(String[].class);
        return new WGConfigItemType<String[]>() {

            @Override
            public Class<String[]> getType() {
                return String[].class;
            }

            @Override
            public TypeSerializer<String[]> getSerializer() {
                return serializer;
            }

            @Override
            @SideOnly(Side.CLIENT)
            public IConfigElement<?> createConfigElement(WGConfigItem<String[]> item, WGDimensionConfig sandbox) {
                return new WGListConfigElement<>(item, sandbox, ConfigGuiType.STRING);
            }
        };
    }
}
