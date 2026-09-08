package com.giga.tech1000.heartbeatz.ui.adapters;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.AsyncListDiffer;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import android.widget.Filter;
import android.widget.Filterable;

import com.giga.tech1000.heartbeatz.ui.adapters.models.BaseRecyclerViewItem;
import com.giga.tech1000.heartbeatz.ui.adapters.view_holders.BaseRecyclerViewHolder;
import com.giga.tech1000.media_player.models.extended_models.PlayerCacheModel;
import com.giga.tech1000.media_player.utils.enums.ItemSource;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public abstract class BaseRecyclerViewAdapter extends RecyclerView.Adapter<BaseRecyclerViewHolder> implements Filterable {

    protected List<BaseRecyclerViewItem> originalItems = new ArrayList<>();
    private final AsyncListDiffer<BaseRecyclerViewItem> diff;
    protected int playingItemId = RecyclerView.NO_POSITION;
    protected ItemSource itemSource;
    protected PlayerCacheModel playingCacheInfo;
    private static final Object PAYLOAD_PLAYING = new Object();
    private final List<Integer> queue = new ArrayList<>();

    private static final DiffUtil.ItemCallback<BaseRecyclerViewItem> DIFF_CALLBACK = new DiffUtil.ItemCallback<>() {
        @Override
        public boolean areItemsTheSame(@NonNull BaseRecyclerViewItem oldItem, @NonNull BaseRecyclerViewItem newItem) {
            return oldItem.getId() == newItem.getId();
        }

        @Override
        public boolean areContentsTheSame(@NonNull BaseRecyclerViewItem oldItem, @NonNull BaseRecyclerViewItem newItem) {
            return oldItem.hashcode() == newItem.hashcode();
        }
    };
    public enum ViewType {
        LIST(1),
        GRID(2);
        private final int count;
        ViewType(int count) {
            this.count = count;
        }
        public int getCount() {
            return count;
        }
    }

    private ViewType viewType;

    public BaseRecyclerViewAdapter(List<BaseRecyclerViewItem> i) {
        this.playingCacheInfo = new PlayerCacheModel(null, -1, ItemSource.NONE);
        this.diff = new AsyncListDiffer<>(this, DIFF_CALLBACK);
        diff.submitList(i);
    }

    @Override
    public final void onBindViewHolder(@NonNull BaseRecyclerViewHolder holder, int position) {
        // Delegate to payload-aware version
        onBindViewHolder(holder, position, Collections.emptyList());
    }


    @Override
    public void onBindViewHolder(@NonNull BaseRecyclerViewHolder holder, int position, @NonNull List<Object> payloads) {
        List<BaseRecyclerViewItem> list = diff.getCurrentList();

        if (position < 0 || position >= list.size()) return;

        BaseRecyclerViewItem item = list.get(position);

        if (!payloads.isEmpty() && payloads.contains(PAYLOAD_PLAYING)) {
            holder.onPlayingStateViewHolder(getPlayingCacheInfo(), item);
            return;
        }

        bind(holder, item);
    }


    protected abstract void bind(
            @NonNull BaseRecyclerViewHolder holder,
            @NonNull BaseRecyclerViewItem item
    );


    @Override
    public int getItemCount() {
        return this.diff.getCurrentList().size();
    }

    @Override
    public int getItemViewType(int position) {
        return this.getItems().get(position).getItemType().ordinal();
    }



    public void setPlayingItemCache(int newPlayingId) {
        //if (playingItemId == newPlayingId) return;

        int oldPlayingId = playingItemId;

        int oldPos = findPositionById(oldPlayingId);
        int newPos = findPositionById(newPlayingId);

        playingItemId = newPlayingId; // ✅ update AFTER resolving positions

        if (oldPos != RecyclerView.NO_POSITION) {
            notifyItemChanged(oldPos, PAYLOAD_PLAYING);
        }
        if (newPos != RecyclerView.NO_POSITION) {
            notifyItemChanged(newPos, PAYLOAD_PLAYING);
        }
    }


    protected boolean isPlayingPosition(BaseRecyclerViewItem item) {
        return item.getId() == playingItemId;
    }

    public void setPlayingCacheInfo(PlayerCacheModel playingCache) {
        this.playingCacheInfo = playingCache;
        this.setPlayingItemCache((int) playingCache.getCurrentSong().id);
    }

    protected PlayerCacheModel getPlayingCacheInfo() {
        return playingCacheInfo;
    }
    protected ItemSource getItemSource() {
        return itemSource;
    }

    int findPositionById(int id) {
        if (id == RecyclerView.NO_ID) return RecyclerView.NO_POSITION;
        List<BaseRecyclerViewItem> list = diff.getCurrentList();
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).getId() == id) return i;
        }
        return RecyclerView.NO_POSITION;
    }

    public void setItems(List<BaseRecyclerViewItem> items) {
        this.originalItems = new ArrayList<>(items);
        diff.submitList(items);
        queue.clear();
        for (BaseRecyclerViewItem item : items) {
            queue.add(item.getId());
        }
    }

    @Override
    public Filter getFilter() {
        return new Filter() {
            @Override
            protected FilterResults performFiltering(CharSequence constraint) {
                List<BaseRecyclerViewItem> filteredList = new ArrayList<>();
                if (constraint == null || constraint.length() == 0) {
                    filteredList.addAll(originalItems);
                } else {
                    String filterPattern = constraint.toString().toLowerCase().trim();
                    for (BaseRecyclerViewItem item : originalItems) {
                        List<String> tokens = item.searchTokens();
                        if (tokens != null) {
                            for (String token : tokens) {
                                if (token != null && token.toLowerCase().contains(filterPattern)) {
                                    filteredList.add(item);
                                    break;
                                }
                            }
                        }
                    }
                }
                FilterResults results = new FilterResults();
                results.values = filteredList;
                return results;
            }

            @Override
            @SuppressWarnings("unchecked")
            protected void publishResults(CharSequence constraint, FilterResults results) {
                diff.submitList((List<BaseRecyclerViewItem>) results.values);
            }
        };
    }

    public List<Integer> getQueue() {
        return queue;
    }

    public void setQueue(List<Integer> que) {
        queue.clear();
        queue.addAll(que);
    }

    public void setViewType(ViewType view) {
        this.viewType = view;
    }

    public List<BaseRecyclerViewItem> getItems() {
        return this.diff.getCurrentList();
    }

    public ViewType getViewType() {
        return this.viewType;
    }
}
