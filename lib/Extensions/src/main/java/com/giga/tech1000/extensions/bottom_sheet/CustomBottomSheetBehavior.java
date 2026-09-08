package com.giga.tech1000.extensions.bottom_sheet;

import static androidx.annotation.RestrictTo.Scope.GROUP_ID;

import android.content.Context;
import android.content.res.TypedArray;
import android.os.Parcel;
import android.os.Parcelable;
import android.util.AttributeSet;
import android.util.Log;
import android.util.TypedValue;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.ViewParent;

import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.RestrictTo;
import androidx.annotation.VisibleForTesting;
import androidx.coordinatorlayout.widget.CoordinatorLayout;
import androidx.core.math.MathUtils;
import androidx.core.view.NestedScrollingChild;
import androidx.core.view.ViewCompat;
import androidx.customview.view.AbsSavedState;
import androidx.customview.widget.ViewDragHelper;
import androidx.viewpager.widget.ViewPager;
import androidx.viewpager2.widget.ViewPager2;

import com.giga.tech1000.extensions.R;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;

/**
 * A CoordinatorLayout.Behavior that provides support for a bottom sheet that can be dragged,
 * expanded, collapsed, anchored, and hidden. This is an extension of the standard BottomSheetBehavior
 * to include an intermediate 'ANCHORED' state.
 *
 * @param <V> The type of the view that this behavior is attached to.
 */
public class CustomBottomSheetBehavior<V extends View> extends CoordinatorLayout.Behavior<V> {
    public final String TAG = this.getClass().getSimpleName();

    /**
     * Callback for monitoring events about bottom sheets.
     */
    public abstract static class BottomSheetCallback {

        /**
         * Called when the bottom sheet changes its state.
         *
         * @param bottomSheet The bottom sheet view.
         * @param oldState    The old state. This will be one of {@link #STATE_DRAGGING},
         *                    {@link #STATE_SETTLING}, {@link #STATE_EXPANDED},
         *                    {@link #STATE_COLLAPSED}, {@link #STATE_ANCHORED} or
         *                    {@link #STATE_HIDDEN}.
         * @param newState    The new state. This will be one of {@link #STATE_DRAGGING},
         *                    {@link #STATE_SETTLING}, {@link #STATE_EXPANDED},
         *                    {@link #STATE_COLLAPSED}, {@link #STATE_ANCHORED} or
         *                    {@link #STATE_HIDDEN}.
         */
        public abstract void onStateChanged(@NonNull View bottomSheet, @State int oldState, @State int newState);

        /**
         * Called when the bottom sheet is being dragged.
         *
         * @param bottomSheet The bottom sheet view.
         * @param slideOffset The new offset of this bottom sheet within [-1,1] range. Offset
         *                    increases as this bottom sheet is moving upward. From 0 to 1 the sheet
         *                    is between collapsed and expanded states and from -1 to 0 it is
         *                    between hidden and collapsed states.
         */
        public abstract void onSlide(@NonNull View bottomSheet, float slideOffset);
    }

    /**
     * Stub/no-op implementations of all methods of {@link BottomSheetCallback}.
     * Override this if you only care about a few of the available callback methods.
     */
    public abstract static class SimpleBottomSheetCallback extends BottomSheetCallback {
        @Override
        public void onStateChanged(@NonNull View bottomSheet, @State int oldState, @State int newState) {
        }

        @Override
        public void onSlide(@NonNull View bottomSheet, float slideOffset) {
        }
    }

    /**
     * The bottom sheet is dragging.
     */
    public static final int STATE_DRAGGING = 1;

    /**
     * The bottom sheet is settling.
     */
    public static final int STATE_SETTLING = 2;

    /**
     * The bottom sheet is expanded.
     */
    public static final int STATE_EXPANDED = 3;

    /**
     * The bottom sheet is collapsed.
     */
    public static final int STATE_COLLAPSED = 4;

    /**
     * The bottom sheet is hidden.
     */
    public static final int STATE_HIDDEN = 5;

    /**
     * The bottom sheet is anchored.
     */
    public static final int STATE_ANCHORED = 6;

    private int mediaPlayerBarHeight;

