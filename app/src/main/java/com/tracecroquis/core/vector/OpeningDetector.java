package com.tracecroquis.core.vector;

import com.tracecroquis.core.geom.G;
import com.tracecroquis.core.img.Mask;
import com.tracecroquis.core.model.Enums.OpeningType;
import com.tracecroquis.core.model.Plan;

import java.util.ArrayList;
import java.util.List;

/** Detection des portes, fenetres et passages. */
public final class OpeningDetector {

    private OpeningDetector() { }


    // ----------------------------------------------------------- percements

    /**
     * Ponte les interruptions de murs (jambages de portes et de fenetres) :
     * insere un mur virtuel portant une ouverture, ce qui referme le graphe
     * des pieces tout en conservant le percement.
     */
    public static void bridgeGaps(Plan plan, Mask ink, VectorOptions o, double minDim) {
        double minGap = Math.max(4, o.openingMinWidthFrac * minDim);
        double maxGap = 0.25 * minDim;
        int[] deg = WallBuilder.degrees(plan);
        int n = plan.nodes.size();
        List<double[]> cands = new ArrayList<>();   // gap, i, j, thickness
        for (int i = 0; i < n; i++) {
            if (deg[i] > 2) continue;
            for (int j = i + 1; j < n; j++) {
                if (deg[j] > 2) continue;
                Plan.Node a = plan.nodes.get(i), b = plan.nodes.get(j);
                double gap = G.dist(a.x, a.y, b.x, b.y);
                if (gap < minGap || gap > maxGap) continue;
                double gapAng = Math.atan2(b.y - a.y, b.x - a.x);
                double thick = alignment(plan, i, j, gapAng, minDim);
                if (thick <= 0) continue;
                // Un percement peut contenir un symbole de menuiserie : on ne rejette
                // que si l'axe du mur est franchement plein sur toute la portee.
                if (ink != null && ink.inkRatio(a.x, a.y, b.x, b.y, 0) > 0.75) continue;
                cands.add(new double[]{gap, i, j, thick});
            }
        }
        java.util.Collections.sort(cands, new java.util.Comparator<double[]>() {
            @Override public int compare(double[] x, double[] y) { return Double.compare(x[0], y[0]); }
        });
        boolean[] bridged = new boolean[n];
        for (double[] c : cands) {
            int i = (int) c[1], j = (int) c[2];
            if (bridged[i] || bridged[j]) continue;
            Plan.Wall w = new Plan.Wall(i, j);
            w.thicknessPx = c[3];
            w.confidence = 0.6;
            plan.walls.add(w);
            Plan.Opening op = new Plan.Opening();
            op.wall = plan.walls.size() - 1;
            op.t = 0.5;
            op.widthM = c[0];              // provisoire, en pixels
            op.type = OpeningType.PASSAGE;
            op.confidence = 0.6;
            plan.openings.add(op);
            bridged[i] = bridged[j] = true;
        }
    }

    /**
     * Verifie qu'un mur arrivant en i et un mur arrivant en j sont tous deux
     * alignes sur la direction du percement. Renvoie l'epaisseur retenue, ou 0.
     */
    private static double alignment(Plan plan, int i, int j, double gapAng, double minDim) {
        double tol = Math.toRadians(22);
        double best = 0;
        for (int wi = 0; wi < plan.walls.size(); wi++) {
            Plan.Wall w1 = plan.walls.get(wi);
            if (w1.a != i && w1.b != i) continue;
            if (Math.abs(G.angleDelta(dirTowards(plan, w1, i), gapAng)) > tol) continue;
            for (int wj = 0; wj < plan.walls.size(); wj++) {
                if (wj == wi) continue;
                Plan.Wall w2 = plan.walls.get(wj);
                if (w2.a != j && w2.b != j) continue;
                if (Math.abs(G.angleDelta(dirTowards(plan, w2, j), gapAng + Math.PI)) > tol) continue;
                Plan.Node a = plan.nodes.get(i), b = plan.nodes.get(j);
                Plan.Node f1 = plan.nodes.get(w1.a == i ? w1.b : w1.a);
                double band = Math.max(Math.max(w1.thicknessPx, w2.thicknessPx), 0.014 * minDim);
                if (G.distPointLine(b.x, b.y, f1.x, f1.y, a.x, a.y) > band) continue;
                double th = (w1.thicknessPx + w2.thicknessPx) / 2;
                if (th > best) best = th;
            }
        }
        return best;
    }

