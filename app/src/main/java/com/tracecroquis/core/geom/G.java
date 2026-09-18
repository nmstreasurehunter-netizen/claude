package com.tracecroquis.core.geom;

import java.util.ArrayList;
import java.util.List;

/** Boite a outils geometrique (Java pur, aucune dependance Android). */
public final class G {

    public static final double EPS = 1e-9;

    private G() { }

    public static double dist(double ax, double ay, double bx, double by) {
        return Math.hypot(bx - ax, by - ay);
    }

    public static double dist2(double ax, double ay, double bx, double by) {
        double dx = bx - ax, dy = by - ay;
        return dx * dx + dy * dy;
    }

    /** Projection du point sur le segment : {x, y, t, distance}. */
    public static double[] project(double px, double py, double ax, double ay, double bx, double by) {
        double dx = bx - ax, dy = by - ay;
        double len2 = dx * dx + dy * dy;
        double t = len2 < EPS ? 0 : ((px - ax) * dx + (py - ay) * dy) / len2;
        double tc = Math.max(0, Math.min(1, t));
        double x = ax + tc * dx, y = ay + tc * dy;
        return new double[]{x, y, t, Math.hypot(px - x, py - y)};
    }

    public static double distPointSeg(double px, double py, double ax, double ay, double bx, double by) {
        return project(px, py, ax, ay, bx, by)[3];
    }

    /** Distance a la droite infinie support du segment. */
    public static double distPointLine(double px, double py, double ax, double ay, double bx, double by) {
        double dx = bx - ax, dy = by - ay;
        double len = Math.hypot(dx, dy);
        if (len < EPS) return dist(px, py, ax, ay);
        return Math.abs((px - ax) * dy - (py - ay) * dx) / len;
    }

    /** Angle du vecteur AB dans [0, 2PI). */
    public static double angle(double ax, double ay, double bx, double by) {
        double a = Math.atan2(by - ay, bx - ax);
        return a < 0 ? a + 2 * Math.PI : a;
    }

    /** Ecart d'orientation de deux directions non orientees, dans [0, PI/2]. */
    public static double angleDiff180(double a, double b) {
        double d = Math.abs(a - b) % Math.PI;
        return Math.min(d, Math.PI - d);
    }

    /** Ecart signe minimal entre deux angles, dans ]-PI, PI]. */
    public static double angleDelta(double a, double b) {
        double d = (b - a) % (2 * Math.PI);
        if (d > Math.PI) d -= 2 * Math.PI;
        if (d <= -Math.PI) d += 2 * Math.PI;
        return d;
    }

    /** Intersection des droites (a1,a2) et (b1,b2). Null si paralleles. */
    public static double[] lineIntersection(double a1x, double a1y, double a2x, double a2y,
                                            double b1x, double b1y, double b2x, double b2y) {
        double d1x = a2x - a1x, d1y = a2y - a1y;
        double d2x = b2x - b1x, d2y = b2y - b1y;
        double den = d1x * d2y - d1y * d2x;
        if (Math.abs(den) < 1e-7) return null;
        double t = ((b1x - a1x) * d2y - (b1y - a1y) * d2x) / den;
        return new double[]{a1x + t * d1x, a1y + t * d1y, t};
    }

    public static boolean segmentsIntersect(double a1x, double a1y, double a2x, double a2y,
                                            double b1x, double b1y, double b2x, double b2y) {
        double d1 = cross(b1x, b1y, b2x, b2y, a1x, a1y);
        double d2 = cross(b1x, b1y, b2x, b2y, a2x, a2y);
        double d3 = cross(a1x, a1y, a2x, a2y, b1x, b1y);
        double d4 = cross(a1x, a1y, a2x, a2y, b2x, b2y);
        return ((d1 > 0) != (d2 > 0)) && ((d3 > 0) != (d4 > 0));
    }

    public static double cross(double ax, double ay, double bx, double by, double px, double py) {
        return (bx - ax) * (py - ay) - (by - ay) * (px - ax);
    }

