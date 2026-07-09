package com.cardinalstar.cubicchunks.api.worldgen.hwaccel;

import java.util.Map;

import com.cardinalstar.cubicchunks.api.worldgen.hwaccel.buffer.GPUBuffer;
import com.github.bsideup.jabel.Desugar;

@Desugar
public record KernelSubmission<K> (K key, Map<String, GPUBuffer> inputs) {

}
