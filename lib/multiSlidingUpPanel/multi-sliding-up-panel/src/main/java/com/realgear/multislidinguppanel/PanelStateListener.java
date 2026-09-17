package com.realgear.multislidinguppanel;

import android.util.Log;
import android.view.View;

/**
 * Coordinates sibling panels when one expands / collapses / hides.
 * Sets {@link BasePanelView#isHidden} when forcing HIDDEN so height stacking
 * does not reserve peak height for invisible panels.
 * Loop starts at index 1 (original library behavior).
 */
public class PanelStateListener {
    private static final String TAG = "UIInfo";
    private final MultiSlidingUpPanelLayout mPanelLayout;

    public PanelStateListener(MultiSlidingUpPanelLayout panelLayout) {
        this.mPanelLayout = panelLayout;
    }

    public void onPanelSliding(IPanel<View> panel, float slidingOffset) {
        // Throttle: only log near edges
        if (slidingOffset < 0.05f || slidingOffset > 0.95f || Math.abs(slidingOffset) < 0.05f) {
            Log.d(TAG, "[PanelStateListener.onSliding] offset=" + slidingOffset
                    + " panel=" + panel.getClass().getSimpleName());
        }
    }

    @SuppressWarnings("unchecked")
    void onPanelCollapsed(IPanel<View> panel) {
        Log.d(TAG, "[PanelStateListener.onPanelCollapsed] START panel="
                + panel.getClass().getSimpleName()
                + " isUserHidden=" + panel.isUserHidden());
        int count = this.mPanelLayout.getChildCount();
        for (int i = 1; i < count; i++) {
            View child = this.mPanelLayout.getChildAt(i);
            if (!(child instanceof IPanel)) continue;
            IPanel<View> temp_panel = (IPanel<View>) child;
            String name = temp_panel.getClass().getSimpleName();
            boolean beforeHidden = temp_panel.isUserHidden();
            int beforeState = temp_panel.getPanelState();

            if (temp_panel instanceof BasePanelView) {
                ((BasePanelView) temp_panel).isHidden = false;
            }
            if (!temp_panel.isUserHidden()) {
                temp_panel.setPanelState(MultiSlidingUpPanelLayout.COLLAPSED);
            }
            temp_panel.getPanelView().setEnabled(true);
            try {
                temp_panel.resetPanelRealHeight();
            } catch (Exception e) {
                Log.w(TAG, "[PanelStateListener.onPanelCollapsed] resetPanelRealHeight failed: " + e);
            }
            Log.d(TAG, "[PanelStateListener.onPanelCollapsed] sibling[" + i + "]=" + name
                    + " beforeState=" + beforeState + " beforeHidden=" + beforeHidden
                    + " afterState=" + temp_panel.getPanelState()
                    + " afterHidden=" + temp_panel.isUserHidden()
                    + " peak=" + (temp_panel instanceof BasePanelView
                        ? ((BasePanelView) temp_panel).getPeakHeight() : -1)
                    + " collapsedH=" + temp_panel.getPanelCollapsedHeight()
                    + " top=" + temp_panel.getPanelView().getTop());
        }
        this.mPanelLayout.requestLayout();
        Log.d(TAG, "[PanelStateListener.onPanelCollapsed] END requestLayout");
    }

    @SuppressWarnings("unchecked")
    void onPanelExpanded(IPanel<View> panel) {
        Log.d(TAG, "[PanelStateListener.onPanelExpanded] START panel="
                + panel.getClass().getSimpleName());
        int count = this.mPanelLayout.getChildCount();
        for (int i = 1; i < count; i++) {
            View child = this.mPanelLayout.getChildAt(i);
            if (!(child instanceof IPanel)) continue;
            IPanel<View> temp_panel = (IPanel<View>) child;
            String name = temp_panel.getClass().getSimpleName();

            if (temp_panel == panel) {
                temp_panel.getPanelView().setEnabled(false);
                Log.d(TAG, "[PanelStateListener.onPanelExpanded] self=" + name + " enabled=false");
            } else {
                if (temp_panel instanceof BasePanelView) {
                    ((BasePanelView) temp_panel).isHidden = true;
                }
                temp_panel.setPanelState(MultiSlidingUpPanelLayout.HIDDEN);
                temp_panel.getPanelView().setEnabled(false);
                Log.d(TAG, "[PanelStateListener.onPanelExpanded] hide sibling=" + name
                        + " isHidden=" + temp_panel.isUserHidden()
                        + " state=" + temp_panel.getPanelState()
                        + " top=" + temp_panel.getPanelView().getTop());
            }
        }
        this.mPanelLayout.requestLayout();
        Log.d(TAG, "[PanelStateListener.onPanelExpanded] END requestLayout");
    }

    @SuppressWarnings("unchecked")
    void onPanelHidden(IPanel<View> panel) {
        Log.d(TAG, "[PanelStateListener.onPanelHidden] panel="
                + panel.getClass().getSimpleName()
                + " isUserHidden=" + panel.isUserHidden());
        int count = this.mPanelLayout.getChildCount();
        for (int i = 1; i < count; i++) {
            View child = this.mPanelLayout.getChildAt(i);
            if (!(child instanceof IPanel)) continue;
            IPanel<View> temp_panel = (IPanel<View>) child;
            if (panel.isUserHidden() && panel != temp_panel) {
                try {
                    temp_panel.resetPanelRealHeight();
                    Log.d(TAG, "[PanelStateListener.onPanelHidden] resetRealHeight sibling="
                            + temp_panel.getClass().getSimpleName()
                            + " collapsedH=" + temp_panel.getPanelCollapsedHeight());
                } catch (Exception e) {
                    Log.w(TAG, "[PanelStateListener.onPanelHidden] reset failed: " + e);
                }
            }
        }
        this.mPanelLayout.requestLayout();
    }
}
