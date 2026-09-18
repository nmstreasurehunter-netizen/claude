package com.tracecroquis.export;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PointF;
import android.graphics.RectF;

import com.tracecroquis.core.geom.G;
import com.tracecroquis.core.model.Enums.OpeningType;
import com.tracecroquis.core.model.Enums.SymbolType;
import com.tracecroquis.core.model.Enums.WallType;
import com.tracecroquis.core.model.Plan;
import com.tracecroquis.util.Fmt;

import java.util.Locale;

/**
 * Dessin du plan vectorise, conforme aux conventions du dessin d'architecture :
 * murs coupes pleins, cloisons plus fines, portes avec leur debattement,
 * fenetres a trois traits, cotes a bouts obliques, noms et surfaces de pieces.
 */
public final class PlanRenderer {

    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float[] pt = new float[2];
    private final Matrix m;
    private final RenderStyle st;
    private final Plan plan;
    private final float scale;

    public PlanRenderer(Plan plan, Matrix planToCanvas, RenderStyle style) {
        this.plan = plan;
        this.m = planToCanvas;
        this.st = style;
        float[] v = {0, 0, 1, 0};
        m.mapPoints(v);
        this.scale = (float) Math.hypot(v[2] - v[0], v[3] - v[1]);
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeJoin(Paint.Join.MITER);
        fill.setStyle(Paint.Style.FILL);
        textPaint.setTextAlign(Paint.Align.CENTER);
    }

    /** Facteur d'echelle plan -> canvas. */
    public float scale() { return scale; }

    private PointF p(double x, double y) {
        pt[0] = (float) x;
        pt[1] = (float) y;
        m.mapPoints(pt);
        return new PointF(pt[0], pt[1]);
    }

    private float mm(float v) {
        return Math.max(0.35f, v * st.mm);
    }

    public void draw(Canvas c) {
        if (st.showSymbols) drawSymbols(c);
        if (st.showWalls) drawWalls(c);
        if (st.showOpenings) drawOpenings(c);
        if (st.showRooms) drawRooms(c);
        if (st.showTexts) drawFreeTexts(c);
        if (st.showDims) drawDims(c);
    }

    // ------------------------------------------------------------------ murs

    private void drawWalls(Canvas c) {
        int[] deg = new int[plan.nodes.size()];
        for (Plan.Wall w : plan.walls) {
            if (w.a < deg.length) deg[w.a]++;
            if (w.b < deg.length) deg[w.b]++;
        }
        for (Plan.Wall w : plan.walls) {
            float[] q = quad(w, deg);
            if (q == null) continue;
            Path path = new Path();
            path.moveTo(q[0], q[1]);
            path.lineTo(q[2], q[3]);
            path.lineTo(q[4], q[5]);
            path.lineTo(q[6], q[7]);
            path.close();
            boolean porteur = w.type == WallType.VOILE || w.type == WallType.VOILE_EXT
                    || w.type == WallType.MUR_PORTEUR;
            if (st.screen) {
                fill.setColor(porteur ? st.wallSolid : st.partition);
                fill.setAlpha(porteur ? 235 : 190);
                c.drawPath(path, fill);
            } else {
                fill.setColor(porteur ? st.wallSolid : st.partition);
                c.drawPath(path, fill);
                stroke.setColor(st.wallOutline);
                stroke.setStrokeWidth(mm(0.18f));
                c.drawPath(path, stroke);
            }
        }
    }

    /** Contour d'un mur : axe deporte de +/- e/2, prolonge aux jonctions. */
    private float[] quad(Plan.Wall w, int[] deg) {
        if (w.a >= plan.nodes.size() || w.b >= plan.nodes.size()) return null;
        Plan.Node a = plan.nodes.get(w.a), b = plan.nodes.get(w.b);
        double len = G.dist(a.x, a.y, b.x, b.y);
        if (len < 1e-6) return null;
        double dx = (b.x - a.x) / len, dy = (b.y - a.y) / len;
        double t = thicknessPx(w) / 2;
        double ea = deg[w.a] > 1 ? t : 0;
        double eb = deg[w.b] > 1 ? t : 0;
        double ax = a.x - dx * ea, ay = a.y - dy * ea;
        double bx = b.x + dx * eb, by = b.y + dy * eb;
        double nx = -dy * t, ny = dx * t;
        PointF p1 = p(ax + nx, ay + ny);
        PointF p2 = p(bx + nx, by + ny);
        PointF p3 = p(bx - nx, by - ny);
        PointF p4 = p(ax - nx, ay - ny);
        return new float[]{p1.x, p1.y, p2.x, p2.y, p3.x, p3.y, p4.x, p4.y};
    }

