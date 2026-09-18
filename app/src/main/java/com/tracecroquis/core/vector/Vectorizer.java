package com.tracecroquis.core.vector;

import com.tracecroquis.core.geom.G;
import com.tracecroquis.core.img.Cc;
import com.tracecroquis.core.img.Dt;
import com.tracecroquis.core.img.Gray;
import com.tracecroquis.core.img.Mask;
import com.tracecroquis.core.img.Prep;
import com.tracecroquis.core.img.Thin;
import com.tracecroquis.core.img.Trace;
import com.tracecroquis.core.model.Enums.WallType;
import com.tracecroquis.core.model.Plan;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Chaine complete de vectorisation d'un croquis. */
public final class Vectorizer {

    /** Rapport d'avancement (0 a 100). */
    public interface Progress {
        void onProgress(int percent, String message);
    }

    /** Resultat de la vectorisation. */
    public static class Result {
        public Plan plan = new Plan();
        public Mask ink;
        public int width, height;
        public long millis;
        public int strokeCount, segCount, arcCount;
        public final List<String> log = new ArrayList<>();
        /** Segments et arcs bruts (diagnostic et affichage de contrôle). */
        public final List<Seg> segs = new ArrayList<>();
        public final List<Arc> arcs = new ArrayList<>();
        /** Echelle deduite des cotes (px/m), 0 si aucune. */
        public double scaleFromDims;
        /** Echelle deduite des surfaces annoncees (px/m), 0 si aucune. */
        public double scaleFromAreas;

        public String summary() {
            return String.format(Locale.FRENCH,
                    "%d murs · %d ouvertures · %d pièces · %d cotes · %d symboles",
                    plan.walls.size(), plan.openings.size(), plan.rooms.size(),
                    plan.dims.size(), plan.symbols.size());
        }
    }

    private Vectorizer() { }

