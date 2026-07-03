package com.cardinalstar.cubicchunks.world.worldgen.noise;

import java.nio.ByteBuffer;
import java.util.Random;

import net.minecraft.util.MathHelper;

import com.cardinalstar.cubicchunks.api.worldgen.hwaccel.KernelBuilder;
import com.cardinalstar.cubicchunks.api.worldgen.hwaccel.buffer.BufferDataType;

/// A standard simplex noise sampler.
public class SimplexSampler implements NoiseSampler {

    protected static final int[][] GRADIENTS = new int[][] { { 1, 1, 0 }, { -1, 1, 0 }, { 1, -1, 0 }, { -1, -1, 0 },
        { 1, 0, 1 }, { -1, 0, 1 }, { 1, 0, -1 }, { -1, 0, -1 }, { 0, 1, 1 }, { 0, -1, 1 }, { 0, 1, -1 }, { 0, -1, -1 },
        { 1, 1, 0 }, { 0, -1, 1 }, { -1, 1, 0 }, { 0, -1, -1 } };
    private static final double SQRT_3 = Math.sqrt(3.0D);
    private static final double SKEW_FACTOR_2D;
    private static final double UNSKEW_FACTOR_2D;
    private final int[] permutations = new int[512];
    public final double originX;
    public final double originY;
    public final double originZ;

    public SimplexSampler(Random random) {
        this.originX = random.nextDouble() * 256.0D;
        this.originY = random.nextDouble() * 256.0D;
        this.originZ = random.nextDouble() * 256.0D;

        for (int i = 0; i < 256; i++) {
            this.permutations[i] = i;
        }

        for (int i = 0; i < 256; ++i) {
            int k = random.nextInt(256 - i);
            int l = this.permutations[i];
            this.permutations[i] = this.permutations[k + i];
            this.permutations[k + i] = l;
        }
    }

    private int getGradient(int hash) {
        return this.permutations[hash & 255];
    }

    protected static double dot(int[] gArr, double x, double y, double z) {
        return (double) gArr[0] * x + (double) gArr[1] * y + (double) gArr[2] * z;
    }

    private double grad(int hash, double x, double y, double z, double distance) {
        double d = distance - x * x - y * y - z * z;
        double f;
        if (d < 0.0D) {
            f = 0.0D;
        } else {
            d *= d;
            f = d * d * dot(GRADIENTS[hash], x, y, z);
        }

        return f;
    }

    @Override
    public double sample(double x, double y) {
        double d = (x + y) * SKEW_FACTOR_2D;
        int i = MathHelper.floor_double(x + d);
        int j = MathHelper.floor_double(y + d);
        double e = (double) (i + j) * UNSKEW_FACTOR_2D;
        double f = (double) i - e;
        double g = (double) j - e;
        double h = x - f;
        double k = y - g;
        byte n;
        byte o;
        if (h > k) {
            n = 1;
            o = 0;
        } else {
            n = 0;
            o = 1;
        }

        double p = h - (double) n + UNSKEW_FACTOR_2D;
        double q = k - (double) o + UNSKEW_FACTOR_2D;
        double r = h - 1.0D + 2.0D * UNSKEW_FACTOR_2D;
        double s = k - 1.0D + 2.0D * UNSKEW_FACTOR_2D;
        int t = i & 255;
        int u = j & 255;
        int v = this.getGradient(t + this.getGradient(u)) % 12;
        int w = this.getGradient(t + n + this.getGradient(u + o)) % 12;
        int z = this.getGradient(t + 1 + this.getGradient(u + 1)) % 12;
        double aa = this.grad(v, h, k, 0.0D, 0.5D);
        double ab = this.grad(w, p, q, 0.0D, 0.5D);
        double ac = this.grad(z, r, s, 0.0D, 0.5D);
        return 70.0D * (aa + ab + ac);
    }

