package com.realgear.multislidinguppanel;

import android.content.Context;
import android.content.res.Resources;
import android.os.Parcel;
import android.os.Parcelable;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.TypedValue;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.FragmentManager;
import androidx.lifecycle.Lifecycle;

public abstract class BasePanelView extends FrameLayout implements IPanel<View> {
    protected MultiSlidingUpPanelLayout mParentSlidingPanel;

    @MultiSlidingUpPanelLayout.PanelState
    protected int mPanelState = MultiSlidingUpPanelLayout.COLLAPSED;

    @MultiSlidingUpPanelLayout.PanelState
    protected int mPrevPanelState = MultiSlidingUpPanelLayout.COLLAPSED;

    @MultiSlidingUpPanelLayout.SlideDirection
    protected int mSlideDirection = MultiSlidingUpPanelLayout.SLIDE_VERTICAL;

    protected int mExpandedHeightOffset;
    protected int mRealPanelHeight;
    protected int mExpandedHeight;
    protected int mPeakHeight;
    protected int mPeakPadding;
    protected int mIndex;

    protected float mSlope;

    public boolean isHidden = false;
    protected boolean isUserHideModeEnabled = false;

    public BasePanelView(@NonNull Context context, MultiSlidingUpPanelLayout panelLayout) {
        super(context);

        setClickable(true);
        this.mParentSlidingPanel = panelLayout;
    }

    public Lifecycle getLifecycle() {
        return this.mParentSlidingPanel.getAdapter().getAppCompatActivity().getLifecycle();
    }

    public FragmentManager getSupportFragmentManager() {
        return this.mParentSlidingPanel.getAdapter().getAppCompatActivity().getSupportFragmentManager();
    }


    ////////////////////////////////// -> Abstract functions
    public abstract void onCreateView();

    public abstract void onBindView();

    public abstract void onPanelStateChanged(@MultiSlidingUpPanelLayout.PanelState int panelSate);

    ////////////////////////////////// -> Override functions

    @NonNull
    @Override
    public BasePanelView getPanelView() {
        return this;
    }

    @Override
    public int getPanelExpandedHeight() {
        // Prefer the actual MultiSlidingUpPanel host height. Edge-to-edge + gesture nav
        // means DisplayMetrics minus status/nav dimen leaves a permanent gap under panels
        // (logs: parent h=1920, expandedH was 1731 → ~189px dead space under bottom nav).
        if (this.mParentSlidingPanel != null) {
            int hostH = this.mParentSlidingPanel.getHeight()
                    - this.mParentSlidingPanel.getPaddingTop()
                    - this.mParentSlidingPanel.getPaddingBottom();
            if (hostH > 0) {
                if (this.mExpandedHeight != hostH) {
                    Log.d("UIInfo", "[BasePanelView.getPanelExpandedHeight] "
                            + getClass().getSimpleName()
                            + " hostH=" + hostH
                            + " (was cached=" + this.mExpandedHeight + ")");
                    this.mExpandedHeight = hostH;
                    // Collapsed real height depends on expanded height math in callers
                    this.mRealPanelHeight = 0;
                }
                return this.mExpandedHeight;
            }
        }
        if (this.mExpandedHeight == 0) {
            // Fallback before first layout only
            DisplayMetrics dm = Resources.getSystem().getDisplayMetrics();
            WindowManager windowManager = (WindowManager) getContext().getSystemService(Context.WINDOW_SERVICE);
            if (windowManager != null) {
                windowManager.getDefaultDisplay().getRealMetrics(dm);
            }
            int offset = this.mParentSlidingPanel != null
                    ? this.mParentSlidingPanel.getNoLimitsOffset() : 0;
            this.mExpandedHeight = dm.heightPixels + offset;
            Log.d("UIInfo", "[BasePanelView.getPanelExpandedHeight] fallback displayH="
                    + this.mExpandedHeight + " self=" + getClass().getSimpleName());
        }
        return this.mExpandedHeight;
    }

    /** Force recalculation after host layout size is known. */
    public void invalidateExpandedHeight() {
        this.mExpandedHeight = 0;
        this.mRealPanelHeight = 0;
    }

    @Override
    public int getPanelCollapsedHeight() {
        return getPanelRealHeight();
    }

    @Override
    public int getPanelSlideDirection() {
        return this.mSlideDirection;
    }

