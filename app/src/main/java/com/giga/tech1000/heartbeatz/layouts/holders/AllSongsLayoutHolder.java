package com.giga.tech1000.heartbeatz.layouts.holders;

import android.view.View;

import androidx.annotation.NonNull;
import androidx.lifecycle.Observer;
import androidx.media3.common.util.UnstableApi;
import androidx.recyclerview.widget.RecyclerView;

import com.giga.tech1000.heartbeatz.R;
import com.giga.tech1000.heartbeatz.layouts.models.BaseLayoutItem;
import com.giga.tech1000.heartbeatz.layouts.models.LibraryLayoutItem;
import com.giga.tech1000.heartbeatz.ui.UIThread;
import com.giga.tech1000.heartbeatz.ui.adapters.helpers.HBGridLayoutManager;
import com.giga.tech1000.heartbeatz.ui.adapters.BaseRecyclerViewAdapter;
import com.giga.tech1000.heartbeatz.ui.adapters.SongViewAdapter;
import com.giga.tech1000.heartbeatz.view_models.extended_models.PlaybackCacheViewModel;
import com.giga.tech1000.media_player.models.extended_models.PlayerCacheModel;
import com.giga.tech1000.heartbeatz.interfaces.MediaNavigation;

@UnstableApi
public class AllSongsLayoutHolder extends LibraryLayoutHolder {

    private SongViewAdapter adapter;
    public final RecyclerView recyclerView;
    private final MediaNavigation mediaNavigation;
    private final PlaybackCacheViewModel playbackViewModel;

    private final Observer<PlayerCacheModel> observer =
            state -> {
                if (adapter != null) {
                    adapter.setPlayingCacheInfo(state);
                }
            };

    public AllSongsLayoutHolder(@NonNull View itemView, MediaNavigation navigation, PlaybackCacheViewModel playbackViewModel) {
        super(itemView);
        this.mediaNavigation = navigation;
        this.playbackViewModel = playbackViewModel;
        recyclerView = itemView.findViewById(R.id.recycler_view);
    }

    @Override
    public void onBindContent(BaseLayoutItem item) {
        LibraryLayoutItem layoutItem = (LibraryLayoutItem) item;
        if (adapter == null) {
            adapter = new SongViewAdapter(layoutItem.getItems(), mediaNavigation, playbackViewModel);
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

