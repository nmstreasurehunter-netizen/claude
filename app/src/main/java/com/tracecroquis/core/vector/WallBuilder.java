package com.tracecroquis.core.vector;

import com.tracecroquis.core.geom.G;
import com.tracecroquis.core.img.Mask;
import com.tracecroquis.core.model.Plan;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Construction des murs : appariement des traits doubles, assemblage du graphe,
 * nettoyage et orthogonalisation.
 */
public final class WallBuilder {

    /** Mur candidat, avant insertion dans le graphe. */
    public static class Cand {
        public double x1, y1, x2, y2;
        public double thicknessPx;
        public boolean doublet;          // issu de deux traits paralleles
        public double score = 1;

        public double len() { return Math.hypot(x2 - x1, y2 - y1); }
    }

    private WallBuilder() { }

    // ------------------------------------------------------------------ 1/3

    /** Apparie les traits paralleles et produit les murs candidats. */
    public static List<Cand> candidates(List<Seg> segs, Mask ink, VectorOptions o, double minDim) {
        double minLen = Math.max(8, o.minWallLenFrac * minDim);
        double maxGap = Math.max(4, o.doubleWallMaxGapFrac * minDim);
        double angTol = Math.toRadians(o.parallelTolDeg);

        List<Cand> out = new ArrayList<>();
        List<Seg> pool = new ArrayList<>();
        for (Seg s : segs) {
            if (s.consumed) continue;
            if (s.band) {
                // Bande pleine : l'axe et l'epaisseur sont mesures directement.
                if (s.len() >= minLen * 0.7) {
                    Cand c = new Cand();
                    c.x1 = s.x1; c.y1 = s.y1; c.x2 = s.x2; c.y2 = s.y2;
                    c.thicknessPx = Math.max(2, s.thickness);
                    c.doublet = true;
                    c.score = s.len() * 1.5;
                    out.add(c);
                    s.consumed = true;
                }
                continue;
            }
            if (s.len() >= minLen * 0.6) pool.add(s);
        }

        // --- recherche des paires paralleles ------------------------------
        List<double[]> pairs = new ArrayList<>();   // i, j, gap, score
        for (int i = 0; i < pool.size(); i++) {
            Seg a = pool.get(i);
            for (int j = i + 1; j < pool.size(); j++) {
                Seg b = pool.get(j);
                if (G.angleDiff180(a.ang(), b.ang()) > angTol) continue;
                double ov = G.overlapRatio(a.x1, a.y1, a.x2, a.y2, b.x1, b.y1, b.x2, b.y2);
                if (ov < 0.5) continue;
                double d1 = G.distPointLine(b.midX(), b.midY(), a.x1, a.y1, a.x2, a.y2);
                double d2 = G.distPointLine(a.midX(), a.midY(), b.x1, b.y1, b.x2, b.y2);
                double gap = (d1 + d2) / 2;
                if (gap < 1.5 || gap > maxGap) continue;
                // Coherence : les deux traits doivent rester paralleles sur toute leur longueur.
                double de1 = G.distPointLine(b.x1, b.y1, a.x1, a.y1, a.x2, a.y2);
                double de2 = G.distPointLine(b.x2, b.y2, a.x1, a.y1, a.x2, a.y2);
                if (Math.abs(de1 - de2) > Math.max(3, gap * 0.6)) continue;
                double score = ov * Math.min(a.len(), b.len()) / (1 + gap * 0.15);
                pairs.add(new double[]{i, j, gap, score});
            }
        }

        // Epaisseur dominante : mediane des ecartements candidats.
        double medianGap = 0;
        if (!pairs.isEmpty()) {
            List<Double> gaps = new ArrayList<>();
            for (double[] p : pairs) gaps.add(p[2]);
            medianGap = G.median(gaps);
        }

        Collections.sort(pairs, new Comparator<double[]>() {
            @Override public int compare(double[] a, double[] b) { return Double.compare(b[3], a[3]); }
        });

        boolean[] taken = new boolean[pool.size()];
        for (double[] p : pairs) {
            int i = (int) p[0], j = (int) p[1];
            if (taken[i] || taken[j]) continue;
            double gap = p[2];
            // Rejette les ecartements tres superieurs a l'epaisseur dominante
            // (deux murs de part et d'autre d'un couloir, par exemple).
            if (medianGap > 0 && gap > Math.max(maxGap * 0.45, medianGap * 2.6)) continue;
            Seg a = pool.get(i), b = pool.get(j);
            Cand c = merge(a, b);
            c.thicknessPx = gap;
            c.doublet = true;
            c.score = p[3];
            if (c.len() >= minLen) {
                out.add(c);
                taken[i] = taken[j] = true;
                a.consumed = b.consumed = true;
            }
        }

        // --- traits simples restants --------------------------------------
        for (int i = 0; i < pool.size(); i++) {
            if (taken[i]) continue;
            Seg s = pool.get(i);
            if (s.len() < minLen) continue;
            Cand c = new Cand();
            c.x1 = s.x1; c.y1 = s.y1; c.x2 = s.x2; c.y2 = s.y2;
            c.thicknessPx = Math.max(1.5, s.thickness);
            c.doublet = false;
            c.score = s.len();
            out.add(c);
            s.consumed = true;
        }
        return out;
    }

