package com.tracecroquis.export;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;

import com.tracecroquis.core.model.Plan;

/** Composition d'une planche complete : cadre, plan a l'echelle, reperes, cartouche. */
public final class SheetRenderer {

    private SheetRenderer() { }

    /**
     * Dessine la planche.
     *
     * @param mm nombre d'unites canvas pour un millimetre papier
     */
    public static void draw(Canvas c, Plan plan, Layout layout, boolean showDims, String unit,
                            TitleBlock block, float mm) {
        float wUnits = (float) (layout.widthMm * mm);
        float hUnits = (float) (layout.heightMm * mm);

        Paint bg = new Paint();
        bg.setColor(0xFFFFFFFF);
        c.drawRect(0, 0, wUnits, hUnits, bg);

        RenderStyle st = RenderStyle.paperStyle(mm);
        st.showDims = showDims;
        st.unit = unit;

        float margin = (float) (layout.marginMm * mm);
        float blockH = layout.titleBlock ? (float) (layout.titleBlockH * mm) : 0;
        RectF area = new RectF(margin, margin, wUnits - margin,
                hUnits - margin - (blockH > 0 ? blockH + 4 * mm : 0));

        Paint frame = new Paint(Paint.ANTI_ALIAS_FLAG);
        frame.setStyle(Paint.Style.STROKE);
        frame.setStrokeWidth(0.35f * mm);
        frame.setColor(0xFF111111);
        c.drawRect(margin * 0.6f, margin * 0.6f, wUnits - margin * 0.6f, hUnits - margin * 0.6f, frame);

        Matrix m = PlanRenderer.scaleMatrix(plan, area, layout.scaleDen, mm);
        PlanRenderer r = new PlanRenderer(plan, m, st);
        c.save();
        c.clipRect(area);
        r.draw(c);
        c.restore();
        r.drawSheetMarks(c, area, layout.scaleDen);

        if (layout.titleBlock && block != null) {
            float boxW = Math.min(180 * mm, wUnits - 2 * margin);
            RectF box = new RectF(wUnits - margin - boxW, hUnits - margin - blockH,
                    wUnits - margin, hUnits - margin);
            block.draw(c, box, plan, layout, st);
        }
    }

    /** Apercu bitmap de la planche (largeur imposee en pixels). */
    public static Bitmap preview(Plan plan, Layout layout, boolean showDims, String unit,
                                 TitleBlock block, int widthPx) {
        float mm = (float) (widthPx / layout.widthMm);
        int hPx = Math.max(1, Math.round((float) (layout.heightMm * mm)));
        Bitmap b = Bitmap.createBitmap(Math.max(1, widthPx), hPx, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        draw(c, plan, layout, showDims, unit, block, mm);
        return b;
    }
}
