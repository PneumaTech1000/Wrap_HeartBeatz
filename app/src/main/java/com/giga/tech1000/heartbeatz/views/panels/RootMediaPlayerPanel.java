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
 * Single Material BottomSheet for player chrome.
 * <ul>
 *   <li>COLLAPSED — mini visible (peek), full faded out, nav shown</li>
 *   <li>EXPANDED — full visible, mini faded out, nav hidden</li>
 *   <li>HIDDEN — no active track</li>
 * </ul>
 * {@link #applySlideOffset(float)} drives mini/full cross-fade + bottom nav with one offset.
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

    private long lastPlaybackUiLogMs;
    private Song currentSong;
    private boolean currentIsPlaying;
    private int currentPlaybackState = Player.STATE_IDLE;
    private long currentPositionMs;
    private boolean isPartyClient;

    @Nullable private BottomSheetBehavior<View> sheetBehavior;
    @Nullable private View sheetContainer;

    private View miniView;
    private View fullView;
    private float lastExpand = 0f;

    public RootMediaPlayerPanel(@NonNull Context context) {
        super(context);
        getContext().setTheme(R.style.Theme_HeartBeatz);
        LayoutInflater.from(getContext()).inflate(R.layout.mediaplayer_root_layout, this, true);
    }

    private void runOnUi(@NonNull Runnable r) {
        if (Looper.myLooper() == Looper.getMainLooper()) r.run();
        else mainHandler.post(r);
    }

    /**
     * Attach into {@code R.id.player_bottom_sheet} and bind coordinated slide.
     */
    public void attachToSheet(@NonNull View sheetContainer) {
        this.sheetContainer = sheetContainer;
        if (getParent() != sheetContainer) {
            if (getParent() != null) {
                ((android.view.ViewGroup) getParent()).removeView(this);
            }
            if (sheetContainer instanceof FrameLayout) {
                ((FrameLayout) sheetContainer).addView(this,
                        new FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
            }
        }

        int peek = getResources().getDimensionPixelSize(R.dimen.bar_and_navigation_height);
        sheetBehavior = BottomSheetBehavior.from(sheetContainer);
        sheetBehavior.setFitToContents(false);
        sheetBehavior.setSkipCollapsed(false);
        sheetBehavior.setHideable(true);
        sheetBehavior.setDraggable(true);
        sheetBehavior.setPeekHeight(peek, false);
        sheetBehavior.setState(BottomSheetBehavior.STATE_HIDDEN);

        sheetBehavior.addBottomSheetCallback(new BottomSheetBehavior.BottomSheetCallback() {
            @Override
            public void onStateChanged(@NonNull View bottomSheet, int newState) {
                UIInfoLog.d("RootMediaPlayer.sheet", "state=" + newState
                        + " top=" + bottomSheet.getTop()
                        + " y=" + bottomSheet.getY()
                        + " h=" + bottomSheet.getHeight()
                        + " expand=" + lastExpand);
                if (newState == BottomSheetBehavior.STATE_COLLAPSED) {
                    applySlideOffset(0f);
                    PlayerChromeController.onSheetStateChanged(BottomSheetBehavior.STATE_COLLAPSED);
                } else if (newState == BottomSheetBehavior.STATE_EXPANDED) {
                    applySlideOffset(1f);
                    PlayerChromeController.onSheetStateChanged(BottomSheetBehavior.STATE_EXPANDED);
                } else if (newState == BottomSheetBehavior.STATE_HIDDEN) {
                    applySlideOffset(0f);
                    PlayerChromeController.onSheetStateChanged(BottomSheetBehavior.STATE_HIDDEN);
                }
            }

            @Override
            public void onSlide(@NonNull View bottomSheet, float slideOffset) {
                // Material: -1 hidden → 0 collapsed → 1 expanded
                float expand = MathUtils.clamp(slideOffset, 0f, 1f);
                applySlideOffset(expand);
            }
        });

        onBindViews();
        // Start with mini fully visible, full hidden (collapsed visuals)
        applySlideOffset(0f);
        UIInfoLog.d("RootMediaPlayer.attach", "peek=" + peek
                + " mini=" + (miniView != null)
                + " full=" + (fullView != null));
    }

    /** @deprecated same as attachToSheet for API compatibility */
    public void attachToHosts(@NonNull FrameLayout miniHost, @NonNull FrameLayout fullHost) {
        UIInfoLog.d("RootMediaPlayer", "attachToHosts ignored — use single player_bottom_sheet");
    }

    /**
     * One offset drives: mini alpha, full alpha, bottom nav (via PlayerChromeController).
     * @param expand 0 = mini only, 1 = full only
     */
    private void applySlideOffset(float expand) {
        lastExpand = MathUtils.clamp(expand, 0f, 1f);
        if (miniView != null) {
            float miniAlpha = 1f - lastExpand;
            miniView.setAlpha(miniAlpha);
            miniView.setVisibility(miniAlpha > 0.02f ? VISIBLE : GONE);
            // Keep mini clickable only when mostly collapsed
            miniView.setClickable(lastExpand < 0.15f);
        }
        if (fullView != null) {
            float fullAlpha = lastExpand;
            fullView.setAlpha(fullAlpha);
            // Keep VISIBLE during drag so cross-fade works (never blank)
            fullView.setVisibility(fullAlpha > 0.02f ? VISIBLE : INVISIBLE);
        }
        PlayerChromeController.onSlide(lastExpand);
        if (lastExpand <= 0f || lastExpand >= 1f) {
            UIInfoLog.d("RootMediaPlayer.slide", "expand=" + lastExpand
                    + " miniA=" + (miniView != null ? miniView.getAlpha() : -1)
                    + " fullA=" + (fullView != null ? fullView.getAlpha() : -1));
        }
    }

    private void onBindViews() {
        miniView = findViewById(R.id.mini_player_view);
        fullView = findViewById(R.id.media_player_view);

        mediaPlayerView = new MediaPlayerView(this, this);
        mediaPlayerBarView = new MediaPlayerBarView(this, this);

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
                            // Nested lyrics sheet only — do not touch main mini/full alphas
                            UIInfoLog.d("RootMediaPlayer.lyrics", "state " + oldState + "→" + newState);
                        }

                        @Override
                        public void onSlide(@NonNull View bottomSheet, float slideOffset) {
                            // Do not fade main player
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
        if (sheetBehavior == null) return STATE_HIDDEN;
        return sheetBehavior.getState();
    }

    public boolean isUserHidden() {
        return getPanelState() == STATE_HIDDEN;
    }

    public void showMiniPlayerCollapsed() {
        if (sheetBehavior == null || sheetContainer == null) return;
        UIInfoLog.d("RootMediaPlayer", "showMini COLLAPSED");
        sheetContainer.setVisibility(VISIBLE);
        setVisibility(VISIBLE);
        int peek = getResources().getDimensionPixelSize(R.dimen.bar_and_navigation_height);
        sheetBehavior.setPeekHeight(peek, false);
        sheetContainer.post(() -> {
            if (sheetBehavior == null) return;
            sheetBehavior.setState(BottomSheetBehavior.STATE_COLLAPSED);
            applySlideOffset(0f);
        });
    }

    public void hideMiniPlayer() {
        if (sheetBehavior == null) return;
        UIInfoLog.d("RootMediaPlayer", "hide HIDDEN");
        sheetBehavior.setState(BottomSheetBehavior.STATE_HIDDEN);
        applySlideOffset(0f);
    }

    public void expandPlayer() {
        if (sheetBehavior == null) return;
        UIInfoLog.d("RootMediaPlayer", "expand EXPANDED");
        sheetBehavior.setState(BottomSheetBehavior.STATE_EXPANDED);
    }

    public void collapsePlayer() {
        if (sheetBehavior == null) return;
        UIInfoLog.d("RootMediaPlayer", "collapse COLLAPSED");
        sheetBehavior.setState(BottomSheetBehavior.STATE_COLLAPSED);
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
