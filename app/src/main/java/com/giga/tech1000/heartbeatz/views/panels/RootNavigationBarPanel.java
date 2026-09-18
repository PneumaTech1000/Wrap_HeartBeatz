package com.giga.tech1000.heartbeatz.views.panels;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;
import androidx.lifecycle.Lifecycle;
import androidx.media3.common.util.UnstableApi;

import com.giga.tech1000.heartbeatz.R;
import com.giga.tech1000.heartbeatz.ui.UIInfoLog;
import com.giga.tech1000.heartbeatz.ui.UIThread;
import com.giga.tech1000.heartbeatz.ui.fragments.FragmentHome;
import com.giga.tech1000.heartbeatz.ui.fragments.FragmentParty;
import com.giga.tech1000.utils.interfaces.DisplayMarginCallback;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.realgear.multislidinguppanel.BasePanelView;
import com.realgear.multislidinguppanel.MultiSlidingUpPanelLayout;

@SuppressLint("ViewConstructor")
@UnstableApi
public class RootNavigationBarPanel extends BasePanelView {

    private BottomNavigationView navigationBar;
    private int currentTabId = R.id.nav_home;
    private Fragment activeFragment;
    private final Context context;


    public RootNavigationBarPanel(@NonNull Context context, MultiSlidingUpPanelLayout panelLayout) {
        super(context, panelLayout);
        this.context = context;

        // These 2 lines are required
        getContext().setTheme(R.style.Theme_HeartBeatz); // Your projects theme
        LayoutInflater.from(getContext()).inflate(R.layout.navigation_bar_root_layout, this, true);
    }

    @Override
    public void onCreateView() {
        // Peak height MUST be set before setPanelState (collapsedH uses peak)
        this.setPeakHeight(getResources().getDimensionPixelSize(R.dimen.navigation_bar_height));

        // Programmatic hide when full player expands is OK; user swipe-to-hide is not
        // (logs: user dragged nav to HIDDEN → mini sat wrong).
        this.setUserHiddenMode(false);

        this.isHidden = false;
        this.setPanelState(MultiSlidingUpPanelLayout.COLLAPSED);

        this.setSlideDirection(MultiSlidingUpPanelLayout.SLIDE_VERTICAL);

        UIInfoLog.d("RootNav.onCreateView", "COLLAPSED isHidden=false peak=" + getPeakHeight());

        getSupportFragmentManager().registerFragmentLifecycleCallbacks(
                new FragmentManager.FragmentLifecycleCallbacks() {
                    @Override
                    public void onFragmentViewCreated(
                            @NonNull FragmentManager fm,
                            @NonNull Fragment f,
                            @NonNull View v,
                            @Nullable Bundle savedInstanceState
                    ) {
                        String tag = f.getTag();
                        if (TAG_HOME.equals(tag) || TAG_PARTY.equals(tag)) {
                            if (!f.isHidden()) {
                                activeFragment = f;
                                updatePaddingWhenWhenBarChanged(
                                        UIThread.getInstance().isPlayerBarVisible());
                            }
                        }
                    }
                },
                false
        );

    }

    @Override
    public void onBindView() {
        navigationBar = findViewById(R.id.root_navigation_bar);

        // Show/hide tabs: keep FragmentHome & FragmentParty alive (no destroy on switch).
        ensureTabsAttached();
        selectTab(R.id.nav_home, false);

        navigationBar.setOnItemSelectedListener(item -> {
            long t0 = android.os.SystemClock.elapsedRealtime();
            int destId = item.getItemId();
            if (destId == currentTabId) {
                UIInfoLog.d("RootNav.nav", "already on tab=" + destId);
                return true;
            }
            selectTab(destId, true);
            UIInfoLog.d("RootNav.nav", "showHide tab=" + destId
                    + " ms=" + (android.os.SystemClock.elapsedRealtime() - t0));
            return true;
        });

        // Initial sync
        updatePaddingWhenWhenBarChanged(UIThread.getInstance().isPlayerBarVisible());

    }

