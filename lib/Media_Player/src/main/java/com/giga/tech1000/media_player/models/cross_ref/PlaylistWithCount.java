package com.giga.tech1000.media_player.models.cross_ref;

import androidx.room.ColumnInfo;
import androidx.room.Embedded;

import com.giga.tech1000.media_player.models.Playlist;

public class PlaylistWithCount {

    @Embedded
    public Playlist playlist;

    @ColumnInfo(name = "numOfSongs")
    public int numOfSongs;

    public Playlist getPlaylist() {
        return playlist;
    }

    public void setPlaylist(Playlist playlist) {
        this.playlist = playlist;
    }

    public int getNumOfSongs() {
        return numOfSongs;
    }

    public void setNumOfSongs(int numOfSongs) {
        this.numOfSongs = numOfSongs;
    }
}

