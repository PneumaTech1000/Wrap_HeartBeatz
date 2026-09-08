package com.giga.tech1000.media_player.models.cross_ref;

import androidx.room.Embedded;
import androidx.room.Relation;

import com.giga.tech1000.media_player.models.Album;
import com.giga.tech1000.media_player.models.Song;

import java.util.List;

public class AlbumWithSongs {
    @Embedded
    public Album album;

    @Relation(
            parentColumn = "albumId",
            entityColumn = "albumId"
    )
    public List<Song> songs;

    public List<Song> getSongs() { return songs; }
}

