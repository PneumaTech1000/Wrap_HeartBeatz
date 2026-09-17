package com.realgear.multislidinguppanel;

import android.view.View;

/**
 * Coordinates sibling panels when one expands / collapses / hides.
 * Sets {@link BasePanelView#isHidden} when forcing HIDDEN so height stacking
 * does not reserve peak height for invisible panels.
 * Loop starts at index 1 (original library behavior).
 */
public class PanelStateListener {
    private final MultiSlidingUpPanelLayout mPanelLayout;

    public PanelStateListener(MultiSlidingUpPanelLayout panelLayout) {
        this.mPanelLayout = panelLayout;
    }

    public void onPanelSliding(IPanel<View> panel, float slidingOffset) {}

    @SuppressWarnings("unchecked")
    void onPanelCollapsed(IPanel<View> panel) {
        int count = this.mPanelLayout.getChildCount();
        for (int i = 1; i < count; i++) {
            View child = this.mPanelLayout.getChildAt(i);
            if (!(child instanceof IPanel)) continue;
            IPanel<View> temp_panel = (IPanel<View>) child;

            if (temp_panel instanceof BasePanelView) {
                ((BasePanelView) temp_panel).isHidden = false;
            }
            if (!temp_panel.isUserHidden()) {
                temp_panel.setPanelState(MultiSlidingUpPanelLayout.COLLAPSED);
            }
            temp_panel.getPanelView().setEnabled(true);
            try {
                temp_panel.resetPanelRealHeight();
            } catch (Exception ignored) {
            }
        }
        this.mPanelLayout.requestLayout();
    }

    @SuppressWarnings("unchecked")
    void onPanelExpanded(IPanel<View> panel) {
        int count = this.mPanelLayout.getChildCount();
        for (int i = 1; i < count; i++) {
            View child = this.mPanelLayout.getChildAt(i);
            if (!(child instanceof IPanel)) continue;
            IPanel<View> temp_panel = (IPanel<View>) child;

            if (temp_panel == panel) {
                temp_panel.getPanelView().setEnabled(false);
            } else {
                if (temp_panel instanceof BasePanelView) {
                    ((BasePanelView) temp_panel).isHidden = true;
                }
                temp_panel.setPanelState(MultiSlidingUpPanelLayout.HIDDEN);
                temp_panel.getPanelView().setEnabled(false);
            }
        }
        this.mPanelLayout.requestLayout();
    }

    @SuppressWarnings("unchecked")
    void onPanelHidden(IPanel<View> panel) {
        int count = this.mPanelLayout.getChildCount();
        for (int i = 1; i < count; i++) {
            View child = this.mPanelLayout.getChildAt(i);
            if (!(child instanceof IPanel)) continue;
            IPanel<View> temp_panel = (IPanel<View>) child;
            if (panel.isUserHidden() && panel != temp_panel) {
                try {
                    temp_panel.resetPanelRealHeight();
                } catch (Exception ignored) {
                }
            }
        }
        this.mPanelLayout.requestLayout();
    }
}
