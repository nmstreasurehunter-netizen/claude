package com.tracecroquis.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;

import androidx.annotation.Nullable;

import com.tracecroquis.R;
import com.tracecroquis.Session;
import com.tracecroquis.core.vector.VectorOptions;
import com.tracecroquis.ui.view.PlanView;
import com.tracecroquis.util.Fmt;

/** Etape 2 : vectorisation, controle visuel et choix des elements a reconnaitre. */
public class Step2Fragment extends StepFragment {

    private PlanView planView;
    private TextView bgLabel, badgeText, scaleNote, subTitle, footNote;
    private ImageView badgeIcon;
    private View tabSketch, tabVector, tabOverlay;
    private int mode = 2;      // 0 croquis, 1 vectorise, 2 superpose

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inf, @Nullable ViewGroup parent, @Nullable Bundle s) {
        View v = inf.inflate(R.layout.fragment_step2, parent, false);
        bindChrome(v, 2, getString(R.string.step2),
                "Le croquis est converti en murs, ouvertures, pièces et cotes. "
                        + "Comparez les trois vues, ajustez les éléments recherchés puis "
                        + "relancez si besoin. Les réglages fins sont dans l'onglet Réglages.");

        subTitle = v.findViewById(R.id.subTitle);
        subTitle.setText(projectSubtitle());
        planView = v.findViewById(R.id.planView);
        bgLabel = v.findViewById(R.id.bgLabel);
        badgeText = v.findViewById(R.id.badgeText);
        badgeIcon = v.findViewById(R.id.badgeIcon);
        scaleNote = v.findViewById(R.id.scaleNote);
        footNote = v.findViewById(R.id.footNote);
        tabSketch = v.findViewById(R.id.tabSketch);
        tabVector = v.findViewById(R.id.tabVector);
        tabOverlay = v.findViewById(R.id.tabOverlay);

        planView.setPlan(session().plan);
        planView.setSketch(session().sketch);
        planView.setEditable(false);

        tabSketch.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View x) { setMode(0); }
        });
        tabVector.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View x) { setMode(1); }
        });
        tabOverlay.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View x) { setMode(2); }
        });

        final SeekBar bg = v.findViewById(R.id.bgBar);
        bg.setProgress(35);
        bg.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar bar, int p, boolean fromUser) {
                if (mode != 0) planView.setSketchAlpha(p / 100f);
                bgLabel.setText(getString(R.string.background_pct, p));
            }
            @Override public void onStartTrackingTouch(SeekBar bar) { }
            @Override public void onStopTrackingTouch(SeekBar bar) { }
        });
        bgLabel.setText(getString(R.string.background_pct, 35));

        VectorOptions o = session().options;
        bindCheck(v, R.id.elWalls, R.string.el_walls, R.drawable.ic_wall, o.detectWalls, 0);
        bindCheck(v, R.id.elOpenings, R.string.el_openings, R.drawable.ic_door, o.detectOpenings, 1);
        bindCheck(v, R.id.elRooms, R.string.el_rooms, R.drawable.ic_text, o.detectRooms, 2);
        bindCheck(v, R.id.elDims, R.string.el_dims, R.drawable.ic_dim_v, o.detectDims, 3);
        bindCheck(v, R.id.elFurniture, R.string.el_furniture, R.drawable.ic_sofa, o.detectFurniture, 4);
        bindCheck(v, R.id.elTextures, R.string.el_textures, R.drawable.ic_grid, o.detectTextures, 5);

        Switch angles = v.findViewById(R.id.swAngles);
        angles.setChecked(o.keepAngles);
        angles.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override public void onCheckedChanged(CompoundButton b, boolean checked) {
                session().options.keepAngles = checked;
                main().prefs().save(session().options);
            }
        });

        v.findViewById(R.id.btnRerun).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View x) { rerun(); }
        });
        v.findViewById(R.id.btnFull).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View x) { planView.resetView(); }
        });
        v.findViewById(R.id.cta).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View x) {
                if (!session().hasPlan()) {
                    toast("Lancez d'abord la vectorisation");
                    return;
                }
                main().goStep(3);
            }
        });

        setMode(2);
        updateBadge();
        return v;
    }

    private void bindCheck(View root, int id, int labelRes, int iconRes, boolean value, final int which) {
        View row = root.findViewById(id);
        ((TextView) row.findViewById(R.id.label)).setText(labelRes);
        ((ImageView) row.findViewById(R.id.icon)).setImageResource(iconRes);
        final CheckBox box = row.findViewById(R.id.check);
        box.setChecked(value);
        row.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                box.setChecked(!box.isChecked());
                VectorOptions o = session().options;
                switch (which) {
                    case 0: o.detectWalls = box.isChecked(); break;
                    case 1: o.detectOpenings = box.isChecked(); break;
                    case 2: o.detectRooms = box.isChecked(); break;
                    case 3: o.detectDims = box.isChecked(); break;
                    case 4: o.detectFurniture = box.isChecked(); break;
                    default: o.detectTextures = box.isChecked(); break;
                }
                main().prefs().save(o);
            }
        });
    }

    private void setMode(int m) {
        mode = m;
        setSelected(tabSketch, m == 0);
        setSelected(tabVector, m == 1);
        setSelected(tabOverlay, m == 2);
        if (m == 0) {
            planView.setSketchAlpha(1f);
            planView.style().showWalls = false;
            planView.style().showOpenings = false;
            planView.style().showRooms = false;
            planView.style().showDims = false;
            planView.style().showSymbols = false;
            planView.style().showTexts = false;
        } else {
            planView.style().showWalls = true;
            planView.style().showOpenings = true;
            planView.style().showRooms = true;
            planView.style().showDims = true;
            planView.style().showSymbols = true;
            planView.style().showTexts = true;
            planView.setSketchAlpha(m == 1 ? 0f : 0.35f);
        }
        planView.invalidate();
    }

    private void updateBadge() {
        Session s = session();
        if (s.result == null || !s.hasPlan()) {
            badgeText.setText(R.string.preview_running);
            badgeText.setTextColor(getResources().getColor(R.color.warn));
            badgeIcon.setColorFilter(getResources().getColor(R.color.warn));
            scaleNote.setText(R.string.scale_next_step);
            footNote.setText(R.string.on_device);
            return;
        }
        badgeText.setText(R.string.preview_ready);
        badgeText.setTextColor(getResources().getColor(R.color.ok));
        badgeIcon.setColorFilter(getResources().getColor(R.color.ok));
        footNote.setText(s.result.summary());
        if (s.plan.pxPerMeter > 0) {
            scaleNote.setText(s.plan.scaleConfirmed
                    ? "Échelle lue sur le croquis : 1 m = " + Fmt.num(s.plan.pxPerMeter, 0) + " px"
                    : getString(R.string.scale_next_step));
        } else {
            scaleNote.setText(R.string.scale_next_step);
        }
    }

    private void rerun() {
        Vectorize.run(main(), false, new Vectorize.Done() {
            @Override public void onFinished(boolean ok, String message) {
                toast(message);
                if (ok) {
                    planView.setPlan(session().plan);
                    planView.setSketch(session().sketch);
                    planView.invalidate();
                }
                updateBadge();
            }
        });
    }
}
