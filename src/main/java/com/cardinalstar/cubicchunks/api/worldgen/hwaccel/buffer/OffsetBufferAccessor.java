package com.cardinalstar.cubicchunks.api.worldgen.hwaccel.buffer;

public class OffsetBufferAccessor implements BufferAccessor {

    public final String buffer, pcName;
    public final BufferDataType dataType;

    public OffsetBufferAccessor(String buffer, String pcName, BufferDataType dataType) {
        this.buffer = buffer;
        this.pcName = pcName;
        this.dataType = dataType;
    }

    @Override
    public BufferDataType getDataType() {
        return dataType;
    }

    @Override
    public String access(String index) {
        return buffer + "[pc." + pcName + " + (" + index + ")]";
    }
}
