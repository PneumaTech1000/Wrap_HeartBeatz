package com.realgear.multislidinguppanel;

import android.view.View;

/**
 * Coordinates sibling panels when one expands / collapses / hides.
 * {@link BasePanelView#isHidden} must match visibility so height stacking stays correct.
 */
public class PanelStateListener {
    private final MultiSlidingUpPanelLayout mPanelLayout;

    public PanelStateListener(MultiSlidingUpPanelLayout panelLayout) {
        this.mPanelLayout = panelLayout;
    }

    public void onPanelSliding(IPanel<View> panel, float slidingOffset) {}

    /**
     * Panel returned to collapsed (e.g. full player → mini player).
     * Re-show siblings that were hidden only for the full expansion.
     */
    @SuppressWarnings("unchecked")
    void onPanelCollapsed(IPanel<View> panel) {
        int count = this.mPanelLayout.getChildCount();
        for (int i = 0; i < count; i++) {
            View child = this.mPanelLayout.getChildAt(i);
            if (!(child instanceof IPanel)) continue;
            IPanel<View> temp_panel = (IPanel<View>) child;

            if (temp_panel == panel) {
                if (temp_panel instanceof BasePanelView) {
                    ((BasePanelView) temp_panel).isHidden = false;
                }
                temp_panel.getPanelView().setEnabled(true);
                continue;
            }

            // Restore bottom nav (and any other siblings) above the mini player
            if (temp_panel instanceof BasePanelView) {
                ((BasePanelView) temp_panel).isHidden = false;
            }
            temp_panel.setPanelState(MultiSlidingUpPanelLayout.COLLAPSED);
            temp_panel.getPanelView().setEnabled(true);
            try {
                temp_panel.resetPanelRealHeight();
            } catch (Exception ignored) {
            }
        }
        this.mPanelLayout.requestLayout();
    }

    /**
     * Full expansion (e.g. full-screen media player).
     * Fully hide other panels so they neither draw nor reserve peak height.
     */
    @SuppressWarnings("unchecked")
    void onPanelExpanded(IPanel<View> panel) {
        int count = this.mPanelLayout.getChildCount();
        for (int i = 0; i < count; i++) {
            View child = this.mPanelLayout.getChildAt(i);
            if (!(child instanceof IPanel)) continue;
            IPanel<View> temp_panel = (IPanel<View>) child;

            if (temp_panel == panel) {
                if (temp_panel instanceof BasePanelView) {
                    ((BasePanelView) temp_panel).isHidden = false;
                }
                temp_panel.getPanelView().setEnabled(false);
                continue;
            }

            if (temp_panel instanceof BasePanelView) {
                ((BasePanelView) temp_panel).isHidden = true;
            }
            temp_panel.setPanelState(MultiSlidingUpPanelLayout.HIDDEN);
            temp_panel.getPanelView().setEnabled(false);
        }
        this.mPanelLayout.requestLayout();
    }

    @SuppressWarnings("unchecked")
    void onPanelHidden(IPanel<View> panel) {
        int count = this.mPanelLayout.getChildCount();
        for (int i = 0; i < count; i++) {
            View child = this.mPanelLayout.getChildAt(i);
            if (!(child instanceof IPanel)) continue;
            IPanel<View> temp_panel = (IPanel<View>) child;
            if (panel != temp_panel && panel.isUserHidden()) {
                try {
                    temp_panel.resetPanelRealHeight();
                } catch (Exception ignored) {
                }
            }
        }
        this.mPanelLayout.requestLayout();
    }
}
