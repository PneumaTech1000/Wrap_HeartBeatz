package com.giga.tech1000.heartbeatz.view_models;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;

import com.giga.tech1000.media_player.database.library.LibraryRepository;
import com.giga.tech1000.media_player.models.cross_ref.PlaylistWithCount;
import com.giga.tech1000.media_player.models.cross_ref.PlaylistWithSongs;

import java.util.List;

public class PlaylistsViewModel extends AndroidViewModel {

    private final LibraryRepository repo;

    public PlaylistsViewModel(@NonNull Application app) {
        super(app);
        repo = new LibraryRepository(app);
    }

    public LiveData<List<PlaylistWithCount>> getPlaylists() {
        return repo.getPlaylists();
    }

    public void createPlaylist(String name) {
        repo.createPlaylist(name, playlistId -> {});
    }


    public LiveData<PlaylistWithSongs> getPlaylistSongs(long id) {
        return repo.getPlaylistWithSongs(id);
    }

    public void addSongs(long playlistId, List<Long> songIds) {
        repo.addSongsToPlaylist(playlistId, songIds, null);
    }

    public void removeSongs(long playlistId, List<Long> songIds) {
        repo.removeSongsFromPlaylist(playlistId, songIds, null);
    }
}

