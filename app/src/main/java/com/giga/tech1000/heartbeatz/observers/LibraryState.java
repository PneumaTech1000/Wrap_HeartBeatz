package com.giga.tech1000.heartbeatz.observers;

import com.giga.tech1000.heartbeatz.ui.adapters.models.BaseRecyclerViewItem;

import java.util.ArrayList;
import java.util.List;

public class LibraryState {

    private List<BaseRecyclerViewItem> songs = new ArrayList<>();
    private List<BaseRecyclerViewItem> albums = new ArrayList<>();
    private List<BaseRecyclerViewItem> artists = new ArrayList<>();
    private List<BaseRecyclerViewItem> genres = new ArrayList<>();
    private List<BaseRecyclerViewItem> playlists = new ArrayList<>();
    private List<BaseRecyclerViewItem> folders = new ArrayList<>();

    public LibraryState() {}

    // Copy constructor for immutability
    public LibraryState(LibraryState other) {
        this.songs = new ArrayList<>(other.songs);
        this.albums = new ArrayList<>(other.albums);
        this.artists = new ArrayList<>(other.artists);
        this.genres = new ArrayList<>(other.genres);
        this.playlists = new ArrayList<>(other.playlists);
        this.folders = new ArrayList<>(other.folders);
    }


    // Songs
    public List<BaseRecyclerViewItem> getSongs() {
        return songs;
    }
    public void setSongs(List<BaseRecyclerViewItem> songs) {
        this.songs = songs;
    }

    // Albums
    public List<BaseRecyclerViewItem> getAlbums() {
        return albums;
    }
    public void setAlbums(List<BaseRecyclerViewItem> albums) {
        this.albums = albums;
    }

    // Artists
    public List<BaseRecyclerViewItem> getArtists() {
        return artists;
    }
    public void setArtists(List<BaseRecyclerViewItem> artists) {
        this.artists = artists;
    }

    // Genres
    public List<BaseRecyclerViewItem> getGenres() {
        return genres;
    }
    public void setGenres(List<BaseRecyclerViewItem> genres) {
        this.genres = genres;
    }

    // Playlists
    public List<BaseRecyclerViewItem> getPlaylists() {
        return playlists;
    }
    public void setPlaylists(List<BaseRecyclerViewItem> playlists) {
        this.playlists = playlists;
    }

    // Folders
    public List<BaseRecyclerViewItem> getFolders() {
        return folders;
    }
    public void setFolders(List<BaseRecyclerViewItem> folders) {
        this.folders = folders;
    }
}
