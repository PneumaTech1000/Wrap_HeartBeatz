package com.giga.tech1000.media_player.models.cross_ref;

import androidx.room.Embedded;
import androidx.room.Relation;

import com.giga.tech1000.media_player.models.Artist;
import com.giga.tech1000.media_player.models.Song;

import java.util.List;

public class ArtistWithSongs {
    @Embedded
    public Artist artist;

    @Relation(
            parentColumn = "artistId",
            entityColumn = "artistId"
    )
    public List<Song> songs;

    public List<Song> getSongs() { return songs; }
}

