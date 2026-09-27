package com.giga.tech1000.media_player;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.OptIn;
import androidx.core.content.ContextCompat;
import androidx.media3.common.MediaItem;
import androidx.media3.common.Player;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.session.MediaController;
import androidx.media3.session.SessionCommand;
import androidx.media3.session.SessionResult;
import androidx.media3.session.SessionToken;

import com.giga.tech1000.media_player.services.MediaPlayerService;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Manages the connection to {@link MediaPlayerService}.
 * Supports safe reconnect when the controller was never started or was released
 * (e.g. after process lifecycle edge cases).
 */
@OptIn(markerClass = UnstableApi.class)
public class MediaPlayerController implements MediaController.Listener, Player.Listener {

    private static final String TAG = "MediaPlayerController";

    public interface ControllerEventsListener {
        void onMediaItemTransition(@Nullable MediaItem mediaItem, int reason);
        void onPlaybackStateChanged(boolean playWhenReady, int state);
        void onRepeatModeChanged(int repeatMode);
        void onShuffleModeEnabledChanged(boolean enabled);
        void onCustomCommand(@NonNull SessionCommand command, @NonNull Bundle args);
    }

    public interface OnConnectedListener {
        void onConnected(@NonNull MediaController controller);
        void onFailed(@NonNull Exception error);
    }

    private final Context context;
    private final ControllerEventsListener eventsListener;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private MediaController mediaController;
    private ListenableFuture<MediaController> controllerFuture;
    private final AtomicBoolean connecting = new AtomicBoolean(false);
    private final List<OnConnectedListener> pending = new ArrayList<>();

    public MediaPlayerController(Context context, ControllerEventsListener eventsListener) {
        this.context = context.getApplicationContext();
        this.eventsListener = eventsListener;
    }

    public void onStart() {
        ensureConnected(null);
    }

    /**
     * Connect if needed. Invokes {@code listener} on main thread when ready (or on failure).
     */
    public void ensureConnected(@Nullable OnConnectedListener listener) {
        if (mediaController != null) {
            if (listener != null) {
                mainHandler.post(() -> listener.onConnected(mediaController));
            }
            return;
        }

        if (listener != null) {
            synchronized (pending) {
                pending.add(listener);
            }
        }

        if (!connecting.compareAndSet(false, true)) {
            // Already connecting — pending listeners will be notified
            return;
        }

        // MediaController SessionToken binds the service; avoid startForegroundService here
        // (FGS from background is restricted on Android 12+).

        try {
            SessionToken sessionToken = new SessionToken(
                    context, new ComponentName(context, MediaPlayerService.class));
            controllerFuture = new MediaController.Builder(context, sessionToken)
                    .setListener(this)
                    .buildAsync();

            controllerFuture.addListener(() -> {
                try {
                    MediaController c = Futures.getDone(controllerFuture);
                    mediaController = c;
                    c.addListener(this);
                    Log.i(TAG, "MediaController connected");

                    eventsListener.onMediaItemTransition(
                            c.getCurrentMediaItem(),
                            Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED);
                    eventsListener.onPlaybackStateChanged(c.getPlayWhenReady(), c.getPlaybackState());
                    eventsListener.onRepeatModeChanged(c.getRepeatMode());
                    eventsListener.onShuffleModeEnabledChanged(c.getShuffleModeEnabled());

                    notifyPendingSuccess(c);
                } catch (Exception e) {
                    Log.e(TAG, "Failed to connect to MediaController", e);
                    mediaController = null;
                    controllerFuture = null;
                    notifyPendingFailure(e instanceof Exception ? (Exception) e : new Exception(e));
                } finally {
                    connecting.set(false);
                }
            }, MoreExecutors.directExecutor());
        } catch (Exception e) {
            Log.e(TAG, "ensureConnected build failed", e);
            connecting.set(false);
            controllerFuture = null;
            notifyPendingFailure(e);
        }
    }

    private void notifyPendingSuccess(@NonNull MediaController c) {
        List<OnConnectedListener> copy;
        synchronized (pending) {
            copy = new ArrayList<>(pending);
            pending.clear();
        }
        mainHandler.post(() -> {
            for (OnConnectedListener l : copy) {
                try {
                    l.onConnected(c);
                } catch (Exception ignored) {
                }
            }
        });
    }

    private void notifyPendingFailure(@NonNull Exception e) {
        List<OnConnectedListener> copy;
        synchronized (pending) {
            copy = new ArrayList<>(pending);
            pending.clear();
        }
        mainHandler.post(() -> {
            for (OnConnectedListener l : copy) {
                try {
                    l.onFailed(e);
                } catch (Exception ignored) {
                }
            }
        });
    }

    public void onDestroy() {
        connecting.set(false);
        synchronized (pending) {
            pending.clear();
        }
        if (controllerFuture != null) {
            try {
                MediaController.releaseFuture(controllerFuture);
            } catch (Exception ignored) {
            }
            controllerFuture = null;
        }
        if (mediaController != null) {
            try {
                mediaController.removeListener(this);
                mediaController.release();
            } catch (Exception ignored) {
            }
            mediaController = null;
        }
    }

    public MediaController getMediaController() {
        return mediaController;
    }

    public ListenableFuture<MediaController> getControllerFuture() {
        return controllerFuture;
    }

    @Override
    public void onMediaItemTransition(@Nullable MediaItem mediaItem, int reason) {
        eventsListener.onMediaItemTransition(mediaItem, reason);
    }

    @Override
    public void onPlaybackStateChanged(@Player.State int playbackState) {
        if (mediaController != null) {
            eventsListener.onPlaybackStateChanged(mediaController.getPlayWhenReady(), playbackState);
        }
    }

    @Override
    public void onPlayWhenReadyChanged(boolean playWhenReady, int reason) {
        if (mediaController != null) {
            eventsListener.onPlaybackStateChanged(playWhenReady, mediaController.getPlaybackState());
        }
    }

    @Override
    public void onIsPlayingChanged(boolean isPlaying) {
        if (mediaController != null) {
            eventsListener.onPlaybackStateChanged(
                    mediaController.getPlayWhenReady(), mediaController.getPlaybackState());
        }
    }

    @Override
    public void onEvents(@NonNull Player player, @NonNull Player.Events events) {
        if (events.contains(Player.EVENT_PLAYBACK_STATE_CHANGED)
                || events.contains(Player.EVENT_PLAY_WHEN_READY_CHANGED)) {
            eventsListener.onPlaybackStateChanged(player.getPlayWhenReady(), player.getPlaybackState());
        }
        if (events.contains(Player.EVENT_MEDIA_ITEM_TRANSITION)) {
            eventsListener.onMediaItemTransition(
                    player.getCurrentMediaItem(),
                    Player.MEDIA_ITEM_TRANSITION_REASON_AUTO);
        }
        if (events.contains(Player.EVENT_REPEAT_MODE_CHANGED)) {
            eventsListener.onRepeatModeChanged(player.getRepeatMode());
        }
        if (events.contains(Player.EVENT_SHUFFLE_MODE_ENABLED_CHANGED)) {
            eventsListener.onShuffleModeEnabledChanged(player.getShuffleModeEnabled());
        }
    }

    @NonNull
    @Override
    public ListenableFuture<SessionResult> onCustomCommand(
            @NonNull MediaController controller,
            @NonNull SessionCommand command,
            @NonNull Bundle args) {
        eventsListener.onCustomCommand(command, args);
        return Futures.immediateFuture(new SessionResult(SessionResult.RESULT_SUCCESS));
    }
}
