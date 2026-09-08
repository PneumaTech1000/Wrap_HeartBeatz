package com.giga.tech1000.heartbeatz.view_models;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;

import com.giga.tech1000.media_player.database.library.LibraryRepository;
import com.giga.tech1000.media_player.models.Genre;
import com.giga.tech1000.media_player.models.cross_ref.GenreWithCount;
import com.giga.tech1000.media_player.models.cross_ref.GenreWithSongs;

import java.util.List;

public class GenresViewModel extends AndroidViewModel {
    private final LibraryRepository repo;
    private final LiveData<List<Genre>> genres;

    public GenresViewModel(@NonNull Application application) {
        super(application);
        repo = new LibraryRepository(application);
        genres = repo.getGenres();
    }

    public LiveData<List<Genre>> getGenres() {
        return genres;
    }

    public LiveData<List<GenreWithCount>> getGenreWithCount() {
        return repo.getGenresWithCount();
    }


    public LiveData<GenreWithSongs> getGenreWithSongs(long id) {
        return repo.getGenreWithSongs(id);
    }
}
