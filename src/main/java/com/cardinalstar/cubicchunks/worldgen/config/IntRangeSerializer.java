package com.cardinalstar.cubicchunks.worldgen.config;

import java.lang.reflect.Type;

import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.serialize.SerializationException;
import org.spongepowered.configurate.serialize.TypeSerializer;

import com.cardinalstar.cubicchunks.api.IntRange;

/**
 * Serializes an {@link IntRange} as {@code { min = -64, max = unbounded }}. An unbounded end is stored in the range
 * as the corresponding limit, and an end at a limit is written as unbounded.
 */
public class IntRangeSerializer implements TypeSerializer<IntRange> {

    public static final String UNBOUNDED = "unbounded";

    private final IntRange limits;
    private final boolean allowUnbounded;

    public IntRangeSerializer(IntRange limits, boolean allowUnbounded) {
        this.limits = limits;
        this.allowUnbounded = allowUnbounded;
    }

    @Override
    public IntRange deserialize(Type type, ConfigurationNode node) throws SerializationException {
        int min = readBound(node.node("min"), limits.getMin());
        int max = readBound(node.node("max"), limits.getMax());
        if (min > max) {
            throw new SerializationException(node, type, "min (" + min + ") is greater than max (" + max + ")");
        }
        return new IntRange(min, max);
    }

    @Override
    public void serialize(Type type, IntRange obj, ConfigurationNode node) throws SerializationException {
        if (obj == null) {
            node.raw(null);
            return;
        }
        writeBound(node.node("min"), obj.getMin(), limits.getMin());
        writeBound(node.node("max"), obj.getMax(), limits.getMax());
    }

    private int readBound(ConfigurationNode node, int unboundedValue) throws SerializationException {
        if (node.virtual()) {
            throw new SerializationException(node, Integer.class, "missing value");
        }
        if (allowUnbounded && UNBOUNDED.equalsIgnoreCase(node.getString())) {
            return unboundedValue;
        }
        Integer value = node.get(Integer.class);
        if (value == null || value < limits.getMin() || value > limits.getMax()) {
            throw new SerializationException(
                node,
                Integer.class,
                "expected an integer in [" + limits.getMin()
                    + ", "
                    + limits.getMax()
                    + "]"
                    + (allowUnbounded ? " or '" + UNBOUNDED + "'" : "")
                    + ", got "
                    + node.raw());
        }
        return value;
    }

    private void writeBound(ConfigurationNode node, int value, int unboundedValue) throws SerializationException {
        if (allowUnbounded && value == unboundedValue) {
            node.set(UNBOUNDED);
        } else {
            node.set(value);
        }
    }
}
