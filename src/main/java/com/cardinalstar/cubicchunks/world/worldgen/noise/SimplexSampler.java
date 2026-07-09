package com.cardinalstar.cubicchunks.world.worldgen.noise;

import java.util.Random;

import net.minecraft.util.MathHelper;

import com.cardinalstar.cubicchunks.api.worldgen.hwaccel.KernelBuilder;
import com.cardinalstar.cubicchunks.api.worldgen.hwaccel.buffer.TransformingBufferAccessor;

/// A standard simplex noise sampler.
public class SimplexSampler implements NoiseSampler {

    protected static final int[][] GRADIENTS = new int[][] { { 1, 1, 0 }, { -1, 1, 0 }, { 1, -1, 0 }, { -1, -1, 0 },
        { 1, 0, 1 }, { -1, 0, 1 }, { 1, 0, -1 }, { -1, 0, -1 }, { 0, 1, 1 }, { 0, -1, 1 }, { 0, 1, -1 }, { 0, -1, -1 },
        { 1, 1, 0 }, { 0, -1, 1 }, { -1, 1, 0 }, { 0, -1, -1 }, };
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
        byte n = (byte) (h > k ? 1 : 0);
        byte o = (byte) (h > k ? 0 : 1);

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

        String permsMacro = builder.createName("PERMS");

        builder.addBufferMacros(
            permsMacro,
            new TransformingBufferAccessor(builder.addConstant(permutations), index -> "((" + index + ") & 255)"));

        String code = """
            float grad$funcName(int hash, vec2 pos, float distance) {
              const int GX[12] = int[12](1,-1,1,-1,1,-1,1,-1,0,0,0,0);
              const int GY[12] = int[12](1,1,-1,-1,0,0,0,0,1,-1,1,-1);

              float d = distance - dot(pos, pos);
              float d4 = d * d * d * d;
              float f = d4 * (float(GX[hash % 12]) * pos.x + float(GY[hash % 12] * pos.y));

              return d < 0.0f ? 0.0f : f;
            }

            float $funcName(float px, float py) {
              float d = (px + py) * $skew;
              int i = int(floor(px + d));
              int j = int(floor(py + d));
              float e = float(i + j) * $unskew;
              float f = float(i) - e;
              float g = float(j) - e;
              float h = px - f;
              float k = py - g;
              int n = h > k ? 1 : 0;
              int o = h > k ? 0 : 1;

              float p = h - float(n) + $unskew;
              float q = k - float(o) + $unskew;
              float r = h - 1.0f + 2.0f * $unskew;
              float s = k - 1.0f + 2.0f * $unskew;
              int t = i & 255;
              int u = j & 255;
              int v = $perms(t + $perms(u)) % 12;
              int w = $perms(t + n + $perms(u + o)) % 12;
              int z = $perms(t + 1 + $perms(u + 1)) % 12;
              float aa = grad$funcName(v, vec2(h, k), 0.5f);
              float ab = grad$funcName(w, vec2(p, q), 0.5f);
              float ac = grad$funcName(z, vec2(r. s), 0.5f);
              return 70.0f * (aa + ab + ac);
            }
            """.replaceAll("\\$funcName", funcName)
            .replaceAll("\\$skew", Float.toString((float) SKEW_FACTOR_2D))
            .replaceAll("\\$unskew", Float.toString((float) UNSKEW_FACTOR_2D))
            .replaceAll("\\$perms", "GET_" + permsMacro);

        builder.preamble.append(code);

        String result = builder.createName("simplex");
        builder.logic.append("  float ")
            .append(result)
            .append(" = ")
            .append(funcName)
            .append("(")
            .append(x)
            .append(", ")
            .append(y)
            .append(");\n");
        return result;
    }

    @Override
    public String compileKernel3D(KernelBuilder builder, String x, String y, String z) {
        throw new UnsupportedOperationException("Not yet implemented");
    }
}
