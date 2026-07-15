package com.cardinalstar.cubicchunks.api.worldgen.hwaccel.buffer;

import com.cardinalstar.cubicchunks.api.worldgen.hwaccel.KernelSubmissionToken;
import com.github.bsideup.jabel.Desugar;

@Desugar
public record BufferDescriptor(KernelSubmissionToken submission, int bufferId, BufferDataType dataType, int lenX,
    int lenY, int lenZ) {

    public void assertLayout(BufferDataType dataType, int lenX) {
        assertLayout(dataType, lenX, 1, 1);
    }

    public void assertLayout(BufferDataType dataType, int lenX, int lenY) {
        assertLayout(dataType, lenX, lenY, 1);
    }

    public void assertLayout(BufferDataType dataType, int lenX, int lenY, int lenZ) {
        if (this.dataType != dataType) throw new IllegalStateException(
            "Expected buffer to contain " + dataType + " but it contains " + this.dataType);

        if (this.lenX != lenX)
            throw new IllegalStateException("Expected buffer X length to be " + lenX + " but it was " + this.lenX);
        if (this.lenY != lenY)
            throw new IllegalStateException("Expected buffer Y length to be " + lenY + " but it was " + this.lenY);
        if (this.lenZ != lenZ)
            throw new IllegalStateException("Expected buffer Z length to be " + lenZ + " but it was " + this.lenZ);
    }

    public void assertLayout(BufferLayout layout) {
        assertLayout(layout.dataType(), layout.lenX(), layout.lenY(), layout.lenZ());
    }

    public int getBufferLength() {
        return dataType.width() * lenX * lenY * lenZ;
    }
}