    /** Direction du mur, orientee vers le noeud donne. */
    private static double dirTowards(Plan plan, Plan.Wall w, int node) {
        Plan.Node far = plan.nodes.get(w.a == node ? w.b : w.a);
        Plan.Node at = plan.nodes.get(node);
        return Math.atan2(at.y - far.y, at.x - far.x);
    }

    // ---------------------------------------------------------------- portes

    /** Portes a partir des arcs de battant. */
    public static void doorsFromArcs(Plan plan, List<Arc> arcs, List<Seg> segs,
                                     VectorOptions o, double minDim) {
        double tol = Math.max(6, o.snapTolFrac * minDim * 2.0);
        for (Arc arc : arcs) {
            if (arc.consumed) continue;
            // Le centre de l'arc est le gond : il repose sur un mur.
            int best = -1;
            double bd = tol;
            double bt = 0;
            for (int i = 0; i < plan.walls.size(); i++) {
                Plan.Wall w = plan.walls.get(i);
                Plan.Node a = plan.nodes.get(w.a), b = plan.nodes.get(w.b);
                double[] pr = G.project(arc.cx, arc.cy, a.x, a.y, b.x, b.y);
                if (pr[3] < bd) { bd = pr[3]; best = i; bt = pr[2]; }
            }
            if (best < 0) continue;
            Plan.Wall w = plan.walls.get(best);
            Plan.Node a = plan.nodes.get(w.a), b = plan.nodes.get(w.b);
            double len = plan.wallLengthPx(w);
            if (len < 1e-6) continue;
            double widthPx = arc.r;
            // Position : le gond est a une extremite du percement.
            double dirX = (b.x - a.x) / len, dirY = (b.y - a.y) / len;
            // Sens du percement : vers le milieu de l'arc projete sur le mur.
            double[] prMid = G.project(arc.midX(), arc.midY(), a.x, a.y, b.x, b.y);
            double sign = prMid[2] >= bt ? 1 : -1;
            double centerT = bt + sign * (widthPx / 2) / len;
            centerT = G.clamp(centerT, 0.02, 0.98);

            Plan.Opening op = existingAt(plan, best, centerT, widthPx / len);
            boolean isNew = op == null;
            if (isNew) op = new Plan.Opening();
            op.wall = best;
            if (isNew) {
                op.t = centerT;
                op.widthM = widthPx;          // provisoire : en pixels, converti apres calibrage
            }
            op.type = OpeningType.PORTE;
            // Cote du battant : signe du produit vectoriel mur -> milieu d'arc.
            double crossV = dirX * (arc.midY() - arc.cy) - dirY * (arc.midX() - arc.cx);
            op.swingSide = crossV >= 0 ? 1 : -1;
            op.hingeSide = sign >= 0 ? -1 : 1;
            op.swingAngleDeg = Math.toDegrees(arc.sweep);
            op.confidence = 0.92;
            if (isNew) plan.openings.add(op);
            arc.consumed = true;
            consumeLeaf(segs, arc, minDim);
        }
        mergeDoubleDoors(plan);
    }

    /** Ouverture deja presente couvrant cette position du mur. */
    private static Plan.Opening existingAt(Plan plan, int wall, double t, double tWidth) {
        for (Plan.Opening op : plan.openings) {
            if (op.wall != wall) continue;
            if (Math.abs(op.t - t) < Math.max(0.5 * tWidth + 0.02, 0.08)) return op;
        }
        return null;
    }

    /** Marque comme utilise le trait du vantail attache a l'arc. */
    private static void consumeLeaf(List<Seg> segs, Arc arc, double minDim) {
        for (Seg s : segs) {
            if (s.consumed) continue;
            double d1 = G.dist(s.x1, s.y1, arc.cx, arc.cy);
            double d2 = G.dist(s.x2, s.y2, arc.cx, arc.cy);
            double near = Math.min(d1, d2);
            if (near > arc.r * 0.35) continue;
            if (Math.abs(s.len() - arc.r) < arc.r * 0.45) s.consumed = true;
        }
    }