    /** Fusionne deux traits paralleles en un axe de mur. */
    private static Cand merge(Seg a, Seg b) {
        Seg base = a.len() >= b.len() ? a : b;
        double dx = base.dirX(), dy = base.dirY();
        double ox = (a.midX() + b.midX()) / 2, oy = (a.midY() + b.midY()) / 2;
        double[] ts = new double[4];
        double[][] pts = {{a.x1, a.y1}, {a.x2, a.y2}, {b.x1, b.y1}, {b.x2, b.y2}};
        for (int i = 0; i < 4; i++) ts[i] = (pts[i][0] - ox) * dx + (pts[i][1] - oy) * dy;
        double tmin = Math.min(Math.min(ts[0], ts[1]), Math.min(ts[2], ts[3]));
        double tmax = Math.max(Math.max(ts[0], ts[1]), Math.max(ts[2], ts[3]));
        Cand c = new Cand();
        c.x1 = ox + tmin * dx; c.y1 = oy + tmin * dy;
        c.x2 = ox + tmax * dx; c.y2 = oy + tmax * dy;
        return c;
    }

    // ------------------------------------------------------------------ 2/3

    /** Insere les candidats dans le graphe du plan (fusion, jonctions, prolongements). */
    public static void assemble(Plan plan, List<Cand> cands, VectorOptions o, double minDim) {
        double snap = Math.max(4, o.snapTolFrac * minDim);
        double extend = Math.max(6, o.extendFrac * minDim);

        for (Cand c : cands) {
            int a = findOrAdd(plan, c.x1, c.y1, snap);
            int b = findOrAdd(plan, c.x2, c.y2, snap);
            if (a == b) continue;
            if (hasWall(plan, a, b)) continue;
            Plan.Wall w = new Plan.Wall(a, b);
            w.thicknessPx = c.thicknessPx;
            w.confidence = c.doublet ? 1.0 : 0.75;
            plan.walls.add(w);
        }

        extendDangling(plan, extend, snap);
        splitAtJunctions(plan, snap);
        healDangling(plan, Math.max(snap, 0.035 * minDim));
        weld(plan, Math.max(2, snap * 0.5));
        splitAtJunctions(plan, snap);
        mergeCollinear(plan, Math.toRadians(8), snap);
        dedupe(plan);
        removeStubs(plan, Math.max(6, o.minWallLenFrac * minDim * 0.6));
        splitAtJunctions(plan, snap * 0.8);
        weld(plan, Math.max(2, snap * 0.35));
        dedupe(plan);
    }

