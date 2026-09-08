package com.giga.tech1000.heartbeatz.ui.adapters.view_holders;

import android.support.v4.media.session.PlaybackStateCompat;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.constraintlayout.widget.ConstraintLayout;

import com.giga.tech1000.heartbeatz.R;
import com.giga.tech1000.heartbeatz.utils.ImageLoader;
import com.giga.tech1000.heartbeatz.ui.adapters.BaseRecyclerViewAdapter;
import com.giga.tech1000.heartbeatz.ui.adapters.models.BaseRecyclerViewItem;
import com.giga.tech1000.heartbeatz.ui.adapters.models.BottomSheetQueueViewItem;
import com.giga.tech1000.media_player.models.extended_models.PlayerCacheModel;
import com.giga.tech1000.visualizer_android.visualizer.DummyBarVisualizer;

public class BottomSheetQueueViewHolder extends BaseRecyclerViewHolder {

    private ConstraintLayout infoWrapper;

    public BottomSheetQueueViewHolder(@NonNull View itemView) {
        super(itemView);
    }

    @Override
    public void onInitializeView(BaseRecyclerViewAdapter.ViewType viewType) { }

    @Override
    public void onBindViewHolder(BaseRecyclerViewItem viewItem) {
        BottomSheetQueueViewItem item = (BottomSheetQueueViewItem) viewItem;

        infoWrapper = findViewById(R.id.bottom_sheet_info_wrapper);

        TextView title = findViewById(R.id.bottom_sheet_queue_title);
        TextView artist = findViewById(R.id.bottom_sheet_queue_artist);
        ImageView art = findViewById(R.id.bottom_sheet_queue_image);


        title.setText(item.getSong().getTitle());
        artist.setText(item.getSong().getArtist());

        ImageLoader.load(art, item.getSong().getAlbumArt());

    }

    @Override
    public void onPlayingStateViewHolder(PlayerCacheModel cacheModel, BaseRecyclerViewItem viewItem) {
        DummyBarVisualizer visualizer = findViewById(R.id.bottom_sheet_visualizer);
//        visualizer.setVisibility(View.GONE);
//        visualizer.setPlaying(false);
        BottomSheetQueueViewItem item = (BottomSheetQueueViewItem) viewItem;
        boolean isItemPlaying = false;
        boolean isPlaybackPlaying = cacheModel.getState() == PlaybackStateCompat.STATE_PLAYING;
        if (cacheModel.getCurrentSong() != null) isItemPlaying = cacheModel.getCurrentSong().id == item.getId();


        float targetScale = isItemPlaying ? 1.03f : 1f;
        float targetAlpha = isItemPlaying ? 1f : 0.6f;


        // 🚨 CRITICAL: cancel previous animations
        infoWrapper.animate().cancel();

        // 🚨 CRITICAL: force-reset state BEFORE animating
        infoWrapper.setScaleX(targetScale);
        infoWrapper.setScaleY(targetScale);
        infoWrapper.setAlpha(targetAlpha);

        // Optional: smooth transition
        infoWrapper.animate()
                .scaleX(targetScale)
                .scaleY(targetScale)
                .alpha(targetAlpha)
                .setDuration(180)
                .start();
        visualizer.setVisibility(isItemPlaying ? View.VISIBLE : View.GONE);
        visualizer.setPlaying(isPlaybackPlaying);

    }


}