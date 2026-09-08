package com.giga.tech1000.heartbeatz.ui.adapters;

import android.view.ViewGroup;

import androidx.annotation.NonNull;

import com.giga.tech1000.heartbeatz.ui.adapters.helpers.BaseViewHelper;
import com.giga.tech1000.heartbeatz.ui.adapters.models.BaseRecyclerViewItem;
import com.giga.tech1000.heartbeatz.ui.adapters.models.media_details.MediaDetailsWithImgViewItem;
import com.giga.tech1000.heartbeatz.ui.adapters.view_holders.BaseRecyclerViewHolder;
import com.giga.tech1000.heartbeatz.ui.adapters.view_holders.MediaDetailsWithImgViewHolder;
import com.giga.tech1000.heartbeatz.view_models.extended_models.PlaybackCacheViewModel;
import com.giga.tech1000.media_player.utils.enums.ItemSource;

import java.util.ArrayList;
import java.util.List;

public class MediaDetailsWithImgAdapter extends BaseRecyclerViewAdapter {

    private final PlaybackCacheViewModel playbackViewModel;

    public MediaDetailsWithImgAdapter(List<BaseRecyclerViewItem> i, PlaybackCacheViewModel playbackViewModel) {
        super(i);
        this.playbackViewModel = playbackViewModel;
    }



    @NonNull
    @Override
    public BaseRecyclerViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        BaseRecyclerViewItem.ItemType itemType = BaseRecyclerViewItem.ItemType.values()[viewType];

        return switch (itemType) {
            case MEDIA_DETAILS ->
                    BaseViewHelper.onCreateViewHolder(MediaDetailsWithImgViewHolder.class, parent, getViewType());
            default -> throw new RuntimeException();
        };
    }

    @Override
    protected void bind(@NonNull BaseRecyclerViewHolder holder, @NonNull BaseRecyclerViewItem item) {
        holder.onInitializeView(getViewType());
        holder.onBindViewHolder(item);
        holder.onPlayingStateViewHolder(getPlayingCacheInfo(), item);

        holder.itemView.setOnClickListener(v -> {
            int songId = (int) (((MediaDetailsWithImgViewItem) item)
                    .getSong().getId());
            if (playbackViewModel != null) {
                playbackViewModel.play(getPosition(songId), getQueue(), ItemSource.MEDIA_DETAILS_WITH_IMG);
            }
        });
    }

    private int getPosition(int songId) {
        for (int i = 0; i < getItems().size(); i++) {
            if (((MediaDetailsWithImgViewItem) getItems().get(i)).getSong().getId()==songId) return i;
        }
        return -1;
    }

    public List<Integer> getQueue() {
        List<Integer> queue = new ArrayList<>();
        if (getItems() != null) {
            for (BaseRecyclerViewItem item : getItems()) {
                queue.add((int) ((MediaDetailsWithImgViewItem) item).getSong().getId());
            }
        }

        return queue;
    }
}
