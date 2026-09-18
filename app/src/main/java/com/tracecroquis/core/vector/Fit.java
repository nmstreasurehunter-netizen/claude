package com.tracecroquis.core.vector;

import com.tracecroquis.core.geom.G;
import com.tracecroquis.core.img.Trace;

import java.util.ArrayList;
import java.util.List;

/** Conversion des polylignes du squelette en segments de droite et arcs de cercle. */
public final class Fit {

    /** Resultat de l'ajustement d'une polyligne. */
    public static final class Result {
        public final List<Seg> segs = new ArrayList<>();
        public final List<Arc> arcs = new ArrayList<>();
    }

    private Fit() { }

    /**
     * Ajuste une polyligne : decoupe aux angles vifs, puis reconnait
     * sur chaque morceau un arc de cercle (battant de porte) ou des segments.
     *
     * @param tol      tolerance de rectitude (px)
     * @param minArcR  rayon minimal accepte pour un arc (px)
     * @param maxArcR  rayon maximal accepte pour un arc (px)
     */
    public static Result fit(Trace.Stroke s, int index, double tol, double minArcR, double maxArcR) {
        Result r = new Result();
        List<double[]> pts = s.pts;
        if (pts.size() < 2) return r;
        for (int[] part : splitAtCorners(pts, Math.max(tol, 1.0))) {
            fitPart(s, index, pts, part[0], part[1], tol, minArcR, maxArcR, r);
        }
        mergeChain(r.segs, Math.toRadians(7), tol);
        return r;
    }

    /** Decoupe la polyligne aux sommets ou la direction change brutalement. */
    public static List<int[]> splitAtCorners(List<double[]> pts, double eps) {
        List<int[]> parts = new ArrayList<>();
        int n = pts.size();
        if (n < 3) {
            parts.add(new int[]{0, n - 1});
            return parts;
        }
        List<double[]> simp = G.simplify(pts, eps);
        int[] idx = mapIndices(pts, simp);
        List<Integer> cuts = new ArrayList<>();
        cuts.add(0);
        for (int i = 1; i + 1 < simp.size(); i++) {
            double[] a = simp.get(i - 1), b = simp.get(i), c = simp.get(i + 1);
            double a1 = Math.atan2(b[1] - a[1], b[0] - a[0]);
            double a2 = Math.atan2(c[1] - b[1], c[0] - b[0]);
            double turn = Math.abs(G.angleDelta(a1, a2));
            double l1 = G.dist(a[0], a[1], b[0], b[1]);
            double l2 = G.dist(b[0], b[1], c[0], c[1]);
            if (turn > Math.toRadians(42) && Math.min(l1, l2) > eps * 1.5) cuts.add(idx[i]);
        }
        cuts.add(n - 1);
        for (int i = 0; i + 1 < cuts.size(); i++) {
            int a = cuts.get(i), b = cuts.get(i + 1);
            if (b > a) parts.add(new int[]{a, b});
        }
        if (parts.isEmpty()) parts.add(new int[]{0, n - 1});
        return parts;
    }