    /**
     * @hide
     */
    @RestrictTo(GROUP_ID)
    @IntDef({STATE_EXPANDED, STATE_COLLAPSED, STATE_DRAGGING, STATE_SETTLING, STATE_HIDDEN, STATE_ANCHORED})
    @Retention(RetentionPolicy.SOURCE)
    public @interface State {
    }

    /**
     * Peek at the 16:9 ratio keyline of its parent.
     * <p>
     * <p>This can be used as a parameter for {@link #setPeekHeight(int)}.
     * {@link #getPeekHeight()} will return this when the value is set.</p>
     */
    public static final int PEEK_HEIGHT_AUTO = -1;

    private static final float HIDE_THRESHOLD = 0.5f;

    private static final float HIDE_FRICTION = 0.1f;

    private float mMinimumVelocity;

    private float mMaximumVelocity;

    private int mPeekHeight;

    private boolean mPeekHeightAuto;

    private int mPeekHeightMin;

    int mAnchorOffset; // Offset for the anchored state from the top of the parent.

    int mExpandedOffset; // Offset for the expanded state from the top of the parent. Usually 0.

    int mCollapsedOffset; // Offset for the collapsed state. Same as maxOffset.

    boolean mHideable;

    private boolean mSkipCollapsed;

    private boolean mSkipAnchored;

    @State
    int mState = STATE_COLLAPSED;

    @State
    int mPrevState = STATE_COLLAPSED;

    ViewDragHelper mViewDragHelper;

    private boolean mIgnoreEvents;

    private boolean mNestedScrolled;

    private boolean mAllowUserDragging = true;

    int mParentHeight;

    WeakReference<V> mViewRef;

    WeakReference<View> mNestedScrollingChildRef;

    private List<BottomSheetCallback> mCallbacks = new ArrayList<>(2);

    private VelocityTracker mVelocityTracker;

    int mActivePointerId;

    private int mInitialY;

    boolean mTouchingScrollingChild;

    // Add this near the top with your other constants

    /**
     * Default constructor for instantiating CustomBottomSheetBehavior.
     */
    public CustomBottomSheetBehavior() {
    }

    /**
     * Default constructor for inflating CustomBottomSheetBehavior from layout.
     *
     * @param context The {@link Context}.
     * @param attrs   The {@link AttributeSet}.
     */
    public CustomBottomSheetBehavior(Context context, AttributeSet attrs) {
        super(context, attrs);
        TypedArray a = context.obtainStyledAttributes(attrs,
                com.google.android.material.R.styleable.BottomSheetBehavior_Layout);
        TypedValue value = a.peekValue(com.google.android.material.R.styleable.BottomSheetBehavior_Layout_behavior_peekHeight);
        if (value != null && value.data == PEEK_HEIGHT_AUTO) {
            setPeekHeight(value.data);
        } else {
            setPeekHeight(a.getDimensionPixelSize(
                    com.google.android.material.R.styleable.BottomSheetBehavior_Layout_behavior_peekHeight, PEEK_HEIGHT_AUTO));
        }
        setHideable(a.getBoolean(com.google.android.material.R.styleable.BottomSheetBehavior_Layout_behavior_hideable, false));
        setSkipCollapsed(a.getBoolean(com.google.android.material.R.styleable.BottomSheetBehavior_Layout_behavior_skipCollapsed,
                false));
        // Custom attribute for skipping the anchored state
        setSkipAnchored(a.getBoolean(R.styleable.CustomBottomSheetBehavior_behavior_skipAnchored, false));
        a.recycle();

        a = context.obtainStyledAttributes(attrs, R.styleable.CustomBottomSheetBehavior);
        // Custom attribute for anchor offset
        mAnchorOffset = (int) a.getDimension(R.styleable.CustomBottomSheetBehavior_behavior_anchorOffset, 0);
        // Custom attribute for default state
        @State int defaultState = a.getInt(R.styleable.CustomBottomSheetBehavior_behavior_defaultState, STATE_COLLAPSED);
        setState(defaultState);
        a.recycle();

        ViewConfiguration configuration = ViewConfiguration.get(context);
        mMaximumVelocity = configuration.getScaledMaximumFlingVelocity();
        mMinimumVelocity = configuration.getScaledMinimumFlingVelocity();
    }

    @Override
    public Parcelable onSaveInstanceState(@NonNull CoordinatorLayout parent, @NonNull V child) {
        return new SavedState(super.onSaveInstanceState(parent, child), mState);
    }

