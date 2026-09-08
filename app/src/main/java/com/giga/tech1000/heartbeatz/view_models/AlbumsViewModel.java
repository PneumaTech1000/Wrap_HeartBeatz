package com.giga.tech1000.heartbeatz.view_models;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;

import com.giga.tech1000.media_player.database.library.LibraryRepository;
import com.giga.tech1000.media_player.models.Album;
import com.giga.tech1000.media_player.models.cross_ref.AlbumWithSongs;

import java.util.List;

public class AlbumsViewModel extends AndroidViewModel {

    private final LibraryRepository repo;
    private final LiveData<List<Album>> albums;

    public AlbumsViewModel(@NonNull Application app) {
        super(app);
        repo = new LibraryRepository(app);
        albums = repo.getAlbums();
    }

    public LiveData<List<Album>> getAlbums() {
        return albums;
    }

    public LiveData<AlbumWithSongs> getAlbumWithSongs(long id) {
        return repo.getAlbumWithSongs(id);
    }
}