    private static int findOrAdd(Plan plan, double x, double y, double tol) {
        int best = -1;
        double bd = tol;
        for (int i = 0; i < plan.nodes.size(); i++) {
            Plan.Node n = plan.nodes.get(i);
            double d = G.dist(n.x, n.y, x, y);
            if (d < bd) { bd = d; best = i; }
        }
        if (best >= 0) return best;
        return plan.addNode(x, y);
    }

    private static boolean hasWall(Plan plan, int a, int b) {
        for (Plan.Wall w : plan.walls) {
            if ((w.a == a && w.b == b) || (w.a == b && w.b == a)) return true;
        }
        return false;
    }

    /** Prolonge les extremites libres pour fermer les angles du croquis. */
    private static void extendDangling(Plan plan, double maxExtend, double tol) {
        for (int pass = 0; pass < 2; pass++) {
            int[] deg = degrees(plan);
            for (int wi = 0; wi < plan.walls.size(); wi++) {
                Plan.Wall w = plan.walls.get(wi);
                for (int end = 0; end < 2; end++) {
                    int ni = end == 0 ? w.a : w.b;
                    if (deg[ni] != 1) continue;
                    Plan.Node n = plan.nodes.get(ni);
                    Plan.Node other = plan.nodes.get(end == 0 ? w.b : w.a);
                    double dx = n.x - other.x, dy = n.y - other.y;
                    double len = Math.hypot(dx, dy);
                    if (len < 1e-6) continue;
                    dx /= len; dy /= len;
                    double[] hit = rayHit(plan, wi, n.x, n.y, dx, dy, maxExtend, tol);
                    if (hit != null) {
                        n.x = hit[0];
                        n.y = hit[1];
                    }
                }
            }
        }
    }

    /** Cherche le premier mur touche par le rayon (hors murs incidents). */
    private static double[] rayHit(Plan plan, int selfIndex, double x, double y,
                                   double dx, double dy, double maxDist, double tol) {
        double bestT = Double.MAX_VALUE;
        double[] best = null;
        for (int i = 0; i < plan.walls.size(); i++) {
            if (i == selfIndex) continue;
            Plan.Wall w = plan.walls.get(i);
            Plan.Node p = plan.nodes.get(w.a), q = plan.nodes.get(w.b);
            double[] inter = G.lineIntersection(x, y, x + dx, y + dy, p.x, p.y, q.x, q.y);
            if (inter == null) continue;
            double t = (inter[0] - x) * dx + (inter[1] - y) * dy;
            if (t < -tol || t > maxDist) continue;
            // Le point doit tomber sur le mur cible (avec une petite tolerance).
            double[] pr = G.project(inter[0], inter[1], p.x, p.y, q.x, q.y);
            if (pr[3] > tol) continue;
            if (t < bestT) { bestT = t; best = new double[]{inter[0], inter[1]}; }
        }
        return best;
    }

    /**
     * Recolle les extremites libres sur le mur le plus proche : referme les
     * angles et les jonctions en T laisses ouverts par le croquis.
     */
    private static void healDangling(Plan plan, double maxDist) {
        for (int pass = 0; pass < 2; pass++) {
            int[] deg = degrees(plan);
            for (int ni = 0; ni < plan.nodes.size(); ni++) {
                if (deg[ni] != 1) continue;
                Plan.Node n = plan.nodes.get(ni);
                double bd = maxDist;
                double[] best = null;
                for (int wi = 0; wi < plan.walls.size(); wi++) {
                    Plan.Wall w = plan.walls.get(wi);
                    if (w.a == ni || w.b == ni) continue;
                    Plan.Node p = plan.nodes.get(w.a), q = plan.nodes.get(w.b);
                    double[] pr = G.project(n.x, n.y, p.x, p.y, q.x, q.y);
                    if (pr[3] < bd) { bd = pr[3]; best = pr; }
                }
                if (best != null) {
                    n.x = best[0];
                    n.y = best[1];
                }
            }
        }
    }

