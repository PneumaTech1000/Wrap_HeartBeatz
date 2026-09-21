package com.giga.tech1000.heartbeatz.views.panels;

import android.annotation.SuppressLint;
import android.content.Context;
import android.net.nsd.NsdServiceInfo;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.FragmentActivity;
import androidx.fragment.app.FragmentManager;
import androidx.lifecycle.Lifecycle;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.common.Player;

import com.giga.tech1000.extensions.bottom_sheet.CustomBottomSheetBehavior;
import com.giga.tech1000.heartbeatz.R;
import com.giga.tech1000.heartbeatz.ui.PlayerChromeController;
import com.giga.tech1000.heartbeatz.ui.UIInfoLog;
import com.giga.tech1000.heartbeatz.views.BottomSheetView;
import com.giga.tech1000.heartbeatz.views.MediaPlayerBarView;
import com.giga.tech1000.heartbeatz.views.MediaPlayerView;
import com.giga.tech1000.media_player.models.Song;
import com.giga.tech1000.utils.interfaces.OnBackPressedHandler;
import com.giga.tech1000.visualizer_android.VisualizerManager;
import com.google.android.material.bottomsheet.BottomSheetBehavior;

import java.util.List;

/**
 * Media player chrome: fixed mini host above bottom nav + full-screen overlay.
 * BottomSheet peek was leaving the mini off-screen (logs: y=height). Nested lyrics
 * sheet may still use CustomBottomSheetBehavior independently.
 */
@UnstableApi
@SuppressLint("ViewConstructor")
public class RootMediaPlayerPanel extends FrameLayout implements OnBackPressedHandler {

    public static final int STATE_HIDDEN = BottomSheetBehavior.STATE_HIDDEN;
    public static final int STATE_COLLAPSED = BottomSheetBehavior.STATE_COLLAPSED;
    public static final int STATE_EXPANDED = BottomSheetBehavior.STATE_EXPANDED;

    private final Context context;
    private MediaPlayerBarView mediaPlayerBarView;
    private MediaPlayerView mediaPlayerView;
    private BottomSheetView bottomSheetView;

    private long lastPlaybackUiLogMs;
    private boolean isFirstPlay = true;
    private boolean isStarted = false;

    private Song currentSong;
    private boolean currentIsPlaying;
    private int currentPlaybackState = Player.STATE_IDLE;
    private long currentPositionMs;
    private boolean isPartyClient = false;

    @Nullable
    private BottomSheetBehavior<View> sheetBehavior;
    @Nullable
    private View sheetContainer;
    @Nullable
    private FrameLayout miniHost;
    @Nullable
    private FrameLayout fullHost;
    private int panelUiState = STATE_HIDDEN;

    public RootMediaPlayerPanel(@NonNull Context context) {
        super(context);
        this.context = context;
        getContext().setTheme(R.style.Theme_HeartBeatz);
        // Layout inflated in attachToHosts so children can be reparented to activity hosts
    }


