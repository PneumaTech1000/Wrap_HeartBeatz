package com.giga.tech1000.heartbeatz.views.panels;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.media3.common.util.UnstableApi;
import androidx.navigation.NavController;
import androidx.navigation.fragment.NavHostFragment;
import androidx.navigation.ui.NavigationUI;

import com.giga.tech1000.heartbeatz.R;
import com.giga.tech1000.heartbeatz.ui.UIInfoLog;
import com.giga.tech1000.heartbeatz.ui.UIThread;
import com.giga.tech1000.utils.interfaces.DisplayMarginCallback;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.realgear.multislidinguppanel.BasePanelView;
import com.realgear.multislidinguppanel.MultiSlidingUpPanelLayout;

@SuppressLint("ViewConstructor")
@UnstableApi
public class RootNavigationBarPanel extends BasePanelView {

    private BottomNavigationView navigationBar;
    private NavController navController;
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
                        // Only care about fragments inside NavHost
                        Fragment navHost = getSupportFragmentManager().findFragmentById(R.id.root_container_view);

                        if (!(navHost instanceof NavHostFragment host)) return;

                        Fragment current = host.getChildFragmentManager().getPrimaryNavigationFragment();

                        if (f == current) {
                            activeFragment = f;
                            // Sync padding state with the actual current player state from UIThread
                            updatePaddingWhenWhenBarChanged(UIThread.getInstance().isPlayerBarVisible());
                        }
                    }
                },
                true
        );

    }

    @Override
    public void onBindView() {
        // 1. Find the NavHostFragment safely
        Fragment fragment = getSupportFragmentManager().findFragmentById(R.id.root_container_view);
        if (!(fragment instanceof NavHostFragment navHostFragment)) {
            throw new IllegalStateException("NavHostFragment not found. Check your XML layout.");
        }

        // 2. Get NavController
        navController = navHostFragment.getNavController();

        // 3. Hook up BottomNavigationView with NavController
        navigationBar = findViewById(R.id.root_navigation_bar);
        NavigationUI.setupWithNavController(navigationBar, navController);

        navigationBar.setOnItemSelectedListener(item -> {
            return NavigationUI.onNavDestinationSelected(item, navController);
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

    public NavController getNavController() { return navController; }

}