    @Override
    public double sample(double x, double y, double z) {
        double e = (x + y + z) * 0.3333333333333333D;
        int i = MathHelper.floor_double(x + e);
        int j = MathHelper.floor_double(y + e);
        int k = MathHelper.floor_double(z + e);
        double g = (double) (i + j + k) * 0.16666666666666666D;
        double h = (double) i - g;
        double l = (double) j - g;
        double m = (double) k - g;
        double n = x - h;
        double o = y - l;
        double p = z - m;
        byte w;
        byte aa;
        byte ab;
        byte ac;
        byte ad;
        byte bc;
        if (n >= o) {
            if (o >= p) {
                w = 1;
                aa = 0;
                ab = 0;
                ac = 1;
                ad = 1;
                bc = 0;
            } else if (n >= p) {
                w = 1;
                aa = 0;
                ab = 0;
                ac = 1;
                ad = 0;
                bc = 1;
            } else {
                w = 0;
                aa = 0;
                ab = 1;
                ac = 1;
                ad = 0;
                bc = 1;
            }
        } else if (o < p) {
            w = 0;
            aa = 0;
            ab = 1;
            ac = 0;
            ad = 1;
            bc = 1;
        } else if (n < p) {
            w = 0;
            aa = 1;
            ab = 0;
            ac = 0;
            ad = 1;
            bc = 1;
        } else {
            w = 0;
            aa = 1;
            ab = 0;
            ac = 1;
            ad = 1;
            bc = 0;
        }

        double bd = n - (double) w + 0.16666666666666666D;
        double be = o - (double) aa + 0.16666666666666666D;
        double bf = p - (double) ab + 0.16666666666666666D;
        double bg = n - (double) ac + 0.3333333333333333D;
        double bh = o - (double) ad + 0.3333333333333333D;
        double bi = p - (double) bc + 0.3333333333333333D;
        double bj = n - 1.0D + 0.5D;
        double bk = o - 1.0D + 0.5D;
        double bl = p - 1.0D + 0.5D;
        int bm = i & 255;
        int bn = j & 255;
        int bo = k & 255;
        int bp = this.getGradient(bm + this.getGradient(bn + this.getGradient(bo))) % 12;
        int bq = this.getGradient(bm + w + this.getGradient(bn + aa + this.getGradient(bo + ab))) % 12;
        int br = this.getGradient(bm + ac + this.getGradient(bn + ad + this.getGradient(bo + bc))) % 12;
        int bs = this.getGradient(bm + 1 + this.getGradient(bn + 1 + this.getGradient(bo + 1))) % 12;
        double bt = this.grad(bp, n, o, p, 0.6D);
        double bu = this.grad(bq, bd, be, bf, 0.6D);
        double bv = this.grad(br, bg, bh, bi, 0.6D);
        double bw = this.grad(bs, bj, bk, bl, 0.6D);
        return 32.0D * (bt + bu + bv + bw);
    }

    static {
        SKEW_FACTOR_2D = 0.5D * (SQRT_3 - 1.0D);
        UNSKEW_FACTOR_2D = (3.0D - SQRT_3) / 6.0D;
    }

