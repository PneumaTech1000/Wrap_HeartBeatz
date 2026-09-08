package com.giga.tech1000.heartbeatz.ui.adapters.view_holders;

import android.support.v4.media.session.PlaybackStateCompat;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.giga.tech1000.heartbeatz.R;
import com.giga.tech1000.heartbeatz.utils.ImageLoader;
import com.giga.tech1000.heartbeatz.ui.adapters.BaseRecyclerViewAdapter;
import com.giga.tech1000.heartbeatz.ui.adapters.models.BaseRecyclerViewItem;
import com.giga.tech1000.heartbeatz.ui.adapters.models.media_details.MediaDetailsWithoutImgViewItem;
import com.giga.tech1000.media_player.models.extended_models.PlayerCacheModel;
import com.giga.tech1000.media_player.utils.enums.ItemSource;
import com.giga.tech1000.visualizer_android.visualizer.DummyBarVisualizer;

public class MediaDetailsWithoutImgViewHolder extends BaseRecyclerViewHolder {


    public MediaDetailsWithoutImgViewHolder(@NonNull View itemView) {
        super(itemView);
    }

    @Override
    public void onInitializeView(BaseRecyclerViewAdapter.ViewType viewType) {

    }

    @Override
    public void onBindViewHolder(BaseRecyclerViewItem viewItem) {
        MediaDetailsWithoutImgViewItem item = (MediaDetailsWithoutImgViewItem) viewItem;

        TextView title = findViewById(R.id.item_library_title);
        TextView artist = findViewById(R.id.item_library_subtitle);
        ImageView art = findViewById(R.id.item_library_art);


        title.setText(item.getSong().getTitle());
        artist.setText(item.getSong().getArtist());

        ImageLoader.load(art, item.getSong().getAlbumArt());

    }

    @Override
    public void onPlayingStateViewHolder(PlayerCacheModel cacheModel, BaseRecyclerViewItem viewItem) {
        DummyBarVisualizer visualizer = findViewById(R.id.item_library_visualizer);
        MediaDetailsWithoutImgViewItem item = (MediaDetailsWithoutImgViewItem) viewItem;
        boolean isItemPlaying = false;
        boolean isPlaybackPlaying = cacheModel.getState() == PlaybackStateCompat.STATE_PLAYING;
        if (cacheModel.getCurrentSong() != null) isItemPlaying = cacheModel.getCurrentSong().id == item.getId();

        if (cacheModel.getSource() == ItemSource.MEDIA_DETAILS_WITHOUT_IMG) {
            visualizer.setVisibility(isItemPlaying ? View.VISIBLE : View.GONE);
            visualizer.setPlaying(isPlaybackPlaying);
        }

    }
}
