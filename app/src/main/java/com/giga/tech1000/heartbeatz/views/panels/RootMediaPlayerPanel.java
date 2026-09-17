package com.giga.tech1000.heartbeatz.views.panels;

import android.annotation.SuppressLint;
import android.content.Context;
import android.net.nsd.NsdServiceInfo;
import androidx.media3.session.legacy.PlaybackStateCompat;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.coordinatorlayout.widget.CoordinatorLayout;
import androidx.media3.common.util.UnstableApi;

import com.giga.tech1000.extensions.bottom_sheet.CustomBottomSheetBehavior;
import com.giga.tech1000.heartbeatz.R;
import com.giga.tech1000.heartbeatz.ui.UIInfoLog;
import com.giga.tech1000.heartbeatz.view_models.extended_models.SettingViewModel;
import com.giga.tech1000.heartbeatz.views.BottomSheetView;
import com.giga.tech1000.heartbeatz.views.MediaPlayerBarView;
import com.giga.tech1000.heartbeatz.views.MediaPlayerView;
import com.giga.tech1000.media_player.models.Song;
import com.giga.tech1000.utils.interfaces.OnBackPressedHandler;
import com.giga.tech1000.visualizer_android.VisualizerManager;
import com.realgear.multislidinguppanel.BasePanelView;
import com.realgear.multislidinguppanel.IPanel;
import com.realgear.multislidinguppanel.MultiSlidingUpPanelLayout;

import java.util.List;

@UnstableApi
@SuppressLint("ViewConstructor")
public class RootMediaPlayerPanel extends BasePanelView implements OnBackPressedHandler {
    private final Context context;
    private MediaPlayerBarView mediaPlayerBarView;
    private MediaPlayerView mediaPlayerView;
    private BottomSheetView bottomSheetView;

    private boolean isFirstPlay = true;
    private boolean isStarted = false;

    private SettingViewModel settingViewModel;

    private Song currentSong;
    private PlaybackStateCompat currentPlaybackState;

    private boolean isPartyClient = false;


    public RootMediaPlayerPanel(@NonNull Context context, MultiSlidingUpPanelLayout panelLayout) {
        super(context, panelLayout);
        this.context = context;

        // These 2 lines are required
        getContext().setTheme(R.style.Theme_HeartBeatz); // Your projects theme
        LayoutInflater.from(getContext()).inflate(R.layout.mediaplayer_root_layout, this, true);

    }

    @Override
    public void onCreateView() {
        // Allow this panel to fully leave the stack so the bottom nav sits flush
        this.setUserHiddenMode(true);

        // The panel will slide up and down
        this.setSlideDirection(MultiSlidingUpPanelLayout.SLIDE_VERTICAL);

        // Sets the panels peak height
        this.setPeakHeight(getResources().getDimensionPixelSize(R.dimen.media_player_bar_height));

        // Mark user-hidden so height stack ignores this panel until a song plays.
        // Prefer setPanelState+flag over hidePanel() during create to avoid drag-helper races.
        this.isHidden = true;
        this.setPanelState(MultiSlidingUpPanelLayout.HIDDEN);
        if (getMultiSlidingUpPanel() != null) {
            getMultiSlidingUpPanel().requestLayout();
        }
        UIInfoLog.d("RootMediaPlayer.onCreateView", "HIDDEN isHidden=true peak=" + getPeakHeight());
        UIInfoLog.layoutChildren("RootMediaPlayer.onCreateView", getMultiSlidingUpPanel());
    }

