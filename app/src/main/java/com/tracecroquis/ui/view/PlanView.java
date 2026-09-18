package com.tracecroquis.ui.view;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PointF;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;

import com.tracecroquis.core.geom.G;
import com.tracecroquis.core.model.Plan;
import com.tracecroquis.export.PlanRenderer;
import com.tracecroquis.export.RenderStyle;

/**
 * Affichage et edition du plan vectorise : navigation (deplacement, zoom),
 * selection des elements, deplacement des murs et des noeuds, pose
 * d'ouvertures et de cotes, choix des deux points de reference d'echelle.
 */
public class PlanView extends View {

    public static final int TOOL_SELECT = 0;
    public static final int TOOL_WALL = 1;
    public static final int TOOL_OPENING = 2;
    public static final int TOOL_DIM = 3;
    public static final int TOOL_TEXT = 4;
    public static final int TOOL_SCALE = 5;

    public static final int SEL_NONE = 0;
    public static final int SEL_WALL = 1;
    public static final int SEL_OPENING = 2;
    public static final int SEL_TEXT = 3;
    public static final int SEL_DIM = 4;
    public static final int SEL_NODE = 5;
    public static final int SEL_ROOM = 6;

    /** Retours vers l'ecran hote. */
    public interface Listener {
        /** Element touche deux fois ou selectionne pour edition. */
        void onPick(int type, int index);

        /** Le plan a ete modifie par une manipulation directe. */
        void onPlanChanged();

        /** Les deux points de reference d'echelle ont ete places. */
        void onScalePoints(double[] a, double[] b);
    }

    private Plan plan;
    private Bitmap sketch;
    /** Planche complete affichee telle quelle (apercu d'export). */
    private Bitmap sheet;
    private float sketchAlpha = 0.35f;
    private boolean editable;
    private int tool = TOOL_SELECT;
    private Listener listener;

    private final Matrix base = new Matrix();
    private final Matrix user = new Matrix();
    private final Matrix total = new Matrix();
    private final Matrix inverse = new Matrix();
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private RenderStyle style;

    private int selType = SEL_NONE;
    private int selIndex = -1;
    private double[] scaleA, scaleB;
    private double[] newWallStart;
    private double[] dragLast;
    private boolean dragged;
    private long downTime;
    private ScaleGestureDetector scaleDetector;
    private boolean scaling;
    private float zoomPercent = 100;

    public PlanView(Context c) { super(c); init(); }

    public PlanView(Context c, AttributeSet a) { super(c, a); init(); }

    private void init() {
        style = RenderStyle.screenStyle(getResources().getDisplayMetrics().density);
        scaleDetector = new ScaleGestureDetector(getContext(),
                new ScaleGestureDetector.SimpleOnScaleGestureListener() {
                    @Override public boolean onScale(ScaleGestureDetector d) {
                        float f = d.getScaleFactor();
                        user.postScale(f, f, d.getFocusX(), d.getFocusY());
                        zoomPercent *= f;
                        invalidate();
                        return true;
                    }
                    @Override public boolean onScaleBegin(ScaleGestureDetector d) {
                        scaling = true;
                        return true;
                    }
                    @Override public void onScaleEnd(ScaleGestureDetector d) {
                        scaling = false;
                    }
                });
    }

    // --------------------------------------------------------------- reglages

    public void setPlan(Plan p) {
        this.plan = p;
        requestFit();
    }

    public Plan getPlan() { return plan; }

    /** Affiche une planche deja composee (etape 4) a la place du plan. */
    public void setSheet(Bitmap b) {
        this.sheet = b;
        requestFit();
    }

    public void setSketch(Bitmap b) {
        this.sketch = b;
        invalidate();
    }

    public void setSketchAlpha(float a) {
        this.sketchAlpha = a;
        invalidate();
    }

    public float getSketchAlpha() { return sketchAlpha; }

    public void setEditable(boolean v) {
        this.editable = v;
        invalidate();
    }

