package com.giga.tech1000.heartbeatz.ui;

import android.util.Log;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.Nullable;

import com.giga.tech1000.heartbeatz.BuildConfig;
import com.google.android.material.bottomsheet.BottomSheetBehavior;

/**
 * Debug-only UI diagnostics. No-ops in release builds.
 */
public final class UIInfoLog {
    public static final String TAG = "UIInfo";

    private UIInfoLog() {}

    public static boolean enabled() {
        return BuildConfig.DEBUG;
    }

    public static void d(String msg) {
        if (!enabled()) return;
        Log.d(TAG, msg);
    }

    public static void d(String where, String msg) {
        if (!enabled()) return;
        Log.d(TAG, "[" + where + "] " + msg);
    }

    /** Material BottomSheet state ints (primary player path). */
    public static String stateName(int state) {
        return switch (state) {
            case BottomSheetBehavior.STATE_DRAGGING -> "DRAGGING";
            case BottomSheetBehavior.STATE_SETTLING -> "SETTLING";
            case BottomSheetBehavior.STATE_EXPANDED -> "EXPANDED";
            case BottomSheetBehavior.STATE_COLLAPSED -> "COLLAPSED";
            case BottomSheetBehavior.STATE_HIDDEN -> "HIDDEN";
            case BottomSheetBehavior.STATE_HALF_EXPANDED -> "HALF_EXPANDED";
            default -> "STATE_" + state;
        };
    }

    public static void panelSnapshot(String where, @Nullable View panel) {
        if (!enabled()) return;
        if (panel == null) {
            d(where, "panel=null");
            return;
        }
        d(where, panel.getClass().getSimpleName()
                + " top=" + panel.getTop()
                + " bottom=" + panel.getBottom()
                + " height=" + panel.getHeight()
                + " elev=" + panel.getElevation()
                + " visible=" + (panel.getVisibility() == View.VISIBLE));
    }

    public static void layoutChildren(String where, @Nullable ViewGroup host) {
        if (!enabled() || host == null) return;
        d(where, host.getClass().getSimpleName()
                + " children=" + host.getChildCount()
                + " h=" + host.getHeight());
        for (int i = 0; i < host.getChildCount(); i++) {
            View c = host.getChildAt(i);
            d(where + "/child[" + i + "]", c.getClass().getSimpleName()
                    + " top=" + c.getTop() + " bottom=" + c.getBottom()
                    + " h=" + c.getHeight());
        }
    }
}