    @Override
    public void onBindView() {
        FrameLayout bottomSheetBehaviourView = findViewById(R.id.media_player_bottom_sheet_behavior);
        CoordinatorLayout fullMediaPlayerView = findViewById(R.id.media_player_view);
        FrameLayout miniMediaPlayerView = findViewById(R.id.mini_player_view);

        mediaPlayerView = new MediaPlayerView(fullMediaPlayerView, this);
        mediaPlayerBarView = new MediaPlayerBarView(miniMediaPlayerView, this);

        CustomBottomSheetBehavior<FrameLayout> bottomSheetBehavior = CustomBottomSheetBehavior.from(bottomSheetBehaviourView);
        bottomSheetBehavior.setSkipAnchored(false);
        bottomSheetBehavior.setAllowUserDragging(false);

        DisplayMetrics dm = getResources().getDisplayMetrics();

        ViewGroup.LayoutParams params = bottomSheetBehaviourView.getLayoutParams();
        params.height = dm.heightPixels - this.getPeakHeight();
        bottomSheetBehaviourView.setLayoutParams(params);

        bottomSheetBehavior.setAnchorOffset((int) (dm.heightPixels * 0.75F));
        bottomSheetBehavior.setPeekHeight(getResources().getDimensionPixelSize(R.dimen.pager_bottom_height));
        bottomSheetBehavior.setMediaPlayerBarHeight(getPeakHeight());
        bottomSheetBehavior.setState(CustomBottomSheetBehavior.STATE_COLLAPSED);

        bottomSheetView = new BottomSheetView(this, bottomSheetBehavior, bottomSheetBehaviourView);


        bottomSheetBehavior.addBottomSheetCallback(new CustomBottomSheetBehavior.BottomSheetCallback() {
            @Override
            public void onStateChanged(@NonNull View bottomSheet, int oldState, int newState) {
                switch (newState) {
                    case CustomBottomSheetBehavior.STATE_ANCHORED:
                    case CustomBottomSheetBehavior.STATE_EXPANDED:
                    case CustomBottomSheetBehavior.STATE_DRAGGING:
                        getMultiSlidingUpPanel().setSlidingEnabled(false);
                        bottomSheetView.setViewVisibility(true);
                        break;

                    default:
                        getMultiSlidingUpPanel().setSlidingEnabled(true);
                        bottomSheetView.setViewVisibility(false);
                        break;
                }
            }

            @Override
            public void onSlide(@NonNull View bottomSheet, float slideOffset) {
                mediaPlayerView.onSliding(slideOffset, MediaPlayerView.STATE_PARTIAL);
                mediaPlayerBarView.onSliding(slideOffset, MediaPlayerBarView.STATE_PARTIAL);
            }
        });

        // Force a sync of the current state immediately after binding views
        syncUIState();
    }

    /**
     * Pushes the current song and playback state to the views if they are ready.
     */
    private void syncUIState() {
        if (mediaPlayerBarView == null || mediaPlayerView == null) return;

        if (currentSong != null) {
            mediaPlayerBarView.onSongChanged(currentSong);
            mediaPlayerView.onSongChanged(currentSong);
        }

        if (currentPlaybackState != null) {
            updatePlaybackViews(currentPlaybackState);
        }
    }

