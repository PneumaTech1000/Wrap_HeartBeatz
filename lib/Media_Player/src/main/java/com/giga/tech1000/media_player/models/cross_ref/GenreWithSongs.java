package com.giga.tech1000.media_player.models.cross_ref;

import androidx.room.Embedded;
import androidx.room.Relation;

import com.giga.tech1000.media_player.models.Genre;
import com.giga.tech1000.media_player.models.Song;

import java.util.List;

public class GenreWithSongs {

    @Embedded
    public Genre genre;

    @Relation(
            entity = Song.class,
            parentColumn = "genreId",
            entityColumn = "genreId"
    )
    public List<Song> songs;

    public List<Song> getSongs() { return songs; }
}

