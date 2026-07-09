package com.cardinalstar.cubicchunks.api.worldgen.hwaccel.buffer;

public enum BufferDataType {

    i32,
    u32,
    i64,
    u64,
    f32,
    f64;

    public int width() {
        return switch (this) {
            case i32, u32, f32 -> 4;
            case i64, u64, f64 -> 8;
        };
    }

    public String fromUint(String expr) {
        return switch (this) {
            case i32 -> "int(" + expr + ")";
            case u32 -> expr;
            case f32 -> "uintBitsToFloat(" + expr + ")";
            case i64, u64, f64 -> throw new UnsupportedOperationException(
                "64-bit GLSL buffer accessors require two uint32 slots and are not yet implemented: " + this);
        };
    }

    public String toUint(String expr) {
        return switch (this) {
            case i32 -> "uint(" + expr + ")";
            case u32 -> expr;
            case f32 -> "floatBitsToUint(" + expr + ")";
            case i64, u64, f64 -> throw new UnsupportedOperationException(
                "64-bit GLSL buffer accessors require two uint32 slots and are not yet implemented: " + this);
        };
    }
}