    public static Result run(int[] argb, int w, int h, VectorOptions o, List<OcrBox> ocr, Progress p) {
        long t0 = System.currentTimeMillis();
        Result res = new Result();
        res.width = w;
        res.height = h;
        Plan plan = res.plan;
        plan.imageW = w;
        plan.imageH = h;
        double minDim = Math.min(w, h);

        step(p, 5, "Préparation de l'image");
        Gray gray = Gray.inkFromArgb(argb, w, h);
        Mask ink = Prep.binarize(gray, o.binarizeK, o.binarizeWindow, o.binarizeMargin);
        if (o.removeGrid) ink = Prep.removeGridLines(ink, null);
        ink = Prep.despeckle(ink, Math.max(4, o.minComponentArea));
        res.ink = ink;
        res.log.add("Encre : " + ink.count() + " px");

        step(p, 15, "Analyse des traits");
        int[] dt = Dt.compute(ink);
        Cc cc = Cc.label(ink);
        cc.measureStrokes(dt);
        res.log.add("Composantes : " + cc.comps.length);

        boolean[] isText = TextAnalyzer.classify(cc, ocr, o, minDim);
        Mask struct = new Mask(w, h);
        for (int i = 0; i < ink.px.length; i++) {
            int l = cc.labels[i];
            if (l > 0 && !isText[l - 1]) struct.px[i] = true;
        }

        step(p, 28, "Squelettisation");
        double medianStroke = medianStrokeWidth(dt, ink);
        // Fermeture : soude les hachures d'un mur, les traits repasses et les
        // doubles traits trop serres, afin d'obtenir un axe unique par mur.
        int rc = o.closingRadius >= 0 ? o.closingRadius
                : Math.min(3, Math.max(1, (int) Math.round(medianStroke * 0.9)));
        Mask closed = rc > 0 ? struct.close(rc) : struct;
        int[] dtClosed = Dt.compute(closed);
        double bandThick = o.bandRadius > 0 ? o.bandRadius * 2
                : Math.max(5.0, 0.0060 * minDim + medianStroke * 0.4);
        res.log.add(String.format(Locale.FRENCH,
                "Fermeture r=%d · seuil mur plein %.1f px", rc, bandThick));

        List<Trace.Stroke> strokes = extract(closed, dtClosed, cc.labels, medianStroke, rc);
        res.strokeCount = strokes.size();
        res.log.add("Traits : " + strokes.size() + " (épaisseur médiane "
                + String.format(Locale.FRENCH, "%.1f", medianStroke) + " px)");

        step(p, 42, "Ajustement des segments");
        List<Seg> segs = new ArrayList<>();
        List<Arc> arcs = new ArrayList<>();
        List<List<Integer>> segsOfStroke = new ArrayList<>();
        double minR = o.doorRadiusMinFrac * minDim;
        double maxR = o.doorRadiusMaxFrac * minDim;
        for (int i = 0; i < strokes.size(); i++) {
            Trace.Stroke st = strokes.get(i);
            boolean isBand = st.thickness >= bandThick;
            double tol = isBand ? Math.max(1.8, st.thickness * 0.30) : Math.max(1.4, st.thickness * 0.45);
            Fit.Result f = Fit.fit(st, i, tol, minR, maxR);
            List<Integer> mine = new ArrayList<>();
            for (Seg sg : f.segs) {
                sg.component = st.component;
                sg.band = isBand;
                mine.add(segs.size());
                segs.add(sg);
            }
            if (!isBand) arcs.addAll(f.arcs);
            segsOfStroke.add(mine);
        }
        res.segCount = segs.size();
        res.arcCount = arcs.size();
        res.segs.addAll(segs);
        res.arcs.addAll(arcs);
        res.log.add("Segments : " + segs.size() + ", arcs : " + arcs.size());

        step(p, 50, "Textes");
        List<TextAnalyzer.Group> groups = TextAnalyzer.group(cc, isText, o, minDim);
        TextAnalyzer.attachOcr(groups, ocr);
        TextAnalyzer.toPlan(plan, groups);

        if (o.detectDims) {
            step(p, 54, "Cotes");
            DimensionDetector.detect(plan, segs, o, minDim);
            res.scaleFromDims = DimensionDetector.solveScale(plan);
            DimensionDetector.markReference(plan);
            res.log.add("Cotes : " + plan.dims.size());
        }

        if (o.detectWalls) {
            step(p, 60, "Construction des murs");
            List<WallBuilder.Cand> cands = WallBuilder.candidates(segs, ink, o, minDim);
            WallBuilder.assemble(plan, cands, o, minDim);
            if (!o.keepAngles) WallBuilder.orthogonalize(plan, o, minDim);
            res.log.add("Murs : " + plan.walls.size());
        }

        if (o.detectOpenings) {
            step(p, 68, "Portes et fenêtres");
            OpeningDetector.bridgeGaps(plan, ink, o, minDim);
            OpeningDetector.windowsFromInlineWalls(plan, o, minDim);
            OpeningDetector.doorsFromArcs(plan, arcs, segs, o, minDim);
            OpeningDetector.gapsFromInk(plan, ink, o, minDim);
            res.log.add("Ouvertures : " + plan.openings.size());
        }

        if (o.detectRooms) {
            step(p, 74, "Pièces");
            RoomBuilder.build(plan, minDim);
            res.log.add("Pièces : " + plan.rooms.size() + " — " + RoomBuilder.lastReport);
        }

        step(p, 80, "Affectation des textes");
        RoomBuilder.assignTexts(plan);
        res.scaleFromAreas = TextAnalyzer.applyToRooms(plan);

        // --- echelle -----------------------------------------------------
        if (res.scaleFromDims > 0) {
            plan.pxPerMeter = res.scaleFromDims;
            plan.scaleConfirmed = true;
            res.log.add(String.format(Locale.FRENCH, "Échelle par cotes : %.1f px/m", plan.pxPerMeter));
        } else if (res.scaleFromAreas > 0) {
            plan.pxPerMeter = res.scaleFromAreas;
            res.log.add(String.format(Locale.FRENCH, "Échelle par surfaces : %.1f px/m", plan.pxPerMeter));
        } else {
            // Repli : une maison individuelle fait typiquement 10 a 13 m de large.
            double[] b = plan.wallBounds();
            double widthPx = Math.max(1, b[2] - b[0]);
            plan.pxPerMeter = widthPx / 11.0;
            res.log.add("Échelle estimée (aucune cote lue)");
        }
        OpeningDetector.applyScale(plan);
        finalizeThickness(plan, o);

        // Surfaces non rattachees : essaie de relire les surfaces apres calibrage.
        TextAnalyzer.applyToRooms(plan);

        if (o.detectTextures || o.detectFurniture) {
            step(p, 90, "Symboles");
            if (o.detectTextures) {
                SymbolDetector.detectStairs(plan, segs, o, minDim);
                SymbolDetector.detectHatching(plan, segs, o, minDim);
            }
            if (o.detectFurniture) {
                boolean[] strokeUsed = new boolean[strokes.size()];
                for (int i = 0; i < strokes.size(); i++) {
                    boolean used = false;
                    for (int si : segsOfStroke.get(i)) {
                        if (segs.get(si).consumed) { used = true; break; }
                    }
                    strokeUsed[i] = used;
                }
                for (Arc a : arcs) {
                    if (a.consumed && a.stroke >= 0 && a.stroke < strokeUsed.length) {
                        strokeUsed[a.stroke] = true;
                    }
                }
                SymbolDetector.detectFurniture(plan, strokes, strokeUsed, o, minDim);
            }
            SymbolDetector.tagFromTexts(plan);
        }

        if (o.detectDims) DimensionDetector.autoDims(plan, minDim);

        step(p, 100, "Terminé");
        res.millis = System.currentTimeMillis() - t0;
        res.log.add("Durée : " + res.millis + " ms");
        return res;
    }