    @Override
    public int getPanelState() {
        return this.mPanelState;
    }

    @Override
    public int getPrevPanelState() {
        return this.mPrevPanelState;
    }

    @Override
    public boolean isUserHidden() {
        return this.isHidden;
    }

    @Override
    public boolean isUserHiddenModeEnabled() {
        return this.isUserHideModeEnabled;
    }

    @Override
    public void disableUserHiddenMode() {
        this.isHidden = false;
    }

    @Override
    public int getPanelTopByPanelState(int panelState) {
        switch (panelState) {
            case MultiSlidingUpPanelLayout.COLLAPSED:
                return (this.getPanelExpandedHeight() - this.getPanelCollapsedHeight());

            case MultiSlidingUpPanelLayout.EXPANDED:
                return 0;

            case MultiSlidingUpPanelLayout.HIDDEN:
                return this.getPanelExpandedHeight();

            default:
                return this.getPanelExpandedHeightOffset();
        }
    }

    @Override
    public MultiSlidingUpPanelLayout getMultiSlidingUpPanel() {
        return this.mParentSlidingPanel;
    }

    @Override
    public void onSliding(@NonNull IPanel<View> panel, int top, int dy, float slidingOffset) {
        if (panel != this && slidingOffset >= 0.0F && !isUserHidden()) {
            int myTop = (int) ((this.getPanelExpandedHeight()) + this.getSlope(((BasePanelView) panel).getPanelRealHeight()) * top);
            this.setTop(myTop);
        }
        else if (panel != this && ((BasePanelView)panel).getFloor() > ((BasePanelView)this).getFloor() && slidingOffset < 0.0F && !this.isUserHidden()) {
            /*if (panel.isUserHidden() && panel.getPrevPanelState() == MultiSlidingUpPanelLayout.HIDDEN) {
                panel.disableUserHiddenMode();
                this.resetPanelRealHeight();
            }*/
            int prev_height = ((BasePanelView) panel).getPeakHeight();

            int collapse_height = panel.getPanelTopByPanelState(MultiSlidingUpPanelLayout.COLLAPSED);
            int hidden_height = panel.getPanelTopByPanelState(MultiSlidingUpPanelLayout.HIDDEN);

            float total = hidden_height - collapse_height;
            float current = (top - this.mParentSlidingPanel.getPaddingTop()) - collapse_height;

            int myTop = (int) (((this.getPanelExpandedHeight() + this.mParentSlidingPanel.getPaddingTop()) - (getPanelCollapsedHeight())) + ((prev_height ) * (current / total)));
            this.setTop(myTop);
        }
    }

    @Override
    public void setPanelState(int panelState) {
        int prev = this.mPanelState;
        this.mPrevPanelState = (this.mPanelState == panelState) ? this.mPrevPanelState : this.mPanelState;

        this.mPanelState = panelState;

        if (this.mPanelState != MultiSlidingUpPanelLayout.EXPANDED) {
            this.mSlope = 0;

        }

        Log.d("UIInfo", "[BasePanelView.setPanelState] " + getClass().getSimpleName()
                + " " + prev + "->" + panelState
                + " isHidden=" + isHidden
                + " floor=" + mIndex
                + " peak=" + getPeakHeight()
                + " collapsedH=" + getPanelCollapsedHeight()
                + " top=" + getTop()
                + " bottom=" + getBottom());

        this.onPanelStateChanged(panelState);
    }

    @Override
    public void setSlideDirection(int slideDirection) {
        this.mSlideDirection = slideDirection;
    }

    @Override
    public void setSlidingUpPanelRoot(MultiSlidingUpPanelLayout rootSlidingUpPanel) {
        this.mParentSlidingPanel = rootSlidingUpPanel;
    }

    ////////////////////////////////// -> Get functions
    public int getPeakHeight() {
        return this.mPeakHeight - this.mPeakPadding;
    }

    public int getPanelExpandedHeightOffset() {
        return this.mExpandedHeightOffset;
    }

    public int getFloor() {
        return this.mIndex;
    }

    public int getPanelRealHeight() {
        if (this.mRealPanelHeight == 0) {
            this.resetPanelRealHeight();
        }

        return this.mRealPanelHeight;
    }

    @Override
    public void resetPanelRealHeight() {
        this.mRealPanelHeight = getPrevPanelsHeight(this.getFloor()) + this.mPeakHeight;
    }

