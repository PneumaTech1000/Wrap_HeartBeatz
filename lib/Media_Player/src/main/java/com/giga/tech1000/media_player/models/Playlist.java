package com.giga.tech1000.media_player.models;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(
        tableName = "playlists",
        indices = { @Index(value = {"name"}, unique = true) }
)
public class Playlist {

    @PrimaryKey(autoGenerate = true)
    private long playlistId;

    public String name;
    public long dateAdded;

    public Playlist(long id, String name, long dateAdded) {
        this.playlistId = id;
        this.name = name;
        this.dateAdded = dateAdded;


    }

    public Playlist() {}

    public long getPlaylistId() {
        return playlistId;
    }

    public String getName() {
        return name;
    }

    public long getDateAdded() {
        return dateAdded;
    }

    public void setPlaylistId(long playlistId) {
        this.playlistId = playlistId;
    }


    @NonNull
    @Override
    public String toString() {
        return "Playlist{" +
                "playlistId=" + playlistId +
                ", name='" + name + '\'' +
                ", dateAdded=" + dateAdded +
                '}';
    }
}
