package com.giga.tech1000.heartbeatz.ui.adapters.view_holders;

import android.view.View;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.giga.tech1000.heartbeatz.R;
import com.giga.tech1000.heartbeatz.utils.ImageLoader;
import com.giga.tech1000.heartbeatz.ui.adapters.BaseRecyclerViewAdapter;
import com.giga.tech1000.heartbeatz.ui.adapters.models.BaseRecyclerViewItem;
import com.giga.tech1000.heartbeatz.ui.adapters.models.SelectSongViewItem;
import com.giga.tech1000.media_player.models.extended_models.PlayerCacheModel;

public class SelectSongViewHolder extends BaseRecyclerViewHolder {


    public SelectSongViewHolder(@NonNull View itemView) {
        super(itemView);
    }

    @Override
    public void onInitializeView(BaseRecyclerViewAdapter.ViewType viewType) {

    }

    @Override
    public void onBindViewHolder(BaseRecyclerViewItem viewItem) {
        SelectSongViewItem item = (SelectSongViewItem) viewItem;

        CheckBox selectionBox = findViewById(R.id.selection_box);
        TextView title = findViewById(R.id.item_library_title);
        TextView artist = findViewById(R.id.item_library_subtitle);
        ImageView art = findViewById(R.id.item_library_art);

        title.setText(item.getSong().getTitle());
        artist.setText(item.getSong().getArtist());

        ImageLoader.load(art, item.getSong().getAlbumArt());

        selectionBox.setOnCheckedChangeListener(null);
        selectionBox.setChecked(item.isSelected());



        selectionBox.setOnCheckedChangeListener((btn, checked) -> {
            item.setSelected(checked);
        });

    }

    @Override
    public void onPlayingStateViewHolder(PlayerCacheModel cacheModel, BaseRecyclerViewItem viewItem) {

    }
}
