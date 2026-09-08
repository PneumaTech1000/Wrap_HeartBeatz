package com.giga.tech1000.media_player.models.extended_models;

import com.giga.tech1000.media_player.models.Song;
import com.giga.tech1000.media_player.utils.enums.ItemSource;

public class PlayerCacheModel {

    private final int state;
    private final Song song;
    private final ItemSource source;

    public PlayerCacheModel(Song song, int state, ItemSource source) {
        this.song = song;
        this.state = state;
        this.source = source;
    }

    public int getState() { return state; }
    public Song getCurrentSong() { return song; }
    public ItemSource getSource() { return source; }

}
