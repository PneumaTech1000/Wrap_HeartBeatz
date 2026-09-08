package com.giga.tech1000.heartbeatz.utils;

import android.app.Activity;
import android.os.SystemClock;
import android.view.View;

import androidx.annotation.NonNull;

import com.giga.tech1000.utils.interfaces.OnBackPressedHandler;
import com.google.android.material.snackbar.Snackbar;

public final class DoubleBackToExitHandler implements OnBackPressedHandler {

    private static final long EXIT_WINDOW_MS = 2000;

    private final Activity activity;
    private final View anchorView;

    private long lastBackPressedTime = 0L;
    private Snackbar snackbar;

    public DoubleBackToExitHandler(
            @NonNull Activity activity,
            @NonNull View anchorView
    ) {
        this.activity = activity;
        this.anchorView = anchorView;
    }

    @Override
    public boolean onBackPressed() {

        long now = SystemClock.elapsedRealtime();

        if (now - lastBackPressedTime < EXIT_WINDOW_MS) {
            dismissSnackBar();
            activity.finish();
            return true;
        }

        lastBackPressedTime = now;
        showSnackBar();
        return true; // consume first back
    }

    private void showSnackBar() {
        dismissSnackBar();

        snackbar = Snackbar.make(
                anchorView,
                "Press back again to exit",
                Snackbar.LENGTH_SHORT
        );

        snackbar.setAnimationMode(Snackbar.ANIMATION_MODE_FADE);
        snackbar.show();
    }

    private void dismissSnackBar() {
        if (snackbar != null && snackbar.isShown()) {
            snackbar.dismiss();
        }
        snackbar = null;
    }
}

