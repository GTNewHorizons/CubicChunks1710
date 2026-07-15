package com.cardinalstar.cubicchunks.api.worldgen.hwaccel.buffer;

import java.util.function.Function;

public class TransformingBufferAccessor implements BufferAccessor {

    private final BufferAccessor next;
    private final Function<String, String> modifyIndex;

    public TransformingBufferAccessor(BufferAccessor next, Function<String, String> modifyIndex) {
        this.next = next;
        this.modifyIndex = modifyIndex;
    }

    @Override
    public BufferDataType getDataType() {
        return next.getDataType();
    }

    @Override
    public String access(String index) {
        return next.access(modifyIndex.apply(index));
    }
}
