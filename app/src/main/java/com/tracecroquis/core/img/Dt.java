package com.tracecroquis.core.img;

/** Carte de distance chanfrein (distance de chaque pixel d'encre au fond). */
public final class Dt {

    private Dt() { }

    /** Distances entieres approchees (1 pour orthogonal, ~1.41 pour diagonal, x1). */
    public static int[] compute(Mask m) {
        int w = m.w, h = m.h;
        final int INF = 1 << 20;
        int[] d = new int[w * h];
        // Chanfrein 5-7 (echelle 5).
        final int A = 5, B = 7;
        for (int i = 0; i < d.length; i++) d[i] = m.px[i] ? INF : 0;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int i = y * w + x;
                if (d[i] == 0) continue;
                int best = d[i];
                if (x > 0) best = Math.min(best, d[i - 1] + A);
                if (y > 0) best = Math.min(best, d[i - w] + A);
                if (x > 0 && y > 0) best = Math.min(best, d[i - w - 1] + B);
                if (x < w - 1 && y > 0) best = Math.min(best, d[i - w + 1] + B);
                d[i] = best;
            }
        }
        for (int y = h - 1; y >= 0; y--) {
            for (int x = w - 1; x >= 0; x--) {
                int i = y * w + x;
                if (d[i] == 0) continue;
                int best = d[i];
                if (x < w - 1) best = Math.min(best, d[i + 1] + A);
                if (y < h - 1) best = Math.min(best, d[i + w] + A);
                if (x < w - 1 && y < h - 1) best = Math.min(best, d[i + w + 1] + B);
                if (x > 0 && y < h - 1) best = Math.min(best, d[i + w - 1] + B);
                d[i] = best;
            }
        }
        for (int i = 0; i < d.length; i++) d[i] = d[i] >= INF ? 0 : (d[i] + 2) / A;
        return d;
    }
}
