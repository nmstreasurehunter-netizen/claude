package com.tracecroquis.core.vector;

import com.tracecroquis.core.geom.G;
import com.tracecroquis.core.model.Plan;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/** Extraction des pieces : faces du graphe planaire des murs. */
public final class RoomBuilder {

    /** Journal de diagnostic du dernier appel a {@link #build}. */
    public static String lastReport = "";

    private RoomBuilder() { }

    /** Renseigne plan.rooms et marque les murs exterieurs. */
    public static void build(Plan plan, double minDim) {
        plan.rooms.clear();
        int nw = plan.walls.size();
        if (nw == 0) return;

        int ne = nw * 2;
        int[] from = new int[ne], to = new int[ne];
        double[] ang = new double[ne];
        for (int i = 0; i < nw; i++) {
            Plan.Wall w = plan.walls.get(i);
            from[2 * i] = w.a; to[2 * i] = w.b;
            from[2 * i + 1] = w.b; to[2 * i + 1] = w.a;
        }
        for (int e = 0; e < ne; e++) {
            Plan.Node a = plan.nodes.get(from[e]), b = plan.nodes.get(to[e]);
            ang[e] = Math.atan2(b.y - a.y, b.x - a.x);
        }

        // Aretes sortantes triees par angle pour chaque noeud.
        List<List<Integer>> out = new ArrayList<>();
        for (int i = 0; i < plan.nodes.size(); i++) out.add(new ArrayList<Integer>());
        for (int e = 0; e < ne; e++) out.get(from[e]).add(e);
        final double[] fang = ang;
        for (List<Integer> l : out) {
            Collections.sort(l, new Comparator<Integer>() {
                @Override public int compare(Integer a, Integer b) {
                    return Double.compare(fang[a], fang[b]);
                }
            });
        }
        int[] posInNode = new int[ne];
        for (int n = 0; n < out.size(); n++) {
            List<Integer> l = out.get(n);
            for (int k = 0; k < l.size(); k++) posInNode[l.get(k)] = k;
        }

        boolean[] visited = new boolean[ne];
        List<List<Integer>> faces = new ArrayList<>();
        for (int e0 = 0; e0 < ne; e0++) {
            if (visited[e0]) continue;
            List<Integer> face = new ArrayList<>();
            int e = e0;
            int guard = 0;
            while (!visited[e] && guard++ < ne + 5) {
                visited[e] = true;
                face.add(e);
                int rev = (e % 2 == 0) ? e + 1 : e - 1;   // arete inverse
                int v = from[rev];
                List<Integer> l = out.get(v);
                int p = posInNode[rev];
                int next = l.get((p + 1) % l.size());
                e = next;
            }
            if (face.size() >= 3) faces.add(face);
        }

        // Aire de chaque face ; la plus grande en valeur absolue est l'exterieur.
        double bestArea = 0;
        int outer = -1;
        List<double[]> areas = new ArrayList<>();
        for (int i = 0; i < faces.size(); i++) {
            List<double[]> poly = polyOf(plan, faces.get(i), from);
            double a = Math.abs(G.polygonArea(poly));
            areas.add(new double[]{a});
            if (a > bestArea) { bestArea = a; outer = i; }
        }

        StringBuilder rep = new StringBuilder("faces=" + faces.size() + " [");
        for (int i = 0; i < faces.size() && i < 12; i++) {
            rep.append(String.format(java.util.Locale.US, "%.0f ", areas.get(i)[0]));
        }
        rep.append("] outer=").append(outer);
        lastReport = rep.toString();

        double minArea = minDim * minDim * 0.004;
        for (int i = 0; i < faces.size(); i++) {
            if (i == outer) continue;
            if (areas.get(i)[0] < minArea) continue;
            List<double[]> poly = polyOf(plan, faces.get(i), from);
            Plan.Room r = new Plan.Room();
            r.poly.addAll(inset(poly, halfThickness(plan, faces.get(i))));
            double[] c = G.interiorPoint(r.poly);
            r.labelX = c[0];
            r.labelY = c[1];
            plan.rooms.add(r);
        }

        // Murs de la face exterieure = murs de facade.
        if (outer >= 0) {
            for (int e : faces.get(outer)) {
                int wi = e / 2;
                if (wi < plan.walls.size()) plan.walls.get(wi).exterior = true;
            }
        }
    }

    private static List<double[]> polyOf(Plan plan, List<Integer> face, int[] from) {
        List<double[]> poly = new ArrayList<>();
        for (int e : face) {
            Plan.Node n = plan.nodes.get(from[e]);
            poly.add(new double[]{n.x, n.y});
        }
        return poly;
    }

    private static double[] halfThickness(Plan plan, List<Integer> face) {
        double[] t = new double[face.size()];
        for (int i = 0; i < face.size(); i++) {
            int wi = face.get(i) / 2;
            t[i] = wi < plan.walls.size() ? plan.walls.get(wi).thicknessPx / 2 : 0;
        }
        return t;
    }

    /**
     * Retrait du polygone vers l'interieur (surface utile de la piece).
     * offsets[i] s'applique a l'arete i -> i+1.
     */
    public static List<double[]> inset(List<double[]> poly, double[] offsets) {
        int n = poly.size();
        if (n < 3) return new ArrayList<>(poly);
        double area = G.polygonArea(poly);
        double sign = area >= 0 ? 1 : -1;
        double[][] lines = new double[n][4];
        for (int i = 0; i < n; i++) {
            double[] p = poly.get(i), q = poly.get((i + 1) % n);
            double dx = q[0] - p[0], dy = q[1] - p[1];
            double len = Math.hypot(dx, dy);
            if (len < 1e-6) { lines[i] = new double[]{p[0], p[1], q[0], q[1]}; continue; }
            // Normale interieure (repere ecran, y vers le bas)
            double nx = -sign * dy / len, ny = sign * dx / len;
            double o = offsets != null && i < offsets.length ? offsets[i] : 0;
            lines[i] = new double[]{p[0] + nx * o, p[1] + ny * o, q[0] + nx * o, q[1] + ny * o};
        }
        List<double[]> out = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            double[] prev = lines[(i - 1 + n) % n], cur = lines[i];
            double[] it = G.lineIntersection(prev[0], prev[1], prev[2], prev[3],
                    cur[0], cur[1], cur[2], cur[3]);
            if (it == null) out.add(new double[]{cur[0], cur[1]});
            else out.add(new double[]{it[0], it[1]});
        }
        return out;
    }

    /** Rattache chaque texte a la piece qui le contient. */
    public static void assignTexts(Plan plan) {
        for (Plan.TextItem t : plan.texts) {
            t.room = roomAt(plan, t.x, t.y);
        }
    }

    public static int roomAt(Plan plan, double x, double y) {
        for (int i = 0; i < plan.rooms.size(); i++) {
            if (G.pointInPolygon(x, y, plan.rooms.get(i).poly)) return i;
        }
        return -1;
    }
}
