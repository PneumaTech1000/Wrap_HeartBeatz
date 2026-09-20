package com.giga.tech1000.heartbeatz.ui;

import android.util.Log;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.Nullable;

import com.giga.tech1000.heartbeatz.BuildConfig;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.realgear.multislidinguppanel.BasePanelView;
import com.realgear.multislidinguppanel.MultiSlidingUpPanelLayout;

/**
 * Debug-only UI diagnostics. No-ops in release builds ({@link BuildConfig#DEBUG} false).
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

    /** Accepts Material BottomSheet or legacy MultiSliding state ints. */
    public static String stateName(int state) {
        return switch (state) {
            case BottomSheetBehavior.STATE_COLLAPSED,
                 MultiSlidingUpPanelLayout.COLLAPSED -> "COLLAPSED";
            case BottomSheetBehavior.STATE_EXPANDED,
                 MultiSlidingUpPanelLayout.EXPANDED -> "EXPANDED";
            case BottomSheetBehavior.STATE_HIDDEN,
                 MultiSlidingUpPanelLayout.HIDDEN -> "HIDDEN";
            case BottomSheetBehavior.STATE_DRAGGING,
                 MultiSlidingUpPanelLayout.DRAGGING -> "DRAGGING";
            case BottomSheetBehavior.STATE_SETTLING -> "SETTLING";
            case BottomSheetBehavior.STATE_HALF_EXPANDED -> "HALF_EXPANDED";
            default -> "STATE_" + state;
        };
    }

    public static void panelSnapshot(String where, @Nullable BasePanelView panel) {
        if (!enabled()) return;
        if (panel == null) {
            d(where, "panel=null");
            return;
        }
        String name = panel.getClass().getSimpleName();
        d(where, name
                + " state=" + stateName(panel.getPanelState())
                + " isHidden=" + panel.isUserHidden()
                + " floor=" + panel.getFloor()
                + " peak=" + panel.getPeakHeight()
                + " collapsedH=" + panel.getPanelCollapsedHeight()
                + " expandedH=" + panel.getPanelExpandedHeight()
                + " top=" + panel.getTop() + " bottom=" + panel.getBottom()
                + " height=" + panel.getHeight()
                + " elev=" + panel.getElevation() + " tz=" + panel.getTranslationZ()
                + " enabled=" + panel.isEnabled());
    }

    public static void layoutChildren(String where, @Nullable ViewGroup host) {
        if (!enabled() || host == null) return;
        d(where, host.getClass().getSimpleName()
                + " children=" + host.getChildCount()
                + " h=" + host.getHeight()
                + " padT=" + host.getPaddingTop()
                + " padB=" + host.getPaddingBottom()
                + (host instanceof MultiSlidingUpPanelLayout
                ? " slidingEnabled=" + ((MultiSlidingUpPanelLayout) host).isEnabled()
                : ""));
        for (int i = 0; i < host.getChildCount(); i++) {
            View c = host.getChildAt(i);
            if (c instanceof BasePanelView) {
                panelSnapshot(where + "/child[" + i + "]", (BasePanelView) c);
            } else {
                d(where + "/child[" + i + "]", c.getClass().getSimpleName()
                        + " top=" + c.getTop() + " bottom=" + c.getBottom()
                        + " h=" + c.getHeight() + " elev=" + c.getElevation());
            }
        }
    }
}
