package com.giga.tech1000.heartbeatz.views.panels;

import android.annotation.SuppressLint;
import android.content.Context;
import android.net.nsd.NsdServiceInfo;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.math.MathUtils;
import androidx.fragment.app.FragmentActivity;
import androidx.fragment.app.FragmentManager;
import androidx.lifecycle.Lifecycle;
import androidx.media3.common.Player;
import androidx.media3.common.util.UnstableApi;

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
 * Mini player = fixed host above bottom nav (always visible when collapsed).
 * Full player = Material BottomSheet (HIDDEN ↔ EXPANDED, skipCollapsed).
 * Slide offset cross-fades mini/full and drives bottom-nav chrome.
 */
@UnstableApi
@SuppressLint("ViewConstructor")
public class RootMediaPlayerPanel extends FrameLayout implements OnBackPressedHandler {

    public static final int STATE_HIDDEN = BottomSheetBehavior.STATE_HIDDEN;
    public static final int STATE_COLLAPSED = BottomSheetBehavior.STATE_COLLAPSED;
    public static final int STATE_EXPANDED = BottomSheetBehavior.STATE_EXPANDED;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private MediaPlayerBarView mediaPlayerBarView;
    private MediaPlayerView mediaPlayerView;
    private BottomSheetView bottomSheetView;

    private Song currentSong;
    private boolean currentIsPlaying;
    private int currentPlaybackState = Player.STATE_IDLE;
    private long currentPositionMs;
    private boolean isPartyClient;

    @Nullable private BottomSheetBehavior<View> sheetBehavior;
    @Nullable private View sheetContainer;
    @Nullable private FrameLayout miniHost;

    private View miniView;
    private View fullView;
    private float lastExpand = 0f;
    private int panelUiState = STATE_HIDDEN;

    public RootMediaPlayerPanel(@NonNull Context context) {
        super(context);
        getContext().setTheme(R.style.Theme_HeartBeatz);
    }

    private void runOnUi(@NonNull Runnable r) {
        if (Looper.myLooper() == Looper.getMainLooper()) r.run();
        else mainHandler.post(r);
    }