    @Override
    public void onRestoreInstanceState(@NonNull CoordinatorLayout parent, @NonNull V child, @NonNull Parcelable state) {
        SavedState ss = (SavedState) state;
        super.onRestoreInstanceState(parent, child, ss.getSuperState());
        // Intermediate states are restored as collapsed state
        if (ss.state == STATE_DRAGGING || ss.state == STATE_SETTLING) {
            mState = STATE_COLLAPSED;
        } else {
            mState = ss.state;
        }
    }

    @Override
    public boolean onLayoutChild(@NonNull CoordinatorLayout parent, @NonNull V child, int layoutDirection) {
        if (ViewCompat.getFitsSystemWindows(parent) && !ViewCompat.getFitsSystemWindows(child)) {
            ViewCompat.setFitsSystemWindows(child, true);
        }
        int savedTop = child.getTop();
        parent.onLayoutChild(child, layoutDirection);

        mParentHeight = parent.getHeight();
        int peekHeight;
        // Inside public boolean onLayoutChild(...)

        mExpandedOffset = Math.max(0, mediaPlayerBarHeight); // <-- THIS IS THE FIX
        mAnchorOffset = Math.max(mParentHeight / 2, mExpandedOffset); // Anchor should respect the new expanded offset
        if (mPeekHeightAuto) {
            if (mPeekHeightMin == 0) {
                mPeekHeightMin = parent.getResources().getDimensionPixelSize(com.google.android.material.R.dimen.design_bottom_sheet_peek_height_min);
            }
            peekHeight = Math.max(mPeekHeightMin, mParentHeight - parent.getWidth() * 9 / 16);
        } else {
            peekHeight = mPeekHeight;
        }

        // Corrected calculation to avoid issues where child height is 0 initially
       mCollapsedOffset = Math.max(mParentHeight - peekHeight, mExpandedOffset);

        // Ensure anchor offset is within bounds
        mAnchorOffset = Math.max(mExpandedOffset, mAnchorOffset);

        int top;
        switch (mState) {
            case STATE_EXPANDED:
                top = mExpandedOffset;
                break;
            case STATE_ANCHORED:
                top = mAnchorOffset;
                break;
            case STATE_HIDDEN:
                top = mParentHeight;
                break;
            case STATE_COLLAPSED:
                top = mCollapsedOffset;
                break;
            case STATE_DRAGGING:
            case STATE_SETTLING:
                top = savedTop;
                break;
            default:
                top = mCollapsedOffset; // Default to collapsed
                break;
        }
        ViewCompat.offsetTopAndBottom(child, top);


        if (mViewDragHelper == null) {
            mViewDragHelper = ViewDragHelper.create(parent, mDragCallback);
        }
        mViewRef = new WeakReference<>(child);
        mNestedScrollingChildRef = new WeakReference<>(findScrollingChild(child));
        return true;
    }

    @Override
    public boolean onInterceptTouchEvent(@NonNull CoordinatorLayout parent, @NonNull V child, @NonNull MotionEvent event) {
        return false;

        /**
        if (!child.isShown()) {
            mIgnoreEvents = true;
            return false;
        }
        int action = event.getActionMasked();
        // Record the velocity
        if (action == MotionEvent.ACTION_DOWN) {
            reset();
        }
        if (mVelocityTracker == null) {
            mVelocityTracker = VelocityTracker.obtain();
        }
        mVelocityTracker.addMovement(event);
        switch (action) {
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                mTouchingScrollingChild = false;
                mActivePointerId = MotionEvent.INVALID_POINTER_ID;
                // Reset the ignore flag
                if (mIgnoreEvents) {
                    mIgnoreEvents = false;
                    return false;
                }
                break;
            case MotionEvent.ACTION_DOWN:
                int initialX = (int) event.getX();
                mInitialY = (int) event.getY();
                View scroll = mNestedScrollingChildRef != null ? mNestedScrollingChildRef.get() : null;
                if (scroll != null && parent.isPointInChildBounds(scroll, initialX, mInitialY)) {
                    mActivePointerId = event.getPointerId(event.getActionIndex());
                    mTouchingScrollingChild = true;
                }
                mIgnoreEvents = mActivePointerId == MotionEvent.INVALID_POINTER_ID &&
                        !parent.isPointInChildBounds(child, initialX, mInitialY);
                break;
        }
        if (!mIgnoreEvents && mViewDragHelper.shouldInterceptTouchEvent(event)) {
            return true;
        }
        // We have to handle cases that the ViewDragHelper does not capture the event because the
        // view is not settled in the proper position.
        View scroll = mNestedScrollingChildRef.get();
        return action == MotionEvent.ACTION_MOVE && scroll != null &&
                !mIgnoreEvents && mState != STATE_DRAGGING &&
                !parent.isPointInChildBounds(scroll, (int) event.getX(), (int) event.getY()) &&
                Math.abs(mInitialY - event.getY()) > mViewDragHelper.getTouchSlop();
        **/
    }


