package com.cardinalstar.cubicchunks.api.worldgen.hwaccel;

import com.github.bsideup.jabel.Desugar;

@Desugar
public record KernelSubmissionToken(KernelExecutor executor, Object key, int id, int level) {

}
