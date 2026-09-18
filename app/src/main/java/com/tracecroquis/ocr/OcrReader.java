package com.tracecroquis.ocr;

import android.graphics.Bitmap;
import android.graphics.Rect;
import android.util.Log;

import com.tracecroquis.core.vector.OcrBox;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Lecture des textes manuscrits ou imprimes du croquis.
 *
 * <p>ML Kit n'est appele que par reflexion : l'application compile et
 * fonctionne meme si la dependance est absente (build hors ligne). Dans ce
 * cas, les textes restent a saisir a l'etape 3.</p>
 */
public final class OcrReader {

    private static final String TAG = "TraceCroquis/OCR";
    private static Boolean available;

    private OcrReader() { }

    /** Vrai si le moteur de reconnaissance est present dans l'APK. */
    public static boolean isAvailable() {
        if (available == null) {
            try {
                Class.forName("com.google.mlkit.vision.text.TextRecognition");
                Class.forName("com.google.android.gms.tasks.Tasks");
                available = Boolean.TRUE;
            } catch (Throwable t) {
                available = Boolean.FALSE;
            }
        }
        return available.booleanValue();
    }

    /**
     * Reconnait les lignes de texte de l'image. A appeler hors du fil principal.
     * Renvoie une liste vide si la reconnaissance est indisponible.
     */
    public static List<OcrBox> read(Bitmap bitmap) {
        List<OcrBox> out = new ArrayList<>();
        if (!isAvailable() || bitmap == null) return out;
        try {
            Class<?> cOptions = Class.forName("com.google.mlkit.vision.text.latin.TextRecognizerOptions");
            Object options = cOptions.getField("DEFAULT_OPTIONS").get(null);

            Class<?> cRecognition = Class.forName("com.google.mlkit.vision.text.TextRecognition");
            Method getClient = null;
            for (Method m : cRecognition.getMethods()) {
                if (m.getName().equals("getClient") && m.getParameterTypes().length == 1) {
                    getClient = m;
                    break;
                }
            }
            if (getClient == null) return out;
            Object recognizer = getClient.invoke(null, options);

            Class<?> cInput = Class.forName("com.google.mlkit.vision.common.InputImage");
            Method fromBitmap = cInput.getMethod("fromBitmap", Bitmap.class, int.class);
            Object input = fromBitmap.invoke(null, bitmap, Integer.valueOf(0));

            Method process = null;
            for (Method m : recognizer.getClass().getMethods()) {
                if (m.getName().equals("process") && m.getParameterTypes().length == 1
                        && m.getParameterTypes()[0].isAssignableFrom(cInput)) {
                    process = m;
                    break;
                }
            }
            if (process == null) return out;
            Object task = process.invoke(recognizer, input);

            Class<?> cTasks = Class.forName("com.google.android.gms.tasks.Tasks");
            Class<?> cTask = Class.forName("com.google.android.gms.tasks.Task");
            Method await = cTasks.getMethod("await", cTask, long.class, TimeUnit.class);
            Object text = await.invoke(null, task, Long.valueOf(25), TimeUnit.SECONDS);
            if (text == null) return out;

            List<?> blocks = (List<?>) text.getClass().getMethod("getTextBlocks").invoke(text);
            for (Object block : blocks) {
                List<?> lines = (List<?>) block.getClass().getMethod("getLines").invoke(block);
                for (Object line : lines) {
                    String value = (String) line.getClass().getMethod("getText").invoke(line);
                    Rect box = (Rect) line.getClass().getMethod("getBoundingBox").invoke(line);
                    if (value == null || box == null) continue;
                    OcrBox b = new OcrBox(box.left, box.top, box.width(), box.height(), value.trim());
                    out.add(b);
                }
            }
            try {
                recognizer.getClass().getMethod("close").invoke(recognizer);
            } catch (Throwable ignored) {
            }
        } catch (Throwable t) {
            Log.w(TAG, "Reconnaissance de texte indisponible : " + t);
        }
        return out;
    }
}
