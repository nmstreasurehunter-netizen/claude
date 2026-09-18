package com.tracecroquis.core.img;

import java.util.ArrayDeque;

/** Composantes connexes (8-connexite). */
public final class Cc {

    public static final class Comp {
        public int id;
        public int area;
        public int minX, minY, maxX, maxY;
        public double cx, cy;
        /** Epaisseur de trait mediane (issue de la carte de distance), en pixels. */
        public double strokeWidth = 1;

        public int width() { return maxX - minX + 1; }
        public int height() { return maxY - minY + 1; }
        public double aspect() {
            return width() / (double) Math.max(1, height());
        }
        public double fill() {
            return area / (double) Math.max(1, width() * height());
        }
    }

    public final int[] labels;      // 0 = fond, sinon index+1 dans comps
    public final Comp[] comps;
    public final int w, h;

    private Cc(int[] labels, Comp[] comps, int w, int h) {
        this.labels = labels; this.comps = comps; this.w = w; this.h = h;
    }

    public static Cc label(Mask m) {
        int w = m.w, h = m.h;
        int[] labels = new int[w * h];
        java.util.List<Comp> list = new java.util.ArrayList<>();
        ArrayDeque<Integer> stack = new ArrayDeque<>();
        int next = 0;
        for (int i = 0; i < w * h; i++) {
            if (!m.px[i] || labels[i] != 0) continue;
            next++;
            Comp c = new Comp();
            c.id = next;
            c.minX = c.minY = Integer.MAX_VALUE;
            c.maxX = c.maxY = Integer.MIN_VALUE;
            long sx = 0, sy = 0;
            stack.push(i);
            labels[i] = next;
            while (!stack.isEmpty()) {
                int p = stack.pop();
                int x = p % w, y = p / w;
                c.area++;
                sx += x; sy += y;
                if (x < c.minX) c.minX = x;
                if (x > c.maxX) c.maxX = x;
                if (y < c.minY) c.minY = y;
                if (y > c.maxY) c.maxY = y;
                for (int dy = -1; dy <= 1; dy++) {
                    int yy = y + dy;
                    if (yy < 0 || yy >= h) continue;
                    for (int dx = -1; dx <= 1; dx++) {
                        int xx = x + dx;
                        if (xx < 0 || xx >= w || (dx == 0 && dy == 0)) continue;
                        int q = yy * w + xx;
                        if (m.px[q] && labels[q] == 0) {
                            labels[q] = next;
                            stack.push(q);
                        }
                    }
                }
            }
            c.cx = sx / (double) c.area;
            c.cy = sy / (double) c.area;
            list.add(c);
        }
        return new Cc(labels, list.toArray(new Comp[0]), w, h);
    }

    /** Renseigne strokeWidth a partir d'une carte de distance (x2 = epaisseur). */
    public void measureStrokes(int[] dt) {
        int[][] hist = new int[comps.length][];
        int[] counts = new int[comps.length];
        for (int i = 0; i < comps.length; i++) hist[i] = new int[64];
        for (int i = 0; i < labels.length; i++) {
            int l = labels[i];
            if (l == 0) continue;
            int d = Math.min(63, dt[i]);
            hist[l - 1][d]++;
            counts[l - 1]++;
        }
        for (int i = 0; i < comps.length; i++) {
            // Epaisseur = 2 x quantile haut de la distance au fond.
            int target = (int) (counts[i] * 0.90);
            int acc = 0, q = 1;
            for (int d = 0; d < 64; d++) {
                acc += hist[i][d];
                if (acc >= target) { q = d; break; }
            }
            comps[i].strokeWidth = Math.max(1, 2.0 * q - 1);
        }
    }

    public Mask maskOf(int compIndex) {
        Mask m = new Mask(w, h);
        int id = compIndex + 1;
        for (int i = 0; i < labels.length; i++) if (labels[i] == id) m.px[i] = true;
        return m;
    }
}
