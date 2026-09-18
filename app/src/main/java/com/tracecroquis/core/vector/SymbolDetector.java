package com.tracecroquis.core.vector;

import com.tracecroquis.core.geom.G;
import com.tracecroquis.core.img.Trace;
import com.tracecroquis.core.model.Enums.SymbolType;
import com.tracecroquis.core.model.Plan;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/** Escaliers, hachures (carrelage, terrasse, isolant), mobilier et vehicules. */
public final class SymbolDetector {

    /** Famille de traits paralleles regulierement espaces. */
    public static class Family {
        public final List<Seg> segs = new ArrayList<>();
        public double spacing;
        public double angle;
        public double minX, minY, maxX, maxY;

        public double w() { return maxX - minX; }
        public double h() { return maxY - minY; }
        public double cx() { return (minX + maxX) / 2; }
        public double cy() { return (minY + maxY) / 2; }
    }

    private SymbolDetector() { }

    /** Recherche les familles de traits paralleles equidistants. */
    public static List<Family> families(List<Seg> segs, double minSpacing, double maxSpacing,
                                        int minCount) {
        List<Family> out = new ArrayList<>();
        List<List<Seg>> buckets = new ArrayList<>();
        for (int i = 0; i < 18; i++) buckets.add(new ArrayList<Seg>());
        for (Seg s : segs) {
            if (s.consumed) continue;
            int b = (int) Math.floor(Math.toDegrees(s.ang()) / 10.0);
            if (b < 0) b = 0;
            if (b > 17) b = 17;
            buckets.get(b).add(s);
        }
        for (int b = 0; b < 18; b++) {
            List<Seg> list = new ArrayList<>(buckets.get(b));
            // Inclut les buckets voisins pour les orientations a cheval.
            list.addAll(buckets.get((b + 1) % 18));
            if (list.size() < minCount) continue;
            double ang = Math.toRadians(b * 10 + 5);
            final double nx = -Math.sin(ang), ny = Math.cos(ang);
            Collections.sort(list, new Comparator<Seg>() {
                @Override public int compare(Seg p, Seg q) {
                    return Double.compare(p.midX() * nx + p.midY() * ny,
                            q.midX() * nx + q.midY() * ny);
                }
            });
            int i = 0;
            while (i < list.size()) {
                int j = i;
                List<Seg> run = new ArrayList<>();
                run.add(list.get(i));
                double lastPos = list.get(i).midX() * nx + list.get(i).midY() * ny;
                double firstGap = -1;
                while (j + 1 < list.size()) {
                    Seg next = list.get(j + 1);
                    double pos = next.midX() * nx + next.midY() * ny;
                    double gap = pos - lastPos;
                    if (gap < minSpacing * 0.5 || gap > maxSpacing) break;
                    if (firstGap > 0 && Math.abs(gap - firstGap) > firstGap * 0.55) break;
                    // Les traits doivent aussi se recouvrir lateralement.
                    Seg prev = run.get(run.size() - 1);
                    if (G.overlapRatio(prev.x1, prev.y1, prev.x2, prev.y2,
                            next.x1, next.y1, next.x2, next.y2) < 0.25) break;
                    if (firstGap < 0) firstGap = gap;
                    run.add(next);
                    lastPos = pos;
                    j++;
                }
                if (run.size() >= minCount) {
                    Family f = new Family();
                    f.segs.addAll(run);
                    f.spacing = firstGap > 0 ? firstGap : minSpacing;
                    f.angle = ang;
                    f.minX = f.minY = Double.MAX_VALUE;
                    f.maxX = f.maxY = -Double.MAX_VALUE;
                    for (Seg s : run) {
                        f.minX = Math.min(f.minX, Math.min(s.x1, s.x2));
                        f.maxX = Math.max(f.maxX, Math.max(s.x1, s.x2));
                        f.minY = Math.min(f.minY, Math.min(s.y1, s.y2));
                        f.maxY = Math.max(f.maxY, Math.max(s.y1, s.y2));
                    }
                    out.add(f);
                }
                i = Math.max(j + 1, i + 1);
            }
        }
        return out;
    }

    /** Escaliers : marches paralleles regulieres et assez longues. */
    public static void detectStairs(Plan plan, List<Seg> segs, VectorOptions o, double minDim) {
        double minSp = 0.006 * minDim, maxSp = 0.055 * minDim;
        List<Family> fams = families(segs, minSp, maxSp, Math.max(3, o.stairMinSteps));
        for (Family f : fams) {
            double meanLen = 0;
            for (Seg s : f.segs) meanLen += s.len();
            meanLen /= f.segs.size();
            if (meanLen < 0.035 * minDim) continue;          // trop court : hachure
            if (meanLen > 0.45 * minDim) continue;           // trop long : mur ou cote
            if (f.spacing > meanLen * 0.9) continue;
            Plan.SymbolItem sym = new Plan.SymbolItem();
            sym.type = SymbolType.ESCALIER;
            for (Seg s : f.segs) {
                sym.paths.add(new double[]{s.x1, s.y1, s.x2, s.y2});
                s.consumed = true;
            }
            sym.computeBounds();
            sym.note = f.segs.size() + " marches";
            sym.confidence = 0.75;
            sym.room = RoomBuilder.roomAt(plan, sym.minX / 2 + sym.maxX / 2, sym.minY / 2 + sym.maxY / 2);
            plan.symbols.add(sym);
        }
    }

