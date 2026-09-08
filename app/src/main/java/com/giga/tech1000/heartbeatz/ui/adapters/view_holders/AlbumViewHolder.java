package com.giga.tech1000.heartbeatz.ui.adapters.view_holders;

import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.giga.tech1000.heartbeatz.R;
import com.giga.tech1000.heartbeatz.utils.ImageLoader;
import com.giga.tech1000.heartbeatz.ui.adapters.BaseRecyclerViewAdapter;
import com.giga.tech1000.heartbeatz.ui.adapters.models.AlbumViewItem;
import com.giga.tech1000.heartbeatz.ui.adapters.models.BaseRecyclerViewItem;
import com.giga.tech1000.media_player.models.Album;
import com.giga.tech1000.media_player.models.extended_models.PlayerCacheModel;

public class AlbumViewHolder extends BaseRecyclerViewHolder {


    public AlbumViewHolder(@NonNull View itemView) {
        super(itemView);
    }

    @Override
    public void onInitializeView(BaseRecyclerViewAdapter.ViewType viewType) {

    }



    @Override
    public void onBindViewHolder(BaseRecyclerViewItem viewItem) {
        AlbumViewItem item = (AlbumViewItem) viewItem;

        TextView title = findViewById(R.id.item_library_title);
        TextView artist = findViewById(R.id.item_library_subtitle);
        ImageView art = findViewById(R.id.item_library_art);

        //String info = item.getAlbum().getArtist().concat(" | ").concat(String.valueOf(item.getAlbum().getSongCount())).concat(" Track");
        Album album = item.getAlbum();
        String info = "Unknown Artist | 0 Track";

        info = item.getAlbum().getArtist() + " | " + item.getAlbum().getSongCount() + " Track";


        title.setText(item.getAlbum().getName());
        artist.setText(info);

        ImageLoader.load(art, item.getAlbum().getArtUri());

    }

    @Override
    public void onPlayingStateViewHolder(PlayerCacheModel cacheModel, BaseRecyclerViewItem viewItem) {

    }
}
