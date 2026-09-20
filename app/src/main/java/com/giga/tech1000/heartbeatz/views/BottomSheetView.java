package com.giga.tech1000.heartbeatz.views;

import com.giga.tech1000.heartbeatz.app_worker.HeartBeatzApp;

import android.net.nsd.NsdServiceInfo;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageButton;

import androidx.annotation.IdRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.MutableLiveData;
import androidx.viewpager2.widget.ViewPager2;

import com.giga.tech1000.extensions.bottom_sheet.CustomBottomSheetBehavior;
import com.giga.tech1000.heartbeatz.R;
import com.giga.tech1000.heartbeatz.ui.UIThread;
import com.giga.tech1000.heartbeatz.ui.adapters.StateFragmentAdapter;
import com.giga.tech1000.heartbeatz.ui.fragments.FragmentBottomSheetLyrics;
import com.giga.tech1000.heartbeatz.ui.fragments.FragmentBottomSheetQueue;
import com.giga.tech1000.heartbeatz.views.panels.RootMediaPlayerPanel;
import com.giga.tech1000.utils.interfaces.BottomSheetQueueAndIndexUpdate;
import com.realgear.readable_bottom_bar.ReadableBottomBar;

import java.util.List;

public class BottomSheetView {
    private final View rootView;
    private final RootMediaPlayerPanel panel;
    private final CustomBottomSheetBehavior<FrameLayout> bottomSheetBehavior;
    private ViewPager2 viewPager;
    private final ReadableBottomBar bottomBar;
    private final ImageButton closeBottomSheet;
    private final BottomSheetQueueAndIndexUpdate playlistCallback;

    private final StateFragmentAdapter adapter;

    private static final int LYRICS_FRAGMENT = 0;
    private static final int QUEUE_FRAGMENT = 1;

    private final MutableLiveData<Boolean> isVisible = new MutableLiveData<>(false);

    public BottomSheetView(RootMediaPlayerPanel p, CustomBottomSheetBehavior<FrameLayout> behavior, View rootView) {
        this.rootView = rootView;
        this.panel = p;
        this.bottomSheetBehavior = behavior;


        bottomBar = findViewById(R.id.bottom_sheet_nav_bar);
        viewPager = findViewById(R.id.bottom_sheet_view_pager);
        closeBottomSheet = findViewById(R.id.close_bottom_sheet);

        adapter = new StateFragmentAdapter(p.getSupportFragmentManager(), p.getLifecycle());
        adapter.addFragment(FragmentBottomSheetLyrics.class, this);
        adapter.addFragment(FragmentBottomSheetQueue.class, this);

        playlistCallback = adapter.getFragment(FragmentBottomSheetQueue.class).getCallback();

        viewPager.setAdapter(adapter);
        bottomBar.setupWithViewPager2(viewPager);

        bottomBar.setOnTabSelectListener(new ReadableBottomBar.OnTabSelectListener() {
            @Override
            public void onTabSelected(int i, @Nullable ReadableBottomBar.Tab tab, int i1, @NonNull ReadableBottomBar.Tab tab1) {
                if (behavior.getState() == CustomBottomSheetBehavior.STATE_COLLAPSED)
                    behavior.setState(CustomBottomSheetBehavior.STATE_EXPANDED);
            }

            @Override
            public void onTabReselected(int i, @NonNull ReadableBottomBar.Tab tab) {
                if (behavior.getState() == CustomBottomSheetBehavior.STATE_COLLAPSED)
                    behavior.setState(CustomBottomSheetBehavior.STATE_EXPANDED);
            }
        });

        closeBottomSheet.setOnClickListener(v -> toggleCloseBtn());

        isVisible.observe(HeartBeatzApp.container(rootView.getContext()).requireUiThread().getLifecycleOwner(), isVisible -> {
            if (isVisible) {
                closeBottomSheet.setImageResource(com.giga.tech1000.icons_pack.R.drawable.keyboard_arrow_down_24px);
            } else {
                closeBottomSheet.setImageResource(com.giga.tech1000.icons_pack.R.drawable.keyboard_arrow_up_24px);

            }
        });

    }

    public void setViewVisibility(boolean isVisible) {
        this.isVisible.setValue(isVisible);
    }

    public MutableLiveData<Boolean> isViewVisibility() {
        return isVisible;
    }

    public void onQueueIndexReady(List<Integer> queue, int queueIndex) {
        if (adapter != null) {
            FragmentBottomSheetQueue fragment = adapter.getFragment(FragmentBottomSheetQueue.class);
            if (fragment != null) {
                fragment.onUpdateQueue(queue, queueIndex);
            }
        }
    }

    public void onHostDiscovered(NsdServiceInfo serviceInfo) {
    }

    public void onServiceRegistered(NsdServiceInfo serviceInfo) {
    }

    private void toggleCloseBtn() {
        if (bottomSheetBehavior.getState() == CustomBottomSheetBehavior.STATE_COLLAPSED) {
            openBottomSheet();
        } else if (bottomSheetBehavior.getState() == CustomBottomSheetBehavior.STATE_EXPANDED) {
            closeBottomSheet();
        }
    }

    public void closeBottomSheet() {
        if (bottomSheetBehavior.getState() == CustomBottomSheetBehavior.STATE_EXPANDED) {
            bottomSheetBehavior.setState(CustomBottomSheetBehavior.STATE_COLLAPSED);
        }
    }

    private void openBottomSheet() {
        if (bottomSheetBehavior.getState() == CustomBottomSheetBehavior.STATE_COLLAPSED) {
            bottomSheetBehavior.setState(CustomBottomSheetBehavior.STATE_EXPANDED);
        }
    }

    public void openLyricsFragment() {
        openBottomSheet();
        viewPager.setCurrentItem(LYRICS_FRAGMENT, true);
        bottomBar.selectTab(bottomBar.getTabs().get(LYRICS_FRAGMENT), true);
    }

    public void openQueueFragment() {
        openBottomSheet();
        viewPager.setCurrentItem(QUEUE_FRAGMENT, true);
        bottomBar.selectTab(bottomBar.getTabs().get(QUEUE_FRAGMENT), true);

    }



    public <T extends View> T findViewById(@IdRes int id) {
        return this.rootView.findViewById(id);
    }



}