    public void setTool(int t) {
        this.tool = t;
        newWallStart = null;
        if (t != TOOL_SCALE) { /* les reperes restent affiches */ }
        invalidate();
    }

    public int getTool() { return tool; }

    public RenderStyle style() { return style; }

    public void setStyle(RenderStyle s) {
        this.style = s;
        invalidate();
    }

    public void setListener(Listener l) { this.listener = l; }

    public void select(int type, int index) {
        selType = type;
        selIndex = index;
        invalidate();
    }

    public int selectionType() { return selType; }

    public int selectionIndex() { return selIndex; }

    public double[] scalePointA() { return scaleA; }

    public double[] scalePointB() { return scaleB; }

    public void setScalePoints(double[] a, double[] b) {
        scaleA = a;
        scaleB = b;
        invalidate();
    }

    public int zoom() { return Math.round(zoomPercent); }

    public void resetView() {
        user.reset();
        zoomPercent = 100;
        invalidate();
    }

    public void requestFit() {
        user.reset();
        zoomPercent = 100;
        invalidate();
    }

    // ---------------------------------------------------------------- dessin

    private void computeBase() {
        base.reset();
        RectF target = new RectF(0, 0, getWidth(), getHeight());
        if (sheet != null) {
            float s = Math.min(target.width() / sheet.getWidth(), target.height() / sheet.getHeight());
            base.postScale(s, s);
            base.postTranslate((target.width() - sheet.getWidth() * s) / 2,
                    (target.height() - sheet.getHeight() * s) / 2);
            return;
        }
        if (plan == null) return;
        double[] b = plan.bounds();
        if (sketch != null) {
            b = new double[]{Math.min(b[0], 0), Math.min(b[1], 0),
                    Math.max(b[2], sketch.getWidth()), Math.max(b[3], sketch.getHeight())};
        }
        float pw = (float) Math.max(1, b[2] - b[0]);
        float ph = (float) Math.max(1, b[3] - b[1]);
        float pad = dp(10);
        float s = Math.min((target.width() - 2 * pad) / pw, (target.height() - 2 * pad) / ph);
        base.postTranslate((float) -b[0], (float) -b[1]);
        base.postScale(s, s);
        base.postTranslate(pad + (target.width() - 2 * pad - pw * s) / 2,
                pad + (target.height() - 2 * pad - ph * s) / 2);
    }

    private void computeTotal() {
        computeBase();
        total.set(base);
        total.postConcat(user);
        total.invert(inverse);
    }

    @Override
    protected void onDraw(Canvas c) {
        super.onDraw(c);
        if (sheet != null) {
            computeTotal();
            paint.setFilterBitmap(true);
            c.drawBitmap(sheet, total, paint);
            return;
        }
        if (plan == null) return;
        computeTotal();

        if (sketch != null && sketchAlpha > 0.01f) {
            paint.setAlpha((int) (sketchAlpha * 255));
            c.drawBitmap(sketch, total, paint);
            paint.setAlpha(255);
        }

        PlanRenderer r = new PlanRenderer(plan, total, style);
        r.draw(c);

        if (editable) drawHandles(c);
        drawScaleMarks(c);
    }

