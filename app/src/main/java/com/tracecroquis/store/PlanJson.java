package com.tracecroquis.store;

import com.tracecroquis.core.model.Enums.OpeningType;
import com.tracecroquis.core.model.Enums.SymbolType;
import com.tracecroquis.core.model.Enums.TextRole;
import com.tracecroquis.core.model.Enums.WallType;
import com.tracecroquis.core.model.Plan;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/** Serialisation du plan (sauvegarde de projet et pile d'annulation). */
public final class PlanJson {

    private PlanJson() { }

    public static JSONObject toJson(Plan p) throws JSONException {
        JSONObject o = new JSONObject();
        o.put("projectName", p.projectName);
        o.put("levelName", p.levelName);
        o.put("sheetNumber", p.sheetNumber);
        o.put("revision", p.revision);
        o.put("owner", p.owner);
        o.put("address", p.address);
        o.put("pxPerMeter", p.pxPerMeter);
        o.put("scaleConfirmed", p.scaleConfirmed);
        o.put("imageW", p.imageW);
        o.put("imageH", p.imageH);

        JSONArray nodes = new JSONArray();
        for (Plan.Node n : p.nodes) {
            nodes.put(new JSONArray().put(n.x).put(n.y));
        }
        o.put("nodes", nodes);

        JSONArray walls = new JSONArray();
        for (Plan.Wall w : p.walls) {
            JSONObject j = new JSONObject();
            j.put("a", w.a).put("b", w.b).put("type", w.type.name());
            j.put("tM", w.thicknessM).put("tPx", w.thicknessPx);
            j.put("ext", w.exterior).put("edit", w.userEdited).put("conf", w.confidence);
            walls.put(j);
        }
        o.put("walls", walls);

        JSONArray ops = new JSONArray();
        for (Plan.Opening op : p.openings) {
            JSONObject j = new JSONObject();
            j.put("w", op.wall).put("t", op.t).put("width", op.widthM).put("type", op.type.name());
            j.put("hinge", op.hingeSide).put("swing", op.swingSide).put("angle", op.swingAngleDeg);
            j.put("conf", op.confidence);
            ops.put(j);
        }
        o.put("openings", ops);

        JSONArray rooms = new JSONArray();
        for (Plan.Room r : p.rooms) {
            JSONObject j = new JSONObject();
            JSONArray poly = new JSONArray();
            for (double[] q : r.poly) poly.put(new JSONArray().put(q[0]).put(q[1]));
            j.put("poly", poly);
            j.put("name", r.name);
            j.put("area", Double.isNaN(r.declaredAreaM2) ? JSONObject.NULL : Double.valueOf(r.declaredAreaM2));
            j.put("lx", r.labelX).put("ly", r.labelY).put("ext", r.exterior);
            rooms.put(j);
        }
        o.put("rooms", rooms);

        JSONArray texts = new JSONArray();
        for (Plan.TextItem t : p.texts) {
            JSONObject j = new JSONObject();
            j.put("x", t.x).put("y", t.y).put("h", t.heightPx).put("a", t.angleDeg);
            j.put("text", t.text).put("role", t.role.name()).put("room", t.room);
            j.put("ocr", t.fromOcr).put("conf", t.confidence);
            texts.put(j);
        }
        o.put("texts", texts);

        JSONArray dims = new JSONArray();
        for (Plan.DimItem d : p.dims) {
            JSONObject j = new JSONObject();
            j.put("x1", d.x1).put("y1", d.y1).put("x2", d.x2).put("y2", d.y2);
            j.put("value", Double.isNaN(d.valueM) ? JSONObject.NULL : Double.valueOf(d.valueM));
            j.put("label", d.label).put("vert", d.vertical).put("ref", d.reference);
            j.put("off", d.offset).put("conf", d.confidence);
            dims.put(j);
        }
        o.put("dims", dims);

        JSONArray syms = new JSONArray();
        for (Plan.SymbolItem s : p.symbols) {
            JSONObject j = new JSONObject();
            j.put("type", s.type.name()).put("room", s.room).put("note", s.note);
            JSONArray paths = new JSONArray();
            for (double[] path : s.paths) {
                JSONArray pa = new JSONArray();
                for (double v : path) pa.put(v);
                paths.put(pa);
            }
            j.put("paths", paths);
            syms.put(j);
        }
        o.put("symbols", syms);
        return o;
    }

