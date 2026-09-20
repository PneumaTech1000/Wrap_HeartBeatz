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
 * Media player chrome hosted in a Material {@link BottomSheetBehavior}.
 * Mini = COLLAPSED (peek), full = EXPANDED, idle = HIDDEN.
 * MultiSlidingUpPanel ANCHORED is not used: Material {@link BottomSheetBehavior@onSlide}
 * supplies slideOffset for mini/full cross-fade. Nested lyrics sheet may still use
 * CustomBottomSheetBehavior.STATE_ANCHORED independently.
 * Bottom navigation is controlled via {@link PlayerChromeController} — not a sliding panel.
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

    public RootMediaPlayerPanel(@NonNull Context context) {
        super(context);
        this.context = context;
        getContext().setTheme(R.style.Theme_HeartBeatz);
        LayoutInflater.from(getContext()).inflate(R.layout.mediaplayer_root_layout, this, true);
    }

    /**
     * Attach this panel into the activity bottom-sheet container and bind behavior.
     */
    public void attachToSheet(@NonNull View sheetContainer) {
        this.sheetContainer = sheetContainer;
        if (getParent() != sheetContainer) {
            if (getParent() != null) {
                ((android.view.ViewGroup) getParent()).removeView(this);
            }
            if (sheetContainer instanceof FrameLayout) {
                ((FrameLayout) sheetContainer).addView(this,
                        new FrameLayout.LayoutParams(
                                LayoutParams.MATCH_PARENT,
                                LayoutParams.MATCH_PARENT));
            }
        }
        sheetBehavior = BottomSheetBehavior.from(sheetContainer);
        sheetBehavior.setPeekHeight(
                getResources().getDimensionPixelSize(R.dimen.media_player_bar_height));
        sheetBehavior.setHideable(true);
        sheetBehavior.setFitToContents(true);
        sheetBehavior.setSkipCollapsed(false);
        sheetBehavior.setState(BottomSheetBehavior.STATE_HIDDEN);
        sheetBehavior.addBottomSheetCallback(new BottomSheetBehavior.BottomSheetCallback() {
            @Override
            public void onStateChanged(@NonNull View bottomSheet, int newState) {
                UIInfoLog.d("RootMediaPlayer.sheet", "state=" + newState);
                PlayerChromeController.onSheetStateChanged(newState);
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
        onBindViews();
    }

    private void onBindViews() {
        mediaPlayerView = new MediaPlayerView(this, this);
        mediaPlayerBarView = new MediaPlayerBarView(this, this);

        FrameLayout lyricsSheet = findViewById(R.id.media_player_bottom_sheet_behavior);
        if (lyricsSheet != null) {
            CustomBottomSheetBehavior<FrameLayout> lyricsBehavior = CustomBottomSheetBehavior.from(lyricsSheet);
            lyricsBehavior.setState(CustomBottomSheetBehavior.STATE_COLLAPSED);
            bottomSheetView = new BottomSheetView(this, lyricsBehavior, this);
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
        if (sheetBehavior == null) return STATE_HIDDEN;
        return sheetBehavior.getState();
    }

    public boolean isUserHidden() {
        return getPanelState() == STATE_HIDDEN;
    }

    public void showMiniPlayerCollapsed() {
        if (sheetBehavior == null) return;
        UIInfoLog.d("RootMediaPlayer", "showMini COLLAPSED");
        sheetBehavior.setState(BottomSheetBehavior.STATE_COLLAPSED);
    }

    public void hideMiniPlayer() {
        if (sheetBehavior == null) return;
        UIInfoLog.d("RootMediaPlayer", "hide HIDDEN");
        sheetBehavior.setState(BottomSheetBehavior.STATE_HIDDEN);
    }

    public void expandPlayer() {
        if (sheetBehavior == null) return;
        sheetBehavior.setState(BottomSheetBehavior.STATE_EXPANDED);
    }

    public void collapsePlayer() {
        if (sheetBehavior == null) return;
        sheetBehavior.setState(BottomSheetBehavior.STATE_COLLAPSED);
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
