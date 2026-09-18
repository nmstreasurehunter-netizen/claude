package com.tracecroquis.ui;

import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;

import com.tracecroquis.R;
import com.tracecroquis.Session;
import com.tracecroquis.core.model.Plan;
import com.tracecroquis.store.ProjectStore;
import com.tracecroquis.util.Async;
import com.tracecroquis.util.Fmt;

import java.util.ArrayList;
import java.util.List;

/** Liste des projets conserves sur l'appareil. */
public class ProjectsFragment extends StepFragment {

    private ProjectStore store;
    private final List<ProjectStore.Entry> items = new ArrayList<>();
    private ListView list;
    private View empty;
    private Adapter adapter;

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inf, @Nullable ViewGroup parent, @Nullable Bundle b) {
        View v = inf.inflate(R.layout.fragment_projects, parent, false);
        store = new ProjectStore(getContext().getApplicationContext());
        list = v.findViewById(R.id.list);
        empty = v.findViewById(R.id.empty);
        adapter = new Adapter();
        list.setAdapter(adapter);

        View back = v.findViewById(R.id.btnBack);
        back.setVisibility(View.INVISIBLE);
        v.findViewById(R.id.btnHelp).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View x) {
                new AlertDialog.Builder(getContext())
                        .setTitle(R.string.tab_projects)
                        .setMessage("Chaque projet conserve le croquis d'origine et le plan "
                                + "vectoriel modifiable. Touchez un projet pour le rouvrir, "
                                + "appui long pour le supprimer.")
                        .setPositiveButton(R.string.ok, null).show();
            }
        });

        v.findViewById(R.id.btnNew).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View x) {
                Session.get().reset();
                main().goStep(1);
            }
        });

        list.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override public void onItemClick(AdapterView<?> a, View view, int pos, long id) {
                open(items.get(pos));
            }
        });
        list.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            @Override public boolean onItemLongClick(AdapterView<?> a, View view, int pos, long id) {
                confirmDelete(items.get(pos));
                return true;
            }
        });

        reload();
        return v;
    }

    private void reload() {
        items.clear();
        items.addAll(store.list());
        adapter.notifyDataSetChanged();
        empty.setVisibility(items.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private void open(final ProjectStore.Entry e) {
        Async.run(new Async.Job<Object[]>() {
            @Override public Object[] run() throws Exception {
                Plan p = store.loadPlan(e.id);
                return new Object[]{p, store.loadSketch(e.id)};
            }
        }, new Async.Done<Object[]>() {
            @Override public void onDone(Object[] r, Exception ex) {
                if (ex != null || r == null) {
                    toast("Projet illisible");
                    return;
                }
                Session s = Session.get();
                s.reset();
                s.plan = (Plan) r[0];
                s.sketch = (android.graphics.Bitmap) r[1];
                s.source = s.sketch;
                s.projectId = e.id;
                s.dirty = false;
                main().goStep(3);
            }
        });
    }

    private void confirmDelete(final ProjectStore.Entry e) {
        new AlertDialog.Builder(getContext())
                .setTitle(e.name)
                .setMessage("Supprimer définitivement ce projet ?")
                .setPositiveButton(R.string.delete, new android.content.DialogInterface.OnClickListener() {
                    @Override public void onClick(android.content.DialogInterface d, int w) {
                        store.delete(e.id);
                        reload();
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private class Adapter extends BaseAdapter {
        @Override public int getCount() { return items.size(); }

        @Override public Object getItem(int i) { return items.get(i); }

        @Override public long getItemId(int i) { return i; }

        @Override public View getView(int i, View convert, ViewGroup parent) {
            View v = convert != null ? convert
                    : LayoutInflater.from(getContext()).inflate(R.layout.item_project, parent, false);
            ProjectStore.Entry e = items.get(i);
            ((TextView) v.findViewById(R.id.name)).setText(e.name
                    + (e.level == null || e.level.isEmpty() ? "" : " · " + e.level));
            ((TextView) v.findViewById(R.id.info)).setText(String.format("%s · %d pièces · %s",
                    Fmt.date(e.modified), e.rooms, Fmt.m2(e.area)));
            ImageView thumb = v.findViewById(R.id.thumb);
            if (e.thumb != null) {
                thumb.setImageBitmap(BitmapFactory.decodeFile(e.thumb.getAbsolutePath()));
            } else {
                thumb.setImageResource(R.drawable.ic_doc);
            }
            return v;
        }
    }
}
