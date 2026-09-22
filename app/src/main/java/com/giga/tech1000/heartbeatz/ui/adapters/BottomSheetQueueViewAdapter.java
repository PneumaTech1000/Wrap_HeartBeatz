package com.giga.tech1000.heartbeatz.ui.adapters;

import android.view.ViewGroup;

import androidx.recyclerview.widget.RecyclerView;

import androidx.annotation.NonNull;

import com.giga.tech1000.heartbeatz.ui.adapters.helpers.BaseViewHelper;
import com.giga.tech1000.heartbeatz.ui.adapters.models.BaseRecyclerViewItem;
import com.giga.tech1000.heartbeatz.ui.adapters.models.BottomSheetQueueViewItem;
import com.giga.tech1000.heartbeatz.ui.adapters.view_holders.BaseRecyclerViewHolder;
import com.giga.tech1000.heartbeatz.ui.adapters.view_holders.BottomSheetQueueViewHolder;
import com.giga.tech1000.heartbeatz.view_models.extended_models.PlaybackCacheViewModel;
import com.giga.tech1000.media_player.utils.enums.ItemSource;

import java.util.ArrayList;
import java.util.List;

public class BottomSheetQueueViewAdapter extends BaseRecyclerViewAdapter {

    private final PlaybackCacheViewModel playbackViewModel;

    public BottomSheetQueueViewAdapter(List<BaseRecyclerViewItem> i, PlaybackCacheViewModel playbackViewModel) {
        super(i);
        this.playbackViewModel = playbackViewModel;
    }

    @NonNull
    @Override
    public BaseRecyclerViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        BaseRecyclerViewItem.ItemType itemType = BaseRecyclerViewItem.ItemType.values()[viewType];

        return switch (itemType) {
            case BOTTOM_SHEET_SONG ->
                    BaseViewHelper.onCreateViewHolder(BottomSheetQueueViewHolder.class, parent, getViewType());
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

        holder.itemView.setOnClickListener(v -> {
            if (playbackViewModel == null) return;
            int pos = holder.getBindingAdapterPosition();
            if (pos == RecyclerView.NO_POSITION) return;
            var cache = getPlayingCacheInfo();
            var source = cache != null ? cache.getSource() : ItemSource.SONGS;
            List<Integer> q = getQueue();
            if (q == null) q = new ArrayList<>();
            playbackViewModel.play(pos, q, source);
        });
    }


}