    /** Deux battants opposes tres proches = porte double. */
    private static void mergeDoubleDoors(Plan plan) {
        for (int i = 0; i < plan.openings.size(); i++) {
            Plan.Opening a = plan.openings.get(i);
            for (int j = i + 1; j < plan.openings.size(); j++) {
                Plan.Opening b = plan.openings.get(j);
                if (a.wall != b.wall) continue;
                if (!a.type.isDoor() || !b.type.isDoor()) continue;
                double gap = Math.abs(a.t - b.t) * plan.wallLengthPx(plan.walls.get(a.wall));
                double sum = (a.widthM + b.widthM);
                if (gap < sum * 0.75 && Math.abs(a.widthM - b.widthM) < sum * 0.3) {
                    a.t = (a.t + b.t) / 2;
                    a.widthM = sum;
                    a.type = OpeningType.PORTE_DOUBLE;
                    plan.openings.remove(j);
                    j--;
                }
            }
        }
    }

    // -------------------------------------------------------------- fenetres

    /**
     * Repere les symboles de fenetre : petit trait parallele loge dans
     * l'epaisseur d'un mur (rectangle de menuiserie du croquis).
     */
    public static void windowsFromInlineWalls(Plan plan, VectorOptions o, double minDim) {
        double angTol = Math.toRadians(o.parallelTolDeg + 4);
        List<Integer> remove = new ArrayList<>();
        for (int i = 0; i < plan.walls.size(); i++) {
            Plan.Wall v = plan.walls.get(i);
            double lv = plan.wallLengthPx(v);
            for (int j = 0; j < plan.walls.size(); j++) {
                if (i == j || remove.contains(Integer.valueOf(j))) continue;
                Plan.Wall w = plan.walls.get(j);
                double lw = plan.wallLengthPx(w);
                if (lv > lw * 0.75) continue;                       // doit etre nettement plus court
                Plan.Node a = plan.nodes.get(w.a), b = plan.nodes.get(w.b);
                Plan.Node p = plan.nodes.get(v.a), q = plan.nodes.get(v.b);
                double angW = Math.atan2(b.y - a.y, b.x - a.x);
                double angV = Math.atan2(q.y - p.y, q.x - p.x);
                if (G.angleDiff180(angW, angV) > angTol) continue;
                double[] pr1 = G.project(p.x, p.y, a.x, a.y, b.x, b.y);
                double[] pr2 = G.project(q.x, q.y, a.x, a.y, b.x, b.y);
                double band = Math.max(w.thicknessPx * 0.9, minDim * 0.012);
                if (pr1[3] > band || pr2[3] > band) continue;
                double t1 = pr1[2], t2 = pr2[2];
                if (Math.min(t1, t2) < -0.02 || Math.max(t1, t2) > 1.02) continue;
                double tc = G.clamp((t1 + t2) / 2, 0.02, 0.98);
                double wpx = Math.abs(t2 - t1) * lw;
                Plan.Opening op = existingAt(plan, j, tc, wpx / lw);
                if (op == null) {
                    op = new Plan.Opening();
                    op.wall = j;
                    op.t = tc;
                    op.widthM = wpx;                  // provisoire en pixels
                    plan.openings.add(op);
                }
                op.type = OpeningType.FENETRE;
                op.confidence = 0.82;
                remove.add(Integer.valueOf(i));
                break;
            }
        }
        java.util.Collections.sort(remove);
        for (int k = remove.size() - 1; k >= 0; k--) {
            int wi = remove.get(k);
            plan.walls.remove(wi);
            for (Plan.Opening op : plan.openings) {
                if (op.wall > wi) op.wall--;
                else if (op.wall == wi) op.wall = -1;
            }
        }
        for (int k = plan.openings.size() - 1; k >= 0; k--) {
            if (plan.openings.get(k).wall < 0) plan.openings.remove(k);
        }
        WallBuilder.compactNodes(plan);
    }

    // -------------------------------------------------------------- passages

