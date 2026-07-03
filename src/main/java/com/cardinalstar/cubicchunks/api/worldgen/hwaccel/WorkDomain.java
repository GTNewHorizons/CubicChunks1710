package com.cardinalstar.cubicchunks.api.worldgen.hwaccel;

import com.cardinalstar.cubicchunks.util.IntFraction;

public class WorkDomain {

    public int samplesX = 1, samplesY = 1, samplesZ = 1;

    public IntFraction scaleX = new IntFraction(), scaleY = new IntFraction(), scaleZ = new IntFraction();

    public WorkDomain() {
    }

    public WorkDomain(int samplesX, int samplesY, int samplesZ) {
        this.samplesX = samplesX;
        this.samplesY = samplesY;
        this.samplesZ = samplesZ;
    }

    public static WorkDomain chunk() {
        return new WorkDomain(16, 1, 16);
    }

    public static WorkDomain cube() {
        return new WorkDomain(16, 16, 16);
    }
}
