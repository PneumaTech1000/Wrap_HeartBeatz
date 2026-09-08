package com.giga.tech1000.media_player.models.cross_ref;

import androidx.room.Embedded;
import androidx.room.Junction;
import androidx.room.Relation;

import com.giga.tech1000.media_player.models.Playlist;
import com.giga.tech1000.media_player.models.Song;

import java.util.List;

public class PlaylistWithSongs {
    @Embedded
    public Playlist playlist;

    @Relation(
            parentColumn = "playlistId",
            entityColumn = "songId",
            associateBy = @Junction(PlaylistSongCrossRef.class)
    )
    public List<Song> songs;

    public List<Song> getSongs() { return songs; }
}