    /** Fusionne les noeuds confondus (evite les faces degenerees). */
    public static void weld(Plan plan, double tol) {
        int n = plan.nodes.size();
        int[] map = new int[n];
        for (int i = 0; i < n; i++) map[i] = i;
        for (int i = 0; i < n; i++) {
            if (map[i] != i) continue;
            Plan.Node a = plan.nodes.get(i);
            for (int j = i + 1; j < n; j++) {
                if (map[j] != j) continue;
                Plan.Node b = plan.nodes.get(j);
                if (G.dist(a.x, a.y, b.x, b.y) <= tol) map[j] = i;
            }
        }
        for (Plan.Wall w : plan.walls) {
            w.a = map[w.a];
            w.b = map[w.b];
        }
        for (int i = plan.walls.size() - 1; i >= 0; i--) {
            if (plan.walls.get(i).a == plan.walls.get(i).b) plan.walls.remove(i);
        }
        compactNodes(plan);
    }

    /** Supprime les murs en double entre deux memes noeuds. */
    private static void dedupe(Plan plan) {
        for (int i = 0; i < plan.walls.size(); i++) {
            Plan.Wall a = plan.walls.get(i);
            if (a.a == a.b) { plan.walls.remove(i--); continue; }
            for (int j = i + 1; j < plan.walls.size(); j++) {
                Plan.Wall b = plan.walls.get(j);
                if ((a.a == b.a && a.b == b.b) || (a.a == b.b && a.b == b.a)) {
                    a.thicknessPx = Math.max(a.thicknessPx, b.thicknessPx);
                    plan.walls.remove(j--);
                }
            }
        }
    }

    /** Coupe les murs traverses par un noeud (jonctions en T). */
    private static void splitAtJunctions(Plan plan, double tol) {
        boolean changed = true;
        int guard = 0;
        while (changed && guard++ < 40) {
            changed = false;
            for (int ni = 0; ni < plan.nodes.size() && !changed; ni++) {
                Plan.Node n = plan.nodes.get(ni);
                for (int wi = 0; wi < plan.walls.size(); wi++) {
                    Plan.Wall w = plan.walls.get(wi);
                    if (w.a == ni || w.b == ni) continue;
                    Plan.Node p = plan.nodes.get(w.a), q = plan.nodes.get(w.b);
                    double len = G.dist(p.x, p.y, q.x, q.y);
                    if (len < 1e-6) continue;
                    double[] pr = G.project(n.x, n.y, p.x, p.y, q.x, q.y);
                    if (pr[3] > tol) continue;
                    double t = pr[2];
                    if (t * len < tol || (1 - t) * len < tol) continue;
                    Plan.Wall w2 = new Plan.Wall(ni, w.b);
                    w2.thicknessPx = w.thicknessPx;
                    w2.type = w.type;
                    w2.thicknessM = w.thicknessM;
                    w2.confidence = w.confidence;
                    w.b = ni;
                    plan.walls.add(w2);
                    changed = true;
                    break;
                }
            }
        }
    }