    private double thicknessPx(Plan.Wall w) {
        if (plan.pxPerMeter > 0 && w.thicknessM > 0) return w.thicknessM * plan.pxPerMeter;
        return Math.max(2, w.thicknessPx);
    }

    // ------------------------------------------------------------ ouvertures

    private void drawOpenings(Canvas c) {
        for (Plan.Opening op : plan.openings) {
            if (op.wall < 0 || op.wall >= plan.walls.size()) continue;
            Plan.Wall w = plan.walls.get(op.wall);
            if (w.a >= plan.nodes.size() || w.b >= plan.nodes.size()) continue;
            Plan.Node a = plan.nodes.get(w.a), b = plan.nodes.get(w.b);
            double len = G.dist(a.x, a.y, b.x, b.y);
            if (len < 1e-6) continue;
            double dx = (b.x - a.x) / len, dy = (b.y - a.y) / len;
            double nx = -dy, ny = dx;
            double t = thicknessPx(w) / 2;
            double wpx = plan.pxPerMeter > 0 ? op.widthM * plan.pxPerMeter : op.widthM;
            wpx = Math.min(wpx, len * 0.98);
            double cx = a.x + (b.x - a.x) * op.t, cy = a.y + (b.y - a.y) * op.t;
            double x1 = cx - dx * wpx / 2, y1 = cy - dy * wpx / 2;
            double x2 = cx + dx * wpx / 2, y2 = cy + dy * wpx / 2;

            // Percement : on efface la matiere du mur.
            Path hole = new Path();
            PointF q1 = p(x1 + nx * t, y1 + ny * t);
            PointF q2 = p(x2 + nx * t, y2 + ny * t);
            PointF q3 = p(x2 - nx * t, y2 - ny * t);
            PointF q4 = p(x1 - nx * t, y1 - ny * t);
            hole.moveTo(q1.x, q1.y);
            hole.lineTo(q2.x, q2.y);
            hole.lineTo(q3.x, q3.y);
            hole.lineTo(q4.x, q4.y);
            hole.close();
            fill.setColor(st.paper);
            fill.setAlpha(255);
            c.drawPath(hole, fill);

            stroke.setColor(st.opening);
            stroke.setStrokeWidth(mm(0.25f));
            // Tableaux (jambages)
            c.drawLine(q1.x, q1.y, q4.x, q4.y, stroke);
            c.drawLine(q2.x, q2.y, q3.x, q3.y, stroke);

            if (op.type.isWindow()) {
                drawWindow(c, x1, y1, x2, y2, nx, ny, t);
            } else if (op.type.isDoor()) {
                drawDoor(c, op, x1, y1, x2, y2, dx, dy, nx, ny, t, wpx);
            }
        }
    }

    private void drawWindow(Canvas c, double x1, double y1, double x2, double y2,
                            double nx, double ny, double t) {
        stroke.setColor(st.opening);
        stroke.setStrokeWidth(mm(0.22f));
        // Trois traits : les deux dormants et l'axe de la menuiserie.
        double[] offs = {t, 0, -t};
        for (double o : offs) {
            PointF s = p(x1 + nx * o, y1 + ny * o);
            PointF e = p(x2 + nx * o, y2 + ny * o);
            c.drawLine(s.x, s.y, e.x, e.y, stroke);
        }
    }

