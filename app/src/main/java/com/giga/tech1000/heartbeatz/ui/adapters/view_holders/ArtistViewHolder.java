package com.giga.tech1000.heartbeatz.ui.adapters.view_holders;

import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.giga.tech1000.heartbeatz.R;
import com.giga.tech1000.heartbeatz.utils.ImageLoader;
import com.giga.tech1000.heartbeatz.ui.adapters.BaseRecyclerViewAdapter;
import com.giga.tech1000.heartbeatz.ui.adapters.models.ArtistViewItem;
import com.giga.tech1000.heartbeatz.ui.adapters.models.BaseRecyclerViewItem;
import com.giga.tech1000.media_player.models.extended_models.PlayerCacheModel;

public class ArtistViewHolder extends BaseRecyclerViewHolder {


    public ArtistViewHolder(@NonNull View itemView) {
        super(itemView);
    }

    @Override
    public void onInitializeView(BaseRecyclerViewAdapter.ViewType viewType) {

    }

    @Override
    public void onBindViewHolder(BaseRecyclerViewItem viewItem) {
        ArtistViewItem item = (ArtistViewItem) viewItem;

        TextView title = findViewById(R.id.item_library_title);
        TextView artist = findViewById(R.id.item_library_subtitle);
        ImageView art = findViewById(R.id.item_library_art);

        String info = item.getArtist().getAlbumCount() + " Album | " + item.getArtist().getTrackCount() + " Track";

        title.setText(item.getArtist().getName());
        artist.setText(info);

        ImageLoader.load(art, item.getArtist().getArtistArtUri());

    }

    @Override
    public void onPlayingStateViewHolder(PlayerCacheModel cacheModel, BaseRecyclerViewItem viewItem) {

    }
}