    @Override
    public boolean onTouchEvent(@NonNull CoordinatorLayout parent, @NonNull V child, @NonNull MotionEvent event) {
//        if (!child.isShown()) {
//            return false;
//        }
//        int action = event.getActionMasked();
//        if (mState == STATE_DRAGGING && action == MotionEvent.ACTION_DOWN) {
//            return true;
//        }
//        if (mViewDragHelper != null) {
//            mViewDragHelper.processTouchEvent(event);
//        }
//        // Record the velocity
//        if (action == MotionEvent.ACTION_DOWN) {
//            reset();
//        }
//        if (mVelocityTracker == null) {
//            mVelocityTracker = VelocityTracker.obtain();
//        }
//        mVelocityTracker.addMovement(event);
//        // The ViewDragHelper may ignore ACTION_MOVE events if it doesn't find a captured view.
//        if (action == MotionEvent.ACTION_MOVE && !mIgnoreEvents) {
//            if (Math.abs(mInitialY - event.getY()) > mViewDragHelper.getTouchSlop()) {
//                mViewDragHelper.captureChildView(child, event.getPointerId(event.getActionIndex()));
//            }
//        }
//        return !mIgnoreEvents;
        return false;
    }

    @Override
    public boolean onStartNestedScroll(@NonNull CoordinatorLayout coordinatorLayout, @NonNull V child,
                                       @NonNull View directTargetChild, @NonNull View target, int axes, int type) {
        mNestedScrolled = false;
        return (axes & ViewCompat.SCROLL_AXIS_VERTICAL) != 0;
        //return false;
    }

    /**
    @Override
    public void onNestedPreScroll(@NonNull CoordinatorLayout coordinatorLayout, @NonNull V child, @NonNull View target, int dx, int dy,
                                  @NonNull int[] consumed, int type) {
        if (type == ViewCompat.TYPE_NON_TOUCH) {
            // Ignore non-touch scrolls
            return;
        }
        View scrollingChild = mNestedScrollingChildRef.get();
        if (target != scrollingChild) {
            return;
        }
        int currentTop = child.getTop();
        int newTop = currentTop - dy;
        if (dy > 0) { // Upward drag
            if (newTop < mExpandedOffset) {
                consumed[1] = currentTop - mExpandedOffset;
                ViewCompat.offsetTopAndBottom(child, -consumed[1]);
                setStateInternal(STATE_EXPANDED);
            } else {
                consumed[1] = dy;
                ViewCompat.offsetTopAndBottom(child, -dy);
                setStateInternal(STATE_DRAGGING);
            }
        } else if (dy < 0) { // Downward drag
            if (!target.canScrollVertically(-1)) {
                if (newTop <= mCollapsedOffset || mHideable) {
                    consumed[1] = dy;
                    ViewCompat.offsetTopAndBottom(child, -dy);
                    setStateInternal(STATE_DRAGGING);
                } else {
                    consumed[1] = currentTop - mCollapsedOffset;
                    ViewCompat.offsetTopAndBottom(child, -consumed[1]);
                    setStateInternal(STATE_COLLAPSED);
                }
            }
        }
        dispatchOnSlide(child.getTop());
        mNestedScrolled = true;
    }


    @Override
    public void onStopNestedScroll(@NonNull CoordinatorLayout coordinatorLayout, @NonNull V child,
                                   @NonNull View target, int type) {
        if (mNestedScrollingChildRef == null || target != mNestedScrollingChildRef.get() || !mNestedScrolled) {
            return;
        }
        // Since there's no velocity (yvel), we just snap to the nearest point.
        // The stopDragging method with yvel=0 handles this perfectly.
        stopDragging(child, 0f);
        mNestedScrolled = false;
    }


    @Override
    public boolean onNestedPreFling(@NonNull CoordinatorLayout coordinatorLayout, @NonNull V child, @NonNull View target,
                                    float velocityX, float velocityY) {
        return target == mNestedScrollingChildRef.get() &&
                (mState != STATE_EXPANDED || super.onNestedPreFling(coordinatorLayout, child, target, velocityX, velocityY));
    }

    **/

