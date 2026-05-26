package com.cardinalstar.cubicchunks.worldgen;

import com.github.bsideup.jabel.Desugar;
import com.gtnewhorizon.gtnhlib.util.data.ImmutableBlockMeta;

@Desugar
public class FillerInfo {

    public ImmutableBlockMeta topFiller;
    public ImmutableBlockMeta bottomFiller;

    public FillerInfo(ImmutableBlockMeta topFiller, ImmutableBlockMeta bottomFiller) {
        this.topFiller = topFiller;
        this.bottomFiller = bottomFiller;
    }

    public FillerInfo() {}
}