    private int getPrevPanelsHeight(int currentPosition) {
        int maxHeight = 0;
        if (this.mParentSlidingPanel == null || this.mParentSlidingPanel.getAdapter() == null) {
            Log.d("UIInfo", "[BasePanelView.getPrevPanelsHeight] parent/adapter null self="
                    + getClass().getSimpleName());
            return 0;
        }

        int count = this.mParentSlidingPanel.getAdapter().getItemCount();
        int i = currentPosition + 1;

        maxHeight = this.mParentSlidingPanel.getNoLimitsOffset();

        StringBuilder sb = new StringBuilder();
        sb.append("[BasePanelView.getPrevPanelsHeight] self=").append(getClass().getSimpleName())
                .append(" pos=").append(currentPosition).append(" noLimits=").append(maxHeight);
        for (; i < count; i++) {
            // During onCreateView, later panels may not be attached yet — getItem returns null
            IPanel panelItem = this.mParentSlidingPanel.getAdapter().getItem(i);
            if (!(panelItem instanceof BasePanelView)) {
                sb.append(" | [").append(i).append("]=null/skip");
                continue;
            }
            BasePanelView panel = (BasePanelView) panelItem;
            int add = panel.isUserHidden() ? 0 : panel.getPeakHeight();
            maxHeight += add;
            sb.append(" | [").append(i).append("]").append(panel.getClass().getSimpleName())
                    .append(" hidden=").append(panel.isUserHidden())
                    .append(" peak=").append(panel.getPeakHeight())
                    .append(" add=").append(add);
        }
        sb.append(" => totalPrev=").append(maxHeight);
        Log.d("UIInfo", sb.toString());

        return maxHeight;
    }

    public float getSlope(int viewHeight) {
        if (this.mSlope == 0)
            this.mSlope = -1.0F * (this.getPanelRealHeight() - this.mParentSlidingPanel.getPaddingTop()) / ((this.getPanelExpandedHeight() + this.mParentSlidingPanel.getPaddingTop()) - viewHeight);

        return this.mSlope;
    }

    int dp2px(int dp) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp, Resources.getSystem().getDisplayMetrics());
    }

    ////////////////////////////////// -> Set functions
    public void setUserHiddenMode(boolean enable) {
        this.isUserHideModeEnabled = enable;
    }
    public void setFloor(int floor) {
        this.mIndex = floor;
    }

    public void setPeakHeight(int peakHeight) {
        this.mPeakHeight = peakHeight;
    }

    public void setPanelExpandedHeightOffset(int offset) {
        this.mExpandedHeightOffset = 0;
    }

    ////////////////////////////////// -> API functions
    @SuppressWarnings("all")
    public void expandPanel() {
        this.mParentSlidingPanel.expandPanel(this);
    }
    @SuppressWarnings("all")
    public void collapsePanel() {
        this.mParentSlidingPanel.collapsePanel(this);
    }
    public void hidePanel() {
        this.mParentSlidingPanel.hidePanel(this);
        this.isHidden =  true;
    }

    ////////////////////////////////// -> Instance functions

    @Nullable
    @Override
    protected Parcelable onSaveInstanceState() {
        Parcelable superState = super.onSaveInstanceState();

        SavedState ss = new SavedState(superState);
        ss.mSavedPanelState = mPanelState;

        return ss;
    }

    @Override
    protected void onRestoreInstanceState(Parcelable state) {
        SavedState ss = (SavedState) state;
        super.onRestoreInstanceState(ss.getSuperState());
        mPanelState = ss.mSavedPanelState;
    }

    private static class SavedState extends View.BaseSavedState {
        int mSavedPanelState;

        SavedState(Parcelable superState) {
            super(superState);
        }

        private SavedState(Parcel in) {
            super(in);

            mSavedPanelState = in.readInt();
        }

        @Override
        public void writeToParcel(Parcel out, int flags) {
            super.writeToParcel(out, flags);
            out.writeInt(mSavedPanelState);
        }

        public static final Creator<SavedState> CREATOR = new Creator<SavedState>() {
            @Override
            public SavedState createFromParcel(Parcel parcel) {
                return new SavedState(parcel);
            }

            @Override
            public SavedState[] newArray(int i) {
                return new SavedState[i];
            }
        };
    }
}
