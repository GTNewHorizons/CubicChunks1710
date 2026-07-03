package com.cardinalstar.cubicchunks.codegen;

import java.util.Locale;

public class StructField {

    public final FieldType type;
    public final String name;
    /** Word index into the Java IntBuffer (sequential, no alignment gaps). */
    public final int wordOffset;
    /** Byte offset in the GLSL std430 layout (alignment-padded). */
    public final int glByteOffset;

    StructField(FieldType type, String name, int wordOffset, int glByteOffset) {
        this.type = type;
        this.name = name;
        this.wordOffset = wordOffset;
        this.glByteOffset = glByteOffset;
    }

    public String getPascalCase() {
        return name.substring(0, 1).toUpperCase(Locale.ROOT) + name.substring(1);
    }
}