    private void drawHandles(Canvas c) {
        paint.setStyle(Paint.Style.FILL);
        float rad = dp(3.5f);
        for (Plan.Node n : plan.nodes) {
            PointF p = map(n.x, n.y);
            paint.setColor(0xFFEF4444);
            c.drawCircle(p.x, p.y, rad, paint);
        }
        if (selType == SEL_WALL && selIndex >= 0 && selIndex < plan.walls.size()) {
            Plan.Wall w = plan.walls.get(selIndex);
            PointF a = map(plan.nodes.get(w.a).x, plan.nodes.get(w.a).y);
            PointF b = map(plan.nodes.get(w.b).x, plan.nodes.get(w.b).y);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(dp(3));
            paint.setColor(0xFF22D3EE);
            c.drawLine(a.x, a.y, b.x, b.y, paint);
            paint.setStyle(Paint.Style.FILL);
            c.drawCircle(a.x, a.y, dp(7), paint);
            c.drawCircle(b.x, b.y, dp(7), paint);
        } else if (selType == SEL_NODE && selIndex >= 0 && selIndex < plan.nodes.size()) {
            PointF p = map(plan.nodes.get(selIndex).x, plan.nodes.get(selIndex).y);
            paint.setColor(0xFF22D3EE);
            c.drawCircle(p.x, p.y, dp(9), paint);
        } else if (selType == SEL_OPENING && selIndex >= 0 && selIndex < plan.openings.size()) {
            Plan.Opening op = plan.openings.get(selIndex);
            if (op.wall >= 0 && op.wall < plan.walls.size()) {
                Plan.Wall w = plan.walls.get(op.wall);
                Plan.Node a = plan.nodes.get(w.a), b = plan.nodes.get(w.b);
                PointF p = map(a.x + (b.x - a.x) * op.t, a.y + (b.y - a.y) * op.t);
                paint.setColor(0xFF22D3EE);
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(dp(2.5f));
                c.drawCircle(p.x, p.y, dp(12), paint);
                paint.setStyle(Paint.Style.FILL);
            }
        } else if (selType == SEL_TEXT && selIndex >= 0 && selIndex < plan.texts.size()) {
            Plan.TextItem t = plan.texts.get(selIndex);
            PointF p = map(t.x, t.y);
            paint.setColor(0xFF22D3EE);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(dp(2));
            c.drawRect(p.x - dp(26), p.y - dp(12), p.x + dp(26), p.y + dp(12), paint);
            paint.setStyle(Paint.Style.FILL);
        }
        if (newWallStart != null) {
            PointF p = map(newWallStart[0], newWallStart[1]);
            paint.setColor(0xFF22D3EE);
            c.drawCircle(p.x, p.y, dp(8), paint);
        }
    }

    private void drawScaleMarks(Canvas c) {
        if (scaleA == null && scaleB == null) return;
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(dp(2));
        paint.setColor(0xFF22D3EE);
        PointF a = scaleA == null ? null : map(scaleA[0], scaleA[1]);
        PointF b = scaleB == null ? null : map(scaleB[0], scaleB[1]);
        if (a != null && b != null) c.drawLine(a.x, a.y, b.x, b.y, paint);
        paint.setStyle(Paint.Style.FILL);
        if (a != null) badge(c, a, "A");
        if (b != null) badge(c, b, "B");
    }

    private void badge(Canvas c, PointF p, String label) {
        paint.setColor(0xFF22D3EE);
        c.drawCircle(p.x, p.y, dp(11), paint);
        paint.setColor(0xFF05101F);
        paint.setTextSize(dp(12));
        paint.setTextAlign(Paint.Align.CENTER);
        c.drawText(label, p.x, p.y + dp(4.2f), paint);
        paint.setTextAlign(Paint.Align.LEFT);
    }

    private PointF map(double x, double y) {
        float[] p = {(float) x, (float) y};
        total.mapPoints(p);
        return new PointF(p[0], p[1]);
    }

    private double[] unmap(float x, float y) {
        float[] p = {x, y};
        inverse.mapPoints(p);
        return new double[]{p[0], p[1]};
    }

    private float dp(float v) {
        return v * getResources().getDisplayMetrics().density;
    }

    /** Tolerance de selection exprimee en unites plan. */
    private double tol() {
        float[] v = {0, 0, 1, 0};
        total.mapPoints(v);
        double s = Math.hypot(v[2] - v[0], v[3] - v[1]);
        return dp(22) / Math.max(1e-6, s);
    }