    private static void fitPart(Trace.Stroke s, int index, List<double[]> pts, int from, int to,
                                double tol, double minArcR, double maxArcR, Result r) {
        int count = to - from + 1;
        if (count < 2) return;
        List<double[]> sub = pts.subList(from, to + 1);
        double len = G.polylineLength(sub);
        if (len < 2.5) return;

        // --- arc de cercle -------------------------------------------------
        if (count >= 7 && len > minArcR * 0.8) {
            double turn = totalTurn(sub);
            double chord = G.dist(sub.get(0)[0], sub.get(0)[1],
                    sub.get(sub.size() - 1)[0], sub.get(sub.size() - 1)[1]);
            double bulge = maxDeviation(sub);
            if (turn > Math.toRadians(30) && turn < Math.toRadians(330) && bulge > Math.max(2.0, tol * 1.6)) {
                double[] c = G.fitCircle(sub, 0, sub.size() - 1);
                if (c != null && c[2] >= minArcR && c[2] <= maxArcR
                        && c[3] < Math.max(1.8, c[2] * 0.10) && chord < c[2] * 2.2) {
                    Arc a = new Arc();
                    a.cx = c[0]; a.cy = c[1]; a.r = c[2]; a.rms = c[3];
                    a.a1 = Math.atan2(sub.get(0)[1] - a.cy, sub.get(0)[0] - a.cx);
                    a.a2 = Math.atan2(sub.get(sub.size() - 1)[1] - a.cy,
                            sub.get(sub.size() - 1)[0] - a.cx);
                    a.sweep = Math.abs(G.angleDelta(a.a1, a.a2));
                    a.thickness = s.thickness;
                    a.stroke = index;
                    if (a.sweep > Math.toRadians(30)) {
                        r.arcs.add(a);
                        return;
                    }
                }
            }
        }

        // --- segments ------------------------------------------------------
        List<double[]> simp = G.simplify(sub, Math.max(tol, 1.0));
        if (simp.size() < 2) return;
        int[] idx = mapIndices(sub, simp);
        for (int i = 0; i + 1 < simp.size(); i++) {
            int i0 = idx[i], i1 = idx[i + 1];
            Seg seg = new Seg();
            double[] line = i1 > i0 + 1 ? G.fitLine(sub, i0, i1) : null;
            if (line != null) {
                double mx = line[0], my = line[1], dx = line[2], dy = line[3];
                double t0 = (sub.get(i0)[0] - mx) * dx + (sub.get(i0)[1] - my) * dy;
                double t1 = (sub.get(i1)[0] - mx) * dx + (sub.get(i1)[1] - my) * dy;
                seg.x1 = mx + t0 * dx; seg.y1 = my + t0 * dy;
                seg.x2 = mx + t1 * dx; seg.y2 = my + t1 * dy;
                seg.residual = line[4];
            } else {
                seg.x1 = simp.get(i)[0]; seg.y1 = simp.get(i)[1];
                seg.x2 = simp.get(i + 1)[0]; seg.y2 = simp.get(i + 1)[1];
            }
            seg.thickness = s.thickness;
            seg.stroke = index;
            seg.component = s.component;
            if (seg.len() >= 2) r.segs.add(seg);
        }
    }

    /** Fusionne les segments consecutifs quasi colineaires d'une meme polyligne. */
    private static void mergeChain(List<Seg> segs, double angTol, double tol) {
        boolean merged = true;
        while (merged) {
            merged = false;
            for (int i = 0; i + 1 < segs.size(); i++) {
                Seg a = segs.get(i), b = segs.get(i + 1);
                if (G.dist(a.x2, a.y2, b.x1, b.y1) > tol + 1) continue;
                if (G.angleDiff180(a.ang(), b.ang()) > angTol) continue;
                if (G.distPointLine(b.x2, b.y2, a.x1, a.y1, a.x2, a.y2) > Math.max(tol, 1.5)) continue;
                a.x2 = b.x2; a.y2 = b.y2;
                a.thickness = (a.thickness + b.thickness) / 2;
                segs.remove(i + 1);
                merged = true;
                break;
            }
        }
    }

    private static int[] mapIndices(List<double[]> pts, List<double[]> simp) {
        int[] idx = new int[simp.size()];
        int k = 0;
        for (int i = 0; i < simp.size(); i++) {
            double[] target = simp.get(i);
            int best = k;
            double bd = Double.MAX_VALUE;
            for (int j = k; j < pts.size(); j++) {
                double d = G.dist2(pts.get(j)[0], pts.get(j)[1], target[0], target[1]);
                if (d < bd) { bd = d; best = j; }
                if (d == 0) break;
            }
            idx[i] = best;
            k = best;
        }
        idx[simp.size() - 1] = pts.size() - 1;
        return idx;
    }

    /** Somme des changements de direction le long de la polyligne. */
    public static double totalTurn(List<double[]> pts) {
        double turn = 0;
        int step = Math.max(1, pts.size() / 24);
        double prev = Double.NaN;
        for (int i = step; i < pts.size(); i += step) {
            double[] a = pts.get(i - step), b = pts.get(i);
            double ang = Math.atan2(b[1] - a[1], b[0] - a[0]);
            if (!Double.isNaN(prev)) turn += Math.abs(G.angleDelta(prev, ang));
            prev = ang;
        }
        return turn;
    }

    /** Fleche maximale de la polyligne par rapport a sa corde. */
    public static double maxDeviation(List<double[]> pts) {
        double[] a = pts.get(0), b = pts.get(pts.size() - 1);
        double max = 0;
        for (double[] p : pts) {
            max = Math.max(max, G.distPointSeg(p[0], p[1], a[0], a[1], b[0], b[1]));
        }
        return max;
    }
}
