package com.tracecroquis.ui;

import android.graphics.Bitmap;
import android.os.Bundle;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;

import com.tracecroquis.R;
import com.tracecroquis.Session;
import com.tracecroquis.core.model.Plan;
import com.tracecroquis.export.Layout;
import com.tracecroquis.export.PdfExporter;
import com.tracecroquis.export.SheetRenderer;
import com.tracecroquis.export.TitleBlock;
import com.tracecroquis.export.VectorExporter;
import com.tracecroquis.store.ProjectStore;
import com.tracecroquis.ui.view.PlanView;
import com.tracecroquis.util.Async;

import java.io.File;

/** Etape 4 : mise en page automatique, cartouche et export PDF, DXF ou SVG. */
public class Step4Fragment extends StepFragment {

    private PlanView planView;
    private TextView fileName, ctaLabel, blockInfo, subTitle, scaleBadge;
    private View fmtPdf, fmtDxf, fmtSvg;
    private Spinner spPaper, spOrient, spScale, spUnit;
    private Switch swDims, swBlock;
    private Layout layout = new Layout();
    private final TitleBlock block = new TitleBlock();
    private boolean binding = true;

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inf, @Nullable ViewGroup parent, @Nullable Bundle b) {
        View v = inf.inflate(R.layout.fragment_step4, parent, false);
        bindChrome(v, 4, getString(R.string.step4),
                "Le format de papier et l'échelle sont choisis automatiquement d'après "
                        + "les cotes du plan. Le PDF s'imprime à 100 % pour conserver l'échelle. "
                        + "Le DXF s'ouvre dans un logiciel de CAO, une unité valant un mètre.");

        subTitle = v.findViewById(R.id.subTitle);
        subTitle.setText(projectSubtitle());
        planView = v.findViewById(R.id.planView);
        fileName = v.findViewById(R.id.fileName);
        ctaLabel = v.findViewById(R.id.ctaLabel);
        blockInfo = v.findViewById(R.id.blockInfo);
        scaleBadge = v.findViewById(R.id.scaleBadge);
        fmtPdf = v.findViewById(R.id.fmtPdf);
        fmtDxf = v.findViewById(R.id.fmtDxf);
        fmtSvg = v.findViewById(R.id.fmtSvg);
        spPaper = v.findViewById(R.id.spPaper);
        spOrient = v.findViewById(R.id.spOrient);
        spScale = v.findViewById(R.id.spScale);
        spUnit = v.findViewById(R.id.spUnit);
        swDims = v.findViewById(R.id.swDims);
        swBlock = v.findViewById(R.id.swBlock);

        Session s = session();
        block.author = main().prefs().owner();
        if (s.plan.owner == null || s.plan.owner.isEmpty()) s.plan.owner = main().prefs().owner();
        if (s.plan.address == null || s.plan.address.isEmpty()) s.plan.address = main().prefs().address();

        spPaper.setAdapter(adapter(new String[]{getString(R.string.auto), "A4", "A3", "A2", "A1", "A0"}));
        spOrient.setAdapter(adapter(new String[]{getString(R.string.auto),
                getString(R.string.landscape), getString(R.string.portrait)}));
        spScale.setAdapter(adapter(new String[]{getString(R.string.auto), "1:20", "1:25", "1:50",
                "1:100", "1:200", "1:500"}));
        spUnit.setAdapter(adapter(new String[]{"m", "cm"}));

        swDims.setChecked(main().prefs().showDims());
        swBlock.setChecked(main().prefs().titleBlock());

        AdapterView.OnItemSelectedListener change = new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> a, View view, int pos, long id) {
                if (!binding) refresh();
            }
            @Override public void onNothingSelected(AdapterView<?> a) { }
        };
        spPaper.setOnItemSelectedListener(change);
        spOrient.setOnItemSelectedListener(change);
        spScale.setOnItemSelectedListener(change);
        spUnit.setOnItemSelectedListener(change);

        CompoundButton.OnCheckedChangeListener toggle = new CompoundButton.OnCheckedChangeListener() {
            @Override public void onCheckedChanged(CompoundButton c, boolean checked) {
                main().prefs().setShowDims(swDims.isChecked());
                main().prefs().setTitleBlock(swBlock.isChecked());
                refresh();
            }
        };
        swDims.setOnCheckedChangeListener(toggle);
        swBlock.setOnCheckedChangeListener(toggle);

        format(fmtPdf, "PDF");
        format(fmtDxf, "DXF");
        format(fmtSvg, "SVG");

        v.findViewById(R.id.btnEditBlock).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View x) { editBlock(); }
        });
        v.findViewById(R.id.btnFull).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View x) { planView.resetView(); }
        });
        v.findViewById(R.id.cta).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View x) { export(false); }
        });
        v.findViewById(R.id.btnShare).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View x) { export(true); }
        });

        binding = false;
        refresh();
        saveProject();
        return v;
    }

    private ArrayAdapter<String> adapter(String[] items) {
        return new ArrayAdapter<>(getContext(), android.R.layout.simple_spinner_dropdown_item, items);
    }

    private void format(final View tile, final String fmt) {
        tile.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                session().format = fmt;
                refresh();
            }
        });
    }

    private void refresh() {
        Session s = session();
        String paper = spPaper.getSelectedItemPosition() == 0 ? ""
                : (String) spPaper.getSelectedItem();
        int scale = 0;
        if (spScale.getSelectedItemPosition() > 0) {
            String sel = (String) spScale.getSelectedItem();
            scale = Integer.parseInt(sel.substring(2));
        }
        boolean autoOrient = spOrient.getSelectedItemPosition() == 0;
        boolean landscape = spOrient.getSelectedItemPosition() == 1;

        layout = Layout.choose(s.plan, swBlock.isChecked(), paper, scale, landscape, autoOrient);
        s.paper = layout.paper;
        s.scaleDen = layout.scaleDen;
        s.landscape = layout.landscape;
        s.unit = (String) spUnit.getSelectedItem();

        setSelected(fmtPdf, "PDF".equals(s.format));
        setSelected(fmtDxf, "DXF".equals(s.format));
        setSelected(fmtSvg, "SVG".equals(s.format));
        ctaLabel.setText("PDF".equals(s.format) ? getString(R.string.save_pdf)
                : "Enregistrer le " + s.format);
        fileName.setText(fileBaseName() + "." + s.format.toLowerCase(java.util.Locale.FRENCH));
        blockInfo.setText(String.format("%s · %s — %s · %s",
                s.plan.projectName, s.plan.levelName, s.plan.sheetNumber, s.plan.revision));
        scaleBadge.setText(s.plan.scaleConfirmed ? R.string.scale_ok : R.string.reference_todo);

        final Plan plan = s.plan;
        final Layout l = layout;
        final boolean dims = swDims.isChecked();
        final String unit = s.unit;
        Async.run(new Async.Job<Bitmap>() {
            @Override public Bitmap run() {
                return SheetRenderer.preview(plan, l, dims, unit, block, 1100);
            }
        }, new Async.Done<Bitmap>() {
            @Override public void onDone(Bitmap bmp, Exception e) {
                if (bmp != null && planView != null) planView.setSheet(bmp);
            }
        });
    }

    private String fileBaseName() {
        Session s = session();
        String name = sanitize(s.plan.projectName) + "_" + sanitize(s.plan.levelName)
                + "_" + sanitize(s.plan.sheetNumber);
        return name.replaceAll("_+", "_");
    }

    private static String sanitize(String s) {
        if (s == null || s.trim().isEmpty()) return "Plan";
        String t = com.tracecroquis.core.vector.TextAnalyzer.stripAccents(s.trim());
        return t.replaceAll("[^A-Za-z0-9\\-]+", "_");
    }

    private void editBlock() {
        final Plan p = session().plan;
        LinearLayout box = new LinearLayout(getContext());
        box.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (18 * getResources().getDisplayMetrics().density);
        box.setPadding(pad, pad / 2, pad, 0);
        final EditText sheet = field(box, "N° de plan", p.sheetNumber);
        final EditText rev = field(box, "Indice", p.revision);
        final EditText owner = field(box, "Maître d'ouvrage", p.owner);
        final EditText addr = field(box, "Adresse du terrain", p.address);
        final EditText ref = field(box, "Référence de la pièce", block.reference);
        android.widget.ScrollView scroll = new android.widget.ScrollView(getContext());
        scroll.addView(box);
        new AlertDialog.Builder(getContext())
                .setTitle(R.string.title_block)
                .setView(scroll)
                .setPositiveButton(R.string.apply, new android.content.DialogInterface.OnClickListener() {
                    @Override public void onClick(android.content.DialogInterface d, int w) {
                        p.sheetNumber = sheet.getText().toString().trim();
                        p.revision = rev.getText().toString().trim();
                        p.owner = owner.getText().toString().trim();
                        p.address = addr.getText().toString().trim();
                        block.reference = ref.getText().toString().trim();
                        main().prefs().setOwner(p.owner);
                        main().prefs().setAddress(p.address);
                        refresh();
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private EditText field(LinearLayout box, String label, String value) {
        TextView l = new TextView(getContext());
        l.setText(label);
        EditText e = new EditText(getContext());
        e.setInputType(InputType.TYPE_CLASS_TEXT);
        e.setText(value == null ? "" : value);
        box.addView(l);
        box.addView(e);
        return e;
    }

    private void export(final boolean share) {
        final Session s = session();
        if (!s.hasPlan()) {
            toast("Aucun plan à exporter");
            return;
        }
        final File dir = new File(getContext().getCacheDir(), "export");
        dir.mkdirs();
        final String fmt = s.format;
        final Layout l = layout;
        final boolean dims = swDims.isChecked();
        final String unit = s.unit;
        final Plan plan = s.plan;
        Async.run(new Async.Job<File>() {
            @Override public File run() throws Exception {
                File out = new File(dir, fileBaseName() + "." + fmt.toLowerCase(java.util.Locale.FRENCH));
                if ("PDF".equals(fmt)) return PdfExporter.export(plan, l, dims, unit, block, out);
                if ("SVG".equals(fmt)) return VectorExporter.svg(plan, l, dims, out);
                return VectorExporter.dxf(plan, out);
            }
        }, new Async.Done<File>() {
            @Override public void onDone(File f, Exception e) {
                if (e != null || f == null) {
                    toast("Échec de l'export : " + (e == null ? "" : e.getMessage()));
                    return;
                }
                String mime = "PDF".equals(fmt) ? "application/pdf"
                        : "SVG".equals(fmt) ? "image/svg+xml" : "application/dxf";
                if (share) main().shareFile(f, mime);
                else main().saveDocument(f, mime);
                saveProject();
            }
        });
    }

    private void saveProject() {
        final Session s = session();
        if (s.projectId == null) s.projectId = ProjectStore.newId();
        final String id = s.projectId;
        final Plan plan = s.plan;
        final Bitmap sketch = s.sketch;
        final ProjectStore store = new ProjectStore(getContext().getApplicationContext());
        Async.run(new Async.Job<Boolean>() {
            @Override public Boolean run() throws Exception {
                store.save(id, plan, sketch);
                return Boolean.TRUE;
            }
        }, new Async.Done<Boolean>() {
            @Override public void onDone(Boolean ok, Exception e) {
                if (e == null) s.dirty = false;
            }
        });
    }
}
