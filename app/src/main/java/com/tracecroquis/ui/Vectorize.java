package com.tracecroquis.ui;

import android.app.Dialog;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;

import com.tracecroquis.Session;
import com.tracecroquis.core.model.Plan;
import com.tracecroquis.core.vector.OcrBox;
import com.tracecroquis.core.vector.VectorOptions;
import com.tracecroquis.core.vector.Vectorizer;
import com.tracecroquis.ocr.OcrReader;
import com.tracecroquis.util.Async;
import com.tracecroquis.util.Img;

import java.util.List;

/** Lancement de la vectorisation avec preparation de l'image et OCR. */
public final class Vectorize {

    public interface Done {
        void onFinished(boolean ok, String message);
    }

    private Vectorize() { }

    /** Prepare le croquis (recadrage, rotation, contraste) puis vectorise. */
    public static void run(final MainActivity act, final boolean rebuildSketch, final Done done) {
        final Session s = Session.get();
        if (s.source == null || s.source.isRecycled()) {
            done.onFinished(false, "Aucun croquis à vectoriser");
            return;
        }
        final VectorOptions opt = s.options;
        final boolean useOcr = act.prefs().useOcr();
        final ProgressDialog dlg = new ProgressDialog(act);
        dlg.show("Analyse du croquis…");

        Async.run(new Async.Job<Vectorizer.Result>() {
            @Override public Vectorizer.Result run() throws Exception {
                Bitmap sketch = s.sketch;
                if (rebuildSketch || sketch == null || sketch.isRecycled()) {
                    sketch = prepare(s, opt.maxDimension);
                    s.sketch = sketch;
                }
                dlg.progress(8, "Lecture des textes…");
                List<OcrBox> ocr = null;
                if (useOcr && OcrReader.isAvailable()) {
                    ocr = OcrReader.read(sketch);
                }
                final int w = sketch.getWidth(), h = sketch.getHeight();
                int[] px = Img.pixels(sketch);
                return Vectorizer.run(px, w, h, opt, ocr, new Vectorizer.Progress() {
                    @Override public void onProgress(int percent, String message) {
                        dlg.progress(percent, message);
                    }
                });
            }
        }, new Async.Done<Vectorizer.Result>() {
            @Override public void onDone(Vectorizer.Result r, Exception e) {
                dlg.dismiss();
                if (e != null || r == null) {
                    done.onFinished(false, "Échec de la vectorisation : "
                            + (e == null ? "inconnu" : e.getMessage()));
                    return;
                }
                Plan previous = s.plan;
                s.result = r;
                s.plan = r.plan;
                if (previous != null) {
                    if (previous.projectName != null && !previous.projectName.isEmpty()) {
                        s.plan.projectName = previous.projectName;
                    }
                    if (previous.levelName != null && !previous.levelName.isEmpty()) {
                        s.plan.levelName = previous.levelName;
                    }
                    s.plan.owner = previous.owner;
                    s.plan.address = previous.address;
                }
                s.clearHistory();
                s.dirty = true;
                done.onFinished(true, r.summary());
            }
        });
    }

    /** Applique le cadrage, la rotation et le contraste choisis a l'etape 1. */
    public static Bitmap prepare(Session s, int maxDim) {
        Bitmap b = s.source;
        if (s.corners != null) {
            b = Img.warp(b, s.corners, maxDim);
        } else {
            b = Bitmap.createScaledBitmap(b,
                    Math.max(1, Math.round(b.getWidth() * scaleFor(b, maxDim))),
                    Math.max(1, Math.round(b.getHeight() * scaleFor(b, maxDim))), true);
        }
        if (s.rotation != 0) b = Img.rotate(b, s.rotation);
        if (Math.abs(s.contrast - 1f) > 0.02f) b = Img.adjust(b, s.contrast, 0);
        return b;
    }

    private static float scaleFor(Bitmap b, int maxDim) {
        int big = Math.max(b.getWidth(), b.getHeight());
        return big <= maxDim ? 1f : maxDim / (float) big;
    }

    /** Petite boite de progression sans dependance externe. */
    public static class ProgressDialog {
        private final MainActivity act;
        private Dialog dialog;
        private ProgressBar bar;
        private TextView label;

        public ProgressDialog(MainActivity act) {
            this.act = act;
        }

        public void show(String message) {
            LinearLayout root = new LinearLayout(act);
            root.setOrientation(LinearLayout.VERTICAL);
            int pad = (int) (22 * act.getResources().getDisplayMetrics().density);
            root.setPadding(pad, pad, pad, pad);
            root.setBackgroundColor(0xFF0C1B35);
            label = new TextView(act);
            label.setText(message);
            label.setTextColor(Color.WHITE);
            label.setTextSize(16);
            label.setGravity(Gravity.CENTER);
            bar = new ProgressBar(act, null, android.R.attr.progressBarStyleHorizontal);
            bar.setMax(100);
            bar.setIndeterminate(false);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.topMargin = pad / 2;
            root.addView(label);
            root.addView(bar, lp);
            dialog = new AlertDialog.Builder(act).setView(root).setCancelable(false).create();
            dialog.show();
        }

        public void progress(final int percent, final String message) {
            Async.ui(new Runnable() {
                @Override public void run() {
                    if (bar != null) bar.setProgress(percent);
                    if (label != null && message != null) label.setText(message);
                }
            });
        }

        public void dismiss() {
            if (dialog != null && dialog.isShowing()) {
                try {
                    dialog.dismiss();
                } catch (Exception ignored) {
                }
            }
        }

        public View view() { return bar; }
    }
}
