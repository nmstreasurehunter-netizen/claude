package com.tracecroquis.ui;

import android.view.View;
import android.widget.PopupMenu;
import android.widget.TextView;

import androidx.fragment.app.Fragment;

import com.tracecroquis.R;
import com.tracecroquis.Session;

/** Base commune aux quatre etapes : entete, fil d'etapes et acces a l'activite. */
public abstract class StepFragment extends Fragment {

    protected Session session() {
        return Session.get();
    }

    protected MainActivity main() {
        return (MainActivity) getActivity();
    }

    protected void toast(String s) {
        MainActivity m = main();
        if (m != null) m.toast(s);
    }

    /** Branche l'entete (retour, aide) et le bandeau d'etapes. */
    protected void bindChrome(View root, final int step, String stepName, final String help) {
        View back = root.findViewById(R.id.btnBack);
        if (back != null) {
            back.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    if (step > 1) main().goStep(step - 1);
                    else main().showTab(MainActivity.TAB_PROJECTS);
                }
            });
        }
        View helpBtn = root.findViewById(R.id.btnHelp);
        if (helpBtn != null) {
            helpBtn.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    new androidx.appcompat.app.AlertDialog.Builder(getContext())
                            .setTitle(R.string.help)
                            .setMessage(help)
                            .setPositiveButton(R.string.ok, null)
                            .show();
                }
            });
        }
        TextView count = root.findViewById(R.id.stepCount);
        if (count != null) count.setText(getString(R.string.step_of, step));
        TextView name = root.findViewById(R.id.stepName);
        if (name != null) name.setText(stepName);

        int[] pills = {R.id.pill1, R.id.pill2, R.id.pill3, R.id.pill4};
        for (int i = 0; i < pills.length; i++) {
            View p = root.findViewById(pills[i]);
            if (p == null) continue;
            if (i + 1 < step) p.setBackgroundResource(R.drawable.bg_pill_done);
            else if (i + 1 == step) p.setBackgroundResource(R.drawable.bg_pill_on);
            else p.setBackgroundResource(R.drawable.bg_pill_off);
        }

        View steps = root.findViewById(R.id.btnSteps);
        if (steps != null) {
            steps.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    PopupMenu menu = new PopupMenu(getContext(), v);
                    menu.getMenu().add(0, 1, 0, "1 · " + getString(R.string.step1));
                    menu.getMenu().add(0, 2, 1, "2 · " + getString(R.string.step2));
                    menu.getMenu().add(0, 3, 2, "3 · " + getString(R.string.step3));
                    menu.getMenu().add(0, 4, 3, "4 · " + getString(R.string.step4));
                    menu.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
                        @Override public boolean onMenuItemClick(android.view.MenuItem item) {
                            main().goStep(item.getItemId());
                            return true;
                        }
                    });
                    menu.show();
                }
            });
        }
    }

    /** Active ou desactive l'aspect "selectionne" d'une tuile. */
    protected void setSelected(View v, boolean sel) {
        if (v != null) v.setSelected(sel);
    }

    protected String projectSubtitle() {
        Session s = session();
        String name = s.plan.projectName == null || s.plan.projectName.isEmpty()
                ? "Maison" : s.plan.projectName;
        String level = s.plan.levelName == null ? "" : s.plan.levelName;
        return level.isEmpty() ? name : name + " · " + level;
    }
}
