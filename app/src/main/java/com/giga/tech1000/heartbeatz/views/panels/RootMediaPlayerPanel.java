package com.giga.tech1000.heartbeatz.views.panels;

import android.annotation.SuppressLint;
import android.content.Context;
import android.net.nsd.NsdServiceInfo;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.ViewConfiguration;
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



    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    /** Panel is not in the hierarchy (children reparented) — never use View.post on this. */
    private void runOnUi(@NonNull Runnable r) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            r.run();
        } else {
            mainHandler.post(r);
        }
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


        // Full player: BottomSheet so user can drag down to mini (skipCollapsed)
        try {
            sheetBehavior = BottomSheetBehavior.from(fullHost);
            sheetBehavior.setFitToContents(false);
            sheetBehavior.setSkipCollapsed(true);
            sheetBehavior.setHideable(true);
            sheetBehavior.setDraggable(true);
            sheetBehavior.setState(BottomSheetBehavior.STATE_HIDDEN);
            sheetBehavior.addBottomSheetCallback(new BottomSheetBehavior.BottomSheetCallback() {
                @Override
                public void onStateChanged(@NonNull View bottomSheet, int newState) {
                    if (newState == BottomSheetBehavior.STATE_HIDDEN
                            || newState == BottomSheetBehavior.STATE_COLLAPSED) {
                        // User dragged full player away → mini
                        if (panelUiState == STATE_EXPANDED) {
                            applyChromeForSheetState(STATE_COLLAPSED);
                        }
                    } else if (newState == BottomSheetBehavior.STATE_EXPANDED) {
                        panelUiState = STATE_EXPANDED;
                        if (miniHost != null) miniHost.setVisibility(View.GONE);
                        PlayerChromeController.onSheetStateChanged(BottomSheetBehavior.STATE_EXPANDED);
                    }
                }

                @Override
                public void onSlide(@NonNull View bottomSheet, float slideOffset) {
                    // -1 hidden … 0 collapsed … 1 expanded
                    float expand = Math.max(0f, slideOffset);
                    PlayerChromeController.onSlide(expand);
                    if (mediaPlayerView != null) {
                        mediaPlayerView.onSliding(expand, MediaPlayerView.STATE_PARTIAL);
                    }
                    if (mediaPlayerBarView != null && expand < 0.15f && miniHost != null
                            && miniHost.getVisibility() != View.VISIBLE
                            && panelUiState != STATE_EXPANDED) {
                        // near collapsed during drag — show mini underneath
                        miniHost.setVisibility(View.VISIBLE);
                    }
                    UIInfoLog.d("RootMediaPlayer.fullSlide", "offset=" + slideOffset + " expand=" + expand);
                }
            });
        } catch (IllegalArgumentException e) {
            UIInfoLog.d("RootMediaPlayer.attachHosts", "fullHost has no BottomSheetBehavior: " + e.getMessage());
            sheetBehavior = null;
        }

        onBindViews();
        installMiniSwipeToExpand();
        UIInfoLog.d("RootMediaPlayer.attachHosts", "miniHost=" + miniHost.getId()
                + " fullHost=" + fullHost.getId()
                + " miniChild=" + miniHost.getChildCount()
                + " fullChild=" + fullHost.getChildCount());
    }

    /**
     * Drag mini bar upward to open full player; tracks expand fraction for nav fade.
     */
    private void installMiniSwipeToExpand() {
        if (miniHost == null || fullHost == null) return;
        final int touchSlop = ViewConfiguration.get(getContext()).getScaledTouchSlop();
        final float[] startY = {0f};
        final float[] startX = {0f};
        final boolean[] dragging = {false};

        miniHost.setOnTouchListener((v, event) -> {
            if (panelUiState == STATE_HIDDEN) return false;
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    startY[0] = event.getRawY();
                    startX[0] = event.getRawX();
                    dragging[0] = false;
                    return false; // allow click
                case MotionEvent.ACTION_MOVE: {
                    float dy = startY[0] - event.getRawY(); // up positive
                    float dx = Math.abs(event.getRawX() - startX[0]);
                    if (!dragging[0] && dy > touchSlop && dy > dx) {
                        dragging[0] = true;
                        fullHost.setVisibility(View.VISIBLE);
                        if (mediaPlayerView != null) mediaPlayerView.showAsFull();
                    }
                    if (dragging[0]) {
                        float h = fullHost.getHeight() > 0 ? fullHost.getHeight() : getResources().getDisplayMetrics().heightPixels;
                        float expand = Math.min(1f, Math.max(0f, dy / (h * 0.45f)));
                        fullHost.setTranslationY(h * (1f - expand));
                        fullHost.setAlpha(0.3f + 0.7f * expand);
                        miniHost.setAlpha(1f - expand);
                        PlayerChromeController.onSlide(expand);
                        UIInfoLog.d("RootMediaPlayer.miniSwipe", "expand=" + expand);
                        return true;
                    }
                    return false;
                }
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL: {
                    if (!dragging[0]) return false;
                    float dy = startY[0] - event.getRawY();
                    float h = fullHost.getHeight() > 0 ? fullHost.getHeight() : getResources().getDisplayMetrics().heightPixels;
                    float expand = Math.min(1f, Math.max(0f, dy / (h * 0.45f)));
                    miniHost.setAlpha(1f);
                    if (expand >= 0.28f) {
                        fullHost.setTranslationY(0f);
                        fullHost.setAlpha(1f);
                        expandPlayer();
                    } else {
                        fullHost.setTranslationY(0f);
                        fullHost.setAlpha(1f);
                        fullHost.setVisibility(View.GONE);
                        PlayerChromeController.onSlide(0f);
                        applyChromeForSheetState(STATE_COLLAPSED);
                    }
                    dragging[0] = false;
                    return true;
                }
                default:
                    return false;
            }
        });
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
                            // Lyrics/queue nested sheet only — do not fade main full player
                            // (that was leaving the full player blank after collapse).
                            UIInfoLog.d("RootMediaPlayer.lyricsSlide", "offset=" + slideOffset);
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
            if (fullHost != null) {
                if (sheetBehavior != null) {
                    sheetBehavior.setState(BottomSheetBehavior.STATE_HIDDEN);
                }
                fullHost.setVisibility(View.GONE);
            }
            if (mediaPlayerBarView != null) mediaPlayerBarView.showAsMini();
            if (mediaPlayerView != null) mediaPlayerView.hideAsFull();
            // Re-apply metadata in case earlier posts were dropped while detached
            if (currentSong != null) {
                if (mediaPlayerBarView != null) mediaPlayerBarView.onSongChanged(currentSong);
                if (mediaPlayerView != null) mediaPlayerView.onSongChanged(currentSong);
            }
            PlayerChromeController.onSheetStateChanged(BottomSheetBehavior.STATE_COLLAPSED);
        } else if (newState == STATE_EXPANDED) {
            if (miniHost != null) miniHost.setVisibility(View.GONE);
            if (fullHost != null) fullHost.setVisibility(View.VISIBLE);
            if (mediaPlayerBarView != null) {
                mediaPlayerBarView.onSliding(1f, MediaPlayerBarView.STATE_PARTIAL);
            }
            if (mediaPlayerView != null) mediaPlayerView.showAsFull();
            if (fullHost != null) {
                fullHost.setTranslationY(0f);
                fullHost.setAlpha(1f);
            }
            if (sheetBehavior != null) {
                fullHost.post(() -> sheetBehavior.setState(BottomSheetBehavior.STATE_EXPANDED));
            }
            PlayerChromeController.onSheetStateChanged(BottomSheetBehavior.STATE_EXPANDED);
            PlayerChromeController.onSlide(1f);
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
        runOnUi(() -> {
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
        runOnUi(() -> {
            if (mediaPlayerBarView != null) mediaPlayerBarView.setPartyClientMode(enabled);
            if (mediaPlayerView != null) mediaPlayerView.setPartyClientMode(enabled);
        });
    }

    public void onHostDiscovered(NsdServiceInfo serviceInfo) {
        runOnUi(() -> {
            if (bottomSheetView != null) bottomSheetView.onHostDiscovered(serviceInfo);
        });
    }

    public void onServiceRegistered(NsdServiceInfo serviceInfo) {
        runOnUi(() -> {
            if (bottomSheetView != null) bottomSheetView.onServiceRegistered(serviceInfo);
        });
    }

    public void onRepeatModeChanged(int repeatMode) {
        runOnUi(() -> {
            if (mediaPlayerView != null) mediaPlayerView.onRepeatModeChanged(repeatMode);
        });
    }

    public void onShuffleModeChanged(boolean isShuffleMode) {
        runOnUi(() -> {
            if (mediaPlayerView != null) mediaPlayerView.onShuffleModeChanged(isShuffleMode);
        });
    }

    public void onSessionIdReady(int id) {
        runOnUi(() -> VisualizerManager.get().attachSession(id));
    }

    public void onQueueIndexReady(List<Integer> queue, int queueIndex) {
        runOnUi(() -> {
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
