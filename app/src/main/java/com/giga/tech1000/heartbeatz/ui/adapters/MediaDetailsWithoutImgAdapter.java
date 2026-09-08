package com.giga.tech1000.heartbeatz.ui.adapters;

import android.view.ViewGroup;

import androidx.annotation.NonNull;

import com.giga.tech1000.heartbeatz.ui.adapters.helpers.BaseViewHelper;
import com.giga.tech1000.heartbeatz.ui.adapters.models.BaseRecyclerViewItem;
import com.giga.tech1000.heartbeatz.ui.adapters.view_holders.BaseRecyclerViewHolder;
import com.giga.tech1000.heartbeatz.ui.adapters.view_holders.MediaDetailsWithoutImgViewHolder;
import com.giga.tech1000.heartbeatz.view_models.extended_models.PlaybackCacheViewModel;
import com.giga.tech1000.media_player.utils.enums.ItemSource;

import java.util.List;

public class MediaDetailsWithoutImgAdapter extends BaseRecyclerViewAdapter {

    private final PlaybackCacheViewModel playbackViewModel;

    public MediaDetailsWithoutImgAdapter(List<BaseRecyclerViewItem> i, PlaybackCacheViewModel playbackViewModel) {
        super(i);
        this.playbackViewModel = playbackViewModel;
    }

    @Override
    protected void bind(@NonNull BaseRecyclerViewHolder holder, @NonNull BaseRecyclerViewItem item) {
        holder.onInitializeView(getViewType());
        holder.onBindViewHolder(item);
        holder.onPlayingStateViewHolder(getPlayingCacheInfo(), item);

        holder.itemView.setOnClickListener(v -> {
            if (playbackViewModel != null) {
                playbackViewModel.play(holder.getBindingAdapterPosition(), getQueue(), ItemSource.MEDIA_DETAILS_WITHOUT_IMG);
            }
        });
    }

    @NonNull
    @Override
    public BaseRecyclerViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        BaseRecyclerViewItem.ItemType itemType = BaseRecyclerViewItem.ItemType.values()[viewType];

        switch (itemType) {
            case MEDIA_DETAILS:
                return BaseViewHelper.onCreateViewHolder(MediaDetailsWithoutImgViewHolder.class, parent, getViewType());
            default:
                return null;
        }
    }

}
