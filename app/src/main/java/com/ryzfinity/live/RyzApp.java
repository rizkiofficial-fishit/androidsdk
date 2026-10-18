package com.ryzfinity.live;

import android.app.Application;

public class RyzApp extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        final Thread.UncaughtExceptionHandler prev = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((t, e) -> {
            Diag.crash(this, t, e);
            if (prev != null) prev.uncaughtException(t, e);
        });
    }
}