    private void drawDoor(Canvas c, Plan.Opening op, double x1, double y1, double x2, double y2,
                          double dx, double dy, double nx, double ny, double t, double wpx) {
        boolean hingeAtStart = op.hingeSide >= 0;
        double hx = hingeAtStart ? x1 : x2;
        double hy = hingeAtStart ? y1 : y2;
        double ox = hingeAtStart ? x2 : x1;      // cote oppose
        double oy = hingeAtStart ? y2 : y1;
        double sw = op.swingSide >= 0 ? 1 : -1;

        if (op.type == OpeningType.PORTE_COULISSANTE) {
            stroke.setColor(st.opening);
            stroke.setStrokeWidth(mm(0.5f));
            PointF s = p(x1 + nx * t * 0.6, y1 + ny * t * 0.6);
            PointF e = p(x2 + nx * t * 0.6, y2 + ny * t * 0.6);
            c.drawLine(s.x, s.y, e.x, e.y, stroke);
            return;
        }
        if (op.type == OpeningType.PORTE_GARAGE) {
            stroke.setColor(st.opening);
            stroke.setStrokeWidth(mm(0.35f));
            PointF s = p(x1, y1);
            PointF e = p(x2, y2);
            c.drawLine(s.x, s.y, e.x, e.y, stroke);
            return;
        }

        int leaves = op.type == OpeningType.PORTE_DOUBLE ? 2 : 1;
        double leafLen = leaves == 2 ? wpx / 2 : wpx;
        for (int i = 0; i < leaves; i++) {
            double px0 = i == 0 ? hx : ox;
            double py0 = i == 0 ? hy : oy;
            double sign = i == 0 ? 1 : -1;
            double dirx = (i == 0 ? (ox - hx) : (hx - ox));
            double diry = (i == 0 ? (oy - hy) : (hy - oy));
            double dl = Math.hypot(dirx, diry);
            if (dl < 1e-6) continue;
            dirx /= dl; diry /= dl;

            // Vantail ouvert a 90 degres par defaut.
            double ang = Math.toRadians(G.clamp(op.swingAngleDeg, 30, 120));
            double cos = Math.cos(ang), sin = Math.sin(ang) * sw;
            double lx = dirx * cos + (-diry) * sin;
            double ly = diry * cos + (dirx) * sin;
            PointF h = p(px0, py0);
            PointF leaf = p(px0 + lx * leafLen, py0 + ly * leafLen);
            stroke.setColor(st.opening);
            stroke.setStrokeWidth(mm(0.4f));
            c.drawLine(h.x, h.y, leaf.x, leaf.y, stroke);

            // Debattement
            PointF closed = p(px0 + dirx * leafLen, py0 + diry * leafLen);
            float rx = (float) (leafLen * scale);
            RectF oval = new RectF(h.x - rx, h.y - rx, h.x + rx, h.y + rx);
            float a0 = (float) Math.toDegrees(Math.atan2(closed.y - h.y, closed.x - h.x));
            float a1 = (float) Math.toDegrees(Math.atan2(leaf.y - h.y, leaf.x - h.x));
            float sweep = a1 - a0;
            while (sweep > 180) sweep -= 360;
            while (sweep < -180) sweep += 360;
            stroke.setStrokeWidth(mm(0.15f));
            stroke.setColor(st.opening);
            stroke.setAlpha(st.screen ? 150 : 120);
            c.drawArc(oval, a0, sweep, false, stroke);
            stroke.setAlpha(255);
        }
    }

    // ---------------------------------------------------------------- pieces

    private void drawRooms(Canvas c) {
        textPaint.setColor(st.roomText);
        for (Plan.Room r : plan.rooms) {
            if (r.poly.size() < 3) continue;
            if (st.screen) {
                Path path = new Path();
                for (int i = 0; i < r.poly.size(); i++) {
                    double[] q = r.poly.get(i);
                    PointF v = p(q[0], q[1]);
                    if (i == 0) path.moveTo(v.x, v.y); else path.lineTo(v.x, v.y);
                }
                path.close();
                fill.setColor(r.exterior ? 0x2216A34A : 0x1A2563EB);
                c.drawPath(path, fill);
            }
            PointF l = p(r.labelX, r.labelY);
            float size = mm(3.2f);
            textPaint.setTextSize(size);
            textPaint.setFakeBoldText(true);
            String name = r.name == null || r.name.trim().isEmpty() ? "" : r.name.toUpperCase(Locale.FRENCH);
            if (!name.isEmpty()) c.drawText(name, l.x, l.y, textPaint);
            if (st.showAreas) {
                double area = plan.areaOf(r);
                if (area > 0.2) {
                    textPaint.setFakeBoldText(false);
                    textPaint.setTextSize(size * 0.85f);
                    c.drawText(Fmt.m2(area), l.x, l.y + size * 1.15f, textPaint);
                }
            }
        }
        textPaint.setFakeBoldText(false);
    }