    @Override
    public String compileKernel2D(KernelBuilder builder, String x, String y) {
        String funcName = builder.createName("simplex2d");

        ByteBuffer permBytes = ByteBuffer.allocateDirect(permutations.length * 4);
        permBytes.asIntBuffer().put(permutations);

        var perms = builder.constants.append(BufferDataType.i32, permBytes);

        float skew = (float) SKEW_FACTOR_2D;
        float unskew = (float) UNSKEW_FACTOR_2D;

        // Gradients: GRADIENTS[][0] and [][1] for indices 0-11
        builder.preamble
            .append("float ").append(funcName).append("(float px, float py) {\n")
            .append("  const int GX[12] = int[12](1,-1,1,-1,1,-1,1,-1,0,0,0,0);\n")
            .append("  const int GY[12] = int[12](1,1,-1,-1,0,0,0,0,1,-1,1,-1);\n")
            .append("  float d = (px + py) * ").append(skew).append("f;\n")
            .append("  int i = int(floor(px + d));\n")
            .append("  int j = int(floor(py + d));\n")
            .append("  float e = float(i + j) * ").append(unskew).append("f;\n")
            .append("  float h = px - (float(i) - e);\n")
            .append("  float k = py - (float(j) - e);\n")
            .append("  int n = h > k ? 1 : 0;\n")
            .append("  int o = h > k ? 0 : 1;\n")
            .append("  float p = h - float(n) + ").append(unskew).append("f;\n")
            .append("  float q = k - float(o) + ").append(unskew).append("f;\n")
            .append("  float r = h - 1.0f + 2.0f * ").append(unskew).append("f;\n")
            .append("  float s = k - 1.0f + 2.0f * ").append(unskew).append("f;\n")
            .append("  int t = i & 255;\n")
            .append("  int u = j & 255;\n")
            .append("  int v = ").append(perms.access("(t + " + perms.access("u") + ") & 255")).append(" % 12;\n")
            .append("  int w = ").append(perms.access("(t + n + " + perms.access("(u + o) & 255") + ") & 255")).append(" % 12;\n")
            .append("  int z = ").append(perms.access("(t + 1 + " + perms.access("(u + 1) & 255") + ") & 255")).append(" % 12;\n")
            .append("  float t0 = max(0.0f, 0.5f - h*h - k*k);\n")
            .append("  float n0 = t0*t0*t0*t0 * (float(GX[v])*h + float(GY[v])*k);\n")
            .append("  float t1 = max(0.0f, 0.5f - p*p - q*q);\n")
            .append("  float n1 = t1*t1*t1*t1 * (float(GX[w])*p + float(GY[w])*q);\n")
            .append("  float t2 = max(0.0f, 0.5f - r*r - s*s);\n")
            .append("  float n2 = t2*t2*t2*t2 * (float(GX[z])*r + float(GY[z])*s);\n")
            .append("  return 70.0f * (n0 + n1 + n2);\n")
            .append("}\n");

        String result = builder.createName("simplex");
        builder.logic.append("  float ").append(result).append(" = ")
            .append(funcName).append("(").append(x).append(", ").append(y).append(");\n");
        return result;
    }

