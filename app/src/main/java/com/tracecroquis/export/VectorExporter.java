package com.tracecroquis.export;

import com.tracecroquis.core.geom.G;
import com.tracecroquis.core.model.Enums.WallType;
import com.tracecroquis.core.model.Plan;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.util.Locale;

/** Exports vectoriels : SVG (mise en page) et DXF (CAO, unites en metres). */
public final class VectorExporter {

    private VectorExporter() { }

    // ------------------------------------------------------------------ SVG

    public static File svg(Plan plan, Layout layout, boolean showDims, File out) throws Exception {
        double ppm = plan.pxPerMeter > 0 ? plan.pxPerMeter : 100;
        double[] b = plan.bounds();
        double k = 1000.0 / layout.scaleDen / ppm;        // px plan -> mm papier
        double w = layout.widthMm, h = layout.heightMm;
        double ox = layout.marginMm - b[0] * k
                + (layout.drawWidth() - (b[2] - b[0]) * k) / 2;
        double oy = layout.marginMm - b[1] * k
                + (layout.drawHeight() - (b[3] - b[1]) * k) / 2;

        Writer o = new OutputStreamWriter(new FileOutputStream(out), "UTF-8");
        o.write(String.format(Locale.US,
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                        + "<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"%.1fmm\" height=\"%.1fmm\" "
                        + "viewBox=\"0 0 %.1f %.1f\">\n", w, h, w, h));
        o.write("<rect width=\"100%\" height=\"100%\" fill=\"#ffffff\"/>\n");
        o.write(String.format(Locale.US, "<g id=\"plan\" data-echelle=\"1:%d\">\n", layout.scaleDen));

        // Murs
        o.write("<g id=\"murs\">\n");
        int[] deg = new int[plan.nodes.size()];
        for (Plan.Wall wl : plan.walls) { deg[wl.a]++; deg[wl.b]++; }
        for (Plan.Wall wl : plan.walls) {
            Plan.Node a = plan.nodes.get(wl.a), q = plan.nodes.get(wl.b);
            double len = G.dist(a.x, a.y, q.x, q.y);
            if (len < 1e-6) continue;
            double dx = (q.x - a.x) / len, dy = (q.y - a.y) / len;
            double t = (wl.thicknessM > 0 ? wl.thicknessM * ppm : wl.thicknessPx) / 2;
            double ea = deg[wl.a] > 1 ? t : 0, eb = deg[wl.b] > 1 ? t : 0;
            double ax = a.x - dx * ea, ay = a.y - dy * ea, bx = q.x + dx * eb, by = q.y + dy * eb;
            double nx = -dy * t, ny = dx * t;
            boolean porteur = wl.type == WallType.VOILE || wl.type == WallType.VOILE_EXT
                    || wl.type == WallType.MUR_PORTEUR;
            o.write(String.format(Locale.US,
                    "<polygon points=\"%.2f,%.2f %.2f,%.2f %.2f,%.2f %.2f,%.2f\" fill=\"%s\" "
                            + "data-type=\"%s\" data-epaisseur=\"%.2f\"/>\n",
                    (ax + nx) * k + ox, (ay + ny) * k + oy, (bx + nx) * k + ox, (by + ny) * k + oy,
                    (bx - nx) * k + ox, (by - ny) * k + oy, (ax - nx) * k + ox, (ay - ny) * k + oy,
                    porteur ? "#101010" : "#3c3c3c", wl.type.name(), wl.thicknessM));
        }
        o.write("</g>\n");

        // Ouvertures
        o.write("<g id=\"ouvertures\" fill=\"#ffffff\" stroke=\"#101010\" stroke-width=\"0.2\">\n");
        for (Plan.Opening op : plan.openings) {
            if (op.wall < 0 || op.wall >= plan.walls.size()) continue;
            Plan.Wall wl = plan.walls.get(op.wall);
            Plan.Node a = plan.nodes.get(wl.a), q = plan.nodes.get(wl.b);
            double len = G.dist(a.x, a.y, q.x, q.y);
            if (len < 1e-6) continue;
            double dx = (q.x - a.x) / len, dy = (q.y - a.y) / len;
            double nx = -dy, ny = dx;
            double t = (wl.thicknessM > 0 ? wl.thicknessM * ppm : wl.thicknessPx) / 2;
            double wpx = Math.min(op.widthM * ppm, len * 0.98);
            double cx = a.x + (q.x - a.x) * op.t, cy = a.y + (q.y - a.y) * op.t;
            double x1 = cx - dx * wpx / 2, y1 = cy - dy * wpx / 2;
            double x2 = cx + dx * wpx / 2, y2 = cy + dy * wpx / 2;
            o.write(String.format(Locale.US,
                    "<polygon points=\"%.2f,%.2f %.2f,%.2f %.2f,%.2f %.2f,%.2f\" data-type=\"%s\"/>\n",
                    (x1 + nx * t) * k + ox, (y1 + ny * t) * k + oy, (x2 + nx * t) * k + ox, (y2 + ny * t) * k + oy,
                    (x2 - nx * t) * k + ox, (y2 - ny * t) * k + oy, (x1 - nx * t) * k + ox, (y1 - ny * t) * k + oy,
                    op.type.name()));
            if (op.type.isDoor()) {
                double hx = op.hingeSide >= 0 ? x1 : x2, hy = op.hingeSide >= 0 ? y1 : y2;
                double lx = hx + nx * wpx * op.swingSide, ly = hy + ny * wpx * op.swingSide;
                o.write(String.format(Locale.US,
                        "<path d=\"M %.2f,%.2f L %.2f,%.2f\" stroke=\"#101010\" fill=\"none\"/>\n",
                        hx * k + ox, hy * k + oy, lx * k + ox, ly * k + oy));
                o.write(String.format(Locale.US,
                        "<path d=\"M %.2f,%.2f A %.2f,%.2f 0 0 %d %.2f,%.2f\" fill=\"none\" stroke=\"#808080\"/>\n",
                        (op.hingeSide >= 0 ? x2 : x1) * k + ox, (op.hingeSide >= 0 ? y2 : y1) * k + oy,
                        wpx * k, wpx * k, op.swingSide >= 0 ? 1 : 0, lx * k + ox, ly * k + oy));
            }
        }
        o.write("</g>\n");

        // Pieces
        o.write("<g id=\"pieces\" font-family=\"sans-serif\" text-anchor=\"middle\" fill=\"#101010\">\n");
        for (Plan.Room r : plan.rooms) {
            double area = plan.areaOf(r);
            String name = r.name == null ? "" : r.name.toUpperCase(Locale.FRENCH);
            o.write(String.format(Locale.US,
                    "<text x=\"%.2f\" y=\"%.2f\" font-size=\"3.2\" font-weight=\"bold\">%s</text>\n",
                    r.labelX * k + ox, r.labelY * k + oy, esc(name)));
            if (area > 0.2) {
                o.write(String.format(Locale.US,
                        "<text x=\"%.2f\" y=\"%.2f\" font-size=\"2.7\">%s</text>\n",
                        r.labelX * k + ox, r.labelY * k + oy + 3.8, String.format(Locale.FRENCH, "%.1f m²", area)));
            }
        }
        o.write("</g>\n");

        if (showDims) {
            o.write("<g id=\"cotes\" stroke=\"#16305e\" stroke-width=\"0.18\" fill=\"#16305e\" "
                    + "font-family=\"sans-serif\" font-size=\"2.8\" text-anchor=\"middle\">\n");
            for (Plan.DimItem d : plan.dims) {
                double v = !Double.isNaN(d.valueM) ? d.valueM
                        : G.dist(d.x1, d.y1, d.x2, d.y2) / ppm;
                o.write(String.format(Locale.US,
                        "<line x1=\"%.2f\" y1=\"%.2f\" x2=\"%.2f\" y2=\"%.2f\"/>\n",
                        d.x1 * k + ox, d.y1 * k + oy, d.x2 * k + ox, d.y2 * k + oy));
                o.write(String.format(Locale.US,
                        "<text x=\"%.2f\" y=\"%.2f\" stroke=\"none\">%s</text>\n",
                        (d.x1 + d.x2) / 2 * k + ox, (d.y1 + d.y2) / 2 * k + oy - 1.2,
                        String.format(Locale.FRENCH, "%.2f m", v)));
            }
            o.write("</g>\n");
        }

        o.write("<g id=\"symboles\" fill=\"none\" stroke=\"#6b7280\" stroke-width=\"0.18\">\n");
        for (Plan.SymbolItem s : plan.symbols) {
            for (double[] path : s.paths) {
                if (path.length < 4) continue;
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i + 1 < path.length; i += 2) {
                    sb.append(String.format(Locale.US, "%s%.2f,%.2f ", i == 0 ? "M " : "L ",
                            path[i] * k + ox, path[i + 1] * k + oy));
                }
                o.write(String.format("<path d=\"%s\" data-type=\"%s\"/>\n", sb.toString().trim(), s.type.name()));
            }
        }
        o.write("</g>\n</g>\n</svg>\n");
        o.close();
        return out;
    }

    private static String esc(String s) {
        return s == null ? "" : s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    // ------------------------------------------------------------------ DXF

    /** DXF R12 : une unite = un metre, calques metier separes. */
    public static File dxf(Plan plan, File out) throws Exception {
        double ppm = plan.pxPerMeter > 0 ? plan.pxPerMeter : 100;
        double[] b = plan.bounds();
        Writer o = new OutputStreamWriter(new FileOutputStream(out), "UTF-8");
        o.write("999\nTraceCroquis 0.1\n");
        o.write("0\nSECTION\n2\nHEADER\n9\n$INSUNITS\n70\n6\n0\nENDSEC\n");
        o.write("0\nSECTION\n2\nTABLES\n0\nTABLE\n2\nLAYER\n70\n8\n");
        String[][] layers = {
                {"MURS", "7"}, {"CLOISONS", "8"}, {"OUVERTURES", "3"}, {"COTES", "5"},
                {"TEXTES", "2"}, {"PIECES", "4"}, {"MOBILIER", "9"}, {"SYMBOLES", "6"}
        };
        for (String[] l : layers) {
            o.write("0\nLAYER\n2\n" + l[0] + "\n70\n0\n62\n" + l[1] + "\n6\nCONTINUOUS\n");
        }
        o.write("0\nENDTAB\n0\nENDSEC\n");
        o.write("0\nSECTION\n2\nENTITIES\n");

        int[] deg = new int[plan.nodes.size()];
        for (Plan.Wall wl : plan.walls) { deg[wl.a]++; deg[wl.b]++; }
        for (Plan.Wall wl : plan.walls) {
            Plan.Node a = plan.nodes.get(wl.a), q = plan.nodes.get(wl.b);
            double len = G.dist(a.x, a.y, q.x, q.y);
            if (len < 1e-6) continue;
            double dx = (q.x - a.x) / len, dy = (q.y - a.y) / len;
            double t = (wl.thicknessM > 0 ? wl.thicknessM * ppm : wl.thicknessPx) / 2;
            double ea = deg[wl.a] > 1 ? t : 0, eb = deg[wl.b] > 1 ? t : 0;
            double ax = a.x - dx * ea, ay = a.y - dy * ea, bx = q.x + dx * eb, by = q.y + dy * eb;
            double nx = -dy * t, ny = dx * t;
            boolean porteur = wl.type == WallType.VOILE || wl.type == WallType.VOILE_EXT
                    || wl.type == WallType.MUR_PORTEUR;
            String layer = porteur ? "MURS" : "CLOISONS";
            double[][] pts = {
                    {ax + nx, ay + ny}, {bx + nx, by + ny}, {bx - nx, by - ny}, {ax - nx, ay - ny}
            };
            for (int i = 0; i < 4; i++) {
                double[] p1 = pts[i], p2 = pts[(i + 1) % 4];
                line(o, layer, mx(p1[0], b, ppm), my(p1[1], b, ppm), mx(p2[0], b, ppm), my(p2[1], b, ppm));
            }
        }
        for (Plan.Opening op : plan.openings) {
            if (op.wall < 0 || op.wall >= plan.walls.size()) continue;
            Plan.Wall wl = plan.walls.get(op.wall);
            Plan.Node a = plan.nodes.get(wl.a), q = plan.nodes.get(wl.b);
            double len = G.dist(a.x, a.y, q.x, q.y);
            if (len < 1e-6) continue;
            double dx = (q.x - a.x) / len, dy = (q.y - a.y) / len;
            double wpx = Math.min(op.widthM * ppm, len * 0.98);
            double cx = a.x + (q.x - a.x) * op.t, cy = a.y + (q.y - a.y) * op.t;
            line(o, "OUVERTURES",
                    mx(cx - dx * wpx / 2, b, ppm), my(cy - dy * wpx / 2, b, ppm),
                    mx(cx + dx * wpx / 2, b, ppm), my(cy + dy * wpx / 2, b, ppm));
        }
        for (Plan.DimItem d : plan.dims) {
            line(o, "COTES", mx(d.x1, b, ppm), my(d.y1, b, ppm), mx(d.x2, b, ppm), my(d.y2, b, ppm));
        }
        for (Plan.Room r : plan.rooms) {
            String label = (r.name == null ? "" : r.name.toUpperCase(Locale.FRENCH));
            double area = plan.areaOf(r);
            text(o, "PIECES", mx(r.labelX, b, ppm), my(r.labelY, b, ppm), 0.25,
                    label + (area > 0.2 ? String.format(Locale.FRENCH, " %.1f m2", area) : ""));
        }
        for (Plan.TextItem t : plan.texts) {
            if (t.text == null || t.text.trim().isEmpty()) continue;
            if (t.room >= 0) continue;
            text(o, "TEXTES", mx(t.x, b, ppm), my(t.y, b, ppm), 0.2, t.text);
        }
        for (Plan.SymbolItem s : plan.symbols) {
            String layer = s.type.name().equals("MOBILIER") ? "MOBILIER" : "SYMBOLES";
            for (double[] path : s.paths) {
                for (int i = 0; i + 3 < path.length; i += 2) {
                    line(o, layer, mx(path[i], b, ppm), my(path[i + 1], b, ppm),
                            mx(path[i + 2], b, ppm), my(path[i + 3], b, ppm));
                }
            }
        }
        o.write("0\nENDSEC\n0\nEOF\n");
        o.close();
        return out;
    }

    private static double mx(double x, double[] b, double ppm) {
        return (x - b[0]) / ppm;
    }

    private static double my(double y, double[] b, double ppm) {
        return (b[3] - y) / ppm;      // DXF : axe Y vers le haut
    }

    private static void line(Writer o, String layer, double x1, double y1, double x2, double y2)
            throws Exception {
        o.write(String.format(Locale.US,
                "0\nLINE\n8\n%s\n10\n%.4f\n20\n%.4f\n30\n0.0\n11\n%.4f\n21\n%.4f\n31\n0.0\n",
                layer, x1, y1, x2, y2));
    }

    private static void text(Writer o, String layer, double x, double y, double height, String value)
            throws Exception {
        o.write(String.format(Locale.US,
                "0\nTEXT\n8\n%s\n10\n%.4f\n20\n%.4f\n30\n0.0\n40\n%.3f\n1\n%s\n72\n1\n11\n%.4f\n21\n%.4f\n31\n0.0\n",
                layer, x, y, height, value.replace("\n", " "), x, y));
    }
}
