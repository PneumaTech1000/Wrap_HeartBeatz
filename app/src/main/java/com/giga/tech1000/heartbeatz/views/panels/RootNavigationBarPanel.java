package com.giga.tech1000.heartbeatz.views.panels;

import android.annotation.SuppressLint;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.MenuItem;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;
import androidx.lifecycle.Lifecycle;
import androidx.media3.common.util.UnstableApi;

import com.giga.tech1000.heartbeatz.R;
import com.giga.tech1000.heartbeatz.ui.UIInfoLog;
import com.giga.tech1000.heartbeatz.ui.fragments.FragmentHome;
import com.giga.tech1000.heartbeatz.ui.fragments.FragmentParty;
import com.giga.tech1000.utils.interfaces.DisplayMarginCallback;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.navigation.NavigationBarView;
import com.realgear.multislidinguppanel.BasePanelView;
import com.realgear.multislidinguppanel.MultiSlidingUpPanelLayout;

/**
 * Bottom navigation as a MultiSlidingUpPanel floor (peak = nav height).
 * Primary tabs use show/hide (no fragment destroy).
 */
@SuppressLint("ViewConstructor")
@UnstableApi
public class RootNavigationBarPanel extends BasePanelView {

    private static final String TAG_HOME = "tab_home";
    private static final String TAG_PARTY = "tab_party";

    private BottomNavigationView navigationBar;
    private int currentTabId = R.id.nav_home;
    @Nullable
    private Fragment activeFragment;

    public RootNavigationBarPanel(@NonNull Context context, MultiSlidingUpPanelLayout panelLayout) {
        super(context, panelLayout);
        getContext().setTheme(R.style.Theme_HeartBeatz);
        LayoutInflater.from(getContext()).inflate(R.layout.navigation_bar_root_layout, this, true);
    }

    @Override
    public void onCreateView() {
        setPeakHeight(getResources().getDimensionPixelSize(R.dimen.navigation_bar_height));
        setUserHiddenMode(false);
        isHidden = false;
        setPanelState(MultiSlidingUpPanelLayout.COLLAPSED);
        setSlideDirection(MultiSlidingUpPanelLayout.SLIDE_VERTICAL);
        UIInfoLog.d("RootNav.onCreateView", "COLLAPSED peak=" + getPeakHeight());
    }

    @Override
    public void onBindView() {
        navigationBar = findViewById(R.id.root_navigation_bar);
        if (navigationBar == null) return;
        navigationBar.setLabelVisibilityMode(NavigationBarView.LABEL_VISIBILITY_LABELED);

        ensureTabsAttached();
        selectTab(R.id.nav_home, false);

        navigationBar.setOnItemSelectedListener(item -> {
            int destId = item.getItemId();
            if (destId == currentTabId) return true;
            selectTab(destId, true);
            return true;
        });

        getSupportFragmentManager().registerFragmentLifecycleCallbacks(
                new FragmentManager.FragmentLifecycleCallbacks() {
                    @Override
                    public void onFragmentResumed(@NonNull FragmentManager fm, @NonNull Fragment f) {
                        if (f instanceof FragmentHome || f instanceof FragmentParty) {
                            activeFragment = f;
                        }
                    }
                }, true);

        updatePaddingWhenWhenBarChanged(false);
    }

    private void ensureTabsAttached() {
        FragmentManager fm = getSupportFragmentManager();
        Fragment home = fm.findFragmentByTag(TAG_HOME);
        Fragment party = fm.findFragmentByTag(TAG_PARTY);
        FragmentTransaction tx = fm.beginTransaction();
        if (home == null) {
            home = new FragmentHome();
            tx.add(R.id.root_container_view, home, TAG_HOME);
        }
        if (party == null) {
            party = new FragmentParty();
            tx.add(R.id.root_container_view, party, TAG_PARTY);
            tx.hide(party);
        }
        tx.commitNowAllowingStateLoss();
        UIInfoLog.d("RootNav.tabs", "attached home+party (show/hide)");
    }

    public void selectTab(int tabId) {
        selectTab(tabId, true);
        if (navigationBar != null) {
            MenuItem item = navigationBar.getMenu().findItem(tabId);
            if (item != null) item.setChecked(true);
        }
    }

    public void selectTab(int tabId, boolean animate) {
        FragmentManager fm = getSupportFragmentManager();
        Fragment home = fm.findFragmentByTag(TAG_HOME);
        Fragment party = fm.findFragmentByTag(TAG_PARTY);
        if (home == null || party == null) {
            ensureTabsAttached();
            home = fm.findFragmentByTag(TAG_HOME);
            party = fm.findFragmentByTag(TAG_PARTY);
        }
        FragmentTransaction tx = fm.beginTransaction();
        if (tabId == R.id.nav_party) {
            if (home != null) tx.hide(home);
            if (party != null) {
                tx.show(party);
                tx.setMaxLifecycle(party, Lifecycle.State.RESUMED);
            }
            if (home != null) tx.setMaxLifecycle(home, Lifecycle.State.STARTED);
            activeFragment = party;
        } else {
            if (party != null) tx.hide(party);
            if (home != null) {
                tx.show(home);
                tx.setMaxLifecycle(home, Lifecycle.State.RESUMED);
            }
            if (party != null) tx.setMaxLifecycle(party, Lifecycle.State.STARTED);
            activeFragment = home;
            tabId = R.id.nav_home;
        }
        tx.commitNowAllowingStateLoss();
        currentTabId = tabId;
        UIInfoLog.d("RootNav.selectTab", "tab=" + tabId
                + " active=" + (activeFragment != null ? activeFragment.getClass().getSimpleName() : "null"));
        updatePaddingWhenWhenBarChanged(
                activeFragment instanceof DisplayMarginCallback);
        // Re-apply with real player visibility after bind
        updatePaddingWhenWhenBarChanged(false);
    }

    public int getCurrentTabId() {
        return currentTabId;
    }

    @Nullable
    public Fragment getActiveFragment() {
        return activeFragment;
    }

    public void updatePaddingWhenWhenBarChanged(boolean isDisplaying) {
        UIInfoLog.d("RootNav.updatePadding", "isDisplaying=" + isDisplaying
                + " activeFragment=" + (activeFragment != null ? activeFragment.getClass().getSimpleName() : "null"));
        if (activeFragment instanceof DisplayMarginCallback listener) {
            listener.onDisplayBarPlayerChanged(isDisplaying);
        }
    }

    @Override
    public void onPanelStateChanged(int i) {
        UIInfoLog.d("RootNav.onPanelStateChanged", "state=" + UIInfoLog.stateName(i)
                + " isHidden=" + isUserHidden());
    }
}
