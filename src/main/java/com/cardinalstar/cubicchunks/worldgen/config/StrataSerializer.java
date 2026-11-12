package com.cardinalstar.cubicchunks.worldgen.config;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.serialize.SerializationException;
import org.spongepowered.configurate.serialize.TypeSerializer;

/**
 * Serializes strata as a list of {@code { block = "minecraft:stone", meta = 0, full = -50, none = -25 }}.
 */
public class StrataSerializer implements TypeSerializer<List<Stratum>> {

    @Override
    public List<Stratum> deserialize(Type type, ConfigurationNode node) throws SerializationException {
        if (!node.isList()) {
            throw new SerializationException(node, type, "expected a list of layers");
        }

        List<Stratum> strata = new ArrayList<>();
        for (ConfigurationNode child : node.childrenList()) {
            strata.add(new Stratum(BlockRefSerializer.read(child), readY(child.node("full")), readY(child.node("none"))));
        }
        return strata;
    }

    @Override
    public void serialize(Type type, List<Stratum> obj, ConfigurationNode node) throws SerializationException {
        if (obj == null) {
            node.raw(null);
            return;
        }

        // Start from an explicit empty list: an empty node wouldn't be written, and would load as the default
        node.raw(Collections.emptyList());
        for (Stratum stratum : obj) {
            ConfigurationNode child = node.appendListNode();
            BlockRefSerializer.write(child, stratum.block);
            child.node("full")
                .set(stratum.full);
            child.node("none")
                .set(stratum.none);
        }
    }

    private static int readY(ConfigurationNode node) throws SerializationException {
        Integer value = node.get(Integer.class);
        if (value == null) {
            throw new SerializationException(node, Integer.class, "expected a Y level");
        }
        return value;
    }
}
