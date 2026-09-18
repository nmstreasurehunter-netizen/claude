package com.tracecroquis.core.vector;

import com.tracecroquis.core.geom.G;
import com.tracecroquis.core.model.Enums.TextRole;
import com.tracecroquis.core.model.Plan;

import java.util.ArrayList;
import java.util.List;

/** Detection des lignes de cote et calibrage de l'echelle. */
public final class DimensionDetector {

    private DimensionDetector() { }

    public static void detect(Plan plan, List<Seg> segs, VectorOptions o, double minDim) {
        double minLen = 0.12 * minDim;
        double tickMax = 0.035 * minDim;
        double nearTick = 0.03 * minDim;
        double textDist = 0.075 * minDim;

        for (Seg s : segs) {
            if (s.consumed) continue;
            if (s.len() < minLen) continue;
            if (s.thickness > 0.012 * minDim + 3) continue;

            int arrows = 0;
            List<Seg> ticks = new ArrayList<>();
            for (int end = 0; end < 2; end++) {
                double ex = end == 0 ? s.x1 : s.x2;
                double ey = end == 0 ? s.y1 : s.y2;
                for (Seg t : segs) {
                    if (t == s || t.consumed) continue;
                    if (t.len() > tickMax) continue;
                    double d = Math.min(G.dist(t.x1, t.y1, ex, ey), G.dist(t.x2, t.y2, ex, ey));
                    if (d > nearTick) continue;
                    double da = Math.toDegrees(G.angleDiff180(t.ang(), s.ang()));
                    if (da > 10 && da < 55) { arrows++; ticks.add(t); break; }
                    if (da >= 75) { ticks.add(t); }   // trait d'attache perpendiculaire
                }
            }

            // Texte de cote associe
            Plan.TextItem bestText = null;
            double bestD = textDist;
            for (Plan.TextItem t : plan.texts) {
                if (t.room >= 0 && t.role == TextRole.NOM_PIECE) continue;
                double value = TextAnalyzer.parseLength(t.text);
                boolean numeric = !Double.isNaN(value) || t.text.isEmpty();
                if (!numeric) continue;
                double[] pr = G.project(t.x, t.y, s.x1, s.y1, s.x2, s.y2);
                if (pr[2] < -0.15 || pr[2] > 1.15) continue;
                if (pr[3] < bestD && !Double.isNaN(value)) { bestD = pr[3]; bestText = t; }
            }

            // Une cote est retenue si elle porte deux fleches, ou une fleche et un texte.
            if (bestText == null && arrows < 2) continue;

            Plan.DimItem d = new Plan.DimItem();
            d.x1 = s.x1; d.y1 = s.y1; d.x2 = s.x2; d.y2 = s.y2;
            d.vertical = Math.abs(s.y2 - s.y1) > Math.abs(s.x2 - s.x1);
            if (bestText != null) {
                d.valueM = TextAnalyzer.parseLength(bestText.text);
                d.label = bestText.text;
                bestText.role = TextRole.COTE;
                bestText.room = -1;
            }
            d.confidence = (arrows >= 2 ? 0.9 : arrows == 1 ? 0.7 : 0.5)
                    * (bestText != null ? 1.0 : 0.7);
            plan.dims.add(d);
            s.consumed = true;
            for (Seg t : ticks) t.consumed = true;
        }
    }

    /**
     * Echelle en pixels par metre deduite des cotes lues.
     * Renvoie 0 si aucune cote exploitable.
     */
    public static double solveScale(Plan plan) {
        List<Double> ratios = new ArrayList<>();
        for (Plan.DimItem d : plan.dims) {
            if (Double.isNaN(d.valueM) || d.valueM <= 0.05) continue;
            double px = G.dist(d.x1, d.y1, d.x2, d.y2);
            if (px < 5) continue;
            ratios.add(px / d.valueM);
        }
        if (ratios.isEmpty()) return 0;
        double med = G.median(ratios);
        List<Double> keep = new ArrayList<>();
        for (double r : ratios) if (Math.abs(r - med) <= med * 0.22) keep.add(r);
        if (keep.isEmpty()) return med;
        double sum = 0;
        for (double r : keep) sum += r;
        return sum / keep.size();
    }

    /** Marque la cote la plus longue comme reference d'echelle. */
    public static void markReference(Plan plan) {
        Plan.DimItem best = null;
        double bl = 0;
        for (Plan.DimItem d : plan.dims) {
            if (Double.isNaN(d.valueM)) continue;
            double l = G.dist(d.x1, d.y1, d.x2, d.y2);
            if (l > bl) { bl = l; best = d; }
        }
        if (best != null) best.reference = true;
    }

    /** Cotes automatiques sur l'emprise du batiment si le croquis n'en porte pas. */
    public static void autoDims(Plan plan, double minDim) {
        if (!plan.dims.isEmpty() || plan.walls.isEmpty()) return;
        double[] b = plan.wallBounds();
        double off = Math.max(12, minDim * 0.045);
        Plan.DimItem h = new Plan.DimItem();
        h.x1 = b[0]; h.y1 = b[1] - off; h.x2 = b[2]; h.y2 = b[1] - off;
        h.confidence = 0.4;
        plan.dims.add(h);
        Plan.DimItem v = new Plan.DimItem();
        v.x1 = b[2] + off; v.y1 = b[1]; v.x2 = b[2] + off; v.y2 = b[3];
        v.vertical = true;
        v.confidence = 0.4;
        plan.dims.add(v);
    }
}
