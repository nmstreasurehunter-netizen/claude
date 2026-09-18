package com.tracecroquis.ui.view;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

/** Apercu du croquis avec ses quatre coins de cadrage deplacables. */
public class CropView extends View {

    private Bitmap bitmap;
    /** Coins dans le repere de l'image (HG, HD, BD, BG). */
    private final float[] corners = new float[8];
    private final float[] mapped = new float[8];
    private final Matrix toView = new Matrix();
    private final Matrix toImage = new Matrix();
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private int dragging = -1;
    private boolean enabledCrop = true;

    public CropView(Context c) { super(c); }

    public CropView(Context c, AttributeSet a) { super(c, a); }

    public void setBitmap(Bitmap b) {
        this.bitmap = b;
        resetCorners();
        invalidate();
    }

    public Bitmap getBitmap() { return bitmap; }

    public void setCropEnabled(boolean v) {
        enabledCrop = v;
        invalidate();
    }

    public boolean isCropEnabled() { return enabledCrop; }

    public void resetCorners() {
        if (bitmap == null) return;
        float w = bitmap.getWidth(), h = bitmap.getHeight();
        float mx = w * 0.02f, my = h * 0.02f;
        corners[0] = mx;      corners[1] = my;
        corners[2] = w - mx;  corners[3] = my;
        corners[4] = w - mx;  corners[5] = h - my;
        corners[6] = mx;      corners[7] = h - my;
        invalidate();
    }

    /** Coins dans le repere image (copie). */
    public float[] getCorners() {
        return corners.clone();
    }

    public void setCorners(float[] c) {
        if (c != null && c.length == 8) System.arraycopy(c, 0, corners, 0, 8);
        invalidate();
    }

    /** Vrai si le cadrage ne couvre pas toute l'image. */
    public boolean isCropped() {
        if (bitmap == null) return false;
        float w = bitmap.getWidth(), h = bitmap.getHeight();
        float tol = Math.max(w, h) * 0.035f;
        return Math.abs(corners[0]) > tol || Math.abs(corners[1]) > tol
                || Math.abs(corners[2] - w) > tol || Math.abs(corners[3]) > tol
                || Math.abs(corners[4] - w) > tol || Math.abs(corners[5] - h) > tol
                || Math.abs(corners[6]) > tol || Math.abs(corners[7] - h) > tol;
    }

    private void computeMatrix() {
        toView.reset();
        if (bitmap == null) return;
        float vw = getWidth(), vh = getHeight();
        float bw = bitmap.getWidth(), bh = bitmap.getHeight();
        float s = Math.min((vw - 24) / bw, (vh - 24) / bh);
        toView.postScale(s, s);
        toView.postTranslate((vw - bw * s) / 2, (vh - bh * s) / 2);
        toView.invert(toImage);
    }

    @Override
    protected void onDraw(Canvas c) {
        super.onDraw(c);
        if (bitmap == null) {
            paint.setColor(0xFF6E87AB);
            paint.setTextSize(getHeight() * 0.05f);
            paint.setTextAlign(Paint.Align.CENTER);
            c.drawText("Aucun croquis", getWidth() / 2f, getHeight() / 2f, paint);
            paint.setTextAlign(Paint.Align.LEFT);
            return;
        }
        computeMatrix();
        c.drawBitmap(bitmap, toView, paint);
        toView.mapPoints(mapped, corners);

        if (!enabledCrop) return;

        Path p = new Path();
        p.moveTo(mapped[0], mapped[1]);
        p.lineTo(mapped[2], mapped[3]);
        p.lineTo(mapped[4], mapped[5]);
        p.lineTo(mapped[6], mapped[7]);
        p.close();
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(dp(2.5f));
        paint.setColor(0xFF22D3EE);
        c.drawPath(p, paint);

        paint.setStyle(Paint.Style.FILL);
        for (int i = 0; i < 4; i++) {
            float x = mapped[2 * i], y = mapped[2 * i + 1];
            paint.setColor(0xFF1E6BFF);
            c.drawCircle(x, y, dp(13), paint);
            paint.setColor(Color.WHITE);
            c.drawCircle(x, y, dp(7), paint);
        }
    }

    private float dp(float v) {
        return v * getResources().getDisplayMetrics().density;
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        if (bitmap == null || !enabledCrop) return false;
        computeMatrix();
        toView.mapPoints(mapped, corners);
        float x = e.getX(), y = e.getY();
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                dragging = -1;
                float best = dp(40);
                for (int i = 0; i < 4; i++) {
                    float d = (float) Math.hypot(mapped[2 * i] - x, mapped[2 * i + 1] - y);
                    if (d < best) { best = d; dragging = i; }
                }
                if (dragging >= 0) {
                    getParent().requestDisallowInterceptTouchEvent(true);
                    return true;
                }
                return false;
            case MotionEvent.ACTION_MOVE:
                if (dragging < 0) return false;
                float[] pt = {x, y};
                toImage.mapPoints(pt);
                corners[2 * dragging] = clamp(pt[0], 0, bitmap.getWidth());
                corners[2 * dragging + 1] = clamp(pt[1], 0, bitmap.getHeight());
                invalidate();
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                dragging = -1;
                getParent().requestDisallowInterceptTouchEvent(false);
                return true;
            default:
                return false;
        }
    }

    private static float clamp(float v, float lo, float hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }

    public RectF bounds() {
        return new RectF(0, 0, bitmap == null ? 1 : bitmap.getWidth(),
                bitmap == null ? 1 : bitmap.getHeight());
    }
}