    // ---------------------------------------------------------------- touche

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        if (sheet != null) {
            computeTotal();
            scaleDetector.onTouchEvent(e);
            if (e.getActionMasked() == MotionEvent.ACTION_DOWN) {
                getParent().requestDisallowInterceptTouchEvent(true);
                dragLast = new double[]{e.getX(), e.getY()};
            } else if (e.getActionMasked() == MotionEvent.ACTION_MOVE && dragLast != null
                    && e.getPointerCount() == 1) {
                user.postTranslate((float) (e.getX() - dragLast[0]), (float) (e.getY() - dragLast[1]));
                dragLast[0] = e.getX();
                dragLast[1] = e.getY();
                invalidate();
            } else if (e.getActionMasked() == MotionEvent.ACTION_UP
                    || e.getActionMasked() == MotionEvent.ACTION_CANCEL) {
                dragLast = null;
                getParent().requestDisallowInterceptTouchEvent(false);
            }
            return true;
        }
        if (plan == null) return false;
        computeTotal();
        scaleDetector.onTouchEvent(e);
        if (scaling) return true;

        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                getParent().requestDisallowInterceptTouchEvent(true);
                downTime = System.currentTimeMillis();
                dragged = false;
                dragLast = new double[]{e.getX(), e.getY()};
                if (editable) startEdit(unmap(e.getX(), e.getY()));
                return true;

            case MotionEvent.ACTION_MOVE:
                if (e.getPointerCount() > 1 || dragLast == null) return true;
                float dx = (float) (e.getX() - dragLast[0]);
                float dy = (float) (e.getY() - dragLast[1]);
                if (Math.hypot(dx, dy) > dp(3)) dragged = true;
                dragLast[0] = e.getX();
                dragLast[1] = e.getY();
                if (editable && dragged && moveSelection(dx, dy)) {
                    invalidate();
                    return true;
                }
                user.postTranslate(dx, dy);
                invalidate();
                return true;

            case MotionEvent.ACTION_UP:
                getParent().requestDisallowInterceptTouchEvent(false);
                if (!dragged && System.currentTimeMillis() - downTime < 400) {
                    handleTap(unmap(e.getX(), e.getY()));
                } else if (editable && dragged) {
                    finishEdit(unmap(e.getX(), e.getY()));
                }
                dragLast = null;
                invalidate();
                return true;

