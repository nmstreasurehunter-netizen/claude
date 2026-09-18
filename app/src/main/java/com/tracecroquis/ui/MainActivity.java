package com.tracecroquis.ui;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;
import androidx.core.graphics.Insets;
import androidx.core.view.OnApplyWindowInsetsListener;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;

import com.tracecroquis.R;
import com.tracecroquis.Session;
import com.tracecroquis.util.Async;
import com.tracecroquis.util.Img;
import com.tracecroquis.util.Prefs;

import java.io.File;
import java.io.FileInputStream;
import java.io.OutputStream;

/** Ecran unique : navigation entre les projets, le parcours en 4 etapes et les reglages. */
public class MainActivity extends AppCompatActivity {

    public static final int TAB_PROJECTS = 0;
    public static final int TAB_CREATE = 1;
    public static final int TAB_SETTINGS = 2;

    private static final int REQ_PICK = 101;
    private static final int REQ_CAMERA = 102;
    private static final int REQ_SAVE = 103;

    private int tab = TAB_CREATE;
    private int step = 1;
    private Uri cameraUri;
    private File pendingExport;
    private Prefs prefs;

    @Override
    protected void onCreate(@Nullable Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_main);
        applyWindowInsets();
        prefs = new Prefs(this);
        Session.get().options = prefs.options();

