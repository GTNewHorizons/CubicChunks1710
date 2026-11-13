package com.cardinalstar.cubicchunks.worldgen.config;

import java.lang.reflect.Type;

import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.serialize.SerializationException;
import org.spongepowered.configurate.serialize.TypeSerializer;

/**
 * Serializes a {@link BlockRef} as {@code { block = "minecraft:stone", meta = 0 }}. {@code meta} may be omitted.
 * Unknown blocks are allowed; whatever uses the setting decides what to do with them.
 * <p>
 * If {@code allowAuto}, the value may also be {@code auto}, which is a null BlockRef.
 */
public class BlockRefSerializer implements TypeSerializer<BlockRef> {

    public static final String AUTO = "auto";

    private final boolean allowAuto;

    public BlockRefSerializer(boolean allowAuto) {
        this.allowAuto = allowAuto;
    }

    @Override
    public BlockRef deserialize(Type type, ConfigurationNode node) throws SerializationException {
        if (allowAuto && AUTO.equalsIgnoreCase(node.getString())) return null;
        return read(node);
    }

    @Override
    public void serialize(Type type, BlockRef obj, ConfigurationNode node) throws SerializationException {
        if (obj == null) {
            if (allowAuto) {
                node.set(AUTO);
            } else {
                node.raw(null);
            }
            return;
        }
        // Replace an "auto" scalar with the block's children
        node.raw(null);
        write(node, obj);
    }

    /** Reads the {@code block} and {@code meta} children of {@code node}. */
    static BlockRef read(ConfigurationNode node) throws SerializationException {
        ConfigurationNode blockNode = node.node("block");
        String id = blockNode.getString();
        if (id == null || id.trim()
            .isEmpty()) {
            throw new SerializationException(blockNode, String.class, "expected a block id, e.g. \"minecraft:stone\"");
        }

        ConfigurationNode metaNode = node.node("meta");
        int meta = metaNode.get(Integer.class, 0);
        if (meta < 0) {
            throw new SerializationException(
                metaNode,
                Integer.class,
                "expected a non-negative meta value, got " + meta);
        }
        return new BlockRef(id.trim(), meta);
    }

    /** Writes the {@code block} and {@code meta} children of {@code node}. */
    static void write(ConfigurationNode node, BlockRef ref) throws SerializationException {
        node.node("block")
            .set(ref.id);
        node.node("meta")
            .set(ref.meta);
    }
}
