package com.tracecroquis;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.graphics.Bitmap;

import com.tracecroquis.core.model.Plan;
import com.tracecroquis.core.vector.VectorOptions;
import com.tracecroquis.core.vector.Vectorizer;
import com.tracecroquis.export.Layout;
import com.tracecroquis.export.PdfExporter;
import com.tracecroquis.export.SheetRenderer;
import com.tracecroquis.export.TitleBlock;
import com.tracecroquis.export.VectorExporter;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.io.File;

/**
 * Vectorise un croquis de synthese puis produit les trois exports.
 * Verifie que toute la chaine fonctionne sous Android.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class PlanExportTest {

    private static final int W = 700, H = 500;

    /** Croquis de synthese : un rectangle avec un refend et une porte. */
    private int[] croquis() {
        int[] px = new int[W * H];
        java.util.Arrays.fill(px, 0xFFFFFFFF);
        rect(px, 60, 60, 640, 440, 4);          // murs exterieurs
        line(px, 350, 60, 350, 230, 3);         // refend haut
        line(px, 350, 300, 350, 440, 3);        // refend bas (percement entre les deux)
        return px;
    }

    private void rect(int[] px, int x1, int y1, int x2, int y2, int t) {
        line(px, x1, y1, x2, y1, t);
        line(px, x2, y1, x2, y2, t);
        line(px, x2, y2, x1, y2, t);
        line(px, x1, y2, x1, y1, t);
    }

    private void line(int[] px, int x1, int y1, int x2, int y2, int t) {
        int steps = Math.max(Math.abs(x2 - x1), Math.abs(y2 - y1));
        for (int i = 0; i <= steps; i++) {
            int x = x1 + (x2 - x1) * i / steps;
            int y = y1 + (y2 - y1) * i / steps;
            for (int dy = -t / 2; dy <= t / 2; dy++) {
                for (int dx = -t / 2; dx <= t / 2; dx++) {
                    int xx = x + dx, yy = y + dy;
                    if (xx >= 0 && yy >= 0 && xx < W && yy < H) px[yy * W + xx] = 0xFF101010;
                }
            }
        }
    }

    @Test
    public void vectoriseEtExporte() throws Exception {
        Vectorizer.Result r = Vectorizer.run(croquis(), W, H, new VectorOptions(), null, null);
        Plan plan = r.plan;
        assertTrue("murs detectes : " + plan.walls.size(), plan.walls.size() >= 4);
        assertTrue("pieces detectees : " + plan.rooms.size(), plan.rooms.size() >= 2);
        assertTrue("echelle", plan.pxPerMeter > 0);

        plan.projectName = "Essai";
        plan.levelName = "Rez-de-chaussée";

        Layout layout = Layout.choose(plan, true, "", 0, true, true);
        assertNotNull(layout.paper);
        assertTrue(layout.scaleDen > 0);

        File dir = new File(System.getProperty("java.io.tmpdir"), "tracecroquis-test");
        dir.mkdirs();

        File svg = VectorExporter.svg(plan, layout, true, new File(dir, "plan.svg"));
        assertTrue("SVG vide", svg.length() > 500);

        File dxf = VectorExporter.dxf(plan, new File(dir, "plan.dxf"));
        assertTrue("DXF vide", dxf.length() > 500);

        Bitmap preview = SheetRenderer.preview(plan, layout, true, "m", new TitleBlock(), 600);
        assertNotNull(preview);
        assertEquals(600, preview.getWidth());

        // android.graphics.pdf.PdfDocument repose sur du code natif, absent
        // d'une machine virtuelle de test : on verifie seulement que la
        // composition de la planche se deroule sans erreur applicative.
        try {
            File pdf = PdfExporter.export(plan, layout, true, "m", new TitleBlock(),
                    new File(dir, "plan.pdf"));
            assertTrue("PDF vide", pdf.length() > 500);
        } catch (IllegalStateException natif) {
            assertTrue("erreur inattendue : " + natif.getMessage(),
                    String.valueOf(natif.getMessage()).contains("closed"));
        }
    }

    @Test
    public void echelleEtEpaisseurs() {
        Vectorizer.Result r = Vectorizer.run(croquis(), W, H, new VectorOptions(), null, null);
        Plan plan = r.plan;
        plan.rescale(50);                       // 50 px = 1 m
        assertEquals(50, plan.pxPerMeter, 0.001);
        for (Plan.Wall w : plan.walls) {
            assertTrue("épaisseur irréaliste : " + w.thicknessM,
                    w.thicknessM >= 0.02 && w.thicknessM <= 0.6);
        }
        assertTrue(plan.totalAreaM2() > 0);
    }
}