    @Override
    public void onPanelStateChanged(int i) {
        UIInfoLog.d("RootNav.onPanelStateChanged", "state=" + UIInfoLog.stateName(i)
                + " isHidden=" + isUserHidden()
                + " top=" + getTop() + " bottom=" + getBottom());
        UIInfoLog.panelSnapshot("RootNav.onPanelStateChanged", this);
    }

    public void updatePaddingWhenWhenBarChanged(boolean isDisplaying) {
        UIInfoLog.d("RootNav.updatePadding", "isDisplaying=" + isDisplaying
                + " activeFragment=" + (activeFragment != null ? activeFragment.getClass().getSimpleName() : "null")
                + " navState=" + UIInfoLog.stateName(getPanelState())
                + " isHidden=" + isUserHidden()
                + " top=" + getTop() + " bottom=" + getBottom()
                + " peak=" + getPeakHeight()
                + " collapsedH=" + getPanelCollapsedHeight());
        if (activeFragment instanceof DisplayMarginCallback listener) {
            listener.onDisplayBarPlayerChanged(isDisplaying);
        }
    }

    public Fragment getActiveFragment() { return activeFragment; }

    /** @deprecated Tabs no longer use NavController; prefer {@link #selectTab(int)}. */
    @Nullable
    public Object getNavController() { return null; }

    public int getCurrentTabId() { return currentTabId; }

    public void selectTab(int tabId) {
        selectTab(tabId, true);
    }

    private void ensureTabsAttached() {
        FragmentManager fm = getSupportFragmentManager();
        Fragment home = fm.findFragmentByTag(TAG_HOME);
        Fragment party = fm.findFragmentByTag(TAG_PARTY);
        FragmentTransaction ft = fm.beginTransaction();
        boolean changed = false;
        if (home == null) {
            home = new FragmentHome();
            ft.add(R.id.root_container_view, home, TAG_HOME);
            changed = true;
        }
        if (party == null) {
            party = new FragmentParty();
            ft.add(R.id.root_container_view, party, TAG_PARTY);
            ft.hide(party);
            changed = true;
        }
        if (changed) {
            ft.setReorderingAllowed(true);
            ft.commitNowAllowingStateLoss();
            UIInfoLog.d("RootNav.tabs", "attached home+party (show/hide)");
        }
    }

    private void selectTab(int tabId, boolean updateMenu) {
        FragmentManager fm = getSupportFragmentManager();
        Fragment home = fm.findFragmentByTag(TAG_HOME);
        Fragment party = fm.findFragmentByTag(TAG_PARTY);
        if (home == null || party == null) {
            ensureTabsAttached();
            home = fm.findFragmentByTag(TAG_HOME);
            party = fm.findFragmentByTag(TAG_PARTY);
        }
        if (home == null || party == null) return;

        FragmentTransaction ft = fm.beginTransaction().setReorderingAllowed(true);
        if (tabId == R.id.nav_party) {
            ft.hide(home).setMaxLifecycle(home, Lifecycle.State.STARTED);
            ft.show(party).setMaxLifecycle(party, Lifecycle.State.RESUMED);
            activeFragment = party;
        } else {
            ft.hide(party).setMaxLifecycle(party, Lifecycle.State.STARTED);
            ft.show(home).setMaxLifecycle(home, Lifecycle.State.RESUMED);
            activeFragment = home;
            tabId = R.id.nav_home;
        }
        ft.commitNowAllowingStateLoss();
        currentTabId = tabId;
        if (updateMenu && navigationBar != null) {
            Menu menu = navigationBar.getMenu();
            for (int i = 0; i < menu.size(); i++) {
                MenuItem mi = menu.getItem(i);
                mi.setChecked(mi.getItemId() == currentTabId);
            }
        }
        updatePaddingWhenWhenBarChanged(UIThread.getInstance().isPlayerBarVisible());
        UIInfoLog.d("RootNav.selectTab", "tab=" + currentTabId
                + " active=" + activeFragment.getClass().getSimpleName()
                + " (no destroy)");
    }

    private static final String TAG_HOME = "tab_home";
    private static final String TAG_PARTY = "tab_party";


}
