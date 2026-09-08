package com.giga.tech1000.media_player.models.cross_ref;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Embedded;

import com.giga.tech1000.media_player.models.Genre;

public class GenreWithCount {

    @Embedded
    public Genre genre;

    @ColumnInfo(name = "songCount")
    public int songCount;

    public GenreWithCount() { }

    public Genre getGenre() {
        return genre;
    }

    public void setGenre(Genre genre) {
        this.genre = genre;
    }

    public int getSongCount() {
        return songCount;
    }

    public void setSongCount(int songCount) {
        this.songCount = songCount;
    }

    @NonNull
    @Override
    public String toString() {
        return "GenreWithCount{" +
                "genre=" + genre +
                ", songCount=" + songCount +
                '}';
    }
}