    /** Squelettise un masque et renvoie ses polylignes elaguees. */
    private static List<Trace.Stroke> extract(Mask m, int[] dt, int[] labels,
                                              double medianStroke, int closing) {
        if (m.count() == 0) return new ArrayList<>();
        Mask skel = Thin.skeleton(m);
        List<Trace.Stroke> strokes = Trace.strokes(skel, dt, labels);
        double spurLen = Math.max(5, medianStroke * 2.2 + closing * 2);
        for (int pass = 0; pass < 3; pass++) {
            if (!Trace.pruneSpurs(skel, strokes, spurLen)) break;
            strokes = Trace.strokes(skel, dt, labels);
        }
        return strokes;
    }

    /** Epaisseur de trait mediane de l'encre (px). */
    private static double medianStrokeWidth(int[] dt, Mask ink) {
        int[] hist = new int[64];
        int n = 0;
        for (int i = 0; i < dt.length; i++) {
            if (!ink.px[i]) continue;
            hist[Math.min(63, dt[i])]++;
            n++;
        }
        if (n == 0) return 1;
        int acc = 0;
        for (int d = 0; d < 64; d++) {
            acc += hist[d];
            if (acc >= n * 0.75) return Math.max(1, 2.0 * d - 1);
        }
        return 2;
    }

    /** Fixe des epaisseurs realistes quand le croquis ne les represente pas. */
    private static void finalizeThickness(Plan plan, VectorOptions o) {
        if (plan.pxPerMeter <= 0) return;
        for (Plan.Wall w : plan.walls) {
            double m = w.thicknessPx / plan.pxPerMeter;
            if (m < 0.045 || m > 0.60) {
                m = w.exterior ? o.defaultExteriorWallM : o.defaultPartitionM;
                w.thicknessPx = m * plan.pxPerMeter;
            }
            w.type = Plan.classify(m);
            if (w.exterior && w.type == WallType.VOILE) w.type = WallType.VOILE_EXT;
            w.thicknessM = Plan.normalizeThickness(m, w.type);
        }
    }

    private static void step(Progress p, int pct, String msg) {
        if (p != null) p.onProgress(pct, msg);
    }

    /** Distance en metres entre deux points du plan. */
    public static double meters(Plan plan, double x1, double y1, double x2, double y2) {
        double px = G.dist(x1, y1, x2, y2);
        return plan.pxPerMeter > 0 ? px / plan.pxPerMeter : 0;
    }
}
