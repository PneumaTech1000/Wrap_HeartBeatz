package com.giga.tech1000.heartbeatz.ui;

import android.util.Log;
import android.view.View;

import androidx.annotation.Nullable;

import com.realgear.multislidinguppanel.BasePanelView;
import com.realgear.multislidinguppanel.IPanel;
import com.realgear.multislidinguppanel.MultiSlidingUpPanelLayout;

/**
 * Single log tag for UI layout / panel debugging. Filter Logcat: {@code UIInfo}
 */
public final class UIInfoLog {
    public static final String TAG = "UIInfo";

    private UIInfoLog() {}

    public static void d(String msg) {
        Log.d(TAG, msg);
    }

    public static void d(String where, String msg) {
        Log.d(TAG, "[" + where + "] " + msg);
    }

    public static String stateName(int state) {
        switch (state) {
            case MultiSlidingUpPanelLayout.COLLAPSED: return "COLLAPSED";
            case MultiSlidingUpPanelLayout.EXPANDED: return "EXPANDED";
            case MultiSlidingUpPanelLayout.HIDDEN: return "HIDDEN";
            case MultiSlidingUpPanelLayout.DRAGGING: return "DRAGGING";
            default: return "STATE_" + state;
        }
    }

    public static void panelSnapshot(String where, @Nullable BasePanelView panel) {
        if (panel == null) {
            d(where, "panel=null");
            return;
        }
        String name = panel.getClass().getSimpleName();
        int top = panel.getTop();
        int bottom = panel.getBottom();
        int h = panel.getHeight();
        float elev = panel.getElevation();
        float tz = panel.getTranslationZ();
        d(where, name
                + " state=" + stateName(panel.getPanelState())
                + " isHidden=" + panel.isUserHidden()
                + " floor=" + panel.getFloor()
                + " peak=" + panel.getPeakHeight()
                + " collapsedH=" + panel.getPanelCollapsedHeight()
                + " expandedH=" + panel.getPanelExpandedHeight()
                + " top=" + top + " bottom=" + bottom + " height=" + h
                + " elev=" + elev + " tz=" + tz
                + " enabled=" + panel.isEnabled());
    }

    public static void layoutChildren(String where, @Nullable MultiSlidingUpPanelLayout layout) {
        if (layout == null) {
            d(where, "layout=null");
            return;
        }
        int n = layout.getChildCount();
        d(where, "MultiSlidingUpPanel children=" + n
                + " h=" + layout.getHeight()
                + " padT=" + layout.getPaddingTop()
                + " padB=" + layout.getPaddingBottom()
                + " slidingEnabled=" + layout.isSlidingEnabled());
        for (int i = 0; i < n; i++) {
            View c = layout.getChildAt(i);
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