    private void drawFreeTexts(Canvas c) {
        textPaint.setColor(st.text);
        textPaint.setFakeBoldText(false);
        for (Plan.TextItem t : plan.texts) {
            if (t.text == null || t.text.trim().isEmpty()) continue;
            if (t.role == com.tracecroquis.core.model.Enums.TextRole.NOM_PIECE && t.room >= 0) continue;
            if (t.role == com.tracecroquis.core.model.Enums.TextRole.SURFACE && t.room >= 0) continue;
            if (t.role == com.tracecroquis.core.model.Enums.TextRole.COTE) continue;
            PointF v = p(t.x, t.y);
            textPaint.setTextSize(mm(2.6f));
            c.drawText(t.text, v.x, v.y, textPaint);
        }
    }

    // ----------------------------------------------------------------- cotes

    private void drawDims(Canvas c) {
        stroke.setColor(st.dim);
        textPaint.setColor(st.dim);
        for (Plan.DimItem d : plan.dims) {
            PointF a = p(d.x1, d.y1);
            PointF b = p(d.x2, d.y2);
            float dx = b.x - a.x, dy = b.y - a.y;
            float len = (float) Math.hypot(dx, dy);
            if (len < 2) continue;
            dx /= len; dy /= len;
            stroke.setStrokeWidth(mm(0.18f));
            c.drawLine(a.x, a.y, b.x, b.y, stroke);
            // Bouts obliques a 45 degres
            float tick = mm(1.6f);
            for (PointF e : new PointF[]{a, b}) {
                float ox = (dx + -dy) * tick / 1.414f;
                float oy = (dy + dx) * tick / 1.414f;
                c.drawLine(e.x - ox, e.y - oy, e.x + ox, e.y + oy, stroke);
            }
            String label;
            if (!Double.isNaN(d.valueM) && d.valueM > 0) {
                label = "m".equals(st.unit) ? Fmt.m(d.valueM) : Fmt.cm(d.valueM);
            } else if (plan.pxPerMeter > 0) {
                double v = G.dist(d.x1, d.y1, d.x2, d.y2) / plan.pxPerMeter;
                label = "m".equals(st.unit) ? Fmt.m(v) : Fmt.cm(v);
            } else {
                label = "";
            }
            if (label.isEmpty()) continue;
            float ang = (float) Math.toDegrees(Math.atan2(dy, dx));
            if (ang > 90) ang -= 180;
            if (ang < -90) ang += 180;
            c.save();
            float mx = (a.x + b.x) / 2, my = (a.y + b.y) / 2;
            c.rotate(ang, mx, my);
            textPaint.setTextSize(mm(2.8f));
            c.drawText(label, mx, my - mm(1.2f), textPaint);
            c.restore();
        }
    }

    // -------------------------------------------------------------- symboles

    private void drawSymbols(Canvas c) {
        stroke.setColor(st.symbol);
        for (Plan.SymbolItem s : plan.symbols) {
            float width = s.type == SymbolType.ESCALIER ? mm(0.25f) : mm(0.18f);
            stroke.setStrokeWidth(width);
            stroke.setColor(colorOf(s.type));
            for (double[] path : s.paths) {
                if (path.length < 4) continue;
                Path pp = new Path();
                for (int i = 0; i + 1 < path.length; i += 2) {
                    PointF v = p(path[i], path[i + 1]);
                    if (i == 0) pp.moveTo(v.x, v.y); else pp.lineTo(v.x, v.y);
                }
                c.drawPath(pp, stroke);
            }
        }
    }

    private int colorOf(SymbolType t) {
        if (!st.screen) return st.symbol;
        switch (t) {
            case ESCALIER: return 0xFFB45309;
            case VEHICULE: return 0xFFB91C1C;
            case CARRELAGE: return 0xFF0EA5E9;
            case TERRASSE: return 0xFF65A30D;
            case ISOLANT: return 0xFFDB2777;
            case SANITAIRE: return 0xFF0891B2;
            default: return st.symbol;
        }
    }

