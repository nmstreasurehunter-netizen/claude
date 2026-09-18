package com.tracecroquis;

import android.graphics.Bitmap;

import com.tracecroquis.core.model.Plan;
import com.tracecroquis.core.vector.VectorOptions;
import com.tracecroquis.core.vector.Vectorizer;
import com.tracecroquis.store.PlanJson;

import org.json.JSONObject;

import java.util.ArrayDeque;
import java.util.Deque;

/** Etat courant du parcours en 4 etapes (partage entre les ecrans). */
public final class Session {

    private static Session instance;

    public static synchronized Session get() {
        if (instance == null) instance = new Session();
        return instance;
    }

    // --- etape 1 ---------------------------------------------------------
    /** Image d'origine importee. */
    public Bitmap source;
    /** Croquis recadre et redresse, utilise pour l'analyse. */
    public Bitmap sketch;
    /** Coins du cadrage dans le repere de l'image source (HG, HD, BD, BG). */
    public float[] corners;
    public int rotation;
    public float contrast = 1f;
    public String sourceName = "";

    // --- etapes 2 a 4 ----------------------------------------------------
    public Plan plan = new Plan();
    public Vectorizer.Result result;
    public VectorOptions options = new VectorOptions();

    public String projectId;
    public boolean dirty;

    // --- export ----------------------------------------------------------
    public String format = "PDF";
    public String paper = "";          // vide = automatique
    public boolean landscape = true;
    public int scaleDen = 0;           // 0 = automatique
    public String unit = "m";
    public boolean autoLayout = true;

    private final Deque<String> undo = new ArrayDeque<>();
    private final Deque<String> redo = new ArrayDeque<>();

    private Session() { }

    public boolean hasSketch() {
        return sketch != null && !sketch.isRecycled();
    }

    public boolean hasPlan() {
        return plan != null && !plan.walls.isEmpty();
    }

    /** Enregistre l'etat du plan avant modification. */
    public void snapshot() {
        try {
            undo.push(PlanJson.toJson(plan).toString());
            if (undo.size() > 25) undo.removeLast();
            redo.clear();
            dirty = true;
        } catch (Exception ignored) {
        }
    }

    public boolean canUndo() { return !undo.isEmpty(); }

    public boolean canRedo() { return !redo.isEmpty(); }

    public void undo() {
        if (undo.isEmpty()) return;
        try {
            redo.push(PlanJson.toJson(plan).toString());
            plan = PlanJson.fromJson(new JSONObject(undo.pop()));
        } catch (Exception ignored) {
        }
    }

    public void redo() {
        if (redo.isEmpty()) return;
        try {
            undo.push(PlanJson.toJson(plan).toString());
            plan = PlanJson.fromJson(new JSONObject(redo.pop()));
        } catch (Exception ignored) {
        }
    }

    public void clearHistory() {
        undo.clear();
        redo.clear();
    }

    /** Nouveau projet : libere les images et remet l'etat a zero. */
    public void reset() {
        if (source != null && !source.isRecycled()) source.recycle();
        if (sketch != null && !sketch.isRecycled()) sketch.recycle();
        source = null;
        sketch = null;
        corners = null;
        rotation = 0;
        contrast = 1f;
        sourceName = "";
        plan = new Plan();
        result = null;
        projectId = null;
        dirty = false;
        paper = "";
        scaleDen = 0;
        autoLayout = true;
        clearHistory();
    }
}
