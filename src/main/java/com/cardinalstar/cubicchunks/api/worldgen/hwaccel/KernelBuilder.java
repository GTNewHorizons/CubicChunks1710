package com.cardinalstar.cubicchunks.api.worldgen.hwaccel;

import com.cardinalstar.cubicchunks.api.worldgen.hwaccel.buffer.ConstantHardwareBuffer;

public class KernelBuilder {

    private int discriminator = 0;

    public final StringBuilder preamble = new StringBuilder();
    public final StringBuilder logic = new StringBuilder();

    public final ConstantHardwareBuffer constants = new ConstantHardwareBuffer("constants");

    public String createName(String human) {
        return human + "_" + (discriminator++);
    }
}
