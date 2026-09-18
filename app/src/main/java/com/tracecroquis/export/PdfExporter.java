package com.tracecroquis.export;

import android.graphics.Canvas;
import android.graphics.pdf.PdfDocument;

import com.tracecroquis.core.model.Plan;

import java.io.File;
import java.io.FileOutputStream;

/** Export PDF a l'echelle, pret a imprimer a 100 %. */
public final class PdfExporter {

    private PdfExporter() { }

    public static File export(Plan plan, Layout layout, boolean showDims, String unit,
                              TitleBlock block, File out) throws Exception {
        float mm = (float) Layout.mmToPt(1);      // 1 mm en points PDF
        int wPt = (int) Math.round(layout.widthPt());
        int hPt = (int) Math.round(layout.heightPt());

        PdfDocument doc = new PdfDocument();
        PdfDocument.PageInfo info = new PdfDocument.PageInfo.Builder(wPt, hPt, 1).create();
        PdfDocument.Page page = doc.startPage(info);
        Canvas c = page.getCanvas();
        SheetRenderer.draw(c, plan, layout, showDims, unit, block, mm);
        doc.finishPage(page);

        FileOutputStream fo = new FileOutputStream(out);
        doc.writeTo(fo);
        fo.close();
        doc.close();
        return out;
    }
}
