package com.tracecroquis.core.img;

/** Image en niveaux de gris, 8 bits. */
public final class Gray {

    public final int w, h;
    public final byte[] px;

    public Gray(int w, int h) {
        this.w = w; this.h = h;
        this.px = new byte[w * h];
    }

    public int get(int x, int y) {
        if (x < 0) x = 0; else if (x >= w) x = w - 1;
        if (y < 0) y = 0; else if (y >= h) y = h - 1;
        return px[y * w + x] & 0xFF;
    }

    public void set(int x, int y, int v) {
        if (x < 0 || y < 0 || x >= w || y >= h) return;
        px[y * w + x] = (byte) (v < 0 ? 0 : (v > 255 ? 255 : v));
    }

    /** Conversion ARGB -> gris pondere pour l'encre (le bleu du stylo reste sombre). */
    public static Gray fromArgb(int[] argb, int w, int h) {
        Gray g = new Gray(w, h);
        for (int i = 0; i < w * h; i++) {
            int c = argb[i];
            int r = (c >> 16) & 0xFF, gg = (c >> 8) & 0xFF, b = c & 0xFF;
            int lum = (r * 299 + gg * 587 + b * 114) / 1000;
            g.px[i] = (byte) lum;
        }
        return g;
    }

    /**
     * Carte d'encre : privilegie les traits fonces et satures (stylo bleu ou noir)
     * et attenue les quadrillages clairs du papier millimetre.
     */
    public static Gray inkFromArgb(int[] argb, int w, int h) {
        Gray g = new Gray(w, h);
        for (int i = 0; i < w * h; i++) {
            int c = argb[i];
            int r = (c >> 16) & 0xFF, gg = (c >> 8) & 0xFF, b = c & 0xFF;
            int max = Math.max(r, Math.max(gg, b));
            int min = Math.min(r, Math.min(gg, b));
            int lum = (r * 299 + gg * 587 + b * 114) / 1000;
            // Un trait de stylo est sombre ; un quadrillage est clair et peu contraste.
            int v = Math.min(lum, (min * 3 + max) / 4);
            g.px[i] = (byte) v;
        }
        return g;
    }

    public Gray copy() {
        Gray o = new Gray(w, h);
        System.arraycopy(px, 0, o.px, 0, px.length);
        return o;
    }
}
