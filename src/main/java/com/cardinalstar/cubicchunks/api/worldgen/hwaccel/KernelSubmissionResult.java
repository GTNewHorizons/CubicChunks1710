package com.cardinalstar.cubicchunks.api.worldgen.hwaccel;

import java.util.Map;

import com.cardinalstar.cubicchunks.api.worldgen.hwaccel.buffer.GPUBuffer;
import com.github.bsideup.jabel.Desugar;

@Desugar
public record KernelSubmissionResult(Map<String, GPUBuffer> outputs) {

}
