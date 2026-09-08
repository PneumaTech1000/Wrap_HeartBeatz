package com.giga.tech1000.heartbeatz.view_models;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;

import com.giga.tech1000.media_player.database.library.LibraryRepository;
import com.giga.tech1000.media_player.models.Song;

import java.util.List;

public class SongsViewModel extends AndroidViewModel {

    private final LibraryRepository repo;
    private final LiveData<List<Song>> songs;

    public SongsViewModel(@NonNull Application app) {
        super(app);
        repo = new LibraryRepository(app);
        songs = repo.getAllSongs();
    }

    public LiveData<List<Song>> getSongs() {
        return songs;
    }

    public void tempDeleteSongById(long songId) {
        repo.tempDeleteSongById(songId);
    }

    public void restoreDeletedSongById(long songId) {
        repo.restoreDeletedSongById(songId);
    }

    public void hideSongById(long songId) {
        repo.hideSongById(songId);
    }

    public void restoreHiddenSongById(long songId) {
        repo.restoreHiddenSongById(songId);
    }
}