    /** Percements deduits des interruptions d'encre le long des murs. */
    public static void gapsFromInk(Plan plan, Mask ink, VectorOptions o, double minDim) {
        double minW = Math.max(6, o.openingMinWidthFrac * minDim);
        for (int i = 0; i < plan.walls.size(); i++) {
            Plan.Wall w = plan.walls.get(i);
            Plan.Node a = plan.nodes.get(w.a), b = plan.nodes.get(w.b);
            double len = plan.wallLengthPx(w);
            if (len < minW * 2) continue;
            int steps = (int) Math.max(8, len);
            boolean[] inked = new boolean[steps + 1];
            double lateral = Math.max(2, w.thicknessPx * 0.75 + 1);
            double dx = (b.x - a.x) / len, dy = (b.y - a.y) / len;
            double nx = -dy, ny = dx;
            for (int s = 0; s <= steps; s++) {
                double t = s / (double) steps;
                double x = a.x + (b.x - a.x) * t, y = a.y + (b.y - a.y) * t;
                boolean hit = false;
                for (int l = -(int) lateral; l <= lateral && !hit; l++) {
                    if (ink.get((int) Math.round(x + nx * l), (int) Math.round(y + ny * l))) hit = true;
                }
                inked[s] = hit;
            }
            int s = 0;
            while (s <= steps) {
                if (inked[s]) { s++; continue; }
                int e = s;
                while (e + 1 <= steps && !inked[e + 1]) e++;
                double gapLen = (e - s + 1) / (double) steps * len;
                double tc = ((s + e) / 2.0) / steps;
                if (gapLen >= minW && gapLen < len * 0.75 && tc > 0.03 && tc < 0.97) {
                    if (!hasOpeningNear(plan, i, tc, gapLen / len)) {
                        Plan.Opening op = new Plan.Opening();
                        op.wall = i;
                        op.t = tc;
                        op.widthM = gapLen;         // provisoire en pixels
                        op.type = OpeningType.PASSAGE;
                        op.confidence = 0.55;
                        plan.openings.add(op);
                    } else {
                        refineNear(plan, i, tc, gapLen);
                    }
                }
                s = e + 1;
            }
        }
    }

    private static boolean hasOpeningNear(Plan plan, int wall, double t, double tWidth) {
        for (Plan.Opening op : plan.openings) {
            if (op.wall != wall) continue;
            if (Math.abs(op.t - t) < Math.max(0.12, tWidth)) return true;
        }
        return false;
    }

    private static void refineNear(Plan plan, int wall, double t, double gapPx) {
        Plan.Opening best = null;
        double bd = Double.MAX_VALUE;
        for (Plan.Opening op : plan.openings) {
            if (op.wall != wall) continue;
            double d = Math.abs(op.t - t);
            if (d < bd) { bd = d; best = op; }
        }
        if (best != null && best.type == OpeningType.PORTE) {
            // Le percement mesure est plus fiable que le rayon de l'arc.
            best.t = t;
            best.widthM = gapPx;
            best.confidence = Math.min(1, best.confidence + 0.05);
        }
    }

    // --------------------------------------------------------- normalisation

    /**
     * Convertit les largeurs stockees en pixels vers des metres et
     * affine le type d'ouverture une fois l'echelle connue.
     */
    public static void applyScale(Plan plan) {
        if (plan.pxPerMeter <= 0) return;
        for (int i = plan.openings.size() - 1; i >= 0; i--) {
            Plan.Opening op = plan.openings.get(i);
            double m = op.widthM / plan.pxPerMeter;
            if (m < 0.35 || m > 6.5) {
                if (op.type == OpeningType.PASSAGE) { plan.openings.remove(i); continue; }
                m = G.clamp(m, 0.6, 3.0);
            }
            op.widthM = m;
            if (op.type.isDoor()) {
                if (m > 1.9) op.type = OpeningType.PORTE_GARAGE;
                else if (m > 1.15) op.type = OpeningType.PORTE_DOUBLE;
                else op.type = OpeningType.PORTE;
            } else if (op.type == OpeningType.FENETRE && m > 1.9) {
                op.type = OpeningType.BAIE;
            } else if (op.type == OpeningType.PASSAGE && m > 2.2) {
                op.type = OpeningType.BAIE;
            }
        }
    }
}
