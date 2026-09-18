package com.tracecroquis.core.vector;

/** Zone de texte reconnue par l'OCR (coordonnees dans l'image analysee). */
public class OcrBox {

    public double x, y, w, h;
    public String text = "";
    public double confidence = 1;
    public boolean used = false;

    public OcrBox() { }

    public OcrBox(double x, double y, double w, double h, String text) {
        this.x = x; this.y = y; this.w = w; this.h = h; this.text = text;
    }

    public double cx() { return x + w / 2; }
    public double cy() { return y + h / 2; }
}
