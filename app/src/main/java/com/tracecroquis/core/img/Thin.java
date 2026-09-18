package com.tracecroquis.core.img;

/** Squelettisation Zhang-Suen. */
public final class Thin {

    private Thin() { }

    public static Mask skeleton(Mask src) {
        Mask m = src.copy();
        int w = m.w, h = m.h;
        boolean changed = true;
        int guard = 0;
        int[] mark = new int[w * h];
        while (changed && guard++ < 200) {
            changed = false;
            for (int step = 0; step < 2; step++) {
                int marked = 0;
                for (int y = 1; y < h - 1; y++) {
                    for (int x = 1; x < w - 1; x++) {
                        int i = y * w + x;
                        if (!m.px[i]) continue;
                        boolean p2 = m.px[i - w], p3 = m.px[i - w + 1], p4 = m.px[i + 1];
                        boolean p5 = m.px[i + w + 1], p6 = m.px[i + w], p7 = m.px[i + w - 1];
                        boolean p8 = m.px[i - 1], p9 = m.px[i - w - 1];
                        int b = (p2 ? 1 : 0) + (p3 ? 1 : 0) + (p4 ? 1 : 0) + (p5 ? 1 : 0)
                                + (p6 ? 1 : 0) + (p7 ? 1 : 0) + (p8 ? 1 : 0) + (p9 ? 1 : 0);
                        if (b < 2 || b > 6) continue;
                        int a = 0;
                        boolean[] seq = {p2, p3, p4, p5, p6, p7, p8, p9, p2};
                        for (int k = 0; k < 8; k++) if (!seq[k] && seq[k + 1]) a++;
                        if (a != 1) continue;
                        if (step == 0) {
                            if (p2 && p4 && p6) continue;
                            if (p4 && p6 && p8) continue;
                        } else {
                            if (p2 && p4 && p8) continue;
                            if (p2 && p6 && p8) continue;
                        }
                        mark[marked++] = i;
                    }
                }
                for (int k = 0; k < marked; k++) m.px[mark[k]] = false;
                if (marked > 0) changed = true;
            }
        }
        return m;
    }

    public static int neighbours(Mask m, int x, int y) {
        int n = 0;
        for (int dy = -1; dy <= 1; dy++) {
            for (int dx = -1; dx <= 1; dx++) {
                if (dx == 0 && dy == 0) continue;
                if (m.get(x + dx, y + dy)) n++;
            }
        }
        return n;
    }
}
