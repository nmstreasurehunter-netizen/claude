package com.tracecroquis.util;

import java.util.Locale;

/** Mise en forme des nombres a la francaise. */
public final class Fmt {

    private Fmt() { }

    public static String m(double meters) {
        return String.format(Locale.FRENCH, "%.2f m", meters);
    }

    public static String m2(double area) {
        return String.format(Locale.FRENCH, "%.1f m²", area);
    }

    public static String cm(double meters) {
        return String.format(Locale.FRENCH, "%.0f cm", meters * 100);
    }

    public static String num(double v, int dec) {
        return String.format(Locale.FRENCH, "%." + dec + "f", v);
    }

    public static double parse(String s, double fallback) {
        if (s == null) return fallback;
        try {
            return Double.parseDouble(s.trim().replace(',', '.').replace(" ", ""));
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    public static String date(long millis) {
        return new java.text.SimpleDateFormat("dd/MM/yyyy", Locale.FRENCH).format(new java.util.Date(millis));
    }
}
