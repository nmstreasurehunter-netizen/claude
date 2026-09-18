package com.tracecroquis.core.vector;

import com.tracecroquis.core.geom.G;

/** Segment de droite issu de la vectorisation. */
public class Seg {

    public double x1, y1, x2, y2;
    /** Epaisseur d'encre mesuree (px). */
    public double thickness = 1;
    /** Index de la polyligne d'origine. */
    public int stroke = -1;
    /** Composante connexe d'origine. */
    public int component = -1;
    public boolean consumed = false;
    /** Vrai si le segment provient d'une bande pleine ou hachuree (mur epais). */
    public boolean band = false;
    /** Ecart moyen des points a la droite ajustee (px) : qualite du trait. */
    public double residual = 0;

    public Seg() { }

    public Seg(double x1, double y1, double x2, double y2) {
        this.x1 = x1; this.y1 = y1; this.x2 = x2; this.y2 = y2;
    }

    public double len() { return Math.hypot(x2 - x1, y2 - y1); }

    public double midX() { return (x1 + x2) / 2; }

    public double midY() { return (y1 + y2) / 2; }

    /** Orientation dans [0, PI[ (direction non orientee). */
    public double ang() {
        double a = Math.atan2(y2 - y1, x2 - x1);
        if (a < 0) a += Math.PI;
        if (a >= Math.PI) a -= Math.PI;
        return a;
    }

    public double dirX() { double l = len(); return l < 1e-9 ? 1 : (x2 - x1) / l; }

    public double dirY() { double l = len(); return l < 1e-9 ? 0 : (y2 - y1) / l; }

    public double distToPoint(double px, double py) {
        return G.distPointSeg(px, py, x1, y1, x2, y2);
    }

    public Seg copy() {
        Seg s = new Seg(x1, y1, x2, y2);
        s.thickness = thickness; s.stroke = stroke; s.component = component; s.band = band;
        s.residual = residual;
        return s;
    }

    /** Etend le segment de d pixels a chaque extremite. */
    public void extend(double d) {
        double dx = dirX(), dy = dirY();
        x1 -= dx * d; y1 -= dy * d;
        x2 += dx * d; y2 += dy * d;
    }

    @Override
    public String toString() {
        return String.format(java.util.Locale.US, "Seg(%.0f,%.0f -> %.0f,%.0f e=%.1f)", x1, y1, x2, y2, thickness);
    }
}
