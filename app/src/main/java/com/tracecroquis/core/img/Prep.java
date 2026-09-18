package com.tracecroquis.core.img;

/** Pretraitement : egalisation d'eclairage, binarisation adaptative, nettoyage. */
public final class Prep {

    private Prep() { }

    /** Moyenne locale par image integrale. */
    public static double[] boxMean(Gray g, int radius) {
        int w = g.w, h = g.h;
        long[] sum = new long[(w + 1) * (h + 1)];
        for (int y = 0; y < h; y++) {
            long row = 0;
            for (int x = 0; x < w; x++) {
                row += g.px[y * w + x] & 0xFF;
                sum[(y + 1) * (w + 1) + (x + 1)] = sum[y * (w + 1) + (x + 1)] + row;
            }
        }
        double[] out = new double[w * h];
        for (int y = 0; y < h; y++) {
            int y0 = Math.max(0, y - radius), y1 = Math.min(h - 1, y + radius);
            for (int x = 0; x < w; x++) {
                int x0 = Math.max(0, x - radius), x1 = Math.min(w - 1, x + radius);
                long s = sum[(y1 + 1) * (w + 1) + (x1 + 1)] - sum[y0 * (w + 1) + (x1 + 1)]
                        - sum[(y1 + 1) * (w + 1) + x0] + sum[y0 * (w + 1) + x0];
                out[y * w + x] = s / (double) ((y1 - y0 + 1) * (x1 - x0 + 1));
            }
        }
        return out;
    }

    /** Seuil global d'Otsu. */
    public static int otsu(Gray g) {
        int[] hist = new int[256];
        for (byte b : g.px) hist[b & 0xFF]++;
        int total = g.px.length;
        double sum = 0;
        for (int i = 0; i < 256; i++) sum += i * (double) hist[i];
        double sumB = 0;
        int wB = 0, best = 128;
        double maxVar = -1;
        for (int t = 0; t < 256; t++) {
            wB += hist[t];
            if (wB == 0) continue;
            int wF = total - wB;
            if (wF == 0) break;
            sumB += t * (double) hist[t];
            double mB = sumB / wB, mF = (sum - sumB) / wF;
            double var = wB * (double) wF * (mB - mF) * (mB - mF);
            if (var > maxVar) { maxVar = var; best = t; }
        }
        return best;
    }

    /**
     * Binarisation Sauvola contrainte par un seuil global.
     *
     * @param k       sensibilite locale (0.15 a 0.40, defaut 0.22)
     * @param window  demi-fenetre en pixels (0 = automatique)
     * @param margin  tolerance autour du seuil global d'Otsu (evite le quadrillage)
     */
    public static Mask binarize(Gray g, double k, int window, int margin) {
        int w = g.w, h = g.h;
        int radius = window > 0 ? window : Math.max(8, Math.min(w, h) / 24);
        double[] mean = boxMean(g, radius);
        Gray sq = new Gray(w, h);
        // Ecart-type local approche par la moyenne des ecarts absolus.
        double[] dev = new double[w * h];
        for (int i = 0; i < w * h; i++) {
            dev[i] = Math.abs((g.px[i] & 0xFF) - mean[i]);
            sq.px[i] = (byte) Math.min(255, (int) dev[i]);
        }
        double[] mdev = boxMean(sq, radius);
        int global = otsu(g);
        Mask m = new Mask(w, h);
        for (int i = 0; i < w * h; i++) {
            int v = g.px[i] & 0xFF;
            double s = mdev[i] * 1.25;   // approximation de l'ecart-type
            double t = mean[i] * (1.0 - k * (1.0 - s / 128.0));
            boolean ink = v < t && v < global + margin;
            m.px[i] = ink;
        }
        return m;
    }

    /** Supprime les taches de moins de minArea pixels. */
    public static Mask despeckle(Mask src, int minArea) {
        Cc cc = Cc.label(src);
        Mask out = new Mask(src.w, src.h);
        for (int i = 0; i < src.px.length; i++) {
            int l = cc.labels[i];
            if (l > 0 && cc.comps[l - 1].area >= minArea) out.px[i] = true;
        }
        return out;
    }

    /**
     * Supprime les lignes de quadrillage : composantes tres fines, tres longues,
     * parfaitement rectilignes et couvrant une grande partie de la feuille.
     */
    public static Mask removeGridLines(Mask src, int[] dt) {
        Cc cc = Cc.label(src);
        int w = src.w, h = src.h;
        boolean[] drop = new boolean[cc.comps.length];
        for (int i = 0; i < cc.comps.length; i++) {
            Cc.Comp c = cc.comps[i];
            int cw = c.maxX - c.minX + 1, ch = c.maxY - c.minY + 1;
            boolean spans = cw > w * 0.75 || ch > h * 0.75;
            boolean thin = (cw <= 3 && ch > 20) || (ch <= 3 && cw > 20);
            if (spans && thin && c.area < Math.max(cw, ch) * 3.5) drop[i] = true;
        }
        Mask out = new Mask(w, h);
        for (int i = 0; i < src.px.length; i++) {
            int l = cc.labels[i];
            if (l > 0 && !drop[l - 1]) out.px[i] = true;
        }
        return out;
    }

    /**
     * Masque de densite : vrai la ou la proportion d'encre dans un voisinage
     * depasse le seuil. Isole les bandes pleines ou hachurees (voiles, murs
     * porteurs, traits repasses) des traits fins isoles (cloisons, symboles).
     */
    public static Mask densityMask(Mask ink, int radius, double threshold) {
        int w = ink.w, h = ink.h;
        int[] sum = new int[(w + 1) * (h + 1)];
        for (int y = 0; y < h; y++) {
            int row = 0;
            for (int x = 0; x < w; x++) {
                if (ink.px[y * w + x]) row++;
                sum[(y + 1) * (w + 1) + (x + 1)] = sum[y * (w + 1) + (x + 1)] + row;
            }
        }
        Mask out = new Mask(w, h);
        for (int y = 0; y < h; y++) {
            int y0 = Math.max(0, y - radius), y1 = Math.min(h - 1, y + radius);
            for (int x = 0; x < w; x++) {
                if (!ink.px[y * w + x]) continue;
                int x0 = Math.max(0, x - radius), x1 = Math.min(w - 1, x + radius);
                int s = sum[(y1 + 1) * (w + 1) + (x1 + 1)] - sum[y0 * (w + 1) + (x1 + 1)]
                        - sum[(y1 + 1) * (w + 1) + x0] + sum[y0 * (w + 1) + x0];
                double d = s / (double) ((y1 - y0 + 1) * (x1 - x0 + 1));
                if (d >= threshold) out.px[y * w + x] = true;
            }
        }
        return out;
    }

    /** Sous-echantillonnage par moyenne (facteur entier). */
    public static Gray downscale(Gray g, int factor) {
        if (factor <= 1) return g;
        int w = g.w / factor, h = g.h / factor;
        Gray o = new Gray(Math.max(1, w), Math.max(1, h));
        for (int y = 0; y < o.h; y++) {
            for (int x = 0; x < o.w; x++) {
                int s = 0, n = 0;
                for (int dy = 0; dy < factor; dy++) {
                    for (int dx = 0; dx < factor; dx++) {
                        s += g.get(x * factor + dx, y * factor + dy);
                        n++;
                    }
                }
                o.px[y * o.w + x] = (byte) (s / Math.max(1, n));
            }
        }
        return o;
    }
}
