package com.tracecroquis.ui;

import android.os.Bundle;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;

import com.tracecroquis.R;
import com.tracecroquis.Session;
import com.tracecroquis.core.vector.VectorOptions;
import com.tracecroquis.ocr.OcrReader;
import com.tracecroquis.util.Fmt;
import com.tracecroquis.util.Prefs;

/**
 * Reglages de la reconnaissance. Chaque curseur agit directement sur la
 * vectorisation : c'est ici que se regle la precision sur vos croquis.
 */
public class SettingsFragment extends StepFragment {

    private LinearLayout container;
    private Prefs prefs;

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inf, @Nullable ViewGroup parent, @Nullable Bundle b) {
        View v = inf.inflate(R.layout.fragment_settings, parent, false);
        container = v.findViewById(R.id.container);
        prefs = main().prefs();
        v.findViewById(R.id.btnBack).setVisibility(View.INVISIBLE);
        v.findViewById(R.id.btnHelp).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View x) {
                new AlertDialog.Builder(getContext())
                        .setTitle(R.string.settings_detection)
                        .setMessage("Ces réglages pilotent la vectorisation. Après modification, "
                                + "relancez la vectorisation à l'étape 2 pour comparer.")
                        .setPositiveButton(R.string.ok, null).show();
            }
        });
        build();
        return v;
    }

    private void build() {
        final VectorOptions o = Session.get().options;

        section(R.string.settings_detection);
        slider("Sensibilité du trait", "Binarisation locale : augmentez pour un crayon clair",
                0.10, 0.45, o.binarizeK, 2, new Set() {
                    @Override public void set(double v) { o.binarizeK = v; save(o); }
                });
        slider("Filtrage du quadrillage", "Baissez pour ignorer un papier millimétré très marqué",
                5, 70, o.binarizeMargin, 0, new Set() {
                    @Override public void set(double v) { o.binarizeMargin = (int) Math.round(v); save(o); }
                });
        slider("Soudure des traits repassés", "-1 = automatique ; augmentez pour un croquis très hachuré",
                -1, 4, o.closingRadius, 0, new Set() {
                    @Override public void set(double v) { o.closingRadius = (int) Math.round(v); save(o); }
                });
        slider("Seuil mur plein (px)", "0 = automatique ; épaisseur à partir de laquelle un trait devient un voile",
                0, 40, o.bandRadius > 0 ? o.bandRadius * 2 : 0, 0, new Set() {
                    @Override public void set(double v) {
                        o.bandRadius = v < 1 ? -1 : (int) Math.round(v / 2);
                        save(o);
                    }
                });
        slider("Longueur minimale d'un mur", "En fraction de la petite dimension du croquis",
                0.01, 0.10, o.minWallLenFrac, 3, new Set() {
                    @Override public void set(double v) { o.minWallLenFrac = v; save(o); }
                });
        slider("Tolérance de raccord", "Rapprochement des extrémités de murs",
                0.004, 0.04, o.snapTolFrac, 3, new Set() {
                    @Override public void set(double v) { o.snapTolFrac = v; save(o); }
                });
        slider("Écartement max. d'un mur double", "Deux traits parallèles plus proches forment un seul mur",
                0.008, 0.09, o.doubleWallMaxGapFrac, 3, new Set() {
                    @Override public void set(double v) { o.doubleWallMaxGapFrac = v; save(o); }
                });
        slider("Tolérance de parallélisme (°)", "Appariement des deux faces d'un mur",
                3, 20, o.parallelTolDeg, 0, new Set() {
                    @Override public void set(double v) { o.parallelTolDeg = v; save(o); }
                });
        slider("Redressement des angles (°)", "Utilisé quand « Respecter les angles » est désactivé",
                4, 30, o.angleSnapDeg, 0, new Set() {
                    @Override public void set(double v) { o.angleSnapDeg = v; save(o); }
                });
        slider("Rayon max. d'un arc de porte", "En fraction de la petite dimension",
                0.04, 0.30, o.doorRadiusMaxFrac, 3, new Set() {
                    @Override public void set(double v) { o.doorRadiusMaxFrac = v; save(o); }
                });
        slider("Hauteur max. d'un texte", "Au-delà, le trait est considéré comme un dessin",
                0.02, 0.12, o.textMaxHeightFrac, 3, new Set() {
                    @Override public void set(double v) { o.textMaxHeightFrac = v; save(o); }
                });
        slider("Résolution d'analyse (px)", "Plus haut = plus précis mais plus lent",
                800, 2400, o.maxDimension, 0, new Set() {
                    @Override public void set(double v) { o.maxDimension = (int) Math.round(v); save(o); }
                });

        toggle("Supprimer le quadrillage", "Efface les lignes traversant toute la feuille",
                o.removeGrid, new Toggle() {
                    @Override public void set(boolean v) { o.removeGrid = v; save(o); }
                });
        toggle("Respecter les angles du croquis", "Désactivé, le plan est redressé à l'équerre",
                o.keepAngles, new Toggle() {
                    @Override public void set(boolean v) { o.keepAngles = v; save(o); }
                });
        toggle("Lecture des textes (OCR)",
                OcrReader.isAvailable() ? "Noms de pièces, surfaces et cotes manuscrites"
                        : "Moteur absent de cette version : textes à saisir à l'étape 3",
                prefs.useOcr(), new Toggle() {
                    @Override public void set(boolean v) { prefs.setUseOcr(v); }
                });

        section(R.string.settings_defaults);
        slider("Épaisseur des murs de façade (cm)", "Utilisée quand le croquis ne la montre pas",
                10, 40, o.defaultExteriorWallM * 100, 0, new Set() {
                    @Override public void set(double v) { o.defaultExteriorWallM = v / 100; save(o); }
                });
        slider("Épaisseur des cloisons (cm)", "Valeur courante : 7 cm",
                4, 20, o.defaultPartitionM * 100, 0, new Set() {
                    @Override public void set(double v) { o.defaultPartitionM = v / 100; save(o); }
                });
        textField("Maître d'ouvrage", prefs.owner(), new Text() {
            @Override public void set(String v) { prefs.setOwner(v); }
        });
        textField("Adresse du terrain", prefs.address(), new Text() {
            @Override public void set(String v) { prefs.setAddress(v); }
        });

        section(R.string.settings_about);
        TextView about = new TextView(getContext());
        about.setTextColor(getResources().getColor(R.color.txt_dim));
        about.setTextSize(14);
        about.setText("TraceCroquis 0.1 — vectorisation de croquis en plans à l'échelle.\n"
                + "Traitement entièrement sur l'appareil, sans envoi de vos documents.\n"
                + "Exports PDF (impression à 100 %), DXF (1 unité = 1 m) et SVG.\n"
                + (OcrReader.isAvailable() ? "Reconnaissance de texte : disponible."
                : "Reconnaissance de texte : non incluse dans cette version."));
        container.addView(about);

        LinearLayout reset = new LinearLayout(getContext());
        reset.setGravity(android.view.Gravity.CENTER);
        reset.setBackgroundResource(R.drawable.bg_cta_ghost);
        reset.setPadding(0, 32, 0, 32);
        TextView rt = new TextView(getContext());
        rt.setText("Réinitialiser les réglages");
        rt.setTextColor(getResources().getColor(R.color.danger));
        reset.addView(rt);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = 28;
        reset.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                prefs.reset();
                Session.get().options = prefs.options();
                container.removeViews(1, container.getChildCount() - 1);
                build();
                toast("Réglages par défaut restaurés");
            }
        });
        container.addView(reset, lp);
    }

    private void save(VectorOptions o) {
        prefs.save(o);
    }

    private void section(int titleRes) {
        TextView t = new TextView(getContext());
        t.setText(titleRes);
        t.setTextColor(getResources().getColor(R.color.accent));
        t.setTextSize(15);
        t.setAllCaps(true);
        t.setPadding(0, 28, 0, 6);
        container.addView(t);
    }

    private interface Set {
        void set(double value);
    }

    private interface Toggle {
        void set(boolean value);
    }

    private interface Text {
        void set(String value);
    }

    private void slider(String label, String hint, final double min, final double max,
                        double value, final int decimals, final Set target) {
        View row = LayoutInflater.from(getContext()).inflate(R.layout.part_slider, container, false);
        ((TextView) row.findViewById(R.id.label)).setText(label);
        ((TextView) row.findViewById(R.id.hint)).setText(hint);
        final TextView out = row.findViewById(R.id.value);
        SeekBar bar = row.findViewById(R.id.bar);
        out.setText(Fmt.num(value, decimals));
        bar.setProgress((int) Math.round((value - min) / (max - min) * 100));
        bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar b, int p, boolean fromUser) {
                double v = min + (max - min) * p / 100.0;
                out.setText(Fmt.num(v, decimals));
                if (fromUser) target.set(v);
            }
            @Override public void onStartTrackingTouch(SeekBar b) { }
            @Override public void onStopTrackingTouch(SeekBar b) { }
        });
        container.addView(row);
    }

    private void toggle(String label, String hint, boolean value, final Toggle target) {
        View row = LayoutInflater.from(getContext()).inflate(R.layout.part_switch, container, false);
        ((TextView) row.findViewById(R.id.label)).setText(label);
        ((TextView) row.findViewById(R.id.hint)).setText(hint);
        Switch sw = row.findViewById(R.id.sw);
        sw.setChecked(value);
        sw.setOnCheckedChangeListener(new android.widget.CompoundButton.OnCheckedChangeListener() {
            @Override public void onCheckedChanged(android.widget.CompoundButton b, boolean checked) {
                target.set(checked);
            }
        });
        container.addView(row);
    }

    private void textField(String label, String value, final Text target) {
        TextView l = new TextView(getContext());
        l.setText(label);
        l.setTextColor(getResources().getColor(R.color.txt_dim));
        l.setPadding(0, 14, 0, 2);
        final EditText e = new EditText(getContext());
        e.setInputType(InputType.TYPE_CLASS_TEXT);
        e.setText(value);
        e.setTextColor(getResources().getColor(R.color.txt));
        e.setBackgroundResource(R.drawable.bg_field);
        e.setPadding(24, 24, 24, 24);
        e.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override public void onFocusChange(View v, boolean focus) {
                if (!focus) target.set(e.getText().toString().trim());
            }
        });
        container.addView(l);
        container.addView(e);
    }
}
