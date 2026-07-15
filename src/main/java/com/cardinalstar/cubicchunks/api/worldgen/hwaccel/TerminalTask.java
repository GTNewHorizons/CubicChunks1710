package com.cardinalstar.cubicchunks.api.worldgen.hwaccel;

import java.nio.ByteBuffer;
import java.util.Map;

public interface TerminalTask {

    void execute(Map<String, ByteBuffer> inputs);
}
