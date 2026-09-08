package com.giga.tech1000.heartbeatz.ui;

import androidx.annotation.OptIn;
import androidx.media3.common.util.UnstableApi;

import com.giga.tech1000.heartbeatz.MainActivity;
import com.giga.tech1000.media_player.CorePlayer;
import com.giga.tech1000.media_player.interfaces.IPlaybackCallback;
import com.giga.tech1000.media_player.interfaces.IPlayerCallback;
import com.giga.tech1000.media_player.models.Song;


@OptIn(markerClass = UnstableApi.class)
public class MediaPlayerThread {

    private final CorePlayer corePlayer;
    private final IPlayerCallback callback;

    public MediaPlayerThread(MainActivity activity, IPlaybackCallback uiCallback) {
        this.corePlayer = new CorePlayer(activity, uiCallback);
        this.callback = this.corePlayer.getCallback();
    }

    public CorePlayer getCorePlayer() { return corePlayer; }

    public IPlayerCallback getCallback() { return this.callback; }

    public Song getCurrentSong() {
        return this.corePlayer != null ? this.corePlayer.getCurrentSong() : null;
    }

    public void onStart() { this.corePlayer.onStart(); }
    public void onDestroy() { this.corePlayer.onDestroy(); }
}
