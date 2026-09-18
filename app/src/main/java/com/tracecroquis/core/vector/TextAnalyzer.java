package com.tracecroquis.core.vector;

import com.tracecroquis.core.geom.G;
import com.tracecroquis.core.img.Cc;
import com.tracecroquis.core.model.Enums.TextRole;
import com.tracecroquis.core.model.Plan;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Reconnaissance des blocs de texte : noms de pieces, surfaces, cotes. */
public final class TextAnalyzer {

    /** Bloc de texte candidat (regroupement de composantes connexes). */
    public static class Group {
        public double minX, minY, maxX, maxY;
        public final List<Integer> comps = new ArrayList<>();
        public String text = "";
        public boolean fromOcr = false;

        public double h() { return maxY - minY + 1; }
        public double w() { return maxX - minX + 1; }
        public double cx() { return (minX + maxX) / 2; }
        public double cy() { return (minY + maxY) / 2; }

        public void add(double x0, double y0, double x1, double y1) {
            if (comps.isEmpty() && minX == 0 && maxX == 0) {
                minX = x0; minY = y0; maxX = x1; maxY = y1;
            } else {
                minX = Math.min(minX, x0); minY = Math.min(minY, y0);
                maxX = Math.max(maxX, x1); maxY = Math.max(maxY, y1);
            }
        }
    }

    /** Noms de pieces reconnus (corrige les erreurs d'OCR). */
    private static final String[] ROOM_WORDS = {
            "CHAMBRE", "CUISINE", "SALON", "SEJOUR", "SÉJOUR", "ENTREE", "ENTRÉE", "GARAGE",
            "SALLE DE BAIN", "SALLE D'EAU", "SDB", "SDD", "WC", "TOILETTES", "DEGAGEMENT",
            "DÉGAGEMENT", "COULOIR", "CELLIER", "BUREAU", "TERRASSE", "PATIO", "DRESSING",
            "BUANDERIE", "HALL", "PALIER", "MEZZANINE", "SUITE", "SAM", "BALCON", "ESCALIER",
            "LINGERIE", "GRENIER", "CAVE", "VERANDA", "VÉRANDA", "ATELIER", "LOCAL TECHNIQUE",
            "SEJOUR / CUISINE", "SALON / SEJOUR", "SALLE A MANGER", "CHAUFFERIE", "PLACARD",
            "ABRI", "PORCHE", "COMBLE", "VIDE SUR SEJOUR"
    };

    private static final Pattern P_AREA = Pattern.compile("([0-9]+(?:[.,][0-9]+)?)\\s*m\\s*2");
    private static final Pattern P_MCM = Pattern.compile("\\b([0-9]{1,2})\\s*m\\s*([0-9]{1,2})\\b");
    private static final Pattern P_M = Pattern.compile("([0-9]+(?:[.,][0-9]+)?)\\s*m\\b");
    private static final Pattern P_CM = Pattern.compile("([0-9]+(?:[.,][0-9]+)?)\\s*cm\\b");
    private static final Pattern P_NUM = Pattern.compile("([0-9]+(?:[.,][0-9]+)?)");

    private TextAnalyzer() { }

    // ------------------------------------------------------------ detection

    /**
     * Marque les composantes qui ressemblent a du texte.
     * Si l'OCR a fourni des boites, elles font autorite.
     */
    public static boolean[] classify(Cc cc, List<OcrBox> ocr, VectorOptions o, double minDim) {
        boolean[] isText = new boolean[cc.comps.length];
        double maxH = o.textMaxHeightFrac * minDim;
        double minH = Math.max(3, o.textMinHeightFrac * minDim);
        if (ocr != null && !ocr.isEmpty()) {
            for (int i = 0; i < cc.comps.length; i++) {
                Cc.Comp c = cc.comps[i];
                double cx = (c.minX + c.maxX) / 2.0, cy = (c.minY + c.maxY) / 2.0;
                for (OcrBox b : ocr) {
                    double pad = Math.max(2, b.h * 0.25);
                    if (cx >= b.x - pad && cx <= b.x + b.w + pad
                            && cy >= b.y - pad && cy <= b.y + b.h + pad
                            && c.height() <= b.h * 1.6 + 4) {
                        isText[i] = true;
                        break;
                    }
                }
            }
            return isText;
        }
        for (int i = 0; i < cc.comps.length; i++) {
            Cc.Comp c = cc.comps[i];
            double h = c.height(), w = c.width();
            if (h < minH || h > maxH) continue;
            if (w > maxH * 12) continue;
            if (Math.max(w, h) > minDim * 0.35) continue;
            if (c.strokeWidth > h * 0.55 + 2) continue;
            double fill = c.fill();
            if (fill < 0.04 || fill > 0.92) continue;
            isText[i] = true;
        }
        return isText;
    }

