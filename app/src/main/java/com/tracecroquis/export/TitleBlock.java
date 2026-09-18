package com.tracecroquis.export;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;

import com.tracecroquis.core.model.Plan;
import com.tracecroquis.util.Fmt;

import java.util.Locale;

/**
 * Cartouche du plan.
 *
 * <p>Reprend les mentions attendues pour une piece jointe a une demande
 * d'autorisation d'urbanisme : identification du projet et du terrain,
 * maitre d'ouvrage, nature et numero du document, indice, echelle, format,
 * date, surfaces.</p>
 */
public final class TitleBlock {

    public String reference = "PCMI — Plan de niveau";
    public String author = "";
    public String note = "Document non contractuel — cotes à vérifier sur site";

    public void draw(Canvas c, RectF box, Plan plan, Layout layout, RenderStyle st) {
        Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
        line.setStyle(Paint.Style.STROKE);
        line.setColor(st.text);
        line.setStrokeWidth(Math.max(0.4f, 0.25f * st.mm));

        Paint label = new Paint(Paint.ANTI_ALIAS_FLAG);
        label.setColor(st.text);
        label.setTextSize(1.9f * st.mm);

        Paint value = new Paint(Paint.ANTI_ALIAS_FLAG);
        value.setColor(st.text);
        value.setTextSize(2.7f * st.mm);

        Paint title = new Paint(Paint.ANTI_ALIAS_FLAG);
        title.setColor(st.text);
        title.setTextSize(3.6f * st.mm);
        title.setFakeBoldText(true);

        c.drawRect(box, line);

        float h = box.height();
        float topH = h * 0.52f;
        float ySplit = box.top + topH;
        c.drawLine(box.left, ySplit, box.right, ySplit, line);

        // Bandeau haut : projet et adresse
        float pad = 1.6f * st.mm;
        c.drawText(safe(plan.projectName, "Projet"), box.left + pad, box.top + 4.2f * st.mm, title);
        c.drawText(safe(plan.levelName, ""), box.left + pad, box.top + 7.8f * st.mm, value);
        String addr = plan.address == null ? "" : plan.address;
        if (!addr.isEmpty()) {
            c.drawText("Terrain : " + addr, box.left + pad, box.top + 11.2f * st.mm, label);
        }
        String mo = plan.owner == null ? "" : plan.owner;
        if (!mo.isEmpty()) {
            c.drawText("Maître d'ouvrage : " + mo, box.left + pad, box.top + 14.2f * st.mm, label);
        }
        c.drawText(reference, box.right - pad - label.measureText(reference),
                box.top + 4.0f * st.mm, label);

        // Bandeau bas : cases normalisees
        String[][] cells = {
                {"N° de plan", safe(plan.sheetNumber, "PL-001")},
                {"Indice", safe(plan.revision, "A")},
                {"Échelle", layout.scaleLabel()},
                {"Format", layout.paper + (layout.landscape ? " paysage" : " portrait")},
                {"Date", Fmt.date(System.currentTimeMillis())},
                {"Surface", String.format(Locale.FRENCH, "%.1f m²", plan.totalAreaM2())},
        };
        float cw = box.width() / cells.length;
        for (int i = 0; i < cells.length; i++) {
            float x = box.left + i * cw;
            if (i > 0) c.drawLine(x, ySplit, x, box.bottom, line);
            c.drawText(cells[i][0], x + pad, ySplit + 2.9f * st.mm, label);
            c.drawText(cells[i][1], x + pad, ySplit + 6.9f * st.mm, value);
        }
    }

    private static String safe(String s, String fallback) {
        return s == null || s.trim().isEmpty() ? fallback : s.trim();
    }
}