    /** Fusionne deux murs colineaires partageant un noeud de degre 2. */
    private static void mergeCollinear(Plan plan, double angTol, double tol) {
        boolean changed = true;
        int guard = 0;
        while (changed && guard++ < 200) {
            changed = false;
            int[] deg = degrees(plan);
            for (int ni = 0; ni < plan.nodes.size(); ni++) {
                if (deg[ni] != 2) continue;
                int i1 = -1, i2 = -1;
                for (int i = 0; i < plan.walls.size(); i++) {
                    Plan.Wall w = plan.walls.get(i);
                    if (w.a == ni || w.b == ni) {
                        if (i1 < 0) i1 = i; else if (i2 < 0) i2 = i;
                    }
                }
                if (i1 < 0 || i2 < 0) continue;
                Plan.Wall w1 = plan.walls.get(i1), w2 = plan.walls.get(i2);
                int f1 = w1.a == ni ? w1.b : w1.a;
                int f2 = w2.a == ni ? w2.b : w2.a;
                if (f1 == f2) continue;
                Plan.Node a = plan.nodes.get(f1), b = plan.nodes.get(f2), n = plan.nodes.get(ni);
                double ang1 = G.angle(a.x, a.y, n.x, n.y);
                double ang2 = G.angle(n.x, n.y, b.x, b.y);
                if (Math.abs(G.angleDelta(ang1, ang2)) > angTol) continue;
                if (Math.abs(w1.thicknessPx - w2.thicknessPx) > Math.max(3, w1.thicknessPx * 0.5)) continue;
                w1.a = f1; w1.b = f2;
                w1.thicknessPx = (w1.thicknessPx + w2.thicknessPx) / 2;
                plan.walls.remove(i2);
                changed = true;
                break;
            }
        }
    }

    /** Supprime les moignons isoles (bruit du croquis). */
    private static void removeStubs(Plan plan, double minLen) {
        boolean changed = true;
        int guard = 0;
        while (changed && guard++ < 50) {
            changed = false;
            int[] deg = degrees(plan);
            for (int i = 0; i < plan.walls.size(); i++) {
                Plan.Wall w = plan.walls.get(i);
                double len = plan.wallLengthPx(w);
                boolean dangling = deg[w.a] == 1 || deg[w.b] == 1;
                if (dangling && len < minLen) {
                    plan.walls.remove(i);
                    changed = true;
                    break;
                }
            }
        }
        compactNodes(plan);
    }

    public static int[] degrees(Plan plan) {
        int[] deg = new int[plan.nodes.size()];
        for (Plan.Wall w : plan.walls) {
            if (w.a < deg.length) deg[w.a]++;
            if (w.b < deg.length) deg[w.b]++;
        }
        return deg;
    }

    /** Supprime les noeuds orphelins et reindexe. */
    public static void compactNodes(Plan plan) {
        int[] map = new int[plan.nodes.size()];
        java.util.Arrays.fill(map, -1);
        for (Plan.Wall w : plan.walls) {
            map[w.a] = 0;
            map[w.b] = 0;
        }
        List<Plan.Node> kept = new ArrayList<>();
        for (int i = 0; i < plan.nodes.size(); i++) {
            if (map[i] == 0) {
                map[i] = kept.size();
                kept.add(plan.nodes.get(i));
            }
        }
        for (Plan.Wall w : plan.walls) {
            w.a = map[w.a];
            w.b = map[w.b];
        }
        plan.nodes.clear();
        plan.nodes.addAll(kept);
    }

    // ------------------------------------------------------------------ 3/3

