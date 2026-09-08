package com.giga.tech1000.media_player;

import android.content.ComponentName;
import android.content.Context;
import android.os.Bundle;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.OptIn;
import androidx.media3.common.FlagSet;
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

import java.util.concurrent.ExecutionException;

/**
 * Manages the connection to a MediaLibraryService.
 * Implements both MediaController.Listener and Player.Listener to capture all events.
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

    private final Context context;
    private final ControllerEventsListener eventsListener;

    private MediaController mediaController;
    private ListenableFuture<MediaController> controllerFuture;

    public MediaPlayerController(Context context, ControllerEventsListener eventsListener) {
        this.context = context;
        this.eventsListener = eventsListener;
    }

    public void onStart() {
        if (mediaController != null) return;

       // Log.d("ASSSSSSSSSS", "onStart: Initializing MediaController connection");
        SessionToken sessionToken = new SessionToken(context, new ComponentName(context, MediaPlayerService.class));
        controllerFuture = new MediaController.Builder(context, sessionToken)
                .setListener(this)
                .buildAsync();

        controllerFuture.addListener(() -> {
            try {
                mediaController = controllerFuture.get();
                //Log.d("ASSSSSSSSSS", "MediaController connected successfully");
                mediaController.addListener(this);
                
                // Initial sync: report current item and state immediately upon connection
                eventsListener.onMediaItemTransition(mediaController.getCurrentMediaItem(), Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED);
                eventsListener.onPlaybackStateChanged(mediaController.getPlayWhenReady(), mediaController.getPlaybackState());
                eventsListener.onRepeatModeChanged(mediaController.getRepeatMode());
                eventsListener.onShuffleModeEnabledChanged(mediaController.getShuffleModeEnabled());

            } catch (ExecutionException | InterruptedException e) {
                Log.e(TAG, "Failed to connect to MediaController", e);
            }
        }, MoreExecutors.directExecutor());
    }

    public void onDestroy() {
        if (controllerFuture != null) {
            MediaController.releaseFuture(controllerFuture);
            controllerFuture = null;
        }
        if (mediaController != null) {
            mediaController.removeListener(this);
            mediaController = null;
        }
    }

    public MediaController getMediaController() {
        return mediaController;
    }

    public ListenableFuture<MediaController> getControllerFuture() {
        return controllerFuture;
    }

    // --- Player.Listener Implementation (Playback Events) ---

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
            eventsListener.onPlaybackStateChanged(mediaController.getPlayWhenReady(), mediaController.getPlaybackState());
        }
    }

    @Override
    public void onEvents(@NonNull Player player, @NonNull Player.Events events) {
        if (events.contains(Player.EVENT_PLAYBACK_STATE_CHANGED)
                || events.contains(Player.EVENT_PLAY_WHEN_READY_CHANGED)) {

            eventsListener.onPlaybackStateChanged(
                    player.getPlayWhenReady(),   // ✅ ALWAYS use this here
                    player.getPlaybackState()
            );
        }

        if (events.contains(Player.EVENT_MEDIA_ITEM_TRANSITION)) {
            eventsListener.onMediaItemTransition(
                    player.getCurrentMediaItem(),
                    Player.MEDIA_ITEM_TRANSITION_REASON_AUTO
            );
        }

        if (events.contains(Player.EVENT_REPEAT_MODE_CHANGED)) {
            eventsListener.onRepeatModeChanged(player.getRepeatMode());
        }

        if (events.contains(Player.EVENT_SHUFFLE_MODE_ENABLED_CHANGED)) {
            eventsListener.onShuffleModeEnabledChanged(player.getShuffleModeEnabled());
        }
    }

    // --- MediaController.Listener Implementation (Session Events) ---

    @NonNull
    @Override
    public ListenableFuture<SessionResult> onCustomCommand(
            @NonNull MediaController controller,
            @NonNull SessionCommand command,
            @NonNull Bundle args
    ) {
        eventsListener.onCustomCommand(command, args);
        return Futures.immediateFuture(new SessionResult(SessionResult.RESULT_SUCCESS));
    }
}
