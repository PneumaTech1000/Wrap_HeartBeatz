package com.giga.tech1000.media_player.models;

import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(
        tableName = "artists",
        indices = {@Index(value = {"name"})}
)
public class Artist {

    @PrimaryKey
    @ColumnInfo(name = "artistId")
    private long id;
    private String name;
    private int albumCount;
    private int trackCount;

    private Uri artistArtUri;

    public Artist(long id, String name, int albumCount, int trackCount, Uri uri) {
        this.id = id;
        this.name = name;
        this.albumCount = albumCount;
        this.trackCount = trackCount;
        this.artistArtUri = uri;
    }

    public Artist() {
    }

    public long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public int getAlbumCount() {
        return albumCount;
    }

    public int getTrackCount() {
        return trackCount;
    }

    public void setId(long id) {
        this.id = id;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setAlbumCount(int albumCount) {
        this.albumCount = albumCount;
    }

    public void setTrackCount(int trackCount) {
        this.trackCount = trackCount;
    }

    public Uri getArtistArtUri() {
        return artistArtUri;
    }

    public void setArtistArtUri(Uri uri) {
        this.artistArtUri = uri;
    }


    @NonNull
    @Override
    public String toString() {
        return "Artist{" +
                "id=" + id +
                ", name='" + name + '\'' +
                ", albumCount=" + albumCount +
                ", trackCount=" + trackCount +
                '}';
    }
}
