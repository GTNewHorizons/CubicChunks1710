package com.cardinalstar.cubicchunks.api.worldgen.hwaccel;

import java.util.Map;

import com.cardinalstar.cubicchunks.api.worldgen.hwaccel.buffer.BufferDescriptor;
import com.github.bsideup.jabel.Desugar;

@Desugar
record Terminal(Map<String, BufferDescriptor> inputs, TerminalTask task) {

}
