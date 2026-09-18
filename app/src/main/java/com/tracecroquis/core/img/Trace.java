package com.tracecroquis.core.img;

import java.util.ArrayList;
import java.util.List;

/** Extraction de polylignes a partir d'un squelette. */
public final class Trace {

    /** Une polyligne du squelette, avec l'epaisseur de trait mesuree. */
    public static final class Stroke {
        public final List<double[]> pts = new ArrayList<>();
        public double thickness = 1;
        public boolean closed = false;
        public int component = -1;

        public double length() {
            double l = 0;
            for (int i = 1; i < pts.size(); i++) {
                l += Math.hypot(pts.get(i)[0] - pts.get(i - 1)[0], pts.get(i)[1] - pts.get(i - 1)[1]);
            }
            return l;
        }
    }

    /** Degre du pixel d'extremite d'une polyligne dans le squelette. */
    public static int endDegree(Mask skel, Stroke s, boolean first) {
        double[] p = first ? s.pts.get(0) : s.pts.get(s.pts.size() - 1);
        return Thin.neighbours(skel, (int) Math.round(p[0]), (int) Math.round(p[1]));
    }

    /**
     * Supprime les barbules : courtes branches nees d'une intersection et
     * s'achevant dans le vide (bruit des traits repasses plusieurs fois).
     *
     * @return vrai si le squelette a ete modifie
     */
    public static boolean pruneSpurs(Mask skel, List<Stroke> strokes, double maxLen) {
        boolean changed = false;
        for (Stroke s : strokes) {
            if (s.closed || s.length() > maxLen) continue;
            int d1 = endDegree(skel, s, true);
            int d2 = endDegree(skel, s, false);
            boolean spur = (d1 >= 3 && d2 == 1) || (d2 >= 3 && d1 == 1);
            if (!spur) continue;
            for (int i = 0; i < s.pts.size(); i++) {
                double[] p = s.pts.get(i);
                int x = (int) Math.round(p[0]), y = (int) Math.round(p[1]);
                if (Thin.neighbours(skel, x, y) >= 3) continue;   // garde le noeud
                skel.set(x, y, false);
                changed = true;
            }
        }
        return changed;
    }

    private static final int[] DX = {1, 1, 0, -1, -1, -1, 0, 1};
    private static final int[] DY = {0, 1, 1, 1, 0, -1, -1, -1};

    private Trace() { }

    /**
     * Parcourt le squelette et renvoie les polylignes elementaires
     * (coupees a chaque noeud : extremite ou intersection).
     */
    public static List<Stroke> strokes(Mask skel, int[] dt, int[] compLabels) {
        int w = skel.w, h = skel.h;
        byte[] deg = new byte[w * h];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int i = y * w + x;
                if (skel.px[i]) deg[i] = (byte) Thin.neighbours(skel, x, y);
            }
        }
        boolean[] used = new boolean[w * h];
        List<Stroke> out = new ArrayList<>();

        // 1) Chemins partant des noeuds (degre != 2).
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int i = y * w + x;
                if (!skel.px[i] || deg[i] == 2) continue;
                for (int d = 0; d < 8; d++) {
                    int nx = x + DX[d], ny = y + DY[d];
                    if (!skel.get(nx, ny)) continue;
                    int ni = ny * w + nx;
                    if (used[ni] && deg[ni] == 2) continue;
                    Stroke s = walk(skel, deg, used, dt, compLabels, x, y, nx, ny);
                    if (s != null && s.pts.size() >= 2) out.add(s);
                }
                used[i] = true;
            }
        }

        // 2) Boucles fermees restantes (tous les pixels de degre 2).
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int i = y * w + x;
                if (!skel.px[i] || used[i] || deg[i] != 2) continue;
                int nx = -1, ny = -1;
                for (int d = 0; d < 8; d++) {
                    if (skel.get(x + DX[d], y + DY[d])) { nx = x + DX[d]; ny = y + DY[d]; break; }
                }
                if (nx < 0) continue;
                Stroke s = walk(skel, deg, used, dt, compLabels, x, y, nx, ny);
                if (s != null && s.pts.size() >= 3) {
                    s.closed = true;
                    out.add(s);
                }
                used[i] = true;
            }
        }
        return out;
    }

    private static Stroke walk(Mask skel, byte[] deg, boolean[] used, int[] dt, int[] comp,
                               int sx, int sy, int fx, int fy) {
        int w = skel.w;
        Stroke s = new Stroke();
        s.pts.add(new double[]{sx, sy});
        int px = sx, py = sy, cx = fx, cy = fy;
        double thick = 0;
        int n = 0;
        int guard = 0;
        while (guard++ < 1_000_000) {
            int ci = cy * w + cx;
            s.pts.add(new double[]{cx, cy});
            if (comp != null && s.component < 0) s.component = comp[ci];
            if (dt != null) { thick += 2.0 * dt[ci]; n++; }
            if (deg[ci] != 2) break;
            used[ci] = true;
            int nx = -1, ny = -1;
            for (int d = 0; d < 8; d++) {
                int ax = cx + DX[d], ay = cy + DY[d];
                if (!skel.get(ax, ay)) continue;
                if (ax == px && ay == py) continue;
                int ai = ay * w + ax;
                if (used[ai] && deg[ai] == 2) continue;
                nx = ax; ny = ay;
                break;
            }
            if (nx < 0) break;
            px = cx; py = cy; cx = nx; cy = ny;
        }
        s.thickness = n > 0 ? thick / n : 1;
        return s;
    }
}
