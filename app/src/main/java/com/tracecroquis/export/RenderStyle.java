package com.tracecroquis.export;

/** Habillage graphique du plan (rendu ecran ou rendu papier). */
public class RenderStyle {

    public int paper = 0xFFFFFFFF;
    public int wallSolid = 0xFF0D0D0D;
    public int partition = 0xFF3C3C3C;
    public int wallOutline = 0xFF000000;
    public int opening = 0xFF101010;
    public int dim = 0xFF16305E;
    public int text = 0xFF101010;
    public int roomText = 0xFF101010;
    public int symbol = 0xFF6B7280;
    public int selection = 0xFF22D3EE;

    /** Unites canvas par millimetre papier (echelle des epaisseurs de trait). */
    public float mm = 3.78f;

    public boolean showWalls = true;
    public boolean showOpenings = true;
    public boolean showRooms = true;
    public boolean showAreas = true;
    public boolean showDims = true;
    public boolean showSymbols = true;
    public boolean showTexts = true;
    public boolean showNorth = true;
    public boolean showScaleBar = true;
    /** Rendu d'ecran : traits colores, murs non remplis. */
    public boolean screen = false;
    public String unit = "m";

    public static RenderStyle paperStyle(float mm) {
        RenderStyle s = new RenderStyle();
        s.mm = mm;
        return s;
    }

    public static RenderStyle screenStyle(float density) {
        RenderStyle s = new RenderStyle();
        s.mm = density * 0.6f;
        s.screen = true;
        s.paper = 0xFFF7F4EC;
        s.wallSolid = 0xFF0B2545;
        s.partition = 0xFF2C5282;
        s.dim = 0xFF7C3AED;
        s.symbol = 0xFF64748B;
        s.showNorth = false;
        s.showScaleBar = false;
        return s;
    }
}