    /** Regroupe les composantes de texte en lignes. */
    public static List<Group> group(Cc cc, boolean[] isText, VectorOptions o, double minDim) {
        List<Group> groups = new ArrayList<>();
        List<Integer> idx = new ArrayList<>();
        for (int i = 0; i < isText.length; i++) if (isText[i]) idx.add(i);
        boolean[] done = new boolean[cc.comps.length];
        for (int k = 0; k < idx.size(); k++) {
            int i = idx.get(k);
            if (done[i]) continue;
            Group g = new Group();
            Cc.Comp c = cc.comps[i];
            g.minX = c.minX; g.minY = c.minY; g.maxX = c.maxX; g.maxY = c.maxY;
            g.comps.add(i);
            done[i] = true;
            boolean grew = true;
            while (grew) {
                grew = false;
                for (int m = 0; m < idx.size(); m++) {
                    int j = idx.get(m);
                    if (done[j]) continue;
                    Cc.Comp d = cc.comps[j];
                    double gh = g.h();
                    double refH = Math.max(gh, d.height());
                    double dxGap = Math.max(0, Math.max(g.minX - d.maxX, d.minX - g.maxX));
                    double dyOver = Math.min(g.maxY, d.maxY) - Math.max(g.minY, d.minY);
                    if (dxGap <= refH * o.textGroupFactor
                            && dyOver > -refH * 0.35
                            && d.height() < refH * 2.4) {
                        g.minX = Math.min(g.minX, d.minX);
                        g.minY = Math.min(g.minY, d.minY);
                        g.maxX = Math.max(g.maxX, d.maxX);
                        g.maxY = Math.max(g.maxY, d.maxY);
                        g.comps.add(j);
                        done[j] = true;
                        grew = true;
                    }
                }
            }
            groups.add(g);
        }
        return groups;
    }

    /** Recopie le texte OCR sur les groupes correspondants. */
    public static void attachOcr(List<Group> groups, List<OcrBox> ocr) {
        if (ocr == null) return;
        for (OcrBox b : ocr) {
            Group best = null;
            double bestOverlap = 0;
            for (Group g : groups) {
                double ox = Math.min(g.maxX, b.x + b.w) - Math.max(g.minX, b.x);
                double oy = Math.min(g.maxY, b.y + b.h) - Math.max(g.minY, b.y);
                if (ox <= 0 || oy <= 0) continue;
                double area = ox * oy;
                if (area > bestOverlap) { bestOverlap = area; best = g; }
            }
            if (best != null) {
                best.text = best.text.isEmpty() ? b.text : best.text + " " + b.text;
                best.fromOcr = true;
                b.used = true;
            }
        }
        // Boites OCR sans composante associee : on les ajoute telles quelles.
        for (OcrBox b : ocr) {
            if (b.used) continue;
            Group g = new Group();
            g.minX = b.x; g.minY = b.y; g.maxX = b.x + b.w; g.maxY = b.y + b.h;
            g.text = b.text;
            g.fromOcr = true;
            groups.add(g);
        }
    }

    /** Cree les textes du plan et leur attribue un role. */
    public static void toPlan(Plan plan, List<Group> groups) {
        for (Group g : groups) {
            Plan.TextItem t = new Plan.TextItem();
            t.x = g.cx();
            t.y = g.cy();
            t.heightPx = g.h();
            t.text = g.text == null ? "" : g.text.trim();
            t.fromOcr = g.fromOcr;
            t.role = roleOf(t.text);
            t.confidence = g.fromOcr ? 0.9 : 0.4;
            plan.texts.add(t);
        }
    }

    public static TextRole roleOf(String raw) {
        if (raw == null || raw.trim().isEmpty()) return TextRole.LIBRE;
        String s = norm(raw);
        if (!Double.isNaN(parseArea(s))) return TextRole.SURFACE;
        if (!Double.isNaN(parseLength(s)) && !hasLetters(s.replace("m", "").replace("cm", ""))) {
            return TextRole.COTE;
        }
        if (matchRoom(raw) != null) return TextRole.NOM_PIECE;
        if (hasLetters(s)) return TextRole.NOM_PIECE;
        return TextRole.LIBRE;
    }

    private static boolean hasLetters(String s) {
        for (int i = 0; i < s.length(); i++) if (Character.isLetter(s.charAt(i))) return true;
        return false;
    }

    public static String norm(String s) {
        String t = s.toLowerCase(Locale.FRENCH)
                .replace('²', '2')
                .replace(' ', ' ')
                .replace("·", " ")
                .replace("≈", " ")
                .replace("~", " ")
                .trim();
        return t;
    }

    // ------------------------------------------------------------- analyses

