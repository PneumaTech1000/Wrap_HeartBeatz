package com.giga.tech1000.heartbeatz.ui.adapters.models.playlist_item;

import com.giga.tech1000.heartbeatz.ui.adapters.models.BaseRecyclerViewItem;

import java.util.List;

public class CreatePlaylistViewItem extends BaseRecyclerViewItem {

    public static final int ID = -1000; // stable, unique, non-colliding

    public CreatePlaylistViewItem() {
        super("Create Playlist", ItemType.CREATE_PLAYLIST);
    }

    @Override
    public List<String> searchTokens() {
        return List.of("create", "new", "playlist");
    }

    @Override
    public int hashcode() {
        return 0; // never changes
    }

    @Override
    public int getId() {
        return ID;
    }
}