    /** Recouvrement des projections de deux segments paralleles, en fraction du plus court. */
    public static double overlapRatio(double a1x, double a1y, double a2x, double a2y,
                                      double b1x, double b1y, double b2x, double b2y) {
        double dx = a2x - a1x, dy = a2y - a1y;
        double len = Math.hypot(dx, dy);
        if (len < EPS) return 0;
        dx /= len; dy /= len;
        double t0 = 0, t1 = len;
        double u0 = (b1x - a1x) * dx + (b1y - a1y) * dy;
        double u1 = (b2x - a1x) * dx + (b2y - a1y) * dy;
        if (u0 > u1) { double t = u0; u0 = u1; u1 = t; }
        double inter = Math.min(t1, u1) - Math.max(t0, u0);
        double shortest = Math.min(len, u1 - u0);
        if (shortest < EPS) return 0;
        return Math.max(0, inter) / shortest;
    }

    // ------------------------------------------------------------- polygones

    public static double polygonArea(List<double[]> poly) {
        double a = 0;
        int n = poly.size();
        for (int i = 0; i < n; i++) {
            double[] p = poly.get(i), q = poly.get((i + 1) % n);
            a += p[0] * q[1] - q[0] * p[1];
        }
        return a * 0.5;
    }

    public static double[] centroid(List<double[]> poly) {
        double a = 0, cx = 0, cy = 0;
        int n = poly.size();
        for (int i = 0; i < n; i++) {
            double[] p = poly.get(i), q = poly.get((i + 1) % n);
            double f = p[0] * q[1] - q[0] * p[1];
            a += f;
            cx += (p[0] + q[0]) * f;
            cy += (p[1] + q[1]) * f;
        }
        if (Math.abs(a) < EPS) {
            for (double[] p : poly) { cx += p[0]; cy += p[1]; }
            return new double[]{cx / Math.max(1, n), cy / Math.max(1, n)};
        }
        a *= 0.5;
        return new double[]{cx / (6 * a), cy / (6 * a)};
    }

    public static boolean pointInPolygon(double x, double y, List<double[]> poly) {
        boolean in = false;
        int n = poly.size();
        for (int i = 0, j = n - 1; i < n; j = i++) {
            double[] pi = poly.get(i), pj = poly.get(j);
            if (((pi[1] > y) != (pj[1] > y))
                    && (x < (pj[0] - pi[0]) * (y - pi[1]) / (pj[1] - pi[1] + EPS) + pi[0])) {
                in = !in;
            }
        }
        return in;
    }

    /** Point interieur "sur" au polygone (centroide si dedans, sinon balayage). */
    public static double[] interiorPoint(List<double[]> poly) {
        double[] c = centroid(poly);
        if (pointInPolygon(c[0], c[1], poly)) return c;
        double minX = Double.MAX_VALUE, maxX = -Double.MAX_VALUE;
        double minY = Double.MAX_VALUE, maxY = -Double.MAX_VALUE;
        for (double[] p : poly) {
            minX = Math.min(minX, p[0]); maxX = Math.max(maxX, p[0]);
            minY = Math.min(minY, p[1]); maxY = Math.max(maxY, p[1]);
        }
        for (int i = 1; i < 10; i++) {
            for (int j = 1; j < 10; j++) {
                double x = minX + (maxX - minX) * i / 10.0;
                double y = minY + (maxY - minY) * j / 10.0;
                if (pointInPolygon(x, y, poly)) return new double[]{x, y};
            }
        }
        return c;
    }

    // ------------------------------------------------------------ polylignes

    public static double polylineLength(List<double[]> pts) {
        double l = 0;
        for (int i = 1; i < pts.size(); i++) {
            l += dist(pts.get(i - 1)[0], pts.get(i - 1)[1], pts.get(i)[0], pts.get(i)[1]);
        }
        return l;
    }

    /** Simplification Douglas-Peucker. */
    public static List<double[]> simplify(List<double[]> pts, double eps) {
        int n = pts.size();
        if (n < 3) return new ArrayList<>(pts);
        boolean[] keep = new boolean[n];
        keep[0] = keep[n - 1] = true;
        dp(pts, 0, n - 1, eps, keep);
        List<double[]> out = new ArrayList<>();
        for (int i = 0; i < n; i++) if (keep[i]) out.add(pts.get(i));
        return out;
    }