    /** Surface en m2 lue dans le texte, NaN sinon. */
    public static double parseArea(String raw) {
        String s = norm(raw);
        Matcher m = P_AREA.matcher(s);
        if (m.find()) {
            double v = num(m.group(1));
            if (v > 0.5 && v < 2000) return v;
        }
        return Double.NaN;
    }

    /** Longueur en metres lue dans le texte, NaN sinon. */
    public static double parseLength(String raw) {
        String s = norm(raw);
        if (!Double.isNaN(parseArea(s))) return Double.NaN;
        Matcher m = P_MCM.matcher(s);
        if (m.find()) {
            double v = num(m.group(1)) + num(m.group(2)) / 100.0;
            if (v > 0.2 && v < 200) return v;
        }
        m = P_M.matcher(s);
        if (m.find()) {
            double v = num(m.group(1));
            if (v > 0.2 && v < 200) return v;
        }
        m = P_CM.matcher(s);
        if (m.find()) {
            double v = num(m.group(1)) / 100.0;
            if (v > 0.05 && v < 200) return v;
        }
        m = P_NUM.matcher(s);
        if (m.find() && s.replaceAll("[0-9.,\\s]", "").isEmpty()) {
            double v = num(m.group(1));
            if (v > 0.4 && v < 100) return v;
        }
        return Double.NaN;
    }

    private static double num(String s) {
        try {
            return Double.parseDouble(s.replace(',', '.'));
        } catch (NumberFormatException e) {
            return Double.NaN;
        }
    }

    /** Recale un nom de piece sur le vocabulaire courant (tolerant aux fautes d'OCR). */
    public static String matchRoom(String raw) {
        if (raw == null) return null;
        String s = stripAccents(raw.toUpperCase(Locale.FRENCH)).replaceAll("[^A-Z' /]", " ")
                .replaceAll("\\s+", " ").trim();
        if (s.isEmpty()) return null;
        String best = null;
        int bestScore = Integer.MAX_VALUE;
        for (String w : ROOM_WORDS) {
            String c = stripAccents(w);
            int d = levenshtein(s, c);
            int limit = Math.max(1, c.length() / 4);
            if (d <= limit && d < bestScore) { bestScore = d; best = w; }
            if (s.startsWith(c) && c.length() >= 4) { return w + s.substring(c.length()); }
        }
        return best;
    }

    public static String stripAccents(String s) {
        StringBuilder sb = new StringBuilder();
        for (char c : s.toCharArray()) {
            switch (c) {
                case 'É': case 'È': case 'Ê': case 'Ë': sb.append('E'); break;
                case 'À': case 'Â': case 'Ä': sb.append('A'); break;
                case 'Î': case 'Ï': sb.append('I'); break;
                case 'Ô': case 'Ö': sb.append('O'); break;
                case 'Ù': case 'Û': case 'Ü': sb.append('U'); break;
                case 'Ç': sb.append('C'); break;
                default: sb.append(c);
            }
        }
        return sb.toString();
    }

    public static int levenshtein(String a, String b) {
        int[] prev = new int[b.length() + 1];
        int[] cur = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) prev[j] = j;
        for (int i = 1; i <= a.length(); i++) {
            cur[0] = i;
            for (int j = 1; j <= b.length(); j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                cur[j] = Math.min(Math.min(cur[j - 1] + 1, prev[j] + 1), prev[j - 1] + cost);
            }
            int[] t = prev; prev = cur; cur = t;
        }
        return prev[b.length()];
    }

    /**
     * Affecte les noms et surfaces lus aux pieces, et renvoie l'echelle deduite
     * des surfaces annoncees (px/m) ou 0.
     */
    public static double applyToRooms(Plan plan) {
        List<Double> scales = new ArrayList<>();
        for (Plan.TextItem t : plan.texts) {
            int ri = t.room;
            if (ri < 0 || ri >= plan.rooms.size()) continue;
            Plan.Room room = plan.rooms.get(ri);
            if (t.role == TextRole.NOM_PIECE && !t.text.isEmpty()) {
                String canon = matchRoom(t.text);
                room.name = canon != null ? canon : t.text;
                if (room.name.toUpperCase(Locale.FRENCH).contains("TERRASSE")
                        || room.name.toUpperCase(Locale.FRENCH).contains("PATIO")
                        || room.name.toUpperCase(Locale.FRENCH).contains("BALCON")) {
                    room.exterior = true;
                }
            } else if (t.role == TextRole.SURFACE) {
                double a = parseArea(t.text);
                if (!Double.isNaN(a)) {
                    room.declaredAreaM2 = a;
                    double pxArea = Math.abs(G.polygonArea(room.poly));
                    if (pxArea > 1 && a > 0.5) scales.add(Math.sqrt(pxArea / a));
                }
            }
        }
        if (scales.size() >= 2) return G.median(scales);
        return scales.isEmpty() ? 0 : scales.get(0);
    }
}