            case MotionEvent.ACTION_CANCEL:
                dragLast = null;
                getParent().requestDisallowInterceptTouchEvent(false);
                return true;
            default:
                return true;
        }
    }

    private void startEdit(double[] p) {
        if (tool == TOOL_WALL || tool == TOOL_DIM) {
            newWallStart = p;
        }
    }

    /** Deplacement direct d'un noeud ou d'un mur selectionne. */
    private boolean moveSelection(float dxView, float dyView) {
        if (tool != TOOL_SELECT || selType == SEL_NONE) return false;
        float[] v = {0, 0, 1, 0};
        total.mapPoints(v);
        double s = Math.hypot(v[2] - v[0], v[3] - v[1]);
        if (s < 1e-6) return false;
        double dx = dxView / s, dy = dyView / s;
        if (selType == SEL_NODE && selIndex >= 0 && selIndex < plan.nodes.size()) {
            plan.nodes.get(selIndex).x += dx;
            plan.nodes.get(selIndex).y += dy;
            return true;
        }
        if (selType == SEL_WALL && selIndex >= 0 && selIndex < plan.walls.size()) {
            Plan.Wall w = plan.walls.get(selIndex);
            plan.nodes.get(w.a).x += dx;
            plan.nodes.get(w.a).y += dy;
            plan.nodes.get(w.b).x += dx;
            plan.nodes.get(w.b).y += dy;
            return true;
        }
        if (selType == SEL_TEXT && selIndex >= 0 && selIndex < plan.texts.size()) {
            plan.texts.get(selIndex).x += dx;
            plan.texts.get(selIndex).y += dy;
            return true;
        }
        if (selType == SEL_OPENING && selIndex >= 0 && selIndex < plan.openings.size()) {
            Plan.Opening op = plan.openings.get(selIndex);
            if (op.wall < 0 || op.wall >= plan.walls.size()) return false;
            Plan.Wall w = plan.walls.get(op.wall);
            Plan.Node a = plan.nodes.get(w.a), b = plan.nodes.get(w.b);
            double len = G.dist(a.x, a.y, b.x, b.y);
            if (len < 1e-6) return false;
            double t = ((b.x - a.x) * dx + (b.y - a.y) * dy) / (len * len);
            op.t = G.clamp(op.t + t, 0.02, 0.98);
            return true;
        }
        return false;
    }

    private void finishEdit(double[] p) {
        if (newWallStart != null && (tool == TOOL_WALL || tool == TOOL_DIM)) {
            double len = G.dist(newWallStart[0], newWallStart[1], p[0], p[1]);
            if (len > tol()) {
                if (tool == TOOL_WALL) addWall(newWallStart, p);
                else addDim(newWallStart, p);
            }
            newWallStart = null;
        }
        if (listener != null) listener.onPlanChanged();
    }

    private void addWall(double[] a, double[] b) {
        int na = snapNode(a);
        int nb = snapNode(b);
        if (na == nb) return;
        Plan.Wall w = new Plan.Wall(na, nb);
        w.thicknessM = 0.07;
        w.thicknessPx = plan.pxPerMeter > 0 ? 0.07 * plan.pxPerMeter : 5;
        w.userEdited = true;
        plan.walls.add(w);
        selType = SEL_WALL;
        selIndex = plan.walls.size() - 1;
    }

    private void addDim(double[] a, double[] b) {
        Plan.DimItem d = new Plan.DimItem();
        d.x1 = a[0]; d.y1 = a[1]; d.x2 = b[0]; d.y2 = b[1];
        d.vertical = Math.abs(b[1] - a[1]) > Math.abs(b[0] - a[0]);
        plan.dims.add(d);
        selType = SEL_DIM;
        selIndex = plan.dims.size() - 1;
    }

    private int snapNode(double[] p) {
        double best = tol();
        int bi = -1;
        for (int i = 0; i < plan.nodes.size(); i++) {
            Plan.Node n = plan.nodes.get(i);
            double d = G.dist(n.x, n.y, p[0], p[1]);
            if (d < best) { best = d; bi = i; }
        }
        if (bi >= 0) return bi;
        return plan.addNode(p[0], p[1]);
    }

    private void handleTap(double[] p) {
        if (tool == TOOL_SCALE) {
            if (scaleA == null || (scaleA != null && scaleB != null)) {
                scaleA = p;
                scaleB = null;
            } else {
                scaleB = p;
                if (listener != null) listener.onScalePoints(scaleA, scaleB);
            }
            invalidate();
            return;
        }
        if (tool == TOOL_OPENING) {
            int wi = hitWall(p);
            if (wi >= 0) {
                addOpening(wi, p);
                if (listener != null) listener.onPlanChanged();
                invalidate();
            }
            return;
        }
        if (tool == TOOL_TEXT) {
            int ti = hitText(p);
            if (ti < 0) {
                Plan.TextItem t = new Plan.TextItem();
                t.x = p[0];
                t.y = p[1];
                t.heightPx = Math.max(10, plan.pxPerMeter * 0.25);
                plan.texts.add(t);
                ti = plan.texts.size() - 1;
            }
            selType = SEL_TEXT;
            selIndex = ti;
            if (listener != null) listener.onPick(SEL_TEXT, ti);
            invalidate();
            return;
        }

        int type = SEL_NONE;
        int index = -1;
        int ni = hitNode(p);
        int oi = hitOpening(p);
        int ti = hitText(p);
        int di = hitDim(p);
        int wi = hitWall(p);
        if (oi >= 0) { type = SEL_OPENING; index = oi; }
        else if (ni >= 0) { type = SEL_NODE; index = ni; }
        else if (ti >= 0) { type = SEL_TEXT; index = ti; }
        else if (di >= 0) { type = SEL_DIM; index = di; }
        else if (wi >= 0) { type = SEL_WALL; index = wi; }
        else {
            int ri = hitRoom(p);
            if (ri >= 0) { type = SEL_ROOM; index = ri; }
        }
        boolean same = type == selType && index == selIndex;
        selType = type;
        selIndex = index;
        invalidate();
        if (listener != null && type != SEL_NONE) {
            if (same || tool != TOOL_SELECT) listener.onPick(type, index);
            else listener.onPick(type, index);
        }
    }

    private void addOpening(int wi, double[] p) {
        Plan.Wall w = plan.walls.get(wi);
        Plan.Node a = plan.nodes.get(w.a), b = plan.nodes.get(w.b);
        double[] pr = G.project(p[0], p[1], a.x, a.y, b.x, b.y);
        Plan.Opening op = new Plan.Opening();
        op.wall = wi;
        op.t = G.clamp(pr[2], 0.05, 0.95);
        op.widthM = 0.83;
        op.type = com.tracecroquis.core.model.Enums.OpeningType.PORTE;
        plan.openings.add(op);
        selType = SEL_OPENING;
        selIndex = plan.openings.size() - 1;
        if (listener != null) listener.onPick(SEL_OPENING, selIndex);
    }

    // ------------------------------------------------------------- detection

    public int hitWall(double[] p) {
        double best = tol();
        int bi = -1;
        for (int i = 0; i < plan.walls.size(); i++) {
            Plan.Wall w = plan.walls.get(i);
            if (w.a >= plan.nodes.size() || w.b >= plan.nodes.size()) continue;
            Plan.Node a = plan.nodes.get(w.a), b = plan.nodes.get(w.b);
            double d = G.distPointSeg(p[0], p[1], a.x, a.y, b.x, b.y);
            double band = Math.max(best, w.thicknessPx * 0.6);
            if (d < band && d < best + w.thicknessPx) { best = d; bi = i; }
        }
        return bi;
    }

    public int hitNode(double[] p) {
        double best = tol() * 0.7;
        int bi = -1;
        for (int i = 0; i < plan.nodes.size(); i++) {
            Plan.Node n = plan.nodes.get(i);
            double d = G.dist(n.x, n.y, p[0], p[1]);
            if (d < best) { best = d; bi = i; }
        }
        return bi;
    }

    public int hitOpening(double[] p) {
        double best = tol() * 0.9;
        int bi = -1;
        for (int i = 0; i < plan.openings.size(); i++) {
            Plan.Opening op = plan.openings.get(i);
            if (op.wall < 0 || op.wall >= plan.walls.size()) continue;
            Plan.Wall w = plan.walls.get(op.wall);
            Plan.Node a = plan.nodes.get(w.a), b = plan.nodes.get(w.b);
            double x = a.x + (b.x - a.x) * op.t, y = a.y + (b.y - a.y) * op.t;
            double d = G.dist(x, y, p[0], p[1]);
            if (d < best) { best = d; bi = i; }
        }
        return bi;
    }

    public int hitText(double[] p) {
        double best = tol() * 1.4;
        int bi = -1;
        for (int i = 0; i < plan.texts.size(); i++) {
            Plan.TextItem t = plan.texts.get(i);
            double d = G.dist(t.x, t.y, p[0], p[1]);
            if (d < best) { best = d; bi = i; }
        }
        return bi;
    }

    public int hitDim(double[] p) {
        double best = tol();
        int bi = -1;
        for (int i = 0; i < plan.dims.size(); i++) {
            Plan.DimItem d = plan.dims.get(i);
            double dd = G.distPointSeg(p[0], p[1], d.x1, d.y1, d.x2, d.y2);
            if (dd < best) { best = dd; bi = i; }
        }
        return bi;
    }

    public int hitRoom(double[] p) {
        for (int i = 0; i < plan.rooms.size(); i++) {
            if (G.pointInPolygon(p[0], p[1], plan.rooms.get(i).poly)) return i;
        }
        return -1;
    }
}
