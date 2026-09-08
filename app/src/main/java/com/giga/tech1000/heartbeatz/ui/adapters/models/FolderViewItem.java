package com.giga.tech1000.heartbeatz.ui.adapters.models;

import android.icu.text.CaseMap;

import com.giga.tech1000.media_player.models.Folder;
import com.giga.tech1000.utils.interfaces.Searchable;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class FolderViewItem extends BaseRecyclerViewItem {

private final Folder folder;
    public FolderViewItem(Folder folder) {
        super(folder.getName(), ItemType.FOLDER);
        this.folder = folder;
    }

    public Folder getFolder() {
        return folder;
    }

    @Override
    public int hashcode() {
        return folder.toString().hashCode();
    }

    @Override
    public int getId() {
        return (int) folder.getId();
    }

    @Override
    public List<String> searchTokens() {
        return Arrays.asList(
                folder.getName(),
                folder.getPath()
        );
    }
}