    /**
     * Sets the height of the bottom sheet when it is collapsed.
     *
     * @param peekHeight The height of the collapsed bottom sheet in pixels, or
     *                   {@link #PEEK_HEIGHT_AUTO} to configure the sheet to peek automatically
     *                   at 16:9 ratio keyline.
     * @attr ref com.google.android.material.R.styleable#BottomSheetBehavior_Layout_behavior_peekHeight
     */
    public final void setPeekHeight(int peekHeight) {
        boolean layout = false;
        if (peekHeight == PEEK_HEIGHT_AUTO) {
            if (!mPeekHeightAuto) {
                mPeekHeightAuto = true;
                layout = true;
            }
        } else if (mPeekHeightAuto || mPeekHeight != peekHeight) {
            mPeekHeightAuto = false;
            mPeekHeight = Math.max(0, peekHeight);
            mCollapsedOffset = mParentHeight - peekHeight;
            layout = true;
        }
        if (layout && mState == STATE_COLLAPSED && mViewRef != null) {
            V view = mViewRef.get();
            if (view != null) {
                view.requestLayout();
            }
        }
    }

    /**
     * Gets the height of the bottom sheet when it is collapsed.
     *
     * @return The height of the collapsed bottom sheet in pixels, or {@link #PEEK_HEIGHT_AUTO}
     * if the sheet is configured to peek automatically at 16:9 ratio keyline.
     * @attr ref com.google.android.material.R.styleable#BottomSheetBehavior_Layout_behavior_peekHeight
     */
    public final int getPeekHeight() {
        return mPeekHeightAuto ? PEEK_HEIGHT_AUTO : mPeekHeight;
    }

    /**
     * Sets whether this bottom sheet can be hidden by dragging it down.
     *
     * @param hideable {@code true} to make this bottom sheet hideable.
     * @attr ref com.google.android.material.R.styleable#BottomSheetBehavior_Layout_behavior_hideable
     */
    public void setHideable(boolean hideable) {
        mHideable = hideable;
    }

    /**
     * Gets whether this bottom sheet can be hidden by dragging it down.
     *
     * @return {@code true} if this bottom sheet can be hidden.
     * @attr ref com.google.android.material.R.styleable#BottomSheetBehavior_Layout_behavior_hideable
     */
    public boolean isHideable() {
        return mHideable;
    }

    /**
     * Sets whether this bottom sheet should skip the collapsed state when it is being dragged
     * up or down.
     *
     * @param skipCollapsed True if the bottom sheet should skip the collapsed state.
     * @attr ref com.google.android.material.R.styleable#BottomSheetBehavior_Layout_behavior_skipCollapsed
     */
    public void setSkipCollapsed(boolean skipCollapsed) {
        mSkipCollapsed = skipCollapsed;
    }

    /**
     * Gets whether this bottom sheet should skip the collapsed state when it is being dragged
     * up or down.
     *
     * @return True if the bottom sheet should skip the collapsed state.
     * @attr ref com.google.android.material.R.styleable#BottomSheetBehavior_Layout_behavior_skipCollapsed
     */
    public boolean getSkipCollapsed() {
        return mSkipCollapsed;
    }

    /**
     * Sets whether this bottom sheet should skip the anchored state when it is being dragged
     * up or down.
     *
     * @param skipAnchored True if the bottom sheet should skip the anchored state.
     */
    public void setSkipAnchored(boolean skipAnchored) {
        mSkipAnchored = skipAnchored;
    }

    public void setMediaPlayerBarHeight(int height) {
        this.mediaPlayerBarHeight = height;
    }

