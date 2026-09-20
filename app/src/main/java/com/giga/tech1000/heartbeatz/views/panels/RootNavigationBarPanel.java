package com.giga.tech1000.heartbeatz.views.panels;

import android.view.Menu;
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
import com.google.android.material.bottomnavigation.LabelVisibilityMode;

/**
 * Bottom navigation + primary tab show/hide. Not a sliding panel —
 * the bar lives in {@code activity_main} under Material layout gravity.
 */
@UnstableApi
public class RootNavigationBarPanel {

    private static final String TAG_HOME = "tab_home";
    private static final String TAG_PARTY = "tab_party";

    private final AppCompatActivity activity;
    private final BottomNavigationView navigationBar;
    private int currentTabId = R.id.nav_home;
    @Nullable
    private Fragment activeFragment;

    public RootNavigationBarPanel(
            @NonNull AppCompatActivity activity,
            @NonNull BottomNavigationView navigationBar) {
        this.activity = activity;
        this.navigationBar = navigationBar;
        navigationBar.setLabelVisibilityMode(LabelVisibilityMode.LABEL_VISIBILITY_LABELED);
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

        activity.getSupportFragmentManager().registerFragmentLifecycleCallbacks(
                new FragmentManager.FragmentLifecycleCallbacks() {
                    @Override
                    public void onFragmentViewCreated(
                            @NonNull FragmentManager fm,
                            @NonNull Fragment f,
                            @NonNull android.view.View v,
                            @Nullable android.os.Bundle savedInstanceState) {
                        String tag = f.getTag();
                        if (TAG_HOME.equals(tag) || TAG_PARTY.equals(tag)) {
                            if (!f.isHidden()) {
                                activeFragment = f;
                                updatePaddingWhenWhenBarChanged(
                                        com.giga.tech1000.heartbeatz.ui.UIThreadBridgePad.isPlayerBarVisible());
                            }
                        }
                    }
                },
                false);

        updatePaddingWhenWhenBarChanged(
                com.giga.tech1000.heartbeatz.ui.UIThreadBridgePad.isPlayerBarVisible());
    }

    public void updatePaddingWhenWhenBarChanged(boolean isDisplaying) {
        UIInfoLog.d("RootNav.updatePadding", "isDisplaying=" + isDisplaying
                + " activeFragment=" + (activeFragment != null
                ? activeFragment.getClass().getSimpleName() : "null"));
        if (activeFragment instanceof DisplayMarginCallback listener) {
            listener.onDisplayBarPlayerChanged(isDisplaying);
        }
    }

    @Nullable
    public Fragment getActiveFragment() {
        return activeFragment;
    }

    public int getCurrentTabId() {
        return currentTabId;
    }

    public void selectTab(int tabId) {
        selectTab(tabId, true);
    }

    private void ensureTabsAttached() {
        FragmentManager fm = activity.getSupportFragmentManager();
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
        FragmentManager fm = activity.getSupportFragmentManager();
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
        if (updateMenu) {
            Menu menu = navigationBar.getMenu();
            for (int i = 0; i < menu.size(); i++) {
                MenuItem mi = menu.getItem(i);
                mi.setChecked(mi.getItemId() == currentTabId);
            }
        }
        updatePaddingWhenWhenBarChanged(
                com.giga.tech1000.heartbeatz.ui.UIThreadBridgePad.isPlayerBarVisible());
        UIInfoLog.d("RootNav.selectTab", "tab=" + currentTabId
                + " active=" + (activeFragment != null
                ? activeFragment.getClass().getSimpleName() : "null")
                + " (no destroy)");
    }
}
