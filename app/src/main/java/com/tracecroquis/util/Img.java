package com.tracecroquis.util;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.media.ExifInterface;
import android.net.Uri;

import java.io.IOException;
import java.io.InputStream;

/** Chargement, redressement et correction des images de croquis. */
public final class Img {

    private Img() { }

    /** Charge une image en la limitant a maxDim pixels de cote. */
    public static Bitmap load(Context ctx, Uri uri, int maxDim) throws IOException {
        BitmapFactory.Options probe = new BitmapFactory.Options();
        probe.inJustDecodeBounds = true;
        InputStream in = ctx.getContentResolver().openInputStream(uri);
        if (in == null) throw new IOException("Image illisible");
        BitmapFactory.decodeStream(in, null, probe);
        in.close();

        int sample = 1;
        int big = Math.max(probe.outWidth, probe.outHeight);
        while (big / sample > maxDim * 2) sample *= 2;

        BitmapFactory.Options opt = new BitmapFactory.Options();
        opt.inSampleSize = sample;
        opt.inPreferredConfig = Bitmap.Config.ARGB_8888;
        in = ctx.getContentResolver().openInputStream(uri);
        if (in == null) throw new IOException("Image illisible");
        Bitmap bmp = BitmapFactory.decodeStream(in, null, opt);
        in.close();
        if (bmp == null) throw new IOException("Format non reconnu");

        bmp = applyExif(ctx, uri, bmp);
        return scaleTo(bmp, maxDim);
    }

    public static Bitmap scaleTo(Bitmap src, int maxDim) {
        int big = Math.max(src.getWidth(), src.getHeight());
        if (big <= maxDim) return src;
        float s = maxDim / (float) big;
        Bitmap out = Bitmap.createScaledBitmap(src,
                Math.max(1, Math.round(src.getWidth() * s)),
                Math.max(1, Math.round(src.getHeight() * s)), true);
        if (out != src) src.recycle();
        return out;
    }

    private static Bitmap applyExif(Context ctx, Uri uri, Bitmap bmp) {
        try {
            InputStream in = ctx.getContentResolver().openInputStream(uri);
            if (in == null) return bmp;
            ExifInterface exif = new ExifInterface(in);
            int o = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
            in.close();
            int deg = 0;
            if (o == ExifInterface.ORIENTATION_ROTATE_90) deg = 90;
            else if (o == ExifInterface.ORIENTATION_ROTATE_180) deg = 180;
            else if (o == ExifInterface.ORIENTATION_ROTATE_270) deg = 270;
            if (deg != 0) return rotate(bmp, deg);
        } catch (Exception ignored) {
        }
        return bmp;
    }

    public static Bitmap rotate(Bitmap src, int degrees) {
        Matrix m = new Matrix();
        m.postRotate(degrees);
        Bitmap out = Bitmap.createBitmap(src, 0, 0, src.getWidth(), src.getHeight(), m, true);
        if (out != src) src.recycle();
        return out;
    }

    /** Contraste et luminosite (valeurs neutres : 1 et 0). */
    public static Bitmap adjust(Bitmap src, float contrast, float brightness) {
        Bitmap out = Bitmap.createBitmap(src.getWidth(), src.getHeight(), Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(out);
        Paint p = new Paint(Paint.FILTER_BITMAP_FLAG);
        float t = 127.5f * (1 - contrast) + brightness;
        p.setColorFilter(new ColorMatrixColorFilter(new ColorMatrix(new float[]{
                contrast, 0, 0, 0, t,
                0, contrast, 0, 0, t,
                0, 0, contrast, 0, t,
                0, 0, 0, 1, 0})));
        c.drawBitmap(src, 0, 0, p);
        return out;
    }

    /**
     * Redressement perspectif a partir des 4 coins (ordre : HG, HD, BD, BG),
     * exprimes en coordonnees de l'image source.
     */
    public static Bitmap warp(Bitmap src, float[] corners, int maxDim) {
        float wTop = dist(corners, 0, 1), wBot = dist(corners, 3, 2);
        float hLeft = dist(corners, 0, 3), hRight = dist(corners, 1, 2);
        int w = Math.max(16, Math.round(Math.max(wTop, wBot)));
        int h = Math.max(16, Math.round(Math.max(hLeft, hRight)));
        float s = Math.min(1f, maxDim / (float) Math.max(w, h));
        w = Math.max(16, Math.round(w * s));
        h = Math.max(16, Math.round(h * s));

        Bitmap out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(out);
        c.drawColor(0xFFFFFFFF);
        Matrix m = new Matrix();
        boolean ok = m.setPolyToPoly(corners, 0,
                new float[]{0, 0, w, 0, w, h, 0, h}, 0, 4);
        if (!ok) {
            c.drawBitmap(src, null, new android.graphics.Rect(0, 0, w, h), new Paint(Paint.FILTER_BITMAP_FLAG));
            return out;
        }
        c.drawBitmap(src, m, new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG));
        return out;
    }

    private static float dist(float[] p, int a, int b) {
        return (float) Math.hypot(p[2 * b] - p[2 * a], p[2 * b + 1] - p[2 * a + 1]);
    }

    public static int[] pixels(Bitmap b) {
        int[] px = new int[b.getWidth() * b.getHeight()];
        b.getPixels(px, 0, b.getWidth(), 0, 0, b.getWidth(), b.getHeight());
        return px;
    }
}