    /**
     * Wire mini + full hosts from activity_main.
     */
    public void attachToHosts(@NonNull FrameLayout miniHost, @NonNull FrameLayout fullHost) {
        this.miniHost = miniHost;
        this.fullHost = fullHost;
        this.sheetContainer = miniHost;

        // Inflate content into this panel once, then move children to hosts
        if (getChildCount() == 0) {
            LayoutInflater.from(getContext()).inflate(R.layout.mediaplayer_root_layout, this, true);
        }
        View mini = findViewById(R.id.mini_player_view);
        View full = findViewById(R.id.media_player_view);
        if (mini != null && mini.getParent() != miniHost) {
            if (mini.getParent() instanceof android.view.ViewGroup) {
                ((android.view.ViewGroup) mini.getParent()).removeView(mini);
            }
            miniHost.addView(mini, new FrameLayout.LayoutParams(
                    LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        }
        if (full != null && full.getParent() != fullHost) {
            if (full.getParent() instanceof android.view.ViewGroup) {
                ((android.view.ViewGroup) full.getParent()).removeView(full);
            }
            fullHost.addView(full, new FrameLayout.LayoutParams(
                    LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        }

        miniHost.setVisibility(View.GONE);
        fullHost.setVisibility(View.GONE);
        panelUiState = STATE_HIDDEN;
        sheetBehavior = null; // no BottomSheet for chrome

        onBindViews();
        UIInfoLog.d("RootMediaPlayer.attachHosts", "miniHost=" + miniHost.getId()
                + " fullHost=" + fullHost.getId()
                + " miniChild=" + miniHost.getChildCount()
                + " fullChild=" + fullHost.getChildCount());
    }

    /** @deprecated use {@link #attachToHosts(FrameLayout, FrameLayout)} */
    public void attachToSheet(@NonNull View sheetContainer) {
        // Legacy no-op path if still called
        UIInfoLog.d("RootMediaPlayer.attachToSheet", "deprecated — use attachToHosts");
    }

    private void onBindViews() {
        View barRoot = miniHost != null ? miniHost : this;
        View fullRoot = fullHost != null ? fullHost : this;
        mediaPlayerView = new MediaPlayerView(fullRoot, this);
        mediaPlayerBarView = new MediaPlayerBarView(barRoot, this);

        FrameLayout lyricsSheet = fullRoot.findViewById(R.id.media_player_bottom_sheet_behavior);
        if (lyricsSheet == null) lyricsSheet = findViewById(R.id.media_player_bottom_sheet_behavior);
        if (lyricsSheet != null) {
            CustomBottomSheetBehavior<FrameLayout> lyricsBehavior = CustomBottomSheetBehavior.from(lyricsSheet);
            lyricsBehavior.setState(CustomBottomSheetBehavior.STATE_COLLAPSED);
            // rootView must be the host that still contains bottom_sheet_* ids (full player host)
            bottomSheetView = new BottomSheetView(this, lyricsBehavior, fullRoot);
            lyricsBehavior.addBottomSheetCallback(
                    new CustomBottomSheetBehavior.BottomSheetCallback() {
                        @Override
                        public void onStateChanged(@NonNull View bottomSheet, int oldState, int newState) {
                            switch (newState) {
                                case CustomBottomSheetBehavior.STATE_ANCHORED:
                                case CustomBottomSheetBehavior.STATE_EXPANDED:
                                case CustomBottomSheetBehavior.STATE_DRAGGING:
                                    if (sheetBehavior != null) sheetBehavior.setDraggable(false);
                                    if (bottomSheetView != null)
                                        bottomSheetView.setViewVisibility(true);
                                    break;
                                default:
                                    if (sheetBehavior != null) sheetBehavior.setDraggable(true);
                                    if (bottomSheetView != null)
                                        bottomSheetView.setViewVisibility(false);
                                    break;
                            }
                        }

                        @Override
                        public void onSlide(@NonNull View bottomSheet, float slideOffset) {
                            if (mediaPlayerView != null) {
                                mediaPlayerView.onSliding(slideOffset, MediaPlayerView.STATE_PARTIAL);
                            }
                            if (mediaPlayerBarView != null) {
                                mediaPlayerBarView.onSliding(slideOffset, MediaPlayerBarView.STATE_PARTIAL);
                            }
                        }
                    });
        }
        syncUIState();
    }

    private void syncUIState() {
        if (mediaPlayerBarView == null || mediaPlayerView == null) return;
        if (currentSong != null) {
            mediaPlayerBarView.onSongChanged(currentSong);
            mediaPlayerView.onSongChanged(currentSong);
        }
        updatePlaybackViews(currentIsPlaying, currentPlaybackState, currentPositionMs);
    }

    public int getPanelState() {
        return panelUiState;
    }

    public boolean isUserHidden() {
        return getPanelState() == STATE_HIDDEN;
    }

    private void applyChromeForSheetState(int newState) {
        panelUiState = newState;
        if (newState == STATE_COLLAPSED) {
            if (miniHost != null) miniHost.setVisibility(View.VISIBLE);
            if (fullHost != null) fullHost.setVisibility(View.GONE);
            if (mediaPlayerBarView != null) mediaPlayerBarView.showAsMini();
            if (mediaPlayerView != null) mediaPlayerView.hideAsFull();
            PlayerChromeController.onSheetStateChanged(BottomSheetBehavior.STATE_COLLAPSED);
        } else if (newState == STATE_EXPANDED) {
            if (miniHost != null) miniHost.setVisibility(View.GONE);
            if (fullHost != null) fullHost.setVisibility(View.VISIBLE);
            if (mediaPlayerBarView != null) {
                mediaPlayerBarView.onSliding(1f, MediaPlayerBarView.STATE_PARTIAL);
            }
            if (mediaPlayerView != null) mediaPlayerView.showAsFull();
            PlayerChromeController.onSheetStateChanged(BottomSheetBehavior.STATE_EXPANDED);
        } else {
            if (miniHost != null) miniHost.setVisibility(View.GONE);
            if (fullHost != null) fullHost.setVisibility(View.GONE);
            PlayerChromeController.onSheetStateChanged(BottomSheetBehavior.STATE_HIDDEN);
        }
        UIInfoLog.d("RootMediaPlayer.applyChrome", "state=" + newState
                + " miniVis=" + (miniHost != null ? miniHost.getVisibility() : -1)
                + " fullVis=" + (fullHost != null ? fullHost.getVisibility() : -1));
    }

    public void showMiniPlayerCollapsed() {
        UIInfoLog.d("RootMediaPlayer", "showMini COLLAPSED fixed-host");
        applyChromeForSheetState(STATE_COLLAPSED);
    }

    public void hideMiniPlayer() {
        UIInfoLog.d("RootMediaPlayer", "hide HIDDEN fixed-host");
        applyChromeForSheetState(STATE_HIDDEN);
    }

    public void expandPlayer() {
        UIInfoLog.d("RootMediaPlayer", "expand EXPANDED fixed-host");
        applyChromeForSheetState(STATE_EXPANDED);
    }

    public void collapsePlayer() {
        UIInfoLog.d("RootMediaPlayer", "collapse COLLAPSED fixed-host");
        applyChromeForSheetState(STATE_COLLAPSED);
    }

    public BottomSheetView getBottomSheetView() {
        return bottomSheetView;
    }

    public void onPlaybackStateChanged(boolean isPlaying, int playbackState, long positionMs) {
        this.currentIsPlaying = isPlaying;
        this.currentPlaybackState = playbackState;
        this.currentPositionMs = positionMs;
        updatePlaybackViews(isPlaying, playbackState, positionMs);
    }

    private void updatePlaybackViews(boolean isPlaying, int playbackState, long positionMs) {
        long now = android.os.SystemClock.elapsedRealtime();
        if (now - lastPlaybackUiLogMs > 2000) {
            lastPlaybackUiLogMs = now;
            UIInfoLog.d("RootMediaPlayer.updatePlaybackViews",
                    "isPlaying=" + isPlaying
                            + " state=" + playbackState
                            + " isFirstPlay=" + isFirstPlay
                            + " isStarted=" + isStarted
                            + " song=" + (currentSong != null)
                            + " panelState=" + getPanelState());
        }
        if (mediaPlayerBarView != null) mediaPlayerBarView.onPlaybackStateChanged(isPlaying, playbackState, positionMs);
        if (mediaPlayerView != null) mediaPlayerView.onPlaybackStateChanged(isPlaying, playbackState, positionMs);
    }

    public void onSongChanged(@Nullable Song song) {
        this.currentSong = song;
        // Fresh install / idle: no media item — do not touch title/art with null Song
        if (song == null) {
            return;
        }
        post(() -> {
            if (mediaPlayerBarView != null) mediaPlayerBarView.onSongChanged(song);
            if (mediaPlayerView != null) mediaPlayerView.onSongChanged(song);
        });
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
        if (bottomSheetView != null
                && Boolean.TRUE.equals(bottomSheetView.isViewVisibility().getValue())) {
            bottomSheetView.closeBottomSheet();
            return true;
        }
        if (getPanelState() == STATE_EXPANDED) {
            collapsePlayer();
            return true;
        }
        return false;
    }

    public FragmentManager getSupportFragmentManager() {
        if (context instanceof FragmentActivity) {
            return ((FragmentActivity) context).getSupportFragmentManager();
        }
        throw new IllegalStateException("Context is not FragmentActivity");
    }

    public Lifecycle getLifecycle() {
        if (context instanceof FragmentActivity) {
            return ((FragmentActivity) context).getLifecycle();
        }
        throw new IllegalStateException("Context is not FragmentActivity");
    }
}
