package com.cardinalstar.cubicchunks.api.worldgen.hwaccel.buffer;

/// Something that can retrieve a value from a flat buffer/equation.
/// Accepts an int and returns whatever type this accessor returns.
public interface BufferAccessor {

    BufferDataType getDataType();

    String access(String index);

}