    public static Plan fromJson(JSONObject o) throws JSONException {
        Plan p = new Plan();
        p.projectName = o.optString("projectName", "Maison");
        p.levelName = o.optString("levelName", "Rez-de-chaussée");
        p.sheetNumber = o.optString("sheetNumber", "PL-001");
        p.revision = o.optString("revision", "A");
        p.owner = o.optString("owner", "");
        p.address = o.optString("address", "");
        p.pxPerMeter = o.optDouble("pxPerMeter", 0);
        p.scaleConfirmed = o.optBoolean("scaleConfirmed", false);
        p.imageW = o.optInt("imageW", 0);
        p.imageH = o.optInt("imageH", 0);

        JSONArray nodes = o.optJSONArray("nodes");
        if (nodes != null) {
            for (int i = 0; i < nodes.length(); i++) {
                JSONArray n = nodes.getJSONArray(i);
                p.addNode(n.getDouble(0), n.getDouble(1));
            }
        }
        JSONArray walls = o.optJSONArray("walls");
        if (walls != null) {
            for (int i = 0; i < walls.length(); i++) {
                JSONObject j = walls.getJSONObject(i);
                Plan.Wall w = new Plan.Wall(j.getInt("a"), j.getInt("b"));
                w.type = enumOf(WallType.class, j.optString("type"), WallType.CLOISON);
                w.thicknessM = j.optDouble("tM", 0.07);
                w.thicknessPx = j.optDouble("tPx", 4);
                w.exterior = j.optBoolean("ext", false);
                w.userEdited = j.optBoolean("edit", false);
                w.confidence = j.optDouble("conf", 1);
                p.walls.add(w);
            }
        }
        JSONArray ops = o.optJSONArray("openings");
        if (ops != null) {
            for (int i = 0; i < ops.length(); i++) {
                JSONObject j = ops.getJSONObject(i);
                Plan.Opening op = new Plan.Opening();
                op.wall = j.optInt("w", -1);
                op.t = j.optDouble("t", 0.5);
                op.widthM = j.optDouble("width", 0.83);
                op.type = enumOf(OpeningType.class, j.optString("type"), OpeningType.PORTE);
                op.hingeSide = j.optInt("hinge", 1);
                op.swingSide = j.optInt("swing", 1);
                op.swingAngleDeg = j.optDouble("angle", 90);
                op.confidence = j.optDouble("conf", 1);
                p.openings.add(op);
            }
        }
        JSONArray rooms = o.optJSONArray("rooms");
        if (rooms != null) {
            for (int i = 0; i < rooms.length(); i++) {
                JSONObject j = rooms.getJSONObject(i);
                Plan.Room r = new Plan.Room();
                JSONArray poly = j.optJSONArray("poly");
                if (poly != null) {
                    for (int k = 0; k < poly.length(); k++) {
                        JSONArray q = poly.getJSONArray(k);
                        r.poly.add(new double[]{q.getDouble(0), q.getDouble(1)});
                    }
                }
                r.name = j.optString("name", "");
                r.declaredAreaM2 = j.isNull("area") ? Double.NaN : j.optDouble("area", Double.NaN);
                r.labelX = j.optDouble("lx", 0);
                r.labelY = j.optDouble("ly", 0);
                r.exterior = j.optBoolean("ext", false);
                p.rooms.add(r);
            }
        }
        JSONArray texts = o.optJSONArray("texts");
        if (texts != null) {
            for (int i = 0; i < texts.length(); i++) {
                JSONObject j = texts.getJSONObject(i);
                Plan.TextItem t = new Plan.TextItem();
                t.x = j.optDouble("x", 0);
                t.y = j.optDouble("y", 0);
                t.heightPx = j.optDouble("h", 14);
                t.angleDeg = j.optDouble("a", 0);
                t.text = j.optString("text", "");
                t.role = enumOf(TextRole.class, j.optString("role"), TextRole.LIBRE);
                t.room = j.optInt("room", -1);
                t.fromOcr = j.optBoolean("ocr", false);
                t.confidence = j.optDouble("conf", 1);
                p.texts.add(t);
            }
        }
        JSONArray dims = o.optJSONArray("dims");
        if (dims != null) {
            for (int i = 0; i < dims.length(); i++) {
                JSONObject j = dims.getJSONObject(i);
                Plan.DimItem d = new Plan.DimItem();
                d.x1 = j.optDouble("x1", 0); d.y1 = j.optDouble("y1", 0);
                d.x2 = j.optDouble("x2", 0); d.y2 = j.optDouble("y2", 0);
                d.valueM = j.isNull("value") ? Double.NaN : j.optDouble("value", Double.NaN);
                d.label = j.optString("label", "");
                d.vertical = j.optBoolean("vert", false);
                d.reference = j.optBoolean("ref", false);
                d.offset = j.optDouble("off", 0);
                d.confidence = j.optDouble("conf", 1);
                p.dims.add(d);
            }
        }
        JSONArray syms = o.optJSONArray("symbols");
        if (syms != null) {
            for (int i = 0; i < syms.length(); i++) {
                JSONObject j = syms.getJSONObject(i);
                Plan.SymbolItem s = new Plan.SymbolItem();
                s.type = enumOf(SymbolType.class, j.optString("type"), SymbolType.INDETERMINE);
                s.room = j.optInt("room", -1);
                s.note = j.optString("note", "");
                JSONArray paths = j.optJSONArray("paths");
                if (paths != null) {
                    for (int k = 0; k < paths.length(); k++) {
                        JSONArray pa = paths.getJSONArray(k);
                        double[] path = new double[pa.length()];
                        for (int m = 0; m < pa.length(); m++) path[m] = pa.getDouble(m);
                        s.paths.add(path);
                    }
                }
                s.computeBounds();
                p.symbols.add(s);
            }
        }
        return p;
    }

    private static <T extends Enum<T>> T enumOf(Class<T> type, String name, T fallback) {
        try {
            return Enum.valueOf(type, name);
        } catch (Exception e) {
            return fallback;
        }
    }
}
