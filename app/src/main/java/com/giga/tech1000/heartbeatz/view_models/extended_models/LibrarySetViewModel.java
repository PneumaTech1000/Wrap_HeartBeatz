package com.giga.tech1000.heartbeatz.view_models;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.MutableLiveData;

import com.giga.tech1000.media_player.database.library.LibraryRepository;

public class LibrarySetViewModel extends AndroidViewModel {
    private final LibraryRepository repo;
    public final MutableLiveData<Integer> selectedTab =
            new MutableLiveData<>(0);

    public final MutableLiveData<String> searchQuery =
            new MutableLiveData<>("");

    public LibrarySetViewModel(@NonNull Application app) {
        super(app);
        repo = new LibraryRepository(app);

    }

    public LibraryRepository getRepo() {
        return repo;
    }
}
