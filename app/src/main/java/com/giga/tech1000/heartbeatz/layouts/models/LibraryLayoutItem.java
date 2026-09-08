package com.giga.tech1000.heartbeatz.layouts.models;

import androidx.annotation.NonNull;

import com.giga.tech1000.heartbeatz.ui.SearchController;
import com.giga.tech1000.heartbeatz.ui.adapters.models.BaseRecyclerViewItem;
import com.giga.tech1000.heartbeatz.ui.adapters.models.playlist_item.CreatePlaylistViewItem;

import java.util.ArrayList;
import java.util.List;

public final class LibraryLayoutItem extends BaseLayoutItem {

    private final List<BaseRecyclerViewItem> items;

    public LibraryLayoutItem(
            @NonNull LayoutType type,
            @NonNull List<BaseRecyclerViewItem> items
    ) {
        super(type);
        this.items = items;
    }

    @Override
    @NonNull
    public List<BaseRecyclerViewItem> getItems() {
        return items;
    }

    /**
     * Factory for filtered page
     */
    public static LibraryLayoutItem filtered(
            @NonNull LayoutType type,
            @NonNull List<BaseRecyclerViewItem> source,
            @NonNull String query,
            @NonNull SearchController search
    ) {
        boolean searching = query != null && !query.trim().isEmpty();

        List<BaseRecyclerViewItem> working = new ArrayList<>();

        for (BaseRecyclerViewItem item : source) {

            // 🔥 Hide Create Playlist during search
            if (type == LayoutType.PLAYLISTS
                    && searching
                    && item instanceof CreatePlaylistViewItem) {
                continue;
            }

            working.add(item);
        }

        // Apply search filtering on the copied list
        List<BaseRecyclerViewItem> result =
                search.filter(working, query);

        return new LibraryLayoutItem(type, result);
    }

}