    /**
     * Gets whether this bottom sheet should skip the anchored state when it is being dragged
     * up or down.
     *
     * @return True if the bottom sheet should skip the anchored state.
     */
    public boolean getSkipAnchored() {
        return mSkipAnchored;
    }

    /**
     * Adds a callback to be notified of bottom sheet events.
     *
     * @param callback The callback to notify when bottom sheet events occur.
     */
    public void addBottomSheetCallback(@NonNull BottomSheetCallback callback) {
        if (!mCallbacks.contains(callback)) {
            mCallbacks.add(callback);
        }
    }

    /**
     * Removes a previously added callback.
     *
     * @param callback The callback to remove.
     */
    public void removeBottomSheetCallback(@NonNull BottomSheetCallback callback) {
        mCallbacks.remove(callback);
    }

    /**
     * Sets the state of the bottom sheet. The bottom sheet will transition to that state with
     * animation.
     *
     * @param state One of {@link #STATE_COLLAPSED}, {@link #STATE_EXPANDED},
     *              {@link #STATE_HIDDEN}, or {@link #STATE_ANCHORED}.
     */
    public final void setState(@State int state) {
        if (state == mState) {
            return;
        }
        if (mViewRef == null) {
            // The view is not laid out yet; modify mState and let onLayoutChild handle it later
            if (state == STATE_COLLAPSED || state == STATE_EXPANDED || state == STATE_ANCHORED ||
                    (mHideable && state == STATE_HIDDEN)) {
                mState = state;
            }
            return;
        }
        V child = mViewRef.get();
        if (child == null) {
            return;
        }
        // Start the animation; wait for layout if not laid out yet
        ViewParent parent = child.getParent();
        if (parent != null && parent.isLayoutRequested() && ViewCompat.isAttachedToWindow(child)) {
            child.post(() -> startSettlingAnimation(child, state));
        } else {
            startSettlingAnimation(child, state);
        }
    }

    /**
     * Gets the current state of the bottom sheet.
     *
     * @return One of {@link #STATE_EXPANDED}, {@link #STATE_COLLAPSED}, {@link #STATE_DRAGGING},
     * {@link #STATE_SETTLING}, {@link #STATE_HIDDEN}, or {@link #STATE_ANCHORED}.
     */
    @State
    public final int getState() {
        return mState;
    }

    void setStateInternal(@State int state) {
        if (mState == state) {
            return;
        }
        int oldState = mState;
        mState = state;
        View bottomSheet = mViewRef.get();
        if (bottomSheet != null && !mCallbacks.isEmpty()) {
            for (int i = 0; i < mCallbacks.size(); i++) {
                mCallbacks.get(i).onStateChanged(bottomSheet, oldState, state);
            }
        }
    }

    private void reset() {
        mActivePointerId = ViewDragHelper.INVALID_POINTER;
        if (mVelocityTracker != null) {
            mVelocityTracker.recycle();
            mVelocityTracker = null;
        }
    }

    boolean shouldHide(View child, float yvel) {
        if (mSkipCollapsed) {
            return true;
        }
        if (child.getTop() < mCollapsedOffset) {
            // It should not hide, but collapse.
            return false;
        }
        final float newTop = child.getTop() + yvel * HIDE_FRICTION;
        return Math.abs(newTop - mCollapsedOffset) / (float) mPeekHeight > HIDE_THRESHOLD;
    }

