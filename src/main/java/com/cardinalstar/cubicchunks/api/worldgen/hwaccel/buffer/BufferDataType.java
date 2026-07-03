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
}
