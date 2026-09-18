package com.tracecroquis;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.os.Looper;
import android.view.View;

import com.tracecroquis.ui.MainActivity;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;

/**
 * Test de non-regression : chaque ecran doit s'afficher sans erreur.
 * Une mise en page invalide (attribut manquant, style incomplet) est
 * detectee ici plutot que sur le telephone.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class EcransTest {

    private MainActivity start() {
        ActivityController<MainActivity> c = Robolectric.buildActivity(MainActivity.class).setup();
        shadowOf(Looper.getMainLooper()).idle();
        return c.get();
    }

    @Test
    public void ecranPrincipalEtEtape1() {
        MainActivity a = start();
        assertNotNull(a.findViewById(R.id.container));
        assertNotNull(a.findViewById(R.id.navCreate));
        View content = a.getSupportFragmentManager().findFragmentById(R.id.container).getView();
        assertNotNull(content);
        assertNotNull(content.findViewById(R.id.cropView));
        assertNotNull(content.findViewById(R.id.stepCount));
    }

    @Test
    public void lesQuatreEtapesSAffichent() {
        MainActivity a = start();
        for (int step = 1; step <= 4; step++) {
            a.goStep(step);
            shadowOf(Looper.getMainLooper()).idle();
            View v = a.getSupportFragmentManager().findFragmentById(R.id.container).getView();
            assertNotNull("étape " + step, v);
            assertNotNull("bandeau d'étapes manquant à l'étape " + step, v.findViewById(R.id.stepCount));
            assertTrue("étape " + step, a.currentStep() == step);
        }
    }

    @Test
    public void projetsEtReglages() {
        MainActivity a = start();
        a.showTab(MainActivity.TAB_PROJECTS);
        shadowOf(Looper.getMainLooper()).idle();
        assertNotNull(a.getSupportFragmentManager().findFragmentById(R.id.container).getView()
                .findViewById(R.id.list));
        a.showTab(MainActivity.TAB_SETTINGS);
        shadowOf(Looper.getMainLooper()).idle();
        View v = a.getSupportFragmentManager().findFragmentById(R.id.container).getView()
                .findViewById(R.id.container);
        assertNotNull(v);
    }
}
