package com.giga.tech1000.heartbeatz.view_models;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;

import com.giga.tech1000.media_player.database.library.LibraryRepository;
import com.giga.tech1000.media_player.models.Artist;
import com.giga.tech1000.media_player.models.cross_ref.ArtistWithSongs;

import java.util.List;

public class ArtistsViewModel extends AndroidViewModel {

    private final LibraryRepository repo;
    private final LiveData<List<Artist>> artists;

    public ArtistsViewModel(@NonNull Application app) {
        super(app);
        repo = new LibraryRepository(app);
        artists = repo.getArtists();
    }

    public LiveData<List<Artist>> getArtists() {
        return artists;
    }

    public LiveData<ArtistWithSongs> getArtistWthSongs(long id) {
        return repo.getArtistWithSongs(id);
    }
}

