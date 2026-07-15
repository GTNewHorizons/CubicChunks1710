package com.cardinalstar.cubicchunks.api.worldgen.hwaccel.buffer;

import com.github.bsideup.jabel.Desugar;

@Desugar
public record BufferLayout(BufferDataType dataType, int lenX, int lenY, int lenZ) {

    public BufferLayout(BufferDataType dataType, int lenX, int lenY) {
        this(dataType, lenX, lenY, 1);
    }

    public BufferLayout(BufferDataType dataType, int lenX) {
        this(dataType, lenX, 1, 1);
    }
}
