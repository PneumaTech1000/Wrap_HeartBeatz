package com.giga.tech1000.media_player.utils;

import android.media.AudioManager;
import androidx.annotation.NonNull;
import androidx.annotation.OptIn;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.common.util.UnstableApi;
import com.giga.tech1000.media_player.PlaybackManager;

@OptIn(markerClass = UnstableApi.class)
public class PlaybackListener implements AudioManager.OnAudioFocusChangeListener, Player.Listener {

    private final PlaybackManager playbackManager;

    public PlaybackListener(PlaybackManager playbackManager) {
        this.playbackManager = playbackManager;
    }

    @Override
    public void onAudioFocusChange(int focusChange) {
        playbackManager.onAudioFocusChanged(focusChange);
    }

    @Override
    public void onPlaybackStateChanged(int state) {
        playbackManager.onUpdatePlaybackState();
        if (state == Player.STATE_ENDED) {
            playbackManager.onAudioCompleted();
        }
    }

    @Override
    public void onPlayWhenReadyChanged(boolean playWhenReady, int reason) {
        Player.Listener.super.onPlayWhenReadyChanged(playWhenReady, reason);
    }

    @Override
    public void onIsPlayingChanged(boolean isPlaying) {
        if (isPlaying) {
            playbackManager.getThread().start();
        } else {
            playbackManager.getThread().stop();
        }
    }

    @Override
    public void onAudioSessionIdChanged(int audioSessionId) {
        playbackManager.setSessionId(audioSessionId);
    }

    @Override
    public void onPlayerError(@NonNull PlaybackException error) {
        // Log and handle error properly in a production app
    }
}