        findViewById(R.id.navProjects).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { showTab(TAB_PROJECTS); }
        });
        findViewById(R.id.navCreate).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { showTab(TAB_CREATE); }
        });
        findViewById(R.id.navSettings).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { showTab(TAB_SETTINGS); }
        });

        if (state != null) {
            tab = state.getInt("tab", TAB_CREATE);
            step = state.getInt("step", 1);
        }
        showTab(tab);
        com.tracecroquis.CrashLog.showIfAny(this);
    }

    /**
     * A partir d'Android 15, une application visant l'API 35 dessine sous les
     * barres systeme : on reporte leurs dimensions en marges interieures.
     */
    private void applyWindowInsets() {
        final View root = findViewById(R.id.root);
        if (root == null) return;
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        new androidx.core.view.WindowInsetsControllerCompat(getWindow(), root)
                .setAppearanceLightStatusBars(false);
        ViewCompat.setOnApplyWindowInsetsListener(root, new OnApplyWindowInsetsListener() {
            @Override public WindowInsetsCompat onApplyWindowInsets(View v, WindowInsetsCompat insets) {
                Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars()
                        | WindowInsetsCompat.Type.displayCutout());
                v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
                return insets;
            }
        });
        ViewCompat.requestApplyInsets(root);
    }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        out.putInt("tab", tab);
        out.putInt("step", step);
    }

    public Prefs prefs() { return prefs; }

    // ------------------------------------------------------------ navigation

    public void showTab(int which) {
        tab = which;
        Fragment f;
        if (which == TAB_PROJECTS) f = new ProjectsFragment();
        else if (which == TAB_SETTINGS) f = new SettingsFragment();
        else f = fragmentForStep(step);
        getSupportFragmentManager().beginTransaction()
                .replace(R.id.container, f)
                .commitAllowingStateLoss();
        updateNav();
    }

    public void goStep(int s) {
        step = Math.max(1, Math.min(4, s));
        tab = TAB_CREATE;
        getSupportFragmentManager().beginTransaction()
                .replace(R.id.container, fragmentForStep(step))
                .commitAllowingStateLoss();
        updateNav();
    }

    public int currentStep() { return step; }

    private Fragment fragmentForStep(int s) {
        switch (s) {
            case 2: return new Step2Fragment();
            case 3: return new Step3Fragment();
            case 4: return new Step4Fragment();
            default: return new Step1Fragment();
        }
    }

    private void updateNav() {
        bindNav(R.id.navProjectsIcon, R.id.navProjectsLabel, tab == TAB_PROJECTS);
        bindNav(R.id.navCreateIcon, R.id.navCreateLabel, tab == TAB_CREATE);
        bindNav(R.id.navSettingsIcon, R.id.navSettingsLabel, tab == TAB_SETTINGS);
        findViewById(R.id.navCreate).setBackgroundResource(
                tab == TAB_CREATE ? R.drawable.bg_nav_sel : 0);
    }

    private void bindNav(int iconId, int labelId, boolean active) {
        ImageView icon = findViewById(iconId);
        TextView label = findViewById(labelId);
        int color = getResources().getColor(active ? R.color.accent : R.color.txt_dim);
        icon.setColorFilter(color);
        label.setTextColor(color);
    }

    @Override
    public void onBackPressed() {
        if (tab == TAB_CREATE && step > 1) goStep(step - 1);
        else if (tab != TAB_CREATE) showTab(TAB_CREATE);
        else super.onBackPressed();
    }

    // --------------------------------------------------------------- images

    public void pickImage() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("image/*");
        try {
            startActivityForResult(i, REQ_PICK);
        } catch (Exception e) {
            Intent g = new Intent(Intent.ACTION_GET_CONTENT);
            g.setType("image/*");
            startActivityForResult(Intent.createChooser(g, getString(R.string.import_image)), REQ_PICK);
        }
    }

    public void takePhoto() {
        try {
            File dir = new File(getCacheDir(), "photos");
            dir.mkdirs();
            File f = new File(dir, "croquis_" + System.currentTimeMillis() + ".jpg");
            cameraUri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", f);
            Intent i = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
            i.putExtra(MediaStore.EXTRA_OUTPUT, cameraUri);
            i.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            startActivityForResult(i, REQ_CAMERA);
        } catch (Exception e) {
            toast("Appareil photo indisponible");
        }
    }

    @Override
    protected void onActivityResult(int req, int res, @Nullable Intent data) {
        super.onActivityResult(req, res, data);
        if (res != Activity.RESULT_OK) return;
        if (req == REQ_PICK && data != null && data.getData() != null) {
            loadSketch(data.getData(), nameOf(data.getData()));
        } else if (req == REQ_CAMERA && cameraUri != null) {
            loadSketch(cameraUri, "photo.jpg");
        } else if (req == REQ_SAVE && data != null && data.getData() != null && pendingExport != null) {
            copyTo(data.getData(), pendingExport);
        }
    }

    private String nameOf(Uri uri) {
        String s = uri.getLastPathSegment();
        if (s == null) return "croquis.jpg";
        int i = s.lastIndexOf('/');
        return i >= 0 ? s.substring(i + 1) : s;
    }

    private void loadSketch(final Uri uri, final String name) {
        final int maxDim = Math.max(900, Session.get().options.maxDimension);
        toast("Chargement du croquis…");
        Async.run(new Async.Job<Bitmap>() {
            @Override public Bitmap run() throws Exception {
                return Img.load(MainActivity.this, uri, maxDim);
            }
        }, new Async.Done<Bitmap>() {
            @Override public void onDone(Bitmap b, Exception e) {
                if (e != null || b == null) {
                    toast("Impossible de lire l'image");
                    return;
                }
                Session s = Session.get();
                if (s.source != null && !s.source.isRecycled()) s.source.recycle();
                s.source = b;
                s.sketch = b;
                s.corners = null;
                s.rotation = 0;
                s.sourceName = name;
                s.plan = new com.tracecroquis.core.model.Plan();
                s.result = null;
                s.clearHistory();
                Fragment f = getSupportFragmentManager().findFragmentById(R.id.container);
                if (f instanceof Step1Fragment) ((Step1Fragment) f).onSketchLoaded();
                else goStep(1);
            }
        });
    }

    // -------------------------------------------------------------- fichiers

    public void saveDocument(File file, String mime) {
        pendingExport = file;
        Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType(mime);
        i.putExtra(Intent.EXTRA_TITLE, file.getName());
        try {
            startActivityForResult(i, REQ_SAVE);
        } catch (Exception e) {
            toast("Enregistrement impossible");
        }
    }

    private void copyTo(Uri target, File src) {
        try {
            OutputStream out = getContentResolver().openOutputStream(target);
            FileInputStream in = new FileInputStream(src);
            byte[] buf = new byte[16384];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            in.close();
            out.close();
            toast("Document enregistré");
        } catch (Exception e) {
            toast("Échec de l'enregistrement");
        }
    }

    public void shareFile(File file, String mime) {
        try {
            Uri uri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", file);
            Intent i = new Intent(Intent.ACTION_SEND);
            i.setType(mime);
            i.putExtra(Intent.EXTRA_STREAM, uri);
            i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(i, getString(R.string.share)));
        } catch (Exception e) {
            toast("Partage impossible");
        }
    }

    public void toast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }
}