    /** Hachures : carrelage, terrasse, isolant dans l'epaisseur des murs. */
    public static void detectHatching(Plan plan, List<Seg> segs, VectorOptions o, double minDim) {
        double minSp = 0.002 * minDim, maxSp = o.hatchMaxSpacingFrac * minDim;
        List<Family> fams = families(segs, minSp, maxSp, 5);
        for (Family f : fams) {
            double meanLen = 0;
            for (Seg s : f.segs) meanLen += s.len();
            meanLen /= f.segs.size();
            SymbolType type = SymbolType.HACHURE;
            double cx = f.cx(), cy = f.cy();
            int room = RoomBuilder.roomAt(plan, cx, cy);
            if (insideWallBand(plan, f, minDim)) {
                type = SymbolType.ISOLANT;
            } else if (room >= 0) {
                Plan.Room r = plan.rooms.get(room);
                String n = r.name.toUpperCase(Locale.FRENCH);
                if (r.exterior || n.contains("TERRASSE") || n.contains("BALCON") || n.contains("PATIO")) {
                    type = SymbolType.TERRASSE;
                } else {
                    type = SymbolType.CARRELAGE;
                }
            } else {
                type = SymbolType.TERRASSE;   // hachures hors du batiment
            }
            Plan.SymbolItem sym = new Plan.SymbolItem();
            sym.type = type;
            for (Seg s : f.segs) {
                sym.paths.add(new double[]{s.x1, s.y1, s.x2, s.y2});
                s.consumed = true;
            }
            sym.computeBounds();
            sym.room = room;
            sym.confidence = 0.6;
            sym.note = String.format(Locale.FRENCH, "%d traits, pas %.0f px", f.segs.size(), f.spacing);
            plan.symbols.add(sym);
        }
    }

    private static boolean insideWallBand(Plan plan, Family f, double minDim) {
        double cx = f.cx(), cy = f.cy();
        for (Plan.Wall w : plan.walls) {
            Plan.Node a = plan.nodes.get(w.a), b = plan.nodes.get(w.b);
            double[] pr = G.project(cx, cy, a.x, a.y, b.x, b.y);
            if (pr[3] <= Math.max(w.thicknessPx * 0.8, 3)
                    && Math.min(f.w(), f.h()) <= w.thicknessPx * 1.6) {
                return true;
            }
        }
        return false;
    }

    /**
     * Mobilier, sanitaires, vehicules : tout ce qui reste a l'interieur d'une piece.
     *
     * @param strokes    polylignes du squelette
     * @param strokeUsed indique les polylignes deja exploitees
     */
    public static void detectFurniture(Plan plan, List<Trace.Stroke> strokes, boolean[] strokeUsed,
                                       VectorOptions o, double minDim) {
        java.util.HashMap<Integer, Plan.SymbolItem> byComp = new java.util.HashMap<>();
        for (int i = 0; i < strokes.size(); i++) {
            if (strokeUsed[i]) continue;
            Trace.Stroke s = strokes.get(i);
            if (s.length() < 0.012 * minDim) continue;
            Integer key = Integer.valueOf(s.component);
            Plan.SymbolItem sym = byComp.get(key);
            if (sym == null) {
                sym = new Plan.SymbolItem();
                sym.type = SymbolType.MOBILIER;
                byComp.put(key, sym);
            }
            List<double[]> simp = G.simplify(s.pts, 1.5);
            double[] path = new double[simp.size() * 2];
            for (int k = 0; k < simp.size(); k++) {
                path[2 * k] = simp.get(k)[0];
                path[2 * k + 1] = simp.get(k)[1];
            }
            sym.paths.add(path);
        }
        for (Plan.SymbolItem sym : byComp.values()) {
            sym.computeBounds();
            double w = sym.maxX - sym.minX, h = sym.maxY - sym.minY;
            if (Math.max(w, h) < 0.02 * minDim) continue;
            int room = RoomBuilder.roomAt(plan, (sym.minX + sym.maxX) / 2, (sym.minY + sym.maxY) / 2);
            sym.room = room;
            if (room >= 0) {
                String n = TextAnalyzer.stripAccents(plan.rooms.get(room).name.toUpperCase(Locale.FRENCH));
                if (n.contains("GARAGE") && Math.max(w, h) > 0.12 * minDim) {
                    sym.type = SymbolType.VEHICULE;
                } else if (n.contains("BAIN") || n.contains("WC") || n.contains("EAU")
                        || n.contains("SDB") || n.contains("SDD") || n.contains("TOILETTE")) {
                    sym.type = SymbolType.SANITAIRE;
                }
            }
            sym.confidence = 0.5;
            plan.symbols.add(sym);
        }
    }

    /** Cheminee : petit symbole adosse a un mur portant le mot "cheminee". */
    public static void tagFromTexts(Plan plan) {
        for (Plan.TextItem t : plan.texts) {
            String n = TextAnalyzer.stripAccents(t.text.toUpperCase(Locale.FRENCH));
            SymbolType type = null;
            if (n.contains("CHEMINEE")) type = SymbolType.CHEMINEE;
            else if (n.equals("N") || n.contains("NORD")) type = SymbolType.NORD;
            if (type == null) continue;
            Plan.SymbolItem best = null;
            double bd = Double.MAX_VALUE;
            for (Plan.SymbolItem s : plan.symbols) {
                double d = G.dist(t.x, t.y, (s.minX + s.maxX) / 2, (s.minY + s.maxY) / 2);
                if (d < bd) { bd = d; best = s; }
            }
            if (best != null && bd < 80) best.type = type;
        }
    }
}
