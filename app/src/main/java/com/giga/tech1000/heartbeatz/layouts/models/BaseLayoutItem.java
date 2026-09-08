package com.giga.tech1000.heartbeatz.layouts.models;

import androidx.annotation.NonNull;

import com.giga.tech1000.heartbeatz.ui.adapters.models.BaseRecyclerViewItem;

import java.util.List;

public abstract class BaseLayoutItem {

    public enum LayoutType {
        ALL_SONGS,
        ALBUMS,
        ARTISTS,
        GENRES,
        FOLDERS,
        PLAYLISTS
    }

    private final LayoutType type;

    protected BaseLayoutItem(@NonNull LayoutType type) {
        this.type = type;
    }

    public final LayoutType getType() {
        return type;
    }

    public long getStableId() {
        return type.ordinal();
    }

    /**
     * Items displayed inside this page
     */
    @NonNull
    public abstract List<BaseRecyclerViewItem> getItems();
}
