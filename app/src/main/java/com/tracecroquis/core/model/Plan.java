package com.tracecroquis.core.model;

import com.tracecroquis.core.model.Enums.OpeningType;
import com.tracecroquis.core.model.Enums.SymbolType;
import com.tracecroquis.core.model.Enums.TextRole;
import com.tracecroquis.core.model.Enums.WallType;

import java.util.ArrayList;
import java.util.List;

/**
 * Modele vectoriel du plan.
 *
 * <p>Toutes les coordonnees sont exprimees en pixels de l'image analysee
 * ("unites plan"). {@link #pxPerMeter} donne la conversion vers le monde reel.
 * Les epaisseurs et largeurs metier sont stockees en metres.</p>
 */
public class Plan {

    // ---------------------------------------------------------------- noeuds

    public static class Node {
        public double x, y;
        public Node(double x, double y) { this.x = x; this.y = y; }
    }

    // ------------------------------------------------------------------ murs

    public static class Wall {
        public int a, b;                       // index de noeuds
        public WallType type = WallType.CLOISON;
        public double thicknessM = 0.07;       // epaisseur reelle (m)
        public double thicknessPx = 4;         // epaisseur mesuree sur le croquis (px)
        public boolean exterior = false;
        public boolean userEdited = false;
        public double confidence = 1.0;

        public Wall() { }
        public Wall(int a, int b) { this.a = a; this.b = b; }
    }

    // ------------------------------------------------------------ ouvertures

    public static class Opening {
        public int wall = -1;                  // index de mur porteur du percement
        public double t = 0.5;                 // position du centre le long du mur (0..1)
        public double widthM = 0.83;
        public OpeningType type = OpeningType.PORTE;
        /** +1 : battant du cote gauche du vecteur mur, -1 : cote droit. */
        public int hingeSide = 1;
        /** +1 : ouverture vers la gauche du vecteur mur, -1 : vers la droite. */
        public int swingSide = 1;
        public double swingAngleDeg = 90;
        public double confidence = 1.0;

        public Opening() { }
        public Opening(int wall, double t, double widthM, OpeningType type) {
            this.wall = wall; this.t = t; this.widthM = widthM; this.type = type;
        }
    }

    // ---------------------------------------------------------------- pieces

    public static class Room {
        public final List<double[]> poly = new ArrayList<>();  // contour ferme, unites plan
        public String name = "";
        /** Surface lue sur le croquis (m2), NaN si inconnue. */
        public double declaredAreaM2 = Double.NaN;
        public double labelX, labelY;
        public boolean exterior = false;       // terrasse, patio, garage non clos...
    }

    // --------------------------------------------------------------- textes

    public static class TextItem {
        public double x, y;                    // ancre (centre)
        public double heightPx = 14;
        public double angleDeg = 0;
        public String text = "";
        public TextRole role = TextRole.LIBRE;
        public int room = -1;                  // piece rattachee, -1 sinon
        public boolean fromOcr = false;
        public double confidence = 1.0;
    }

    // ----------------------------------------------------------------- cotes

    public static class DimItem {
        public double x1, y1, x2, y2;          // ligne de cote
        public double offset = 0;              // deport par rapport a la ligne de reference
        /** Valeur lue sur le croquis (m), NaN si non lue. */
        public double valueM = Double.NaN;
        public String label = "";
        public boolean vertical = false;
        /** Cote servant de reference d'echelle. */
        public boolean reference = false;
        public double confidence = 1.0;
    }

    // -------------------------------------------------------------- symboles

    public static class SymbolItem {
        public SymbolType type = SymbolType.INDETERMINE;
        /** Polylignes brutes (chaque entree : x0,y0,x1,y1,...). */
        public final List<double[]> paths = new ArrayList<>();
        public double minX, minY, maxX, maxY;
        public String note = "";
        public int room = -1;
        public double confidence = 1.0;

        public void computeBounds() {
            minX = minY = Double.MAX_VALUE;
            maxX = maxY = -Double.MAX_VALUE;
            for (double[] p : paths) {
                for (int i = 0; i + 1 < p.length; i += 2) {
                    if (p[i] < minX) minX = p[i];
                    if (p[i] > maxX) maxX = p[i];
                    if (p[i + 1] < minY) minY = p[i + 1];
                    if (p[i + 1] > maxY) maxY = p[i + 1];
                }
            }
            if (minX > maxX) { minX = minY = maxX = maxY = 0; }
        }
    }

    // ------------------------------------------------------------------ plan

    public String projectName = "Maison";
    public String levelName = "Rez-de-chaussée";
    public String sheetNumber = "PL-001";
    public String revision = "A";
    public String owner = "";
    public String address = "";

    public final List<Node> nodes = new ArrayList<>();
    public final List<Wall> walls = new ArrayList<>();
    public final List<Opening> openings = new ArrayList<>();
    public final List<Room> rooms = new ArrayList<>();
    public final List<TextItem> texts = new ArrayList<>();
    public final List<DimItem> dims = new ArrayList<>();
    public final List<SymbolItem> symbols = new ArrayList<>();

    /** Facteur d'echelle : nombre de pixels plan pour un metre reel. */
    public double pxPerMeter = 0;
    /** Vrai des que l'echelle est validee (cote lue ou saisie utilisateur). */
    public boolean scaleConfirmed = false;

    /** Largeur / hauteur de l'image analysee (unites plan). */
    public int imageW, imageH;

