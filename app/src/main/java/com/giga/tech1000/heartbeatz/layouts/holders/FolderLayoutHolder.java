package com.giga.tech1000.heartbeatz.layouts.holders;

import android.view.View;

import androidx.annotation.NonNull;
import androidx.lifecycle.Observer;
import androidx.recyclerview.widget.RecyclerView;

import com.giga.tech1000.heartbeatz.R;
import com.giga.tech1000.heartbeatz.layouts.models.BaseLayoutItem;
import com.giga.tech1000.heartbeatz.layouts.models.LibraryLayoutItem;
import com.giga.tech1000.heartbeatz.ui.UIThread;
import com.giga.tech1000.heartbeatz.ui.adapters.helpers.HBGridLayoutManager;
import com.giga.tech1000.heartbeatz.ui.adapters.BaseRecyclerViewAdapter;
import com.giga.tech1000.heartbeatz.ui.adapters.FolderViewAdapter;
import com.giga.tech1000.media_player.models.extended_models.PlayerCacheModel;
import com.giga.tech1000.heartbeatz.interfaces.MediaNavigation;

public class FolderLayoutHolder extends LibraryLayoutHolder {
private final RecyclerView recyclerView;
private FolderViewAdapter adapter;
private final MediaNavigation mediaNavigation;

    private final Observer<PlayerCacheModel> observer =
            state -> {
                if (adapter != null) {
                    adapter.setPlayingCacheInfo(state);
                }
            };

    public FolderLayoutHolder(@NonNull View itemView, MediaNavigation navigation) {
        super(itemView);
        this.mediaNavigation = navigation;
        recyclerView = itemView.findViewById(R.id.recycler_view);
    }

    @Override
    protected void onBindContent(BaseLayoutItem item) {
        LibraryLayoutItem layoutItem = (LibraryLayoutItem) item;
        if (adapter == null) {
            adapter = new FolderViewAdapter(layoutItem.getItems(), mediaNavigation);
            adapter.setViewType(BaseRecyclerViewAdapter.ViewType.LIST);
            recyclerView.setLayoutManager(new HBGridLayoutManager(itemView.getContext(), adapter));
            recyclerView.setAdapter(adapter);
        }
        adapter.setItems(layoutItem.getItems());
    }

    @Override
    public void onViewAttachedToWindow() {
        UIThread.getInstance().getPlayingCache().getPlayerCacheInfo().observeForever(observer);
    }

    @Override
    public void onViewDetachedFromWindow() {
        UIThread.getInstance().getPlayingCache().getPlayerCacheInfo().removeObserver(observer);
    }
}
