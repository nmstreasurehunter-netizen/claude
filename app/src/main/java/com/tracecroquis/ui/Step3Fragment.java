package com.tracecroquis.ui;

import android.os.Bundle;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;

import com.tracecroquis.R;
import com.tracecroquis.Session;
import com.tracecroquis.core.geom.G;
import com.tracecroquis.core.model.Enums.OpeningType;
import com.tracecroquis.core.model.Enums.TextRole;
import com.tracecroquis.core.model.Enums.WallType;
import com.tracecroquis.core.model.Plan;
import com.tracecroquis.core.vector.RoomBuilder;
import com.tracecroquis.core.vector.TextAnalyzer;
import com.tracecroquis.ui.view.PlanView;
import com.tracecroquis.util.Fmt;

/** Etape 3 : verification et correction du plan, calibrage de l'echelle. */
public class Step3Fragment extends StepFragment implements PlanView.Listener {

    private PlanView planView;
    private View toolSelect, toolWalls, toolOpenings, toolDims, toolTexts, toolScale;
    private View scalePanel;
    private EditText refValue;
    private Spinner refUnit;
    private TextView refLabel, scaleInfo, subTitle, savedNote, hint;
    private ImageView refIcon;

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inf, @Nullable ViewGroup parent, @Nullable Bundle b) {
        View v = inf.inflate(R.layout.fragment_step3, parent, false);
        bindChrome(v, 3, getString(R.string.step3),
                "Touchez un mur, une ouverture, une cote ou un texte pour le modifier. "
                        + "Avec l'outil Échelle, placez les points A et B sur une longueur connue "
                        + "puis saisissez sa valeur réelle : tout le plan est recalibré.");

        subTitle = v.findViewById(R.id.subTitle);
        subTitle.setText(projectSubtitle());
        planView = v.findViewById(R.id.planView);
        planView.setPlan(session().plan);
        planView.setSketch(session().sketch);
        planView.setEditable(true);
        planView.setListener(this);
        planView.setSketchAlpha(0.2f);

        toolSelect = v.findViewById(R.id.toolSelect);
        toolWalls = v.findViewById(R.id.toolWalls);
        toolOpenings = v.findViewById(R.id.toolOpenings);
        toolDims = v.findViewById(R.id.toolDims);
        toolTexts = v.findViewById(R.id.toolTexts);
        toolScale = v.findViewById(R.id.toolScale);
        scalePanel = v.findViewById(R.id.scalePanel);
        refValue = v.findViewById(R.id.refValue);
        refUnit = v.findViewById(R.id.refUnit);
        refLabel = v.findViewById(R.id.refLabel);
        refIcon = v.findViewById(R.id.refIcon);
        scaleInfo = v.findViewById(R.id.scaleInfo);
        savedNote = v.findViewById(R.id.savedNote);
        hint = v.findViewById(R.id.hint);

        tool(toolSelect, PlanView.TOOL_SELECT);
        tool(toolWalls, PlanView.TOOL_WALL);
        tool(toolOpenings, PlanView.TOOL_OPENING);
        tool(toolDims, PlanView.TOOL_DIM);
        tool(toolTexts, PlanView.TOOL_TEXT);
        tool(toolScale, PlanView.TOOL_SCALE);

        ArrayAdapter<String> units = new ArrayAdapter<>(getContext(),
                android.R.layout.simple_spinner_dropdown_item, new String[]{"m", "cm"});
        refUnit.setAdapter(units);

        Switch sw = v.findViewById(R.id.swSketch);
        sw.setChecked(true);
        sw.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override public void onCheckedChanged(CompoundButton b2, boolean checked) {
                planView.setSketchAlpha(checked ? 0.2f : 0f);
            }
        });

        v.findViewById(R.id.btnUndo).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View x) {
                session().undo();
                planView.setPlan(session().plan);
                planView.invalidate();
                updateScaleInfo();
            }
        });
        v.findViewById(R.id.btnRedo).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View x) {
                session().redo();
                planView.setPlan(session().plan);
                planView.invalidate();
                updateScaleInfo();
            }
        });
        v.findViewById(R.id.btnFull).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View x) { planView.resetView(); }
        });
        v.findViewById(R.id.btnApplyScale).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View x) { applyScale(); }
        });
        v.findViewById(R.id.cta).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View x) {
                if (session().plan.pxPerMeter <= 0) {
                    toast("Renseignez d'abord l'échelle");
                    return;
                }
                main().goStep(4);
            }
        });

        initScalePoints();
        updateScaleInfo();
        return v;
    }

    private void tool(final View button, final int which) {
        button.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                planView.setTool(which);
                setSelected(toolSelect, which == PlanView.TOOL_SELECT);
                setSelected(toolWalls, which == PlanView.TOOL_WALL);
                setSelected(toolOpenings, which == PlanView.TOOL_OPENING);
                setSelected(toolDims, which == PlanView.TOOL_DIM);
                setSelected(toolTexts, which == PlanView.TOOL_TEXT);
                setSelected(toolScale, which == PlanView.TOOL_SCALE);
                hint.setText(hintFor(which));
            }
        });
    }

    private String hintFor(int tool) {
        switch (tool) {
            case PlanView.TOOL_WALL: return "Glissez pour tracer un mur";
            case PlanView.TOOL_OPENING: return "Touchez un mur pour poser une ouverture";
            case PlanView.TOOL_DIM: return "Glissez pour créer une cote";
            case PlanView.TOOL_TEXT: return "Touchez pour placer un texte";
            case PlanView.TOOL_SCALE: return "Placez les points A puis B";
            default: return getString(R.string.tap_to_edit);
        }
    }

    /** Reprend la cote de reference detectee comme segment A-B. */
    private void initScalePoints() {
        Plan p = session().plan;
        for (Plan.DimItem d : p.dims) {
            if (d.reference || !Double.isNaN(d.valueM)) {
                planView.setScalePoints(new double[]{d.x1, d.y1}, new double[]{d.x2, d.y2});
                if (!Double.isNaN(d.valueM)) refValue.setText(Fmt.num(d.valueM, 2));
                return;
            }
        }
        double[] b = p.wallBounds();
        planView.setScalePoints(new double[]{b[0], b[1]}, new double[]{b[2], b[1]});
        if (p.pxPerMeter > 0) {
            refValue.setText(Fmt.num((b[2] - b[0]) / p.pxPerMeter, 2));
        }
    }

    private void applyScale() {
        double[] a = planView.scalePointA();
        double[] b = planView.scalePointB();
        if (a == null || b == null) {
            toast("Placez les points A et B sur le plan");
            return;
        }
        double value = Fmt.parse(refValue.getText().toString(), 0);
        if (refUnit.getSelectedItemPosition() == 1) value /= 100.0;
        if (value <= 0.01) {
            toast("Saisissez la distance réelle");
            return;
        }
        double px = G.dist(a[0], a[1], b[0], b[1]);
        if (px < 2) {
            toast("Les points A et B sont trop proches");
            return;
        }
        session().snapshot();
        session().plan.rescale(px / value);
        planView.invalidate();
        updateScaleInfo();
        toast("Échelle appliquée");
    }

    private void updateScaleInfo() {
        Plan p = session().plan;
        boolean ok = p.pxPerMeter > 0;
        refLabel.setText(ok && p.scaleConfirmed ? R.string.reference_ok : R.string.reference_todo);
        int color = getResources().getColor(ok && p.scaleConfirmed ? R.color.ok : R.color.warn);
        refLabel.setTextColor(color);
        refIcon.setColorFilter(color);
        double[] bb = p.wallBounds();
        String info = ok
                ? String.format("Emprise : %s × %s · %d pièces · %s",
                Fmt.m((bb[2] - bb[0]) / p.pxPerMeter), Fmt.m((bb[3] - bb[1]) / p.pxPerMeter),
                p.rooms.size(), Fmt.m2(p.totalAreaM2()))
                : "Échelle inconnue : placez A et B sur une longueur connue.";
        scaleInfo.setText(info);
        savedNote.setText(session().dirty ? R.string.saved : R.string.saved);
    }

    // ------------------------------------------------------- retours du plan

    @Override
    public void onPick(int type, int index) {
        switch (type) {
            case PlanView.SEL_WALL: editWall(index); break;
            case PlanView.SEL_OPENING: editOpening(index); break;
            case PlanView.SEL_TEXT: editText(index); break;
            case PlanView.SEL_DIM: editDim(index); break;
            case PlanView.SEL_ROOM: editRoom(index); break;
            default: break;
        }
    }

    @Override
    public void onPlanChanged() {
        session().dirty = true;
        rebuildRooms();
        planView.invalidate();
        updateScaleInfo();
    }

    @Override
    public void onScalePoints(double[] a, double[] b) {
        Plan p = session().plan;
        if (p.pxPerMeter > 0) {
            refValue.setText(Fmt.num(G.dist(a[0], a[1], b[0], b[1]) / p.pxPerMeter, 2));
        }
        toast("Saisissez la distance réelle entre A et B");
    }

    /** Recalcule les pieces apres modification des murs. */
    private void rebuildRooms() {
        Plan p = session().plan;
        if (p.walls.isEmpty()) return;
        double minDim = Math.max(50, Math.min(p.imageW, p.imageH));
        RoomBuilder.build(p, minDim);
        RoomBuilder.assignTexts(p);
        TextAnalyzer.applyToRooms(p);
    }

    // -------------------------------------------------------------- dialogues

    private void editWall(final int index) {
        final Plan p = session().plan;
        if (index < 0 || index >= p.walls.size()) return;
        final Plan.Wall w = p.walls.get(index);
        View box = LayoutInflater.from(getContext()).inflate(R.layout.dialog_wall, null);
        ((TextView) box.findViewById(R.id.title)).setText("Mur");
        ((TextView) box.findViewById(R.id.info)).setText(String.format("Longueur %s · %s",
                Fmt.m(p.wallLengthM(w)), w.exterior ? "façade" : "intérieur"));

        final Spinner sp = box.findViewById(R.id.spType);
        final WallType[] types = WallType.values();
        String[] labels = new String[types.length];
        for (int i = 0; i < types.length; i++) {
            labels[i] = types[i].label + " (" + Math.round(types[i].defaultThickness * 100) + " cm)";
        }
        sp.setAdapter(new ArrayAdapter<>(getContext(),
                android.R.layout.simple_spinner_dropdown_item, labels));
        sp.setSelection(indexOf(types, w.type));

        final EditText th = box.findViewById(R.id.edThickness);
        th.setText(Fmt.num(w.thicknessM * 100, 0));

        LinearLayout quick = box.findViewById(R.id.quick);
        final double[] presets = {0.05, 0.07, 0.10, 0.15, 0.20, 0.25};
        for (final double preset : presets) {
            TextView t = new TextView(getContext());
            t.setText(Math.round(preset * 100) + "");
            t.setTextColor(getResources().getColor(R.color.accent));
            t.setBackgroundResource(R.drawable.bg_chip);
            t.setPadding(24, 14, 24, 14);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0,
                    LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            lp.rightMargin = 8;
            t.setGravity(android.view.Gravity.CENTER);
            t.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) { th.setText(Fmt.num(preset * 100, 0)); }
            });
            quick.addView(t, lp);
        }

        final AlertDialog dlg = new AlertDialog.Builder(getContext()).setView(box).create();
        box.findViewById(R.id.btnOk).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                session().snapshot();
                w.type = types[sp.getSelectedItemPosition()];
                double cm = Fmt.parse(th.getText().toString(), w.thicknessM * 100);
                w.thicknessM = Math.max(0.02, cm / 100.0);
                if (p.pxPerMeter > 0) w.thicknessPx = w.thicknessM * p.pxPerMeter;
                w.userEdited = true;
                onPlanChanged();
                dlg.dismiss();
            }
        });
        box.findViewById(R.id.btnDelete).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                session().snapshot();
                for (int i = p.openings.size() - 1; i >= 0; i--) {
                    Plan.Opening op = p.openings.get(i);
                    if (op.wall == index) p.openings.remove(i);
                    else if (op.wall > index) op.wall--;
                }
                p.walls.remove(index);
                planView.select(PlanView.SEL_NONE, -1);
                onPlanChanged();
                dlg.dismiss();
            }
        });
        dlg.show();
    }

    private void editOpening(final int index) {
        final Plan p = session().plan;
        if (index < 0 || index >= p.openings.size()) return;
        final Plan.Opening op = p.openings.get(index);
        View box = LayoutInflater.from(getContext()).inflate(R.layout.dialog_opening, null);
        ((TextView) box.findViewById(R.id.title)).setText("Ouverture");
        ((TextView) box.findViewById(R.id.info)).setText(String.format("Largeur %s",
                Fmt.m(op.widthM)));

        final Spinner sp = box.findViewById(R.id.spType);
        final OpeningType[] types = OpeningType.values();
        String[] labels = new String[types.length];
        for (int i = 0; i < types.length; i++) labels[i] = types[i].label;
        sp.setAdapter(new ArrayAdapter<>(getContext(),
                android.R.layout.simple_spinner_dropdown_item, labels));
        sp.setSelection(indexOf(types, op.type));

        final EditText wd = box.findViewById(R.id.edWidth);
        wd.setText(Fmt.num(op.widthM * 100, 0));
        final SeekBar pos = box.findViewById(R.id.barPos);
        pos.setProgress((int) (op.t * 100));

        box.findViewById(R.id.btnFlipHinge).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                session().snapshot();
                op.hingeSide = -op.hingeSide;
                planView.invalidate();
            }
        });
        box.findViewById(R.id.btnFlipSwing).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                session().snapshot();
                op.swingSide = -op.swingSide;
                planView.invalidate();
            }
        });

        final AlertDialog dlg = new AlertDialog.Builder(getContext()).setView(box).create();
        box.findViewById(R.id.btnOk).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                session().snapshot();
                op.type = types[sp.getSelectedItemPosition()];
                op.widthM = Math.max(0.2, Fmt.parse(wd.getText().toString(), op.widthM * 100) / 100.0);
                op.t = G.clamp(pos.getProgress() / 100.0, 0.02, 0.98);
                onPlanChanged();
                dlg.dismiss();
            }
        });
        box.findViewById(R.id.btnDelete).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                session().snapshot();
                p.openings.remove(index);
                planView.select(PlanView.SEL_NONE, -1);
                onPlanChanged();
                dlg.dismiss();
            }
        });
        dlg.show();
    }

    private void editText(final int index) {
        final Plan p = session().plan;
        if (index < 0 || index >= p.texts.size()) return;
        final Plan.TextItem t = p.texts.get(index);
        final EditText ed = new EditText(getContext());
        ed.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        ed.setText(t.text);
        final String[] roles = {"Nom de pièce", "Surface", "Cote", "Texte libre"};
        final Spinner sp = new Spinner(getContext());
        sp.setAdapter(new ArrayAdapter<>(getContext(),
                android.R.layout.simple_spinner_dropdown_item, roles));
        sp.setSelection(t.role == TextRole.NOM_PIECE ? 0 : t.role == TextRole.SURFACE ? 1
                : t.role == TextRole.COTE ? 2 : 3);
        LinearLayout box = new LinearLayout(getContext());
        box.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (18 * getResources().getDisplayMetrics().density);
        box.setPadding(pad, pad / 2, pad, 0);
        box.addView(ed);
        box.addView(sp);
        new AlertDialog.Builder(getContext())
                .setTitle("Texte")
                .setView(box)
                .setPositiveButton(R.string.apply, new android.content.DialogInterface.OnClickListener() {
                    @Override public void onClick(android.content.DialogInterface d, int w) {
                        session().snapshot();
                        t.text = ed.getText().toString();
                        int r = sp.getSelectedItemPosition();
                        t.role = r == 0 ? TextRole.NOM_PIECE : r == 1 ? TextRole.SURFACE
                                : r == 2 ? TextRole.COTE : TextRole.LIBRE;
                        onPlanChanged();
                    }
                })
                .setNeutralButton(R.string.delete, new android.content.DialogInterface.OnClickListener() {
                    @Override public void onClick(android.content.DialogInterface d, int w) {
                        session().snapshot();
                        p.texts.remove(index);
                        planView.select(PlanView.SEL_NONE, -1);
                        onPlanChanged();
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void editDim(final int index) {
        final Plan p = session().plan;
        if (index < 0 || index >= p.dims.size()) return;
        final Plan.DimItem d = p.dims.get(index);
        final EditText ed = new EditText(getContext());
        ed.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        double current = !Double.isNaN(d.valueM) ? d.valueM
                : (p.pxPerMeter > 0 ? G.dist(d.x1, d.y1, d.x2, d.y2) / p.pxPerMeter : 0);
        ed.setText(Fmt.num(current, 2));
        new AlertDialog.Builder(getContext())
                .setTitle("Cote (m)")
                .setView(ed)
                .setPositiveButton(R.string.apply, new android.content.DialogInterface.OnClickListener() {
                    @Override public void onClick(android.content.DialogInterface x, int w) {
                        session().snapshot();
                        double v = Fmt.parse(ed.getText().toString(), Double.NaN);
                        d.valueM = v;
                        if (v > 0.05) {
                            planView.setScalePoints(new double[]{d.x1, d.y1}, new double[]{d.x2, d.y2});
                            refValue.setText(Fmt.num(v, 2));
                            p.rescale(G.dist(d.x1, d.y1, d.x2, d.y2) / v);
                            d.reference = true;
                        }
                        onPlanChanged();
                    }
                })
                .setNeutralButton(R.string.delete, new android.content.DialogInterface.OnClickListener() {
                    @Override public void onClick(android.content.DialogInterface x, int w) {
                        session().snapshot();
                        p.dims.remove(index);
                        onPlanChanged();
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void editRoom(final int index) {
        final Plan p = session().plan;
        if (index < 0 || index >= p.rooms.size()) return;
        final Plan.Room r = p.rooms.get(index);
        final EditText name = new EditText(getContext());
        name.setText(r.name);
        final EditText area = new EditText(getContext());
        area.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        area.setText(Double.isNaN(r.declaredAreaM2) ? "" : Fmt.num(r.declaredAreaM2, 1));
        LinearLayout box = new LinearLayout(getContext());
        box.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (18 * getResources().getDisplayMetrics().density);
        box.setPadding(pad, pad / 2, pad, 0);
        TextView l1 = new TextView(getContext());
        l1.setText("Nom de la pièce");
        TextView l2 = new TextView(getContext());
        l2.setText("Surface annoncée (m², vide = calculée)");
        box.addView(l1);
        box.addView(name);
        box.addView(l2);
        box.addView(area);
        new AlertDialog.Builder(getContext())
                .setTitle(String.format("Pièce · %s", Fmt.m2(p.areaOf(r))))
                .setView(box)
                .setPositiveButton(R.string.apply, new android.content.DialogInterface.OnClickListener() {
                    @Override public void onClick(android.content.DialogInterface d, int w) {
                        session().snapshot();
                        r.name = name.getText().toString().trim();
                        String a = area.getText().toString().trim();
                        r.declaredAreaM2 = a.isEmpty() ? Double.NaN : Fmt.parse(a, Double.NaN);
                        planView.invalidate();
                        updateScaleInfo();
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private static <T> int indexOf(T[] values, T value) {
        for (int i = 0; i < values.length; i++) if (values[i] == value) return i;
        return 0;
    }
}
