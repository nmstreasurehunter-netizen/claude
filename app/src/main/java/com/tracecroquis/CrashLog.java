package com.tracecroquis;

import android.app.Application;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Build;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;

import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;

/**
 * Journal de plantage local.
 *
 * <p>En cas d'erreur fatale, la trace est enregistree dans le dossier de
 * l'application ; au demarrage suivant, elle est proposee a la copie. Cela
 * permet de diagnostiquer un probleme sans cable ni outil de developpement.</p>
 */
public final class CrashLog {

    private static final String FILE = "dernier-plantage.txt";

    private CrashLog() { }

    /** Installe le gestionnaire d'erreurs. A appeler dans Application.onCreate. */
    public static void install(final Application app) {
        final Thread.UncaughtExceptionHandler previous = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
            @Override public void uncaughtException(Thread t, Throwable e) {
                try {
                    write(app, t, e);
                } catch (Throwable ignored) {
                }
                if (previous != null) previous.uncaughtException(t, e);
            }
        });
    }

    private static void write(Context ctx, Thread t, Throwable e) throws Exception {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        pw.println("TraceCroquis " + version(ctx));
        pw.println("Appareil : " + Build.MANUFACTURER + " " + Build.MODEL);
        pw.println("Android : " + Build.VERSION.RELEASE + " (API " + Build.VERSION.SDK_INT + ")");
        pw.println("Fil : " + t.getName());
        pw.println();
        e.printStackTrace(pw);
        pw.flush();
        FileOutputStream out = new FileOutputStream(new File(ctx.getFilesDir(), FILE));
        out.write(sw.toString().getBytes("UTF-8"));
        out.close();
    }

    public static String version(Context ctx) {
        try {
            return ctx.getPackageManager().getPackageInfo(ctx.getPackageName(), 0).versionName;
        } catch (Exception e) {
            return "?";
        }
    }

    /** Affiche le dernier plantage s'il y en a un, avec copie possible. */
    public static void showIfAny(final Context ctx) {
        final File f = new File(ctx.getFilesDir(), FILE);
        if (!f.exists()) return;
        String text;
        try {
            byte[] buf = new byte[(int) Math.min(f.length(), 60000)];
            java.io.FileInputStream in = new java.io.FileInputStream(f);
            int n = in.read(buf);
            in.close();
            text = new String(buf, 0, Math.max(0, n), "UTF-8");
        } catch (Exception e) {
            f.delete();
            return;
        }
        final String report = text;
        new AlertDialog.Builder(ctx)
                .setTitle("Rapport de plantage")
                .setMessage(report)
                .setPositiveButton("Copier", new android.content.DialogInterface.OnClickListener() {
                    @Override public void onClick(android.content.DialogInterface d, int w) {
                        ClipboardManager cm = (ClipboardManager) ctx.getSystemService(Context.CLIPBOARD_SERVICE);
                        if (cm != null) cm.setPrimaryClip(ClipData.newPlainText("TraceCroquis", report));
                        Toast.makeText(ctx, "Rapport copié", Toast.LENGTH_SHORT).show();
                        f.delete();
                    }
                })
                .setNegativeButton("Ignorer", new android.content.DialogInterface.OnClickListener() {
                    @Override public void onClick(android.content.DialogInterface d, int w) {
                        f.delete();
                    }
                })
                .show();
    }
}
