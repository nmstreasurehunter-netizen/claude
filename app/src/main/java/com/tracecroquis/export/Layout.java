package com.tracecroquis.export;

import com.tracecroquis.core.model.Plan;

import java.util.Locale;

/** Choix automatique du format de papier et de l'echelle. */
public class Layout {

    /** Formats normalises (largeur x hauteur en mm, portrait). */
    public static final String[] PAPERS = {"A4", "A3", "A2", "A1", "A0"};
    public static final double[][] SIZES = {
            {210, 297}, {297, 420}, {420, 594}, {594, 841}, {841, 1189}
    };
    /** Echelles usuelles du batiment, de la plus detaillee a la plus reduite. */
    public static final int[] SCALES = {20, 25, 50, 100, 200, 500};
    /** Ordre de preference pour un plan de permis de construire. */
    private static final int[] PREFERRED = {50, 100, 25, 20, 200, 500};

    public String paper = "A4";
    public boolean landscape = true;
    public int scaleDen = 50;
    public double widthMm = 297;
    public double heightMm = 210;
    public double marginMm = 10;
    public double titleBlockH = 32;
    public boolean titleBlock = true;

    public static double[] size(String paper) {
        for (int i = 0; i < PAPERS.length; i++) {
            if (PAPERS[i].equalsIgnoreCase(paper)) return SIZES[i];
        }
        return SIZES[0];
    }

    /** Surface utile pour le dessin (mm). */
    public double drawWidth() {
        return widthMm - 2 * marginMm;
    }

    public double drawHeight() {
        return heightMm - 2 * marginMm - (titleBlock ? titleBlockH + 4 : 0);
    }

    public double widthPt() { return widthMm * 72.0 / 25.4; }

    public double heightPt() { return heightMm * 72.0 / 25.4; }

    public static double mmToPt(double mm) { return mm * 72.0 / 25.4; }

    /**
     * Determine le format et l'echelle les plus adaptes a l'emprise du plan.
     *
     * @param plan       plan calibre
     * @param withTitle  reserver la place du cartouche
     * @param forcePaper format impose ("" = automatique)
     * @param forceScale denominateur impose (0 = automatique)
     */
    public static Layout choose(Plan plan, boolean withTitle, String forcePaper, int forceScale,
                                boolean forceLandscape, boolean autoOrientation) {
        Layout best = new Layout();
        best.titleBlock = withTitle;

        double[] b = plan.bounds();
        double ppm = plan.pxPerMeter > 0 ? plan.pxPerMeter : 100;
        // Emprise reelle, marge de respiration de 6 % pour les cotes.
        double wM = Math.max(0.5, (b[2] - b[0]) / ppm) * 1.06;
        double hM = Math.max(0.5, (b[3] - b[1]) / ppm) * 1.06;

        for (int pi = 0; pi < PAPERS.length; pi++) {
            if (!forcePaper.isEmpty() && !PAPERS[pi].equalsIgnoreCase(forcePaper)) continue;
            for (int rot = 0; rot < 2; rot++) {
                boolean land = rot == 0 ? (autoOrientation ? (wM >= hM) : forceLandscape)
                        : (autoOrientation ? (wM < hM) : !forceLandscape);
                Layout l = new Layout();
                l.paper = PAPERS[pi];
                l.landscape = land;
                l.titleBlock = withTitle;
                l.widthMm = land ? SIZES[pi][1] : SIZES[pi][0];
                l.heightMm = land ? SIZES[pi][0] : SIZES[pi][1];
                l.marginMm = PAPERS[pi].equals("A4") ? 10 : 12;
                l.titleBlockH = PAPERS[pi].equals("A4") ? 30 : 36;
                for (int den : (forceScale > 0 ? new int[]{forceScale} : PREFERRED)) {
                    double needW = wM * 1000.0 / den;
                    double needH = hM * 1000.0 / den;
                    if (needW <= l.drawWidth() && needH <= l.drawHeight()) {
                        l.scaleDen = den;
                        return l;
                    }
                }
                if (!forcePaper.isEmpty() && forceScale > 0) {
                    l.scaleDen = forceScale;
                    best = l;
                }
            }
        }
        if (!forcePaper.isEmpty() || forceScale > 0) return best;
        // Rien ne tient : A0 a l'echelle la plus reduite.
        Layout l = new Layout();
        l.paper = "A0";
        l.landscape = wM >= hM;
        l.widthMm = l.landscape ? SIZES[4][1] : SIZES[4][0];
        l.heightMm = l.landscape ? SIZES[4][0] : SIZES[4][1];
        l.scaleDen = 500;
        l.titleBlock = withTitle;
        return l;
    }

    public String scaleLabel() {
        return "1:" + scaleDen;
    }

    @Override
    public String toString() {
        return String.format(Locale.FRENCH, "%s %s · 1:%d",
                paper, landscape ? "paysage" : "portrait", scaleDen);
    }
}