    @VisibleForTesting
    View findScrollingChild(View view) {
        if (ViewCompat.isNestedScrollingEnabled(view)) {
            return view;
        }
        if (view instanceof ViewPager) {
            ViewPager viewPager = (ViewPager) view;
            View currentViewPagerChild = getCurrentView(viewPager);
            if (currentViewPagerChild == null) {
                return null;
            }
            View scrollingChild = findScrollingChild(currentViewPagerChild);
            if (scrollingChild != null) {
                return scrollingChild;
            }
        } else if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0, count = group.getChildCount(); i < count; i++) {
                View scrollingChild = findScrollingChild(group.getChildAt(i));
                if (scrollingChild != null) {
                    return scrollingChild;
                }
            }
        }
        return null;
    }

    private View getCurrentView(ViewPager viewPager) {
        final int currentItem = viewPager.getCurrentItem();
        if (viewPager.getAdapter() == null) {
            return null;
        }
        for (int i = 0; i < viewPager.getChildCount(); i++) {
            final View child = viewPager.getChildAt(i);
            final ViewPager.LayoutParams layoutParams = (ViewPager.LayoutParams) child.getLayoutParams();
            if (!layoutParams.isDecor) {
                // This does not work well.
            }
        }
        return null; // Can't get the view unfortunately
    }


    void startSettlingAnimation(View child, int state) {
        int top;
        if (state == STATE_COLLAPSED) {
            top = mCollapsedOffset;
        } else if (state == STATE_ANCHORED) {
            top = mAnchorOffset;
        } else if (state == STATE_EXPANDED) {
            top = mExpandedOffset;
        } else if (mHideable && state == STATE_HIDDEN) {
            top = mParentHeight;
        } else {
            throw new IllegalArgumentException("Illegal state argument: " + state);
        }
        if (mViewDragHelper.smoothSlideViewTo(child, child.getLeft(), top)) {
            setStateInternal(STATE_SETTLING);
            ViewCompat.postOnAnimation(child, new SettleRunnable(child, state));
        } else {
            setStateInternal(state);
        }
    }

    private final ViewDragHelper.Callback mDragCallback = new ViewDragHelper.Callback() {
        @Override
        public boolean tryCaptureView(@NonNull View child, int pointerId) {
            if (!mAllowUserDragging || mState == STATE_DRAGGING || mTouchingScrollingChild) {
                return false;
            }
            if (mState == STATE_EXPANDED && mActivePointerId == pointerId) {
                View scroll = mNestedScrollingChildRef != null ? mNestedScrollingChildRef.get() : null;
                if (scroll != null && scroll.canScrollVertically(-1)) {
                    return false;
                }
            }
            return mViewRef != null && mViewRef.get() == child;
        }

        @Override
        public void onViewPositionChanged(@NonNull View changedView, int left, int top, int dx, int dy) {
            dispatchOnSlide(top);
        }

        @Override
        public void onViewDragStateChanged(int state) {
            if (state == ViewDragHelper.STATE_DRAGGING) {
                setStateInternal(STATE_DRAGGING);
            }
        }

        // REPLACE the entire method in your CustomBottomSheetBehavior.java

        @Override
        public void onViewReleased(@NonNull View releasedChild, float xvel, float yvel) {
           stopDragging(releasedChild, yvel);
        }


        // REPLACE the entire method in your CustomBottomSheetBehavior.java

        @Override
        public int clampViewPositionVertical(@NonNull View child, int top, int dy) {

            int bottomBound = mHideable ? mParentHeight : mCollapsedOffset;

            return Math.max(mExpandedOffset, Math.min(top, bottomBound));
        }


        @Override
        public int clampViewPositionHorizontal(@NonNull View child, int left, int dx) {
            return child.getLeft();
        }

        @Override
        public int getViewVerticalDragRange(@NonNull View child) {
            return mHideable ? mParentHeight - mExpandedOffset : mCollapsedOffset - mExpandedOffset;
        }
    };

    private void stopDragging(@NonNull View child, float yvel) {
        int top;
        @State int targetState;

        // Determine target state based on velocity and position
        if (yvel < -HIDE_THRESHOLD) { // Strong fling up
            top = mExpandedOffset;
            targetState = STATE_EXPANDED;
        } else if (yvel > HIDE_THRESHOLD) { // Strong fling down
            top = mCollapsedOffset;
            targetState = STATE_COLLAPSED;
        } else { // No significant fling, snap to the nearest point
            int currentTop = child.getTop();

            // Find the distance to each snap point
            int distExpanded = Math.abs(currentTop - mExpandedOffset);
            int distAnchor = Math.abs(currentTop - mAnchorOffset);
            int distCollapsed = Math.abs(currentTop - mCollapsedOffset);

            // Find which distance is the smallest
            if (distExpanded <= distAnchor && distExpanded <= distCollapsed) {
                top = mExpandedOffset;
                targetState = STATE_EXPANDED;
            } else if (distAnchor < distExpanded && distAnchor < distCollapsed) {
                top = mAnchorOffset;
                targetState = STATE_ANCHORED;
            } else {
                top = mCollapsedOffset;
                targetState = STATE_COLLAPSED;
            }
        }

        // Settle the view at the calculated target position
        if (mViewDragHelper.smoothSlideViewTo(child, child.getLeft(), top)) {
            setStateInternal(STATE_SETTLING);
            ViewCompat.postOnAnimation(child, new SettleRunnable(child, targetState));
        } else {
            setStateInternal(targetState);
        }
    }



    private int constrain(int amount, int low, int high) {
        return amount < low ? low : (Math.min(amount, high));
    }

    public void setAllowUserDragging(boolean allowUserDragging) {
        mAllowUserDragging = allowUserDragging;
    }

