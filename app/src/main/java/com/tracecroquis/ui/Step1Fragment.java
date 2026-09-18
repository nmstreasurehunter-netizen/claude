package com.tracecroquis.ui;

import android.os.Bundle;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;

import com.tracecroquis.R;
import com.tracecroquis.Session;
import com.tracecroquis.ui.view.CropView;

/** Etape 1 : import du croquis, cadrage, redressement, contraste. */
public class Step1Fragment extends StepFragment {

    private CropView crop;
    private TextView fileName;
    private TextView projectName;
    private SeekBar contrastBar;
    private View toolCrop, toolRotate, toolDeskew, toolContrast;

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inf, @Nullable ViewGroup parent, @Nullable Bundle s) {
        View v = inf.inflate(R.layout.fragment_step1, parent, false);
        bindChrome(v, 1, getString(R.string.step1),
                "Importez une photo du croquis, ajustez les quatre coins autour de la feuille "
                        + "puis lancez la vectorisation. Un trait net et une cote écrite "
                        + "(par exemple 10,20 m) améliorent nettement le résultat.");

        crop = v.findViewById(R.id.cropView);
        fileName = v.findViewById(R.id.fileName);
        projectName = v.findViewById(R.id.projectName);
        contrastBar = v.findViewById(R.id.contrastBar);
        toolCrop = v.findViewById(R.id.toolCrop);
        toolRotate = v.findViewById(R.id.toolRotate);
        toolDeskew = v.findViewById(R.id.toolDeskew);
        toolContrast = v.findViewById(R.id.toolContrast);

        v.findViewById(R.id.btnCamera).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View x) { main().takePhoto(); }
        });
        v.findViewById(R.id.btnImport).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View x) { main().pickImage(); }
        });
        v.findViewById(R.id.btnRename).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View x) { rename(); }
        });
        v.findViewById(R.id.btnFull).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View x) {
                crop.setCropEnabled(!crop.isCropEnabled());
                updateTools();
            }
        });
        toolCrop.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View x) {
                crop.setCropEnabled(true);
                contrastBar.setVisibility(View.GONE);
                updateTools();
            }
        });
        toolRotate.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View x) { rotate(); }
        });
        toolDeskew.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View x) { deskew(); }
        });
        toolContrast.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View x) {
                contrastBar.setVisibility(contrastBar.getVisibility() == View.VISIBLE
                        ? View.GONE : View.VISIBLE);
                updateTools();
            }
        });
        contrastBar.setProgress((int) ((session().contrast - 0.5f) / 1.5f * 100));
        contrastBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar bar, int p, boolean user) {
                session().contrast = 0.5f + p / 100f * 1.5f;
            }
            @Override public void onStartTrackingTouch(SeekBar bar) { }
            @Override public void onStopTrackingTouch(SeekBar bar) { }
        });

        v.findViewById(R.id.cta).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View x) { vectorize(); }
        });

        refresh();
        return v;
    }

    /** Appele par l'activite quand une image vient d'etre chargee. */
    public void onSketchLoaded() {
        refresh();
    }

    private void refresh() {
        if (crop == null) return;
        Session s = session();
        if (s.source != null && !s.source.isRecycled()) {
            crop.setBitmap(s.source);
            if (s.corners != null) crop.setCorners(s.corners);
            fileName.setText(s.sourceName.isEmpty() ? "croquis.jpg" : s.sourceName);
        } else {
            fileName.setText(R.string.no_image);
        }
        projectName.setText(projectSubtitle());
        updateTools();
    }

    private void updateTools() {
        setSelected(toolCrop, crop.isCropEnabled());
        setSelected(toolContrast, contrastBar.getVisibility() == View.VISIBLE);
        setSelected(toolRotate, false);
        setSelected(toolDeskew, session().corners != null);
    }

    private void rotate() {
        Session s = session();
        if (s.source == null) return;
        s.source = com.tracecroquis.util.Img.rotate(s.source, 90);
        s.corners = null;
        s.sketch = null;          // sera reconstruit lors de la vectorisation
        crop.setBitmap(s.source);
        updateTools();
    }

    private void deskew() {
        Session s = session();
        if (s.source == null) return;
        if (crop.isCropped()) {
            s.corners = crop.getCorners();
            toast("Redressement appliqué à la vectorisation");
        } else {
            s.corners = null;
            crop.resetCorners();
            toast("Cadrage réinitialisé");
        }
        updateTools();
    }

    private void rename() {
        final EditText name = new EditText(getContext());
        name.setInputType(InputType.TYPE_CLASS_TEXT);
        name.setText(session().plan.projectName);
        final EditText level = new EditText(getContext());
        level.setInputType(InputType.TYPE_CLASS_TEXT);
        level.setText(session().plan.levelName);
        LinearLayout box = new LinearLayout(getContext());
        box.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (18 * getResources().getDisplayMetrics().density);
        box.setPadding(pad, pad / 2, pad, 0);
        TextView l1 = new TextView(getContext());
        l1.setText("Projet");
        TextView l2 = new TextView(getContext());
        l2.setText("Niveau");
        box.addView(l1);
        box.addView(name);
        box.addView(l2);
        box.addView(level);
        new AlertDialog.Builder(getContext())
                .setTitle(R.string.project_name)
                .setView(box)
                .setPositiveButton(R.string.ok, new android.content.DialogInterface.OnClickListener() {
                    @Override public void onClick(android.content.DialogInterface d, int w) {
                        session().plan.projectName = name.getText().toString().trim();
                        session().plan.levelName = level.getText().toString().trim();
                        projectName.setText(projectSubtitle());
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void vectorize() {
        Session s = session();
        if (s.source == null || s.source.isRecycled()) {
            toast("Importez d'abord un croquis");
            return;
        }
        s.corners = crop.isCropped() ? crop.getCorners() : null;
        Vectorize.run(main(), true, new Vectorize.Done() {
            @Override public void onFinished(boolean ok, String message) {
                toast(message);
                if (ok) main().goStep(2);
            }
        });
    }
}
