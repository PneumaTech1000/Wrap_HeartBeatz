package com.giga.tech1000.heartbeatz.ui.adapters.view_holders;

import android.view.View;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.giga.tech1000.heartbeatz.R;
import com.giga.tech1000.heartbeatz.utils.ImageLoader;
import com.giga.tech1000.heartbeatz.ui.adapters.BaseRecyclerViewAdapter;
import com.giga.tech1000.heartbeatz.ui.adapters.models.BaseRecyclerViewItem;
import com.giga.tech1000.heartbeatz.ui.adapters.models.SongViewItem;
import com.giga.tech1000.media_player.models.extended_models.PlayerCacheModel;
import com.giga.tech1000.media_player.utils.enums.ItemSource;
import com.giga.tech1000.visualizer_android.visualizer.DummyBarVisualizer;

public class SongViewHolder extends BaseRecyclerViewHolder {

    public ImageButton moreBtn;

    public SongViewHolder(@NonNull View itemView) {
        super(itemView);

        moreBtn = findViewById(R.id.item_library_more);
    }

    @Override
    public void onInitializeView(BaseRecyclerViewAdapter.ViewType viewType) {
    }

    @Override
    public void onBindViewHolder(BaseRecyclerViewItem viewItem) {
        SongViewItem item = (SongViewItem) viewItem;

        TextView title = findViewById(R.id.item_library_title);
        TextView artist = findViewById(R.id.item_library_subtitle);
        ImageView art = findViewById(R.id.item_library_art);


        title.setText(item.getSong().getTitle());
        artist.setText(item.getSong().getArtist());

        ImageLoader.load(art, item.getSong().getAlbumArt());

    }

    @Override
    public void onPlayingStateViewHolder(PlayerCacheModel playingCache, BaseRecyclerViewItem viewItem) {
        DummyBarVisualizer visualizer = findViewById(R.id.item_library_visualizer);
        SongViewItem item = (SongViewItem) viewItem;
        boolean isItemPlaying = false;
        boolean isPlaybackPlaying = playingCache.isPlaying();
        if (playingCache.getCurrentSong() != null) isItemPlaying = playingCache.getCurrentSong().id == item.getId();

        if (playingCache.getSource() == ItemSource.ALL_SONGS) {
            visualizer.setVisibility(isItemPlaying ? View.VISIBLE : View.GONE);
            visualizer.setPlaying(isPlaybackPlaying);
        }

    }


}
