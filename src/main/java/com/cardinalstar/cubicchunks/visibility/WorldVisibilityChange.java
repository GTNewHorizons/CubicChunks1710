package com.cardinalstar.cubicchunks.visibility;

import com.cardinalstar.cubicchunks.util.HashSet2D;
import com.cardinalstar.cubicchunks.util.HashSet3D;

public class WorldVisibilityChange {

    public final HashSet3D cubesToUnload = new HashSet3D();
    public final HashSet3D cubesToLoad = new HashSet3D();
    public final HashSet2D columnsToUnload = new HashSet2D();
    public final HashSet2D columnsToLoad = new HashSet2D();
}
