package com.tracecroquis.util;

import android.os.Handler;
import android.os.Looper;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Execution en tache de fond avec retour sur le fil principal. */
public final class Async {

    public interface Job<T> {
        T run() throws Exception;
    }

    public interface Done<T> {
        void onDone(T result, Exception error);
    }

    private static final ExecutorService POOL = Executors.newFixedThreadPool(
            Math.max(2, Runtime.getRuntime().availableProcessors() - 1));
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private Async() { }

    public static <T> void run(final Job<T> job, final Done<T> done) {
        POOL.execute(new Runnable() {
            @Override public void run() {
                T result = null;
                Exception error = null;
                try {
                    result = job.run();
                } catch (Exception e) {
                    error = e;
                }
                final T r = result;
                final Exception err = error;
                MAIN.post(new Runnable() {
                    @Override public void run() { done.onDone(r, err); }
                });
            }
        });
    }

    public static void ui(Runnable r) {
        MAIN.post(r);
    }
}
