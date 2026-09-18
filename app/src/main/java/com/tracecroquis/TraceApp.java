package com.tracecroquis;

import android.app.Application;

/** Point d'entree de l'application : installe le journal de plantage. */
public class TraceApp extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        CrashLog.install(this);
    }
}