    @Override
    public String compileKernel3D(KernelBuilder builder, String x, String y, String z) {
        String funcName = builder.createName("simplex3d");

        ByteBuffer permBytes = ByteBuffer.allocateDirect(permutations.length * 4);
        permBytes.asIntBuffer().put(permutations);

        var perms = builder.constants.append(BufferDataType.i32, permBytes);

        // Gradients: GRADIENTS[][0..2] for indices 0-11
        builder.preamble
            .append("float ").append(funcName).append("(float px, float py, float pz) {\n")
            .append("  const int GX[12] = int[12](1,-1,1,-1,1,-1,1,-1,0,0,0,0);\n")
            .append("  const int GY[12] = int[12](1,1,-1,-1,0,0,0,0,1,-1,1,-1);\n")
            .append("  const int GZ[12] = int[12](0,0,0,0,1,1,-1,-1,1,1,-1,-1);\n")
            .append("  float sk = (px + py + pz) * 0.3333333333f;\n")
            .append("  int i = int(floor(px + sk));\n")
            .append("  int j = int(floor(py + sk));\n")
            .append("  int k = int(floor(pz + sk));\n")
            .append("  float g = float(i + j + k) * 0.1666666667f;\n")
            .append("  float x0 = px - (float(i) - g);\n")
            .append("  float y0 = py - (float(j) - g);\n")
            .append("  float z0 = pz - (float(k) - g);\n")
            // 6-way vertex ordering (mirrors the Java if/else chain exactly)
            .append("  int i1, j1, k1, i2, j2, k2;\n")
            .append("  if (x0 >= y0) {\n")
            .append("    if (y0 >= z0)      { i1=1;j1=0;k1=0; i2=1;j2=1;k2=0; }\n")
            .append("    else if (x0 >= z0) { i1=1;j1=0;k1=0; i2=1;j2=0;k2=1; }\n")
            .append("    else               { i1=0;j1=0;k1=1; i2=1;j2=0;k2=1; }\n")
            .append("  } else {\n")
            .append("    if (y0 < z0)       { i1=0;j1=0;k1=1; i2=0;j2=1;k2=1; }\n")
            .append("    else if (x0 < z0)  { i1=0;j1=1;k1=0; i2=0;j2=1;k2=1; }\n")
            .append("    else               { i1=0;j1=1;k1=0; i2=1;j2=1;k2=0; }\n")
            .append("  }\n")
            .append("  float x1 = x0 - float(i1) + 0.1666666667f;\n")
            .append("  float y1 = y0 - float(j1) + 0.1666666667f;\n")
            .append("  float z1 = z0 - float(k1) + 0.1666666667f;\n")
            .append("  float x2 = x0 - float(i2) + 0.3333333333f;\n")
            .append("  float y2 = y0 - float(j2) + 0.3333333333f;\n")
            .append("  float z2 = z0 - float(k2) + 0.3333333333f;\n")
            .append("  float x3 = x0 - 0.5f;\n")
            .append("  float y3 = y0 - 0.5f;\n")
            .append("  float z3 = z0 - 0.5f;\n")
            .append("  int ii = i & 255;\n")
            .append("  int jj = j & 255;\n")
            .append("  int kk = k & 255;\n")
            .append("  int g0 = ").append(perms.access("(ii + " + perms.access("(jj + " + perms.access("kk") + ") & 255") + ") & 255")).append(" % 12;\n")
            .append("  int g1 = ").append(perms.access("(ii + i1 + " + perms.access("(jj + j1 + " + perms.access("(kk + k1) & 255") + ") & 255") + ") & 255")).append(" % 12;\n")
            .append("  int g2 = ").append(perms.access("(ii + i2 + " + perms.access("(jj + j2 + " + perms.access("(kk + k2) & 255") + ") & 255") + ") & 255")).append(" % 12;\n")
            .append("  int g3 = ").append(perms.access("(ii + 1 + " + perms.access("(jj + 1 + " + perms.access("(kk + 1) & 255") + ") & 255") + ") & 255")).append(" % 12;\n")
            .append("  float d0 = 0.6f - x0*x0 - y0*y0 - z0*z0;\n")
            .append("  float n0 = (d0 < 0.0f) ? 0.0f : d0*d0*d0*d0 * (float(GX[g0])*x0 + float(GY[g0])*y0 + float(GZ[g0])*z0);\n")
            .append("  float d1 = 0.6f - x1*x1 - y1*y1 - z1*z1;\n")
            .append("  float n1 = (d1 < 0.0f) ? 0.0f : d1*d1*d1*d1 * (float(GX[g1])*x1 + float(GY[g1])*y1 + float(GZ[g1])*z1);\n")
            .append("  float d2 = 0.6f - x2*x2 - y2*y2 - z2*z2;\n")
            .append("  float n2 = (d2 < 0.0f) ? 0.0f : d2*d2*d2*d2 * (float(GX[g2])*x2 + float(GY[g2])*y2 + float(GZ[g2])*z2);\n")
            .append("  float d3 = 0.6f - x3*x3 - y3*y3 - z3*z3;\n")
            .append("  float n3 = (d3 < 0.0f) ? 0.0f : d3*d3*d3*d3 * (float(GX[g3])*x3 + float(GY[g3])*y3 + float(GZ[g3])*z3);\n")
            .append("  return 32.0f * (n0 + n1 + n2 + n3);\n")
            .append("}\n");

        String result = builder.createName("simplex");
        builder.logic.append("  float ").append(result).append(" = ")
            .append(funcName).append("(").append(x).append(", ").append(y).append(", ").append(z).append(");\n");
        return result;
    }
}
