package com.giga.tech1000.heartbeatz.ui.adapters;

import android.view.ViewGroup;

import androidx.annotation.NonNull;

import com.giga.tech1000.heartbeatz.ui.adapters.helpers.BaseViewHelper;
import com.giga.tech1000.heartbeatz.ui.adapters.models.BaseRecyclerViewItem;
import com.giga.tech1000.heartbeatz.ui.adapters.models.playlist_item.CreatePlaylistViewItem;
import com.giga.tech1000.heartbeatz.ui.adapters.models.playlist_item.PlaylistViewItem;
import com.giga.tech1000.heartbeatz.ui.adapters.view_holders.BaseRecyclerViewHolder;
import com.giga.tech1000.heartbeatz.ui.adapters.view_holders.CreatePlaylistViewHolder;
import com.giga.tech1000.heartbeatz.ui.adapters.view_holders.PlaylistViewHolder;
import com.giga.tech1000.heartbeatz.interfaces.MediaNavigation;

import java.util.List;

public class PlaylistViewAdapter extends BaseRecyclerViewAdapter {
    private final MediaNavigation mediaNavigation;

    public PlaylistViewAdapter(List<BaseRecyclerViewItem> i, MediaNavigation navigation) {
        super(i);
        this.mediaNavigation = navigation;
    }

    @NonNull
    @Override
    public BaseRecyclerViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        BaseRecyclerViewItem.ItemType itemType = BaseRecyclerViewItem.ItemType.values()[viewType];

        return switch (itemType) {
            case CREATE_PLAYLIST ->
                    BaseViewHelper.onCreateViewHolder(CreatePlaylistViewHolder.class, parent, getViewType());
            case PLAYLIST ->
                    new PlaylistViewHolder(com.giga.tech1000.heartbeatz.ui.adapters.helpers.BaseViewHelper.inflate(parent, PlaylistViewHolder.class, getViewType()), mediaNavigation);
            default -> throw new RuntimeException();
        };
    }

    @Override
    protected void bind(@NonNull BaseRecyclerViewHolder holder, @NonNull BaseRecyclerViewItem item) {
        holder.onInitializeView(getViewType());
        holder.onBindViewHolder(item);
        holder.onPlayingStateViewHolder(getPlayingCacheInfo(), item);


        holder.itemView.setOnClickListener(v -> {
            if (item instanceof CreatePlaylistViewItem) {
                mediaNavigation.openSearchDialog(); // 👈
                return;
            }

            PlaylistViewItem playlistItem = (PlaylistViewItem) item;
            String title = playlistItem.getPlaylist().getPlaylist().getName();
            long id = playlistItem.getPlaylist().getPlaylist().getPlaylistId();

            mediaNavigation.openPlaylist(title, id);
        });
    }




}
