package com.cardinalstar.cubicchunks.codegen;

public enum FieldType {
    i32,
    u32,
    i64,
    u64,
    f32,
    f64;

    public String javaType() {
        switch (this) {
            case i32:
            case u32:
                return "int";
            case i64:
            case u64:
                return "long";
            case f32:
                return "float";
            case f64:
                return "double";
            default:
                throw new IllegalArgumentException();
        }
    }

    public String glslType() {
        switch (this) {
            case i32:
                return "int";
            case u32:
                return "uint";
            case i64:
                return "long";
            case u64:
                return "ulong";
            case f32:
                return "float";
            case f64:
                return "double";
            default:
                throw new IllegalArgumentException();
        }
    }

    public int wordWidth() {
        switch (this) {
            case i32:
            case u32:
            case f32:
                return 1;
            case i64:
            case u64:
            case f64:
                return 2;
            default:
                throw new IllegalArgumentException();
        }
    }

    /** Byte size of this type. */
    public int byteSize() {
        return wordWidth() * 4;
    }

    /** std430 alignment requirement in bytes. */
    public int glAlign() {
        return wordWidth() == 2 ? 8 : 4;
    }
}