    // ------------------------------------------------------- reperes du plan

    /** Fleche du nord et echelle graphique (rendu papier). */
    public void drawSheetMarks(Canvas c, RectF area, int scaleDen) {
        if (st.showNorth) {
            float cx = area.right - mm(14), cy = area.top + mm(14), r = mm(6);
            stroke.setColor(st.text);
            stroke.setStrokeWidth(mm(0.25f));
            c.drawCircle(cx, cy, r, stroke);
            Path arrow = new Path();
            arrow.moveTo(cx, cy - r);
            arrow.lineTo(cx - r * 0.35f, cy + r * 0.5f);
            arrow.lineTo(cx, cy + r * 0.15f);
            arrow.lineTo(cx + r * 0.35f, cy + r * 0.5f);
            arrow.close();
            fill.setColor(st.text);
            c.drawPath(arrow, fill);
            textPaint.setColor(st.text);
            textPaint.setTextSize(mm(2.6f));
            c.drawText("N", cx, cy - r - mm(1.2f), textPaint);
        }
        if (st.showScaleBar && scaleDen > 0) {
            float x = area.left + mm(2), y = area.bottom - mm(3);
            float meter = (float) (1000.0 / scaleDen * st.mm);   // 1 m dessine
            int steps = 5;
            fill.setColor(st.text);
            stroke.setColor(st.text);
            stroke.setStrokeWidth(mm(0.2f));
            for (int i = 0; i < steps; i++) {
                RectF r = new RectF(x + i * meter, y - mm(1.4f), x + (i + 1) * meter, y);
                if (i % 2 == 0) c.drawRect(r, fill); else c.drawRect(r, stroke);
            }
            textPaint.setColor(st.text);
            textPaint.setTextSize(mm(2.2f));
            textPaint.setTextAlign(Paint.Align.LEFT);
            c.drawText("0", x, y + mm(2.6f), textPaint);
            c.drawText(steps + " m", x + steps * meter - mm(4), y + mm(2.6f), textPaint);
            textPaint.setTextAlign(Paint.Align.CENTER);
        }
    }

    /** Matrice ajustant le plan dans un rectangle (avec marge). */
    public static Matrix fitMatrix(Plan plan, RectF target, float padding) {
        double[] b = plan.bounds();
        float pw = (float) Math.max(1, b[2] - b[0]);
        float ph = (float) Math.max(1, b[3] - b[1]);
        float sx = (target.width() - 2 * padding) / pw;
        float sy = (target.height() - 2 * padding) / ph;
        float s = Math.min(sx, sy);
        Matrix m = new Matrix();
        m.postTranslate((float) -b[0], (float) -b[1]);
        m.postScale(s, s);
        m.postTranslate(target.left + padding + (target.width() - 2 * padding - pw * s) / 2,
                target.top + padding + (target.height() - 2 * padding - ph * s) / 2);
        return m;
    }

    /** Matrice a l'echelle exacte demandee (rendu papier). */
    public static Matrix scaleMatrix(Plan plan, RectF target, int scaleDen, float mmUnit) {
        double ppm = plan.pxPerMeter > 0 ? plan.pxPerMeter : 100;
        float s = (float) (1000.0 / scaleDen * mmUnit / ppm);   // px plan -> unites canvas
        double[] b = plan.bounds();
        float w = (float) (b[2] - b[0]) * s;
        float h = (float) (b[3] - b[1]) * s;
        Matrix m = new Matrix();
        m.postTranslate((float) -b[0], (float) -b[1]);
        m.postScale(s, s);
        m.postTranslate(target.left + (target.width() - w) / 2, target.top + (target.height() - h) / 2);
        return m;
    }

    public static int alpha(int color, int a) {
        return (color & 0x00FFFFFF) | ((a & 0xFF) << 24);
    }

    public static int dim(int color, float f) {
        int r = (int) (Color.red(color) * f), g = (int) (Color.green(color) * f), b = (int) (Color.blue(color) * f);
        return Color.argb(Color.alpha(color), Math.min(255, r), Math.min(255, g), Math.min(255, b));
    }
}
