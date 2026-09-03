package com.hanma.echocore;

import android.app.Application;

public class CrashShieldApp extends Application {
    @Override public void onCreate() {
        super.onCreate();
        final Thread.UncaughtExceptionHandler previous = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> {
            try {
                DiagnosticsStore d = new DiagnosticsStore(this);
                d.processCrash(thread == null ? "unknown" : thread.getName(), throwable);
                if (thread != null && thread.getName() != null && thread.getName().contains("EchoCoreAutoLink")) {
                    SecurePrefs p = new SecurePrefs(this);
                    int recent = p.getInt("autolink_worker_crashes", 0) + 1;
                    p.putInt("autolink_worker_crashes", recent);
                    if (recent >= 3) {
                        p.putBool(CloudLinkService.KEY_ENABLED, false);
                        d.setState("SAFE_MODE");
                        d.event("SAFE_MODE", "AutoLink worker crashed repeatedly; service auto-disabled until repair.");
                    }
                }
            } catch (Throwable ignored) {}
            if (previous != null) previous.uncaughtException(thread, throwable);
        });
    }
}