    private static void dp(List<double[]> pts, int i0, int i1, double eps, boolean[] keep) {
        if (i1 <= i0 + 1) return;
        double[] a = pts.get(i0), b = pts.get(i1);
        double best = -1;
        int bi = -1;
        for (int i = i0 + 1; i < i1; i++) {
            double[] p = pts.get(i);
            double d = distPointSeg(p[0], p[1], a[0], a[1], b[0], b[1]);
            if (d > best) { best = d; bi = i; }
        }
        if (best > eps && bi > 0) {
            keep[bi] = true;
            dp(pts, i0, bi, eps, keep);
            dp(pts, bi, i1, eps, keep);
        }
    }

    /** Droite des moindres carres (ACP) : {x0, y0, dx, dy, rms}. */
    public static double[] fitLine(List<double[]> pts, int from, int to) {
        int n = to - from + 1;
        if (n < 2) return null;
        double mx = 0, my = 0;
        for (int i = from; i <= to; i++) { mx += pts.get(i)[0]; my += pts.get(i)[1]; }
        mx /= n; my /= n;
        double sxx = 0, syy = 0, sxy = 0;
        for (int i = from; i <= to; i++) {
            double dx = pts.get(i)[0] - mx, dy = pts.get(i)[1] - my;
            sxx += dx * dx; syy += dy * dy; sxy += dx * dy;
        }
        double theta = 0.5 * Math.atan2(2 * sxy, sxx - syy);
        double dx = Math.cos(theta), dy = Math.sin(theta);
        double rms = 0;
        for (int i = from; i <= to; i++) {
            double ex = pts.get(i)[0] - mx, ey = pts.get(i)[1] - my;
            double perp = -ex * dy + ey * dx;
            rms += perp * perp;
        }
        rms = Math.sqrt(rms / n);
        return new double[]{mx, my, dx, dy, rms};
    }

    /** Cercle des moindres carres (Kasa) : {cx, cy, r, rms}. Null si degenere. */
    public static double[] fitCircle(List<double[]> pts, int from, int to) {
        int n = to - from + 1;
        if (n < 4) return null;
        double sx = 0, sy = 0, sxx = 0, syy = 0, sxy = 0, sxz = 0, syz = 0, sz = 0;
        for (int i = from; i <= to; i++) {
            double x = pts.get(i)[0], y = pts.get(i)[1];
            double z = x * x + y * y;
            sx += x; sy += y; sxx += x * x; syy += y * y; sxy += x * y;
            sxz += x * z; syz += y * z; sz += z;
        }
        double a11 = 2 * (sxx - sx * sx / n);
        double a12 = 2 * (sxy - sx * sy / n);
        double a22 = 2 * (syy - sy * sy / n);
        double b1 = sxz - sx * sz / n;
        double b2 = syz - sy * sz / n;
        double det = a11 * a22 - a12 * a12;
        if (Math.abs(det) < 1e-6) return null;
        double cx = (b1 * a22 - b2 * a12) / det;
        double cy = (a11 * b2 - a12 * b1) / det;
        double r = 0;
        for (int i = from; i <= to; i++) {
            r += dist(cx, cy, pts.get(i)[0], pts.get(i)[1]);
        }
        r /= n;
        double rms = 0;
        for (int i = from; i <= to; i++) {
            double d = dist(cx, cy, pts.get(i)[0], pts.get(i)[1]) - r;
            rms += d * d;
        }
        rms = Math.sqrt(rms / n);
        return new double[]{cx, cy, r, rms};
    }

    public static double clamp(double v, double lo, double hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }

    public static double median(double[] values, int count) {
        if (count <= 0) return 0;
        double[] c = new double[count];
        System.arraycopy(values, 0, c, 0, count);
        java.util.Arrays.sort(c);
        return c[count / 2];
    }

    public static double median(List<Double> values) {
        if (values.isEmpty()) return 0;
        List<Double> c = new ArrayList<>(values);
        java.util.Collections.sort(c);
        return c.get(c.size() / 2);
    }
}
