package com.giga.tech1000.heartbeatz.ui.adapters;

import android.view.ViewGroup;

import androidx.annotation.NonNull;

import com.giga.tech1000.heartbeatz.ui.adapters.helpers.BaseViewHelper;
import com.giga.tech1000.heartbeatz.ui.adapters.models.AlbumViewItem;
import com.giga.tech1000.heartbeatz.ui.adapters.models.BaseRecyclerViewItem;
import com.giga.tech1000.heartbeatz.ui.adapters.view_holders.AlbumViewHolder;
import com.giga.tech1000.heartbeatz.ui.adapters.view_holders.BaseRecyclerViewHolder;
import com.giga.tech1000.media_player.models.Album;
import com.giga.tech1000.heartbeatz.interfaces.MediaNavigation;

import java.util.List;

public class AlbumViewAdapter extends BaseRecyclerViewAdapter {

    private final MediaNavigation mediaNavigation;

    public AlbumViewAdapter(List<BaseRecyclerViewItem> i, MediaNavigation navigation) {
        super(i);
        this.mediaNavigation = navigation;
    }

    @NonNull
    @Override
    public BaseRecyclerViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        BaseRecyclerViewItem.ItemType itemType = BaseRecyclerViewItem.ItemType.values()[viewType];

        return switch (itemType) {
            case ALBUM ->
                    BaseViewHelper.onCreateViewHolder(AlbumViewHolder.class, parent, getViewType());
            default -> throw new IllegalStateException(
                    "Unsupported viewType: " + viewType
            );
        };
    }


    @Override
    protected void bind(@NonNull BaseRecyclerViewHolder holder, @NonNull BaseRecyclerViewItem item) {
        AlbumViewItem albumItem = (AlbumViewItem) item;

        holder.onInitializeView(getViewType());
        holder.onBindViewHolder(albumItem);
        holder.onPlayingStateViewHolder(getPlayingCacheInfo(), item);

        holder.itemView.setOnClickListener(v -> {
            Album album = albumItem.getAlbum();
            mediaNavigation.openAlbum(album.getArtUri(), album.getName(), album.getId());
        });
    }
}


