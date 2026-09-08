package com.giga.tech1000.heartbeatz.ui.adapters;

import android.view.ViewGroup;

import androidx.annotation.NonNull;

import com.giga.tech1000.heartbeatz.ui.adapters.helpers.BaseViewHelper;
import com.giga.tech1000.heartbeatz.ui.adapters.models.BaseRecyclerViewItem;
import com.giga.tech1000.heartbeatz.ui.adapters.models.FolderViewItem;
import com.giga.tech1000.heartbeatz.ui.adapters.view_holders.BaseRecyclerViewHolder;
import com.giga.tech1000.heartbeatz.ui.adapters.view_holders.FolderViewHolder;
import com.giga.tech1000.heartbeatz.interfaces.MediaNavigation;

import java.util.List;

public class FolderViewAdapter extends BaseRecyclerViewAdapter {
    private final MediaNavigation mediaNavigation;


    public FolderViewAdapter(List<BaseRecyclerViewItem> i, MediaNavigation navigation) {
        super(i);
        this.mediaNavigation = navigation;
    }


    @NonNull
    @Override
    public BaseRecyclerViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        BaseRecyclerViewItem.ItemType itemType = BaseRecyclerViewItem.ItemType.values()[viewType];

        switch (itemType) {
            case FOLDER:
                return BaseViewHelper.onCreateViewHolder(FolderViewHolder.class, parent, getViewType());
            default:
                return null;
        }

    }

    @Override
    protected void bind(@NonNull BaseRecyclerViewHolder holder, @NonNull BaseRecyclerViewItem item) {
        holder.onInitializeView(getViewType());
        holder.onBindViewHolder(item);
        holder.onPlayingStateViewHolder(getPlayingCacheInfo(), item);

        FolderViewItem folderItem = (FolderViewItem) item;
        String title = folderItem.getFolder().getName();
        String path = folderItem.getFolder().getPath();

        holder.itemView.setOnClickListener(v -> mediaNavigation.openFolder(title, path));
    }
}
