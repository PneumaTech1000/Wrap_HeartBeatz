package com.giga.tech1000.media_player.models.extended_models;

import com.giga.tech1000.media_player.models.Song;
import com.giga.tech1000.media_player.utils.enums.ItemSource;

import java.util.Objects;

/**
 * Snapshot of current playback for list UI.
 * {@code playerState} is {@link androidx.media3.common.Player} state;
 * {@code isPlaying} is the Media3 play-when-ready / actually playing flag.
 */
public class PlayerCacheModel {

    private final int playerState;
    private final boolean isPlaying;
    private final Song song;
    private final ItemSource source;

    public PlayerCacheModel(Song song, int playerState, ItemSource source) {
        this(song, playerState, false, source);
    }

    public PlayerCacheModel(Song song, int playerState, boolean isPlaying, ItemSource source) {
        this.song = song;
        this.playerState = playerState;
        this.isPlaying = isPlaying;
        this.source = source;
    }

    /** Media3 {@link androidx.media3.common.Player} state (IDLE, BUFFERING, READY, ENDED). */
    public int getState() {
        return playerState;
    }

    public boolean isPlaying() {
        return isPlaying;
    }

    public Song getCurrentSong() {
        return song;
    }

    public ItemSource getSource() {
        return source;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof PlayerCacheModel that)) return false;
        return playerState == that.playerState
                && isPlaying == that.isPlaying
                && Objects.equals(song, that.song)
                && source == that.source;
    }

    @Override
    public int hashCode() {
        return Objects.hash(playerState, isPlaying, song, source);
    }
}