    @Override
    public void onPanelStateChanged(int i) {
        // Never call nav.hidePanel()/collapsePanel() — those call setSlidingUpPanel()
        // and steal the active sliding panel (breaks expand).
        // Only mutate isHidden + setPanelState + requestLayout.
        boolean miniVisible = (i == MultiSlidingUpPanelLayout.COLLAPSED) && !isUserHidden();
        boolean fullVisible = (i == MultiSlidingUpPanelLayout.EXPANDED);
        boolean playerHidden = (i == MultiSlidingUpPanelLayout.HIDDEN) || isUserHidden();
        UIInfoLog.d("RootMediaPlayer.onPanelStateChanged",
                "state=" + UIInfoLog.stateName(i)
                + " miniVisible=" + miniVisible
                + " fullVisible=" + fullVisible
                + " playerHidden=" + playerHidden
                + " isUserHidden=" + isUserHidden()
                + " top=" + getTop() + " bottom=" + getBottom()
                + " peak=" + getPeakHeight()
                + " collapsedH=" + getPanelCollapsedHeight());

        RootNavigationBarPanel nav = null;
        try {
            if (getMultiSlidingUpPanel() != null
                    && getMultiSlidingUpPanel().getAdapter() != null) {
                nav = getMultiSlidingUpPanel().getAdapter().getItem(RootNavigationBarPanel.class);
            }
        } catch (Exception ignored) {
        }
        if (nav == null) return;

        if (fullVisible) {
            // Full player: remove nav from height stack so player is truly full-screen
            nav.isHidden = true;
            if (nav.getPanelState() != MultiSlidingUpPanelLayout.HIDDEN) {
                nav.setPanelState(MultiSlidingUpPanelLayout.HIDDEN);
            }
            nav.updatePaddingWhenWhenBarChanged(false);
        } else {
            // Mini or idle: nav must be visible and counted in media collapsed height
            // so the mini bar sits *above* the bottom nav (not under it).
            nav.isHidden = false;
            if (nav.getPanelState() != MultiSlidingUpPanelLayout.COLLAPSED) {
                nav.setPanelState(MultiSlidingUpPanelLayout.COLLAPSED);
            }
            try {
                resetPanelRealHeight();
                nav.resetPanelRealHeight();
            } catch (Exception ignored) {
            }
            nav.updatePaddingWhenWhenBarChanged(miniVisible);
        }
        if (getMultiSlidingUpPanel() != null) {
            getMultiSlidingUpPanel().requestLayout();
        }
        UIInfoLog.panelSnapshot("RootMediaPlayer.afterStateSync", this);
        if (nav != null) UIInfoLog.panelSnapshot("RootMediaPlayer.navAfterSync", nav);
        UIInfoLog.layoutChildren("RootMediaPlayer.afterStateSync", getMultiSlidingUpPanel());
    }

    @Override
    public void onSliding(@NonNull IPanel<View> panel, int top, int dy, float slidingOffset) {
        super.onSliding(panel, top, dy, slidingOffset);

        mediaPlayerView.onSliding(slidingOffset, MediaPlayerView.STATE_NORMAL);
        mediaPlayerBarView.onSliding(slidingOffset, MediaPlayerBarView.STATE_NORMAL);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (mediaPlayerView != null)
            VisualizerManager.get().register(mediaPlayerView.getPlayerWaveVisualizer());
        if (mediaPlayerBarView != null)
            VisualizerManager.get().register(mediaPlayerBarView.getPlayerBarVisualizer());
    }

    @Override
    protected void onDetachedFromWindow() {
        if (mediaPlayerView != null)
            VisualizerManager.get().unregister(mediaPlayerView.getPlayerWaveVisualizer());
        if (mediaPlayerBarView != null)
            VisualizerManager.get().unregister(mediaPlayerBarView.getPlayerBarVisualizer());
        super.onDetachedFromWindow();
    }

    public BottomSheetView getBottomSheetView() {
        return bottomSheetView;
    }


    public void onPlaybackStateChanged(PlaybackStateCompat state) {
        this.currentPlaybackState = state;
        post(() -> {
            if (mediaPlayerBarView != null && mediaPlayerView != null) {
                updatePlaybackViews(currentPlaybackState);
            }
        });
    }

    private void updatePlaybackViews(PlaybackStateCompat state) {
        UIInfoLog.d("RootMediaPlayer.updatePlaybackViews",
                "pbState=" + state.getState()
                + " isFirstPlay=" + isFirstPlay
                + " isStarted=" + isStarted
                + " song=" + (currentSong != null)
                + " panelState=" + UIInfoLog.stateName(getPanelState())
                + " isHidden=" + isUserHidden());
        if (isFirstPlay) {
            if (isStarted && state.getState() == PlaybackStateCompat.STATE_PLAYING) {
                UIInfoLog.d("RootMediaPlayer.updatePlaybackViews", "-> expandPanel()");
                expandPanel();
                isFirstPlay = false;
            } else if (!isStarted && currentSong != null)  {
                UIInfoLog.d("RootMediaPlayer.updatePlaybackViews", "-> collapsePanel()");
                collapsePanel();
                isStarted = true;
            }
        }

        mediaPlayerBarView.onPlaybackStateChanged(state);
        mediaPlayerView.onPlaybackStateChanged(state);
    }



