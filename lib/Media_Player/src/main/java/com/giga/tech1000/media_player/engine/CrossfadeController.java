package com.giga.tech1000.media_player.engine;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.OptIn;
import androidx.media3.common.C;
import androidx.media3.common.Player;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.exoplayer.ExoPlayer;

/**
 * Single-player crossfade: fade out near end of item, advance, fade in on next.
 * Works with Media3 playlist gapless preload when {@link PlaybackTransitionSettings#isGaplessEnabled()}.
 * <p>
 * Disabled automatically for party client streams (short buffers / seek-heavy).
 */
@OptIn(markerClass = UnstableApi.class)
public final class CrossfadeController implements Player.Listener {

    private static final String TAG = "CrossfadeController";
    private static final long TICK_MS = 50L;

    private final Handler main = new Handler(Looper.getMainLooper());
    private final PlaybackTransitionSettings settings;

    @Nullable private ExoPlayer player;
    private boolean enabled = true;
    private boolean fadingOut;
    private boolean fadingIn;
    private int lastMediaIndex = C.INDEX_UNSET;
    private float targetVolume = 1f;

    private final Runnable tick = this::onTick;

    public CrossfadeController(@NonNull PlaybackTransitionSettings settings) {
        this.settings = settings;
    }

    public void attach(@NonNull ExoPlayer player) {
        detach();
        this.player = player;
        player.addListener(this);
        player.setVolume(1f);
        targetVolume = 1f;
        main.post(tick);
        Log.i(TAG, "attached crossfadeMs=" + settings.getCrossfadeMs()
                + " gapless=" + settings.isGaplessEnabled());
    }

    public void detach() {
        main.removeCallbacks(tick);
        if (player != null) {
            try {
                player.removeListener(this);
                player.setVolume(1f);
            } catch (Exception ignored) { }
        }
        player = null;
        fadingOut = false;
        fadingIn = false;
    }

    /** Party guest / Live Lock: disable volume automation. */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        if (!enabled && player != null) {
            fadingOut = false;
            fadingIn = false;
            player.setVolume(1f);
        }
    }

    public void onSettingsChanged() {
        // Next tick picks up new crossfade length
        Log.i(TAG, "settings changed crossfadeMs=" + settings.getCrossfadeMs());
    }

    @Override
    public void onMediaItemTransition(@Nullable androidx.media3.common.MediaItem mediaItem, int reason) {
        ExoPlayer p = player;
        if (p == null) return;
        int idx = p.getCurrentMediaItemIndex();
        if (idx != lastMediaIndex) {
            lastMediaIndex = idx;
            if (enabled && settings.getCrossfadeMs() > 0 && reason != Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED) {
                // Fade in on new item
                fadingOut = false;
                fadingIn = true;
                p.setVolume(0f);
            } else {
                p.setVolume(1f);
                fadingIn = false;
            }
        }
    }

    private void onTick() {
        ExoPlayer p = player;
        if (p == null) return;
        try {
            if (enabled) {
                int fadeMs = settings.getCrossfadeMs();
                if (fadeMs > 0 && p.getPlaybackState() == Player.STATE_READY && p.getPlayWhenReady()) {
                    long duration = p.getDuration();
                    long position = p.getCurrentPosition();
                    if (duration > 0 && duration != C.TIME_UNSET) {
                        long remain = duration - position;
                        if (!fadingIn && remain > 0 && remain <= fadeMs && p.hasNextMediaItem()) {
                            fadingOut = true;
                            float vol = Math.max(0f, Math.min(1f, remain / (float) fadeMs));
                            p.setVolume(vol);
                            targetVolume = vol;
                            // When nearly silent, advance so gapless engine can continue
                            if (remain <= TICK_MS * 2L && vol <= 0.05f) {
                                fadingOut = false;
                                p.seekToNextMediaItem();
                                // onMediaItemTransition starts fade-in
                            }
                        } else if (fadingIn) {
                            float step = TICK_MS / (float) Math.max(fadeMs / 2, 200);
                            float v = p.getVolume() + step;
                            if (v >= 1f) {
                                v = 1f;
                                fadingIn = false;
                            }
                            p.setVolume(v);
                            targetVolume = v;
                        } else if (!fadingOut) {
                            if (p.getVolume() < 0.99f) {
                                p.setVolume(1f);
                            }
                            targetVolume = 1f;
                        }
                    }
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "tick: " + e.getMessage());
        }
        main.postDelayed(tick, TICK_MS);
    }
}
