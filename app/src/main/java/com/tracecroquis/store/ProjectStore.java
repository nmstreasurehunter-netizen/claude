package com.tracecroquis.store;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import com.tracecroquis.core.model.Plan;

import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FileInputStream;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/** Projets enregistres sur l'appareil (plan modifiable + croquis + vignette). */
public final class ProjectStore {

    /** Entree de la liste des projets. */
    public static class Entry {
        public String id;
        public String name;
        public String level;
        public long modified;
        public File thumb;
        public int rooms;
        public double area;
    }

    private final File root;

    public ProjectStore(Context ctx) {
        root = new File(ctx.getFilesDir(), "projets");
        if (!root.exists()) root.mkdirs();
    }

    public File dir(String id) {
        File d = new File(root, id);
        if (!d.exists()) d.mkdirs();
        return d;
    }

    public static String newId() {
        return "p" + System.currentTimeMillis();
    }

    public void save(String id, Plan plan, Bitmap sketch) throws Exception {
        File d = dir(id);
        JSONObject o = PlanJson.toJson(plan);
        o.put("modified", System.currentTimeMillis());
        write(new File(d, "plan.json"), o.toString());
        if (sketch != null && !sketch.isRecycled()) {
            FileOutputStream fo = new FileOutputStream(new File(d, "croquis.jpg"));
            sketch.compress(Bitmap.CompressFormat.JPEG, 82, fo);
            fo.close();
            int tw = 320;
            int th = Math.max(1, sketch.getHeight() * tw / Math.max(1, sketch.getWidth()));
            Bitmap thumb = Bitmap.createScaledBitmap(sketch, tw, th, true);
            fo = new FileOutputStream(new File(d, "vignette.jpg"));
            thumb.compress(Bitmap.CompressFormat.JPEG, 78, fo);
            fo.close();
            if (thumb != sketch) thumb.recycle();
        }
    }

    public Plan loadPlan(String id) throws Exception {
        String s = read(new File(dir(id), "plan.json"));
        return PlanJson.fromJson(new JSONObject(s));
    }

    public Bitmap loadSketch(String id) {
        File f = new File(dir(id), "croquis.jpg");
        if (!f.exists()) return null;
        return BitmapFactory.decodeFile(f.getAbsolutePath());
    }

    public List<Entry> list() {
        List<Entry> out = new ArrayList<>();
        File[] dirs = root.listFiles();
        if (dirs == null) return out;
        for (File d : dirs) {
            File pj = new File(d, "plan.json");
            if (!pj.exists()) continue;
            try {
                JSONObject o = new JSONObject(read(pj));
                Entry e = new Entry();
                e.id = d.getName();
                e.name = o.optString("projectName", "Projet");
                e.level = o.optString("levelName", "");
                e.modified = o.optLong("modified", pj.lastModified());
                e.rooms = o.optJSONArray("rooms") == null ? 0 : o.optJSONArray("rooms").length();
                File t = new File(d, "vignette.jpg");
                e.thumb = t.exists() ? t : null;
                Plan p = PlanJson.fromJson(o);
                e.area = p.totalAreaM2();
                out.add(e);
            } catch (Exception ignored) {
            }
        }
        Collections.sort(out, new Comparator<Entry>() {
            @Override public int compare(Entry a, Entry b) {
                return Long.compare(b.modified, a.modified);
            }
        });
        return out;
    }

    public void delete(String id) {
        File d = new File(root, id);
        File[] fs = d.listFiles();
        if (fs != null) for (File f : fs) f.delete();
        d.delete();
    }

    private static void write(File f, String content) throws Exception {
        FileOutputStream o = new FileOutputStream(f);
        o.write(content.getBytes("UTF-8"));
        o.close();
    }

    private static String read(File f) throws Exception {
        FileInputStream in = new FileInputStream(f);
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) > 0) bo.write(buf, 0, n);
        in.close();
        return new String(bo.toByteArray(), "UTF-8");
    }
}