    public void onSongChanged(Song song) {
        this.currentSong = song;
        post(() -> {
            if (song == null) {
                hideMiniPlayer();
                return;
            }
            if (mediaPlayerBarView != null && mediaPlayerView != null) {
                mediaPlayerBarView.onSongChanged(currentSong);
                mediaPlayerView.onSongChanged(currentSong);
            }
            // Ensure mini player is in the stack when a song is active
            showMiniPlayerCollapsed();
        });
    }

    /**
     * Shows the mini player at collapsed peak height (above the bottom navigation).
     */
    public void showMiniPlayerCollapsed() {
        UIInfoLog.d("RootMediaPlayer.showMiniPlayerCollapsed",
                "isHidden=" + isUserHidden() + " state=" + UIInfoLog.stateName(getPanelState()));
        if (isUserHidden() || getPanelState() == MultiSlidingUpPanelLayout.HIDDEN) {
            collapsePanel();
        }
        post(() -> UIInfoLog.layoutChildren("RootMediaPlayer.showMini.posted", getMultiSlidingUpPanel()));
    }

    /**
     * Fully removes the mini player from the panel stack so the bottom nav is flush.
     */
    public void hideMiniPlayer() {
        UIInfoLog.d("RootMediaPlayer.hideMiniPlayer",
                "isHidden=" + isUserHidden() + " state=" + UIInfoLog.stateName(getPanelState()));
        if (!isUserHidden() || getPanelState() != MultiSlidingUpPanelLayout.HIDDEN) {
            hidePanel();
        }
        post(() -> UIInfoLog.layoutChildren("RootMediaPlayer.hideMini.posted", getMultiSlidingUpPanel()));
    }

    @Nullable
    public Song getCurrentSong() {
        return currentSong;
    }

    public void setPartyClientMode(boolean enabled) {
        this.isPartyClient = enabled;
        post(() -> {
            if (mediaPlayerBarView != null) mediaPlayerBarView.setPartyClientMode(enabled);
            if (mediaPlayerView != null) mediaPlayerView.setPartyClientMode(enabled);
        });
    }

    public void onHostDiscovered(NsdServiceInfo serviceInfo) {
        post(() -> {
            if (bottomSheetView != null) bottomSheetView.onHostDiscovered(serviceInfo);
        });
    }

    public void onServiceRegistered(NsdServiceInfo serviceInfo) {
        post(() -> {
            if (bottomSheetView != null) bottomSheetView.onServiceRegistered(serviceInfo);
        });
    }

    public void onRepeatModeChanged(int repeatMode) {
        post(() -> {
            if (mediaPlayerView != null) mediaPlayerView.onRepeatModeChanged(repeatMode);
        });
    }

    public void onShuffleModeChanged(boolean isShuffleMode) {
        post(() -> {
            if (mediaPlayerView != null) mediaPlayerView.onShuffleModeChanged(isShuffleMode);
        });
    }


    public void onSessionIdReady(int id) {
        post(() -> VisualizerManager.get().attachSession(id));
    }

    public void onQueueIndexReady(List<Integer> queue, int queueIndex) {
        post(() -> {
            if (bottomSheetView != null) bottomSheetView.onQueueIndexReady(queue, queueIndex);
        });
    }



    @Override
    public boolean onBackPressed() {
        if (Boolean.TRUE.equals(bottomSheetView.isViewVisibility().getValue())) {
            bottomSheetView.closeBottomSheet();
            return true;
        }
        if (getPanelState() == MultiSlidingUpPanelLayout.EXPANDED) {
            getMultiSlidingUpPanel().collapsePanel();
            return true;
        }
        return false;
    }



}