    /** Redressement : alignement sur les axes dominants du croquis. */
    public static void orthogonalize(Plan plan, VectorOptions o, double minDim) {
        if (plan.walls.isEmpty()) return;
        double theta = dominantAngle(plan);
        double cos = Math.cos(-theta), sin = Math.sin(-theta);
        // Repere tourne
        double[][] rot = new double[plan.nodes.size()][2];
        for (int i = 0; i < plan.nodes.size(); i++) {
            Plan.Node n = plan.nodes.get(i);
            rot[i][0] = n.x * cos - n.y * sin;
            rot[i][1] = n.x * sin + n.y * cos;
        }
        double snapTol = Math.toRadians(o.angleSnapDeg);
        // 1) alignement iteratif des murs quasi axiaux
        for (int iter = 0; iter < 24; iter++) {
            double[][] acc = new double[plan.nodes.size()][2];
            int[] cnt = new int[plan.nodes.size()];
            for (Plan.Wall w : plan.walls) {
                double dx = rot[w.b][0] - rot[w.a][0];
                double dy = rot[w.b][1] - rot[w.a][1];
                double ang = Math.atan2(dy, dx);
                double m90 = nearestAxis(ang);
                if (Math.abs(G.angleDelta(ang, m90)) > snapTol) continue;
                boolean horizontal = Math.abs(Math.cos(m90)) > 0.5;
                if (horizontal) {
                    double y = (rot[w.a][1] + rot[w.b][1]) / 2;
                    acc[w.a][1] += y; cnt[w.a]++;
                    acc[w.b][1] += y; cnt[w.b]++;
                    acc[w.a][0] += rot[w.a][0];
                    acc[w.b][0] += rot[w.b][0];
                } else {
                    double x = (rot[w.a][0] + rot[w.b][0]) / 2;
                    acc[w.a][0] += x; cnt[w.a]++;
                    acc[w.b][0] += x; cnt[w.b]++;
                    acc[w.a][1] += rot[w.a][1];
                    acc[w.b][1] += rot[w.b][1];
                }
            }
            for (int i = 0; i < rot.length; i++) {
                if (cnt[i] == 0) continue;
                rot[i][0] = acc[i][0] / cnt[i];
                rot[i][1] = acc[i][1] / cnt[i];
            }
        }
        // 2) regroupement des coordonnees proches
        cluster(rot, 0, Math.max(3, minDim * 0.010));
        cluster(rot, 1, Math.max(3, minDim * 0.010));
        // 3) retour au repere image
        cos = Math.cos(theta); sin = Math.sin(theta);
        for (int i = 0; i < plan.nodes.size(); i++) {
            Plan.Node n = plan.nodes.get(i);
            n.x = rot[i][0] * cos - rot[i][1] * sin;
            n.y = rot[i][0] * sin + rot[i][1] * cos;
        }
    }

    private static double nearestAxis(double ang) {
        double best = 0;
        double bd = Double.MAX_VALUE;
        for (int k = -2; k <= 2; k++) {
            double a = k * Math.PI / 2;
            double d = Math.abs(G.angleDelta(ang, a));
            if (d < bd) { bd = d; best = a; }
        }
        return best;
    }

    private static void cluster(double[][] pts, int axis, double tol) {
        Integer[] idx = new Integer[pts.length];
        for (int i = 0; i < idx.length; i++) idx[i] = i;
        final int ax = axis;
        final double[][] p = pts;
        java.util.Arrays.sort(idx, new Comparator<Integer>() {
            @Override public int compare(Integer a, Integer b) {
                return Double.compare(p[a][ax], p[b][ax]);
            }
        });
        int i = 0;
        while (i < idx.length) {
            int j = i;
            double sum = pts[idx[i]][axis];
            while (j + 1 < idx.length && pts[idx[j + 1]][axis] - pts[idx[i]][axis] <= tol) {
                j++;
                sum += pts[idx[j]][axis];
            }
            double mean = sum / (j - i + 1);
            for (int k = i; k <= j; k++) pts[idx[k]][axis] = mean;
            i = j + 1;
        }
    }

    /** Orientation dominante du batiment, dans [0, PI/2[. */
    public static double dominantAngle(Plan plan) {
        double[] hist = new double[90];
        for (Plan.Wall w : plan.walls) {
            Plan.Node a = plan.nodes.get(w.a), b = plan.nodes.get(w.b);
            double ang = Math.atan2(b.y - a.y, b.x - a.x);
            double deg = Math.toDegrees(ang) % 90;
            if (deg < 0) deg += 90;
            int bin = (int) Math.floor(deg) % 90;
            double len = G.dist(a.x, a.y, b.x, b.y);
            hist[bin] += len;
            hist[(bin + 89) % 90] += len * 0.4;
            hist[(bin + 1) % 90] += len * 0.4;
        }
        int best = 0;
        for (int i = 1; i < 90; i++) if (hist[i] > hist[best]) best = i;
        double deg = best;
        if (deg > 45) deg -= 90;
        return Math.toRadians(deg);
    }
}
