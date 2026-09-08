package com.giga.tech1000.heartbeatz.ui.adapters;

import android.net.Uri;
import android.view.ViewGroup;

import androidx.annotation.NonNull;

import com.giga.tech1000.heartbeatz.ui.adapters.helpers.BaseViewHelper;
import com.giga.tech1000.heartbeatz.ui.adapters.models.ArtistViewItem;
import com.giga.tech1000.heartbeatz.ui.adapters.models.BaseRecyclerViewItem;
import com.giga.tech1000.heartbeatz.ui.adapters.view_holders.ArtistViewHolder;
import com.giga.tech1000.heartbeatz.ui.adapters.view_holders.BaseRecyclerViewHolder;
import com.giga.tech1000.heartbeatz.interfaces.MediaNavigation;

import java.util.List;

public class ArtistViewAdapter extends BaseRecyclerViewAdapter {

    private final MediaNavigation mediaNavigation;

    public ArtistViewAdapter(List<BaseRecyclerViewItem> i, MediaNavigation navigation) {
        super(i);
        this.mediaNavigation = navigation;
    }

    @NonNull
    @Override
    public BaseRecyclerViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        BaseRecyclerViewItem.ItemType itemType = BaseRecyclerViewItem.ItemType.values()[viewType];

        return switch (itemType) {
            case ARTIST ->
                    BaseViewHelper.onCreateViewHolder(ArtistViewHolder.class, parent, getViewType());
            default -> throw new IllegalStateException(
                    "Unsupported viewType: " + viewType
            );
        };
    }


    @Override
    protected void bind(@NonNull BaseRecyclerViewHolder holder, @NonNull BaseRecyclerViewItem item) {
        holder.onInitializeView(getViewType());
        holder.onBindViewHolder(item);
        holder.onPlayingStateViewHolder(getPlayingCacheInfo(), item);

        ArtistViewItem artistItem = (ArtistViewItem) item;
        Uri artUri = artistItem.getArtist().getArtistArtUri();
        String title = artistItem.getArtist().getName();
        long id = artistItem.getArtist().getId();

        holder.itemView.setOnClickListener(v -> mediaNavigation.openArtist(artUri, title, id));
    }
}
