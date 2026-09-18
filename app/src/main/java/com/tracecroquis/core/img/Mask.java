package com.tracecroquis.core.img;

/** Masque binaire (true = encre). */
public final class Mask {

    public final int w, h;
    public final boolean[] px;

    public Mask(int w, int h) {
        this.w = w; this.h = h;
        this.px = new boolean[w * h];
    }

    public boolean get(int x, int y) {
        return x >= 0 && y >= 0 && x < w && y < h && px[y * w + x];
    }

    public void set(int x, int y, boolean v) {
        if (x < 0 || y < 0 || x >= w || y >= h) return;
        px[y * w + x] = v;
    }

    public int count() {
        int c = 0;
        for (boolean b : px) if (b) c++;
        return c;
    }

    public Mask copy() {
        Mask m = new Mask(w, h);
        System.arraycopy(px, 0, m.px, 0, px.length);
        return m;
    }

    public Mask dilate(int r) {
        Mask out = new Mask(w, h);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                if (!px[y * w + x]) continue;
                for (int dy = -r; dy <= r; dy++) {
                    int yy = y + dy;
                    if (yy < 0 || yy >= h) continue;
                    for (int dx = -r; dx <= r; dx++) {
                        int xx = x + dx;
                        if (xx < 0 || xx >= w) continue;
                        out.px[yy * w + xx] = true;
                    }
                }
            }
        }
        return out;
    }

    public Mask erode(int r) {
        Mask out = new Mask(w, h);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                boolean keep = true;
                for (int dy = -r; dy <= r && keep; dy++) {
                    for (int dx = -r; dx <= r; dx++) {
                        if (!get(x + dx, y + dy)) { keep = false; break; }
                    }
                }
                out.px[y * w + x] = keep;
            }
        }
        return out;
    }

    /** Fermeture morphologique : soude les traits repetes d'un croquis a main levee. */
    public Mask close(int r) {
        if (r <= 0) return this;
        return dilate(r).erode(r);
    }

    /** Fraction d'encre le long d'un segment, echantillonnee avec une tolerance laterale. */
    public double inkRatio(double x1, double y1, double x2, double y2, int lateral) {
        double len = Math.hypot(x2 - x1, y2 - y1);
        int steps = (int) Math.max(2, len);
        int hit = 0;
        double nx = -(y2 - y1) / Math.max(1e-6, len), ny = (x2 - x1) / Math.max(1e-6, len);
        for (int i = 0; i <= steps; i++) {
            double t = i / (double) steps;
            double x = x1 + (x2 - x1) * t, y = y1 + (y2 - y1) * t;
            boolean found = false;
            for (int l = -lateral; l <= lateral && !found; l++) {
                if (get((int) Math.round(x + nx * l), (int) Math.round(y + ny * l))) found = true;
            }
            if (found) hit++;
        }
        return hit / (double) (steps + 1);
    }
}
