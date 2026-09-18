package com.tracecroquis.util;

import android.content.Context;
import android.content.SharedPreferences;

import com.tracecroquis.core.vector.VectorOptions;

/** Reglages persistants : parametres de detection et valeurs d'export. */
public final class Prefs {

    private static final String FILE = "tracecroquis";

    private final SharedPreferences sp;

    public Prefs(Context ctx) {
        sp = ctx.getApplicationContext().getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    // --- detection --------------------------------------------------------

    public VectorOptions options() {
        VectorOptions o = new VectorOptions();
        o.maxDimension = sp.getInt("maxDimension", o.maxDimension);
        o.binarizeK = sp.getFloat("binarizeK", (float) o.binarizeK);
        o.binarizeMargin = sp.getInt("binarizeMargin", o.binarizeMargin);
        o.minComponentArea = sp.getInt("minComponentArea", o.minComponentArea);
        o.removeGrid = sp.getBoolean("removeGrid", o.removeGrid);
        o.closingRadius = sp.getInt("closingRadius", o.closingRadius);
        o.bandRadius = sp.getInt("bandRadius", o.bandRadius);
        o.minWallLenFrac = sp.getFloat("minWallLenFrac", (float) o.minWallLenFrac);
        o.snapTolFrac = sp.getFloat("snapTolFrac", (float) o.snapTolFrac);
        o.doubleWallMaxGapFrac = sp.getFloat("doubleWallMaxGapFrac", (float) o.doubleWallMaxGapFrac);
        o.parallelTolDeg = sp.getFloat("parallelTolDeg", (float) o.parallelTolDeg);
        o.keepAngles = sp.getBoolean("keepAngles", o.keepAngles);
        o.angleSnapDeg = sp.getFloat("angleSnapDeg", (float) o.angleSnapDeg);
        o.defaultExteriorWallM = sp.getFloat("defaultExteriorWallM", (float) o.defaultExteriorWallM);
        o.defaultPartitionM = sp.getFloat("defaultPartitionM", (float) o.defaultPartitionM);
        o.doorRadiusMaxFrac = sp.getFloat("doorRadiusMaxFrac", (float) o.doorRadiusMaxFrac);
        o.textMaxHeightFrac = sp.getFloat("textMaxHeightFrac", (float) o.textMaxHeightFrac);
        o.detectWalls = sp.getBoolean("detectWalls", o.detectWalls);
        o.detectOpenings = sp.getBoolean("detectOpenings", o.detectOpenings);
        o.detectRooms = sp.getBoolean("detectRooms", o.detectRooms);
        o.detectDims = sp.getBoolean("detectDims", o.detectDims);
        o.detectFurniture = sp.getBoolean("detectFurniture", o.detectFurniture);
        o.detectTextures = sp.getBoolean("detectTextures", o.detectTextures);
        return o;
    }

    public void save(VectorOptions o) {
        sp.edit()
                .putInt("maxDimension", o.maxDimension)
                .putFloat("binarizeK", (float) o.binarizeK)
                .putInt("binarizeMargin", o.binarizeMargin)
                .putInt("minComponentArea", o.minComponentArea)
                .putBoolean("removeGrid", o.removeGrid)
                .putInt("closingRadius", o.closingRadius)
                .putInt("bandRadius", o.bandRadius)
                .putFloat("minWallLenFrac", (float) o.minWallLenFrac)
                .putFloat("snapTolFrac", (float) o.snapTolFrac)
                .putFloat("doubleWallMaxGapFrac", (float) o.doubleWallMaxGapFrac)
                .putFloat("parallelTolDeg", (float) o.parallelTolDeg)
                .putBoolean("keepAngles", o.keepAngles)
                .putFloat("angleSnapDeg", (float) o.angleSnapDeg)
                .putFloat("defaultExteriorWallM", (float) o.defaultExteriorWallM)
                .putFloat("defaultPartitionM", (float) o.defaultPartitionM)
                .putFloat("doorRadiusMaxFrac", (float) o.doorRadiusMaxFrac)
                .putFloat("textMaxHeightFrac", (float) o.textMaxHeightFrac)
                .putBoolean("detectWalls", o.detectWalls)
                .putBoolean("detectOpenings", o.detectOpenings)
                .putBoolean("detectRooms", o.detectRooms)
                .putBoolean("detectDims", o.detectDims)
                .putBoolean("detectFurniture", o.detectFurniture)
                .putBoolean("detectTextures", o.detectTextures)
                .apply();
    }

    // --- export -----------------------------------------------------------

    public boolean useOcr() { return sp.getBoolean("useOcr", true); }

    public void setUseOcr(boolean v) { sp.edit().putBoolean("useOcr", v).apply(); }

    public String owner() { return sp.getString("owner", ""); }

    public void setOwner(String v) { sp.edit().putString("owner", v).apply(); }

    public String address() { return sp.getString("address", ""); }

    public void setAddress(String v) { sp.edit().putString("address", v).apply(); }

    public boolean showDims() { return sp.getBoolean("showDims", true); }

    public void setShowDims(boolean v) { sp.edit().putBoolean("showDims", v).apply(); }

    public boolean titleBlock() { return sp.getBoolean("titleBlock", true); }

    public void setTitleBlock(boolean v) { sp.edit().putBoolean("titleBlock", v).apply(); }

    public void reset() { sp.edit().clear().apply(); }
}
