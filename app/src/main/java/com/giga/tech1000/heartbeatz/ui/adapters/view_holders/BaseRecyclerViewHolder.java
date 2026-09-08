package com.giga.tech1000.heartbeatz.ui.adapters.view_holders;

import android.view.View;

import androidx.annotation.IdRes;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.giga.tech1000.heartbeatz.ui.adapters.BaseRecyclerViewAdapter;
import com.giga.tech1000.heartbeatz.ui.adapters.models.BaseRecyclerViewItem;
import com.giga.tech1000.media_player.models.extended_models.PlayerCacheModel;

public abstract class BaseRecyclerViewHolder extends RecyclerView.ViewHolder {


    public BaseRecyclerViewHolder(@NonNull View itemView) {
        super(itemView);
    }

    public abstract void onInitializeView(BaseRecyclerViewAdapter.ViewType viewType);

    public abstract void onBindViewHolder(BaseRecyclerViewItem viewItem);

    public abstract void onPlayingStateViewHolder(PlayerCacheModel playingCache, BaseRecyclerViewItem viewItem);



    public <T extends View> T findViewById(@IdRes int id) {
        return itemView.findViewById(id);
    }


}
