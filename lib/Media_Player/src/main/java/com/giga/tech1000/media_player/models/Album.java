package com.giga.tech1000.media_player.models;

import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(
        tableName = "albums",
        indices = {
                @Index(value = {"name"}),
                @Index(value = {"artist"})
        }
)
public class Album {

    @PrimaryKey
    @ColumnInfo(name = "albumId")
    private long id;
    private String name;
    private String artist;
    private Uri artUri;
    private int songCount;



    public Album(long id, String name, String artist, Uri artUri, int songCount) {
        this.id = id;
        this.name = name;
        this.artist = artist;
        this.artUri = artUri;
        this.songCount = songCount;
    }

    public Album() {}



    public long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getArtist() {
        return artist;
    }

    public Uri getArtUri() {
        return artUri;
    }

    public int getSongCount() {
        return songCount;
    }

    public void setId(long id) {
        this.id = id;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setArtist(String artist) {
        this.artist = artist;
    }

    public void setArtUri(Uri artUri) {
        this.artUri = artUri;
    }

    public void setSongCount(int songCount) {
        this.songCount = songCount;
    }


    @NonNull
    @Override
    public String toString() {
        return "Album{" +
                "id=" + id +
                ", name='" + name + '\'' +
                ", artist='" + artist + '\'' +
                ", artUri=" + artUri +
                ", songCount=" + songCount +
                '}';
    }
}
