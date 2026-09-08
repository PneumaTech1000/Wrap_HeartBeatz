package com.giga.tech1000.heartbeatz.ui.adapters;

import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.media3.common.util.UnstableApi;

import com.giga.tech1000.heartbeatz.interfaces.MediaNavigation;
import com.giga.tech1000.heartbeatz.ui.adapters.helpers.BaseViewHelper;
import com.giga.tech1000.heartbeatz.ui.adapters.models.BaseRecyclerViewItem;
import com.giga.tech1000.heartbeatz.ui.adapters.models.SongViewItem;
import com.giga.tech1000.heartbeatz.ui.adapters.view_holders.BaseRecyclerViewHolder;
import com.giga.tech1000.heartbeatz.ui.adapters.view_holders.SongViewHolder;
import com.giga.tech1000.heartbeatz.view_models.extended_models.PlaybackCacheViewModel;
import com.giga.tech1000.media_player.utils.enums.ItemSource;

import java.util.List;

@UnstableApi
public class SongViewAdapter extends BaseRecyclerViewAdapter {
    private final MediaNavigation mediaNavigation;
    private final PlaybackCacheViewModel playbackViewModel;

    public SongViewAdapter(List<BaseRecyclerViewItem> items, MediaNavigation mediaNavigation, PlaybackCacheViewModel playbackViewModel) {
        super(items);
        this.mediaNavigation = mediaNavigation;
        this.playbackViewModel = playbackViewModel;
    }

    @Override
    protected void bind(@NonNull BaseRecyclerViewHolder holder, @NonNull BaseRecyclerViewItem item) {
        holder.onInitializeView(getViewType());
        holder.onBindViewHolder(item);
        holder.onPlayingStateViewHolder(getPlayingCacheInfo(), item);

        SongViewHolder songHolder = (SongViewHolder) holder;
        SongViewItem songItem = (SongViewItem) item;

        holder.itemView.setOnClickListener(v -> {
            int songId = (int) songItem.getSong().getId();
            if (playbackViewModel != null) {
                playbackViewModel.play(holder.getBindingAdapterPosition(), getQueue(), ItemSource.ALL_SONGS);
            }
        });

        songHolder.moreBtn.setOnClickListener(v -> {
            int songId = (int) songItem.getSong().getId();
            mediaNavigation.openMoreInSong(songId);
        });
    }


    @NonNull
    @Override
    public BaseRecyclerViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        BaseRecyclerViewItem.ItemType itemType = BaseRecyclerViewItem.ItemType.values()[viewType];

        switch (itemType) {
            case SONG:
                return BaseViewHelper.onCreateViewHolder(SongViewHolder.class, parent, getViewType());
            default:
                return null;
        }

    }


}
