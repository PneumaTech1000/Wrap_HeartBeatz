package com.giga.tech1000.heartbeatz.ui.adapters.view_holders;

import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.giga.tech1000.heartbeatz.R;
import com.giga.tech1000.heartbeatz.ui.adapters.BaseRecyclerViewAdapter;
import com.giga.tech1000.heartbeatz.ui.adapters.models.BaseRecyclerViewItem;
import com.giga.tech1000.heartbeatz.ui.adapters.models.media_details.MediaDetailsWithImgViewItem;
import com.giga.tech1000.media_player.models.extended_models.PlayerCacheModel;
import com.giga.tech1000.media_player.utils.enums.ItemSource;
import com.giga.tech1000.visualizer_android.visualizer.DummyBarVisualizer;

public class MediaDetailsWithImgViewHolder extends BaseRecyclerViewHolder {


    public MediaDetailsWithImgViewHolder(@NonNull View itemView) {
        super(itemView);
    }

    @Override
    public void onInitializeView(BaseRecyclerViewAdapter.ViewType viewType) {

    }

    @Override
    public void onBindViewHolder(BaseRecyclerViewItem viewItem) {
        MediaDetailsWithImgViewItem item = (MediaDetailsWithImgViewItem) viewItem;

        TextView title = findViewById(R.id.item_library_title);
        TextView artist = findViewById(R.id.item_library_subtitle);

        title.setText(item.getSong().getTitle());
        artist.setText(item.getSong().getArtist());

    }

    @Override
    public void onPlayingStateViewHolder(PlayerCacheModel cacheModel, BaseRecyclerViewItem viewItem) {
        DummyBarVisualizer visualizer = findViewById(R.id.item_library_visualizer);
        MediaDetailsWithImgViewItem item = (MediaDetailsWithImgViewItem) viewItem;
        boolean isItemPlaying = false;
        boolean isPlaybackPlaying = cacheModel.isPlaying();
        if (cacheModel.getCurrentSong() != null) isItemPlaying = cacheModel.getCurrentSong().id == item.getId();

        if (cacheModel.getSource() == ItemSource.MEDIA_DETAILS_WITH_IMG) {
            visualizer.setVisibility(isItemPlaying ? View.VISIBLE : View.GONE);
            visualizer.setPlaying(isPlaybackPlaying);
        }
    }
}
