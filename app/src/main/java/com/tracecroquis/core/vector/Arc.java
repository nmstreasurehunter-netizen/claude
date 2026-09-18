package com.tracecroquis.core.vector;

/** Arc de cercle detecte (typiquement un battant de porte). */
public class Arc {

    public double cx, cy, r;
    /** Angles de debut et de fin (radians, sens trigonometrique ecran). */
    public double a1, a2;
    public double sweep;       // amplitude absolue (radians)
    public double rms;         // erreur d'ajustement
    public double thickness = 1;
    public int stroke = -1;
    public boolean consumed = false;

    public double startX() { return cx + r * Math.cos(a1); }
    public double startY() { return cy + r * Math.sin(a1); }
    public double endX() { return cx + r * Math.cos(a2); }
    public double endY() { return cy + r * Math.sin(a2); }

    public double midX() { return cx + r * Math.cos((a1 + a2) / 2); }
    public double midY() { return cy + r * Math.sin((a1 + a2) / 2); }
}