    public int addNode(double x, double y) {
        nodes.add(new Node(x, y));
        return nodes.size() - 1;
    }

    public Node n(int i) { return nodes.get(i); }

    public double wallLengthPx(Wall w) {
        Node p = nodes.get(w.a), q = nodes.get(w.b);
        return Math.hypot(q.x - p.x, q.y - p.y);
    }

    public double wallLengthM(Wall w) {
        return pxPerMeter > 0 ? wallLengthPx(w) / pxPerMeter : 0;
    }

    public double toMeters(double px) {
        return pxPerMeter > 0 ? px / pxPerMeter : 0;
    }

    public double toPx(double meters) {
        return pxPerMeter > 0 ? meters * pxPerMeter : 0;
    }

    /** Emprise du plan en unites plan : minX, minY, maxX, maxY. */
    public double[] bounds() {
        double minX = Double.MAX_VALUE, minY = Double.MAX_VALUE;
        double maxX = -Double.MAX_VALUE, maxY = -Double.MAX_VALUE;
        for (Node n : nodes) {
            minX = Math.min(minX, n.x); maxX = Math.max(maxX, n.x);
            minY = Math.min(minY, n.y); maxY = Math.max(maxY, n.y);
        }
        for (SymbolItem s : symbols) {
            minX = Math.min(minX, s.minX); maxX = Math.max(maxX, s.maxX);
            minY = Math.min(minY, s.minY); maxY = Math.max(maxY, s.maxY);
        }
        for (DimItem d : dims) {
            minX = Math.min(minX, Math.min(d.x1, d.x2)); maxX = Math.max(maxX, Math.max(d.x1, d.x2));
            minY = Math.min(minY, Math.min(d.y1, d.y2)); maxY = Math.max(maxY, Math.max(d.y1, d.y2));
        }
        if (minX > maxX) return new double[]{0, 0, Math.max(1, imageW), Math.max(1, imageH)};
        return new double[]{minX, minY, maxX, maxY};
    }

    /** Emprise des seuls murs (sans cotes ni textes) : utile pour le cadrage. */
    public double[] wallBounds() {
        double minX = Double.MAX_VALUE, minY = Double.MAX_VALUE;
        double maxX = -Double.MAX_VALUE, maxY = -Double.MAX_VALUE;
        for (Wall w : walls) {
            Node p = nodes.get(w.a), q = nodes.get(w.b);
            minX = Math.min(minX, Math.min(p.x, q.x)); maxX = Math.max(maxX, Math.max(p.x, q.x));
            minY = Math.min(minY, Math.min(p.y, q.y)); maxY = Math.max(maxY, Math.max(p.y, q.y));
        }
        if (minX > maxX) return bounds();
        return new double[]{minX, minY, maxX, maxY};
    }

    /** Surface habitable cumulee des pieces interieures (m2). */
    public double totalAreaM2() {
        double s = 0;
        for (Room r : rooms) {
            if (r.exterior) continue;
            s += areaOf(r);
        }
        return s;
    }

    public double areaOf(Room r) {
        if (!Double.isNaN(r.declaredAreaM2) && r.declaredAreaM2 > 0) return r.declaredAreaM2;
        if (pxPerMeter <= 0) return 0;
        double a = 0;
        int n = r.poly.size();
        for (int i = 0; i < n; i++) {
            double[] p = r.poly.get(i), q = r.poly.get((i + 1) % n);
            a += p[0] * q[1] - q[0] * p[1];
        }
        return Math.abs(a) * 0.5 / (pxPerMeter * pxPerMeter);
    }

    /** Applique une nouvelle echelle en conservant les dimensions reelles mesurees. */
    public void rescale(double newPxPerMeter) {
        if (newPxPerMeter <= 0) return;
        if (pxPerMeter > 0) {
            double f = pxPerMeter / newPxPerMeter;
            for (Opening o : openings) o.widthM *= f;
        }
        pxPerMeter = newPxPerMeter;
        scaleConfirmed = true;
        applyScaleToThickness();
    }

    /** Recalcule les epaisseurs metier apres un changement d'echelle. */
    public void applyScaleToThickness() {
        if (pxPerMeter <= 0) return;
        for (Wall w : walls) {
            if (w.userEdited) continue;
            double m = w.thicknessPx / pxPerMeter;
            w.type = classify(m);
            w.thicknessM = normalizeThickness(m, w.type);
        }
    }

    public static WallType classify(double meters) {
        if (meters >= 0.145) return WallType.VOILE;
        if (meters >= 0.115) return WallType.MUR_PORTEUR;
        if (meters >= 0.085) return WallType.CLOISON_DOUBLAGE;
        if (meters >= 0.02) return WallType.CLOISON;
        return WallType.CLOISON;
    }

    /** Aligne l'epaisseur mesuree sur les valeurs normalisees du batiment. */
    public static double normalizeThickness(double meters, WallType type) {
        double[] std;
        if (type == WallType.VOILE || type == WallType.VOILE_EXT || type == WallType.MUR_PORTEUR) {
            std = new double[]{0.15, 0.16, 0.20, 0.25, 0.30, 0.36, 0.40};
        } else {
            std = new double[]{0.05, 0.07, 0.072, 0.10, 0.12};
        }
        double best = std[0], bd = Math.abs(meters - std[0]);
        for (double v : std) {
            double d = Math.abs(meters - v);
            if (d < bd) { bd = d; best = v; }
        }
        return best;
    }
}