    /**
     * @param miniHost fixed bar above bottom nav
     * @param sheetContainer full-player BottomSheet container
     */
    public void attachToSheet(@NonNull View sheetContainer, @NonNull FrameLayout miniHost) {
        this.sheetContainer = sheetContainer;
        this.miniHost = miniHost;

        // Full player content into sheet
        if (getParent() != sheetContainer) {
            if (getParent() != null) {
                ((android.view.ViewGroup) getParent()).removeView(this);
            }
            LayoutInflater.from(getContext()).inflate(R.layout.mediaplayer_root_layout, this, true);
            if (sheetContainer instanceof FrameLayout) {
                ((FrameLayout) sheetContainer).addView(this,
                        new FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
            }
        }

        // Mini bar into fixed host (separate from sheet — always on-screen when shown)
        miniHost.removeAllViews();
        View miniBar = LayoutInflater.from(getContext())
                .inflate(R.layout.media_player_bar_bottom_sheet, miniHost, false);
        miniHost.addView(miniBar, new FrameLayout.LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        miniView = miniBar;
        fullView = findViewById(R.id.media_player_view);

        miniHost.setVisibility(GONE);
        miniHost.setAlpha(1f);
        sheetContainer.setVisibility(VISIBLE);

        sheetBehavior = BottomSheetBehavior.from(sheetContainer);
        sheetBehavior.setFitToContents(false);
        sheetBehavior.setSkipCollapsed(true);
        sheetBehavior.setHideable(true);
        sheetBehavior.setDraggable(true);
        sheetBehavior.setPeekHeight(0);
        sheetBehavior.setState(BottomSheetBehavior.STATE_HIDDEN);

        sheetBehavior.addBottomSheetCallback(new BottomSheetBehavior.BottomSheetCallback() {
            @Override
            public void onStateChanged(@NonNull View bottomSheet, int newState) {
                UIInfoLog.d("RootMediaPlayer.sheet", "state=" + newState
                        + " top=" + bottomSheet.getTop()
                        + " y=" + bottomSheet.getY());
                if (newState == BottomSheetBehavior.STATE_EXPANDED) {
                    panelUiState = STATE_EXPANDED;
                    applySlideOffset(1f);
                    PlayerChromeController.onSheetStateChanged(BottomSheetBehavior.STATE_EXPANDED);
                } else if (newState == BottomSheetBehavior.STATE_HIDDEN
                        || newState == BottomSheetBehavior.STATE_COLLAPSED) {
                    // skipCollapsed: drag-down ends in HIDDEN → show fixed mini
                    if (currentSong != null) {
                        panelUiState = STATE_COLLAPSED;
                        applySlideOffset(0f);
                        PlayerChromeController.onSheetStateChanged(BottomSheetBehavior.STATE_COLLAPSED);
                    } else {
                        panelUiState = STATE_HIDDEN;
                        applySlideOffset(0f);
                        if (miniHost != null) miniHost.setVisibility(GONE);
                        PlayerChromeController.onSheetStateChanged(BottomSheetBehavior.STATE_HIDDEN);
                    }
                }
            }

            @Override
            public void onSlide(@NonNull View bottomSheet, float slideOffset) {
                // -1 hidden → 0 "collapsed" → 1 expanded (skipCollapsed: 0 rarely held)
                float expand = MathUtils.clamp(slideOffset, 0f, 1f);
                // When coming from hidden, slideOffset goes -1..1; map negative to 0..1 of visible
                if (slideOffset < 0f) {
                    expand = MathUtils.clamp(slideOffset + 1f, 0f, 1f);
                }
                applySlideOffset(expand);
            }
        });

        onBindViews();
        applySlideOffset(0f);
        UIInfoLog.d("RootMediaPlayer.attach", "miniHost children=" + miniHost.getChildCount()
                + " full=" + (fullView != null)
                + " mini=" + (miniView != null));
    }

    /** Legacy single-arg — no-op safety */
    public void attachToSheet(@NonNull View sheetContainer) {
        UIInfoLog.d("RootMediaPlayer", "attachToSheet(single) needs miniHost — call 2-arg version");
    }

    public void attachToHosts(@NonNull FrameLayout miniHost, @NonNull FrameLayout fullHost) {
        attachToSheet(fullHost, miniHost);
    }

    /**
     * MultiSliding-style fadeStart=0.25 cross-fade + nav.
     * expand 0 = mini only (fixed host VISIBLE), 1 = full only.
     */
    private void applySlideOffset(float expand) {
        lastExpand = MathUtils.clamp(expand, 0f, 1f);
        final float fadeStart = 0.25f;

        float miniAlpha;
        float fullAlpha;
        if (lastExpand <= fadeStart) {
            miniAlpha = 1f;
            fullAlpha = 0f;
        } else {
            float t = (lastExpand - fadeStart) / (1f - fadeStart);
            miniAlpha = 1f - t;
            fullAlpha = t;
        }

        if (miniHost != null) {
            // Fixed mini: visible whenever not fully expanded
            if (lastExpand < 0.98f && currentSong != null && panelUiState != STATE_HIDDEN) {
                miniHost.setVisibility(VISIBLE);
            } else if (lastExpand >= 0.98f) {
                miniHost.setVisibility(GONE);
            }
            miniHost.setAlpha(miniAlpha);
            UIInfoLog.d("RootMediaPlayer.miniHost",
                    "vis=" + miniHost.getVisibility()
                            + " alpha=" + miniHost.getAlpha()
                            + " w=" + miniHost.getWidth()
                            + " h=" + miniHost.getHeight()
                            + " y=" + miniHost.getY());
        }
        if (miniView != null) {
            miniView.setAlpha(1f); // host handles fade
            miniView.setVisibility(VISIBLE);
        }
        if (fullView != null) {
            fullView.setAlpha(Math.max(fullAlpha, lastExpand > 0.02f ? 0.15f : 0f));
            fullView.setVisibility(lastExpand > 0.02f ? VISIBLE : INVISIBLE);
            if (lastExpand >= 0.98f) {
                fullView.setAlpha(1f);
                fullView.setVisibility(VISIBLE);
            }
        }
        PlayerChromeController.onSlide(lastExpand);
    }

    private void onBindViews() {
        mediaPlayerView = new MediaPlayerView(this, this);
        // Bar binds to miniHost content
        View barRoot = miniHost != null ? miniHost : this;
        mediaPlayerBarView = new MediaPlayerBarView(barRoot, this);

        FrameLayout lyricsSheet = findViewById(R.id.media_player_bottom_sheet_behavior);
        if (lyricsSheet != null) {
            CustomBottomSheetBehavior<FrameLayout> lyricsBehavior =
                    CustomBottomSheetBehavior.from(lyricsSheet);
            lyricsBehavior.setState(CustomBottomSheetBehavior.STATE_COLLAPSED);
            bottomSheetView = new BottomSheetView(this, lyricsBehavior, this);
            lyricsBehavior.addBottomSheetCallback(
                    new CustomBottomSheetBehavior.BottomSheetCallback() {
                        @Override
                        public void onStateChanged(@NonNull View bottomSheet, int oldState, int newState) {
                            UIInfoLog.d("RootMediaPlayer.lyrics", "state " + oldState + "→" + newState);
                        }

                        @Override
                        public void onSlide(@NonNull View bottomSheet, float slideOffset) {
                        }
                    });
        }
        syncUIState();
    }

    private void syncUIState() {
        if (currentSong != null) {
            if (mediaPlayerBarView != null) mediaPlayerBarView.onSongChanged(currentSong);
            if (mediaPlayerView != null) mediaPlayerView.onSongChanged(currentSong);
        }
        if (mediaPlayerBarView != null) {
            mediaPlayerBarView.onPlaybackStateChanged(currentIsPlaying, currentPlaybackState, currentPositionMs);
        }
        if (mediaPlayerView != null) {
            mediaPlayerView.onPlaybackStateChanged(currentIsPlaying, currentPlaybackState, currentPositionMs);
        }
    }

    public int getPanelState() {
        if (panelUiState == STATE_COLLAPSED || panelUiState == STATE_EXPANDED || panelUiState == STATE_HIDDEN) {
            return panelUiState;
        }
        if (sheetBehavior == null) return STATE_HIDDEN;
        int s = sheetBehavior.getState();
        if (s == BottomSheetBehavior.STATE_EXPANDED) return STATE_EXPANDED;
        if (s == BottomSheetBehavior.STATE_HIDDEN) {
            return currentSong != null ? STATE_COLLAPSED : STATE_HIDDEN;
        }
        return s;
    }

    public boolean isUserHidden() {
        return getPanelState() == STATE_HIDDEN;
    }

    public void showMiniPlayerCollapsed() {
        if (miniHost == null) return;
        panelUiState = STATE_COLLAPSED;
        miniHost.setVisibility(VISIBLE);
        miniHost.setAlpha(1f);
        if (miniView != null) {
            miniView.setVisibility(VISIBLE);
            miniView.setAlpha(1f);
        }
        if (sheetBehavior != null) {
            sheetBehavior.setState(BottomSheetBehavior.STATE_HIDDEN);
        }
        applySlideOffset(0f);
        if (currentSong != null && mediaPlayerBarView != null) {
            mediaPlayerBarView.onSongChanged(currentSong);
        }
        UIInfoLog.d("RootMediaPlayer", "showMini FIXED host vis=" + miniHost.getVisibility()
                + " alpha=" + miniHost.getAlpha()
                + " h=" + miniHost.getHeight()
                + " children=" + miniHost.getChildCount());
        PlayerChromeController.onSheetStateChanged(BottomSheetBehavior.STATE_COLLAPSED);
    }

    public void hideMiniPlayer() {
        panelUiState = STATE_HIDDEN;
        if (miniHost != null) miniHost.setVisibility(GONE);
        if (sheetBehavior != null) sheetBehavior.setState(BottomSheetBehavior.STATE_HIDDEN);
        applySlideOffset(0f);
        PlayerChromeController.onSheetStateChanged(BottomSheetBehavior.STATE_HIDDEN);
        UIInfoLog.d("RootMediaPlayer", "hide HIDDEN");
    }

    public void expandPlayer() {
        panelUiState = STATE_EXPANDED;
        if (sheetBehavior != null) {
            if (sheetContainer != null) sheetContainer.setVisibility(VISIBLE);
            sheetBehavior.setState(BottomSheetBehavior.STATE_EXPANDED);
        }
        applySlideOffset(1f);
        UIInfoLog.d("RootMediaPlayer", "expand EXPANDED");
    }

    public void collapsePlayer() {
        showMiniPlayerCollapsed();
        UIInfoLog.d("RootMediaPlayer", "collapse → mini");
    }

    public BottomSheetView getBottomSheetView() {
        return bottomSheetView;
    }

    public void onPlaybackStateChanged(boolean isPlaying, int playbackState, long positionMs) {
        currentIsPlaying = isPlaying;
        currentPlaybackState = playbackState;
        currentPositionMs = positionMs;
        runOnUi(() -> {
            if (mediaPlayerBarView != null) {
                mediaPlayerBarView.onPlaybackStateChanged(isPlaying, playbackState, positionMs);
            }
            if (mediaPlayerView != null) {
                mediaPlayerView.onPlaybackStateChanged(isPlaying, playbackState, positionMs);
            }
        });
    }

    public void onSongChanged(@Nullable Song song) {
        currentSong = song;
        if (song == null) return;
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
        isPartyClient = enabled;
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

    public FragmentManager getSupportFragmentManager() {
        return ((FragmentActivity) getContext()).getSupportFragmentManager();
    }

    public Lifecycle getLifecycle() {
        return ((FragmentActivity) getContext()).getLifecycle();
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
}