// Add this method to your CustomBottomSheetBehavior.java class

    @Override
    public boolean onDependentViewChanged(@NonNull CoordinatorLayout parent, @NonNull V child, @NonNull View dependency) {
        // This line is crucial. It tells the CoordinatorLayout that a change in the
        // bottom sheet's position should trigger a redraw of its dependencies (the views behind it).
        return true;
    }


    public void setAnchorOffset(int anchorOffset) {
        if (mAnchorOffset != anchorOffset) {
            mAnchorOffset = anchorOffset;
            if (mViewRef != null && mViewRef.get() != null) {
                mViewRef.get().requestLayout();
            }
        }
    }

    public int getAnchorOffset() {
        return mAnchorOffset;
    }

    void dispatchOnSlide(int top) {
        View bottomSheet = mViewRef.get();
        if (bottomSheet != null && !mCallbacks.isEmpty()) {
            float slideOffset;
            if (top > mCollapsedOffset) {
                slideOffset = (float) (mCollapsedOffset - top) / (mParentHeight - mCollapsedOffset);
            } else {
                slideOffset = (float) (mCollapsedOffset - top) / (mCollapsedOffset - mExpandedOffset);
            }

            for (int i = 0; i < mCallbacks.size(); i++) {
                mCallbacks.get(i).onSlide(bottomSheet, slideOffset);
            }
        }
    }


    private class SettleRunnable implements Runnable {

        private final View mView;

        @State
        private final int mTargetState;

        SettleRunnable(View view, @State int targetState) {
            mView = view;
            mTargetState = targetState;
        }

        @Override
        public void run() {
            if (mViewDragHelper != null && mViewDragHelper.continueSettling(true)) {
                ViewCompat.postOnAnimation(mView, this);
            } else {
                setStateInternal(mTargetState);
            }
        }
    }

    protected static class SavedState extends AbsSavedState {
        @State
        final int state;

        public SavedState(Parcel source) {
            this(source, null);
        }

        public SavedState(Parcel source, ClassLoader loader) {
            super(source, loader);
            //noinspection ResourceType
            state = source.readInt();
        }

        public SavedState(Parcelable superState, @State int state) {
            super(superState);
            this.state = state;
        }

        @Override
        public void writeToParcel(Parcel out, int flags) {
            super.writeToParcel(out, flags);
            out.writeInt(state);
        }

        public static final Creator<SavedState> CREATOR = new ClassLoaderCreator<SavedState>() {
            @Override
            public SavedState createFromParcel(Parcel in, ClassLoader loader) {
                return new SavedState(in, loader);
            }

            @Override
            public SavedState createFromParcel(Parcel in) {
                return new SavedState(in, null);
            }

            @Override
            public SavedState[] newArray(int size) {
                return new SavedState[size];
            }
        };
    }

    /**
     * A utility function to get the instance of the {@link CustomBottomSheetBehavior} from a view.
     *
     * @param view The view to search from.
     * @param <V>  The type of the view.
     * @return The {@link CustomBottomSheetBehavior} associated with the view.
     */
    @SuppressWarnings("unchecked")
    public static <V extends View> CustomBottomSheetBehavior<V> from(V view) {
        ViewGroup.LayoutParams params = view.getLayoutParams();
        if (!(params instanceof CoordinatorLayout.LayoutParams)) {
            throw new IllegalArgumentException("The view is not a direct child of CoordinatorLayout");
        }
        CoordinatorLayout.Behavior behavior = ((CoordinatorLayout.LayoutParams) params).getBehavior();
        if (!(behavior instanceof CustomBottomSheetBehavior)) {
            throw new IllegalArgumentException("The view is not associated with CustomBottomSheetBehavior");
        }
        return (CustomBottomSheetBehavior<V>) behavior;
    }
}
