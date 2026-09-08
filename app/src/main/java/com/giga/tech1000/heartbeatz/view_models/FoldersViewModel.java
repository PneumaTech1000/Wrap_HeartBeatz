package com.giga.tech1000.heartbeatz.view_models;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;

import com.giga.tech1000.media_player.database.library.LibraryRepository;
import com.giga.tech1000.media_player.models.Folder;
import com.giga.tech1000.media_player.models.cross_ref.FolderWithSongs;

import java.util.List;

public class FoldersViewModel extends AndroidViewModel {

    private final LibraryRepository repo;
    private final LiveData<List<Folder>> folders;

    public FoldersViewModel(@NonNull Application app) {
        super(app);
        repo = new LibraryRepository(app);
        folders = repo.getFolders();
    }

    public LiveData<List<Folder>> getFolders() {
        return folders;
    }

    public LiveData<FolderWithSongs> getSongsByFolder(String folder) {
        return repo.getSongsByFolder(folder);
    }
}

