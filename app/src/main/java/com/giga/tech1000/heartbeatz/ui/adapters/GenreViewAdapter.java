package com.giga.tech1000.heartbeatz.ui.adapters;

import android.net.Uri;
import android.view.ViewGroup;

import androidx.annotation.NonNull;

import com.giga.tech1000.heartbeatz.ui.adapters.helpers.BaseViewHelper;
import com.giga.tech1000.heartbeatz.ui.adapters.models.BaseRecyclerViewItem;
import com.giga.tech1000.heartbeatz.ui.adapters.models.GenreViewItem;
import com.giga.tech1000.heartbeatz.ui.adapters.view_holders.BaseRecyclerViewHolder;
import com.giga.tech1000.heartbeatz.ui.adapters.view_holders.GenreViewHolder;
import com.giga.tech1000.heartbeatz.interfaces.MediaNavigation;

import java.util.List;

public class GenreViewAdapter extends BaseRecyclerViewAdapter {

    private final MediaNavigation mediaNavigation;

    public GenreViewAdapter(List<BaseRecyclerViewItem> i, MediaNavigation navigation) {
        super(i);
        this.mediaNavigation = navigation;
    }

    @NonNull
    @Override
    public BaseRecyclerViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        BaseRecyclerViewItem.ItemType itemType = BaseRecyclerViewItem.ItemType.values()[viewType];

        return switch (itemType) {
            case GENRE ->
                    BaseViewHelper.onCreateViewHolder(GenreViewHolder.class, parent, getViewType());
            default -> throw new RuntimeException();
        };
    }


    @Override
    protected void bind(@NonNull BaseRecyclerViewHolder holder, @NonNull BaseRecyclerViewItem item) {
        holder.onInitializeView(getViewType());
        holder.onBindViewHolder(item);
        holder.onPlayingStateViewHolder(getPlayingCacheInfo(), item);

        GenreViewItem genreItem = (GenreViewItem) item;
        Uri artUri = genreItem.getGenreWithCount().getGenre().getArtUri();
        String title = genreItem.getGenreWithCount().getGenre().getName();
        long id = genreItem.getGenreWithCount().getGenre().getId();

        holder.itemView.setOnClickListener(v -> mediaNavigation.openGenre(artUri, title, id));
    }
}
