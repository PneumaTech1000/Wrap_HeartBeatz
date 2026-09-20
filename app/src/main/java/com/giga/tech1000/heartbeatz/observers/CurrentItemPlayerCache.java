package com.giga.tech1000.heartbeatz.observers;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.media3.common.Player;

import com.giga.tech1000.media_player.models.Song;
import com.giga.tech1000.media_player.models.extended_models.PlayerCacheModel;
import com.giga.tech1000.media_player.utils.enums.ItemSource;

public class CurrentItemPlayerCache {

    private final MutableLiveData<PlayerCacheModel> result = new MutableLiveData<>();
    private final MutableLiveData<Long> progress = new MutableLiveData<>();

    private int playbackState = Player.STATE_IDLE;
    private boolean isPlaying;
    private Song song;
    private ItemSource itemSource = null;

    private PlayerCacheModel lastEmitted = null;

    public void cachePlaybackStateChanged(int state) {
        update(song, state, isPlaying, itemSource);
    }

    public void cachePlaybackStateChanged(boolean playing, int state) {
        update(song, state, playing, itemSource);
    }

    public void cachePlayingSong(Song s) {
        update(s, playbackState, isPlaying, itemSource);
    }

    public void cachePlayerItemSource(@NonNull ItemSource s) {
        update(song, playbackState, isPlaying, s);
    }

    public void updateProgress(long position) {
        progress.postValue(position);
    }

    private void update(Song newSong, int newState, boolean playing, ItemSource newSource) {
        song = newSong;
        playbackState = newState;
        isPlaying = playing;
        itemSource = newSource;

        if (song == null) return;

        PlayerCacheModel current =
                new PlayerCacheModel(song, playbackState, isPlaying, itemSource);

        if (!current.equals(lastEmitted)) {
            lastEmitted = current;
            result.postValue(current);
        }
    }

    public LiveData<PlayerCacheModel> getPlayerCacheInfo() {
        return result;
    }

    public LiveData<Long> getProgress() {
        return progress;
    }
}
