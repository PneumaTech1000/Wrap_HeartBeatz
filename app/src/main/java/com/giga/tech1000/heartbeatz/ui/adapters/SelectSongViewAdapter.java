package com.giga.tech1000.heartbeatz.ui.adapters;

import android.view.ViewGroup;
import android.widget.CheckBox;

import androidx.annotation.NonNull;

import com.giga.tech1000.heartbeatz.R;
import com.giga.tech1000.heartbeatz.ui.adapters.helpers.BaseViewHelper;
import com.giga.tech1000.heartbeatz.ui.adapters.models.BaseRecyclerViewItem;
import com.giga.tech1000.heartbeatz.ui.adapters.models.SelectSongViewItem;
import com.giga.tech1000.heartbeatz.ui.adapters.view_holders.BaseRecyclerViewHolder;
import com.giga.tech1000.heartbeatz.ui.adapters.view_holders.SelectSongViewHolder;

import java.util.ArrayList;
import java.util.List;

public class SelectSongViewAdapter extends BaseRecyclerViewAdapter {


    public SelectSongViewAdapter(List<BaseRecyclerViewItem> i) {
        super(i);
    }

    @Override
    protected void bind(@NonNull BaseRecyclerViewHolder holder, @NonNull BaseRecyclerViewItem item) {
        holder.onInitializeView(getViewType());
        holder.onBindViewHolder(item);
        holder.onPlayingStateViewHolder(getPlayingCacheInfo(), item);

        holder.itemView.setOnClickListener(v -> {
            SelectSongViewItem songItem = (SelectSongViewItem) item;

            boolean newState = !songItem.isSelected();
            songItem.setSelected(newState);

            // 🔥 notify UI immediately
            CheckBox box = holder.itemView.findViewById(R.id.selection_box);
            box.setChecked(newState);
        });
    }

    @NonNull
    @Override
    public BaseRecyclerViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        BaseRecyclerViewItem.ItemType itemType = BaseRecyclerViewItem.ItemType.values()[viewType];

        switch (itemType) {
            case SELECTION_SONG:
                return BaseViewHelper.onCreateViewHolder(SelectSongViewHolder.class, parent, getViewType());
            default:
                throw new IllegalStateException(
                        "Unsupported ItemType: " + itemType
                );
        }
    }


    public List<Long> getSelectedSongsId() {
        List<Long> selectedIds = new ArrayList<>();
        if (getItems() != null) {
            for (BaseRecyclerViewItem item : getItems()) {
                if (((SelectSongViewItem) item).isSelected()) {
                    selectedIds.add(((SelectSongViewItem) item).getSong().getId());
                }
            }
        }
        return selectedIds;
    }
}
