package com.giga.tech1000.heartbeatz.observers;

import androidx.media3.session.legacy.PlaybackStateCompat;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.giga.tech1000.media_player.models.Song;
import com.giga.tech1000.media_player.models.extended_models.PlayerCacheModel;
import com.giga.tech1000.media_player.utils.enums.ItemSource;

public class CurrentItemPlayerCache {

    private final MutableLiveData<PlayerCacheModel> result = new MutableLiveData<>();
    private final MutableLiveData<Long> progress = new MutableLiveData<>();

    private int playbackState = PlaybackStateCompat.STATE_NONE;
    private Song song;
    private ItemSource itemSource = null;

    private PlayerCacheModel lastEmitted = null;

    public void cachePlaybackStateChanged(int state) {
        update(song, state, itemSource);
    }

    public void cachePlayingSong(Song s) {
        update(s, playbackState, itemSource);
    }

    public void cachePlayerItemSource(@NonNull ItemSource s) {
        update(song, playbackState, s);
    }

    public void updateProgress(long position) {
        progress.postValue(position);
    }

    private void update(Song newSong, int newState, ItemSource newSource) {
        song = newSong;
        playbackState = newState;
        itemSource = newSource;

        // Not ready yet
        if (song == null) return;

        PlayerCacheModel current =
                new PlayerCacheModel(song, playbackState, itemSource);

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
