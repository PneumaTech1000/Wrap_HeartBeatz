package com.giga.tech1000.media_player.models.extended_models;

import android.net.Uri;

import androidx.annotation.NonNull;

import com.giga.tech1000.media_player.models.Song;

import java.util.List;

public class MediaDetail {

    public enum Type {
        ALBUM_SONG,
        ARTIST_SONG,
        PLAYLIST_SONG,
        GENRE_SONG,
        FOLDER_SONG
    }

    private Type type;
    private String title;
    private Uri uri;
    private List<Song> songs;

    public MediaDetail(Type type, Uri uri, String title, List<Song> songs) {
        this.type = type;
        this.uri = uri;
        this.title = title;
        this.songs = songs;
    }

    public Type getType() {
        return type;
    }

    public void setType(Type type) {
        this.type = type;
    }

    public Uri getUri() {
        return uri;
    }

    public void setUri(Uri uri) {
        this.uri = uri;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public List<Song> getSongs() {
        return songs;
    }

    public void setSongs(List<Song> songs) {
        this.songs = songs;
    }


    @NonNull
    @Override
    public String toString() {
        return "MediaDetail{" +
                "type=" + type +
                ", title='" + title + '\'' +
                '}';
    }
}
