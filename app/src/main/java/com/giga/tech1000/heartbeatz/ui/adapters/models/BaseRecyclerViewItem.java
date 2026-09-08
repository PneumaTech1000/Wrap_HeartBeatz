package com.giga.tech1000.heartbeatz.ui.adapters.models;

import com.giga.tech1000.utils.interfaces.Searchable;

import java.util.List;

public abstract class BaseRecyclerViewItem implements Searchable {


    public enum ItemType {
        SONG,
        SELECTION_SONG,
        BOTTOM_SHEET_SONG,
        ALBUM, ARTIST, FOLDER, GENRE,
        PLAYLIST, CREATE_PLAYLIST,
        MEDIA_DETAILS
    }

    private final String title;
    private final ItemType itemType;

    public BaseRecyclerViewItem(String title, ItemType itemType) {
        this.title = title;
        this.itemType = itemType;
    }

    public String getTitle() {
        return title;
    }

    public ItemType getItemType() {
        return itemType;
    }

    public abstract List<String> searchTokens();

    public abstract int hashcode();

    public abstract int getId();
}
