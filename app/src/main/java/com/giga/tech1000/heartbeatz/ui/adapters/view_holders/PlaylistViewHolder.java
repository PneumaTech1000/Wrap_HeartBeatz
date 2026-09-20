package com.giga.tech1000.heartbeatz.ui.adapters.view_holders;

import android.graphics.PorterDuff;
import android.view.View;
import android.widget.ImageView;
import android.widget.ImageButton;
import android.widget.PopupMenu;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.giga.tech1000.heartbeatz.R;
import com.giga.tech1000.heartbeatz.ui.adapters.BaseRecyclerViewAdapter;
import com.giga.tech1000.heartbeatz.ui.adapters.models.BaseRecyclerViewItem;
import com.giga.tech1000.heartbeatz.ui.adapters.models.playlist_item.PlaylistViewItem;
import com.giga.tech1000.media_player.models.extended_models.DefaultPlaylists;
import com.giga.tech1000.media_player.models.extended_models.PlayerCacheModel;
import com.giga.tech1000.heartbeatz.interfaces.MediaNavigation;

public class PlaylistViewHolder extends BaseRecyclerViewHolder {

    private final MediaNavigation navigation;

    public PlaylistViewHolder(@NonNull View itemView, MediaNavigation navigation) {
        super(itemView);
        this.navigation = navigation;
    }

    @Override
    public void onInitializeView(BaseRecyclerViewAdapter.ViewType viewType) {

    }

    @Override
    public void onBindViewHolder(BaseRecyclerViewItem viewItem) {
        PlaylistViewItem item = (PlaylistViewItem) viewItem;

        TextView title = findViewById(R.id.item_library_title);
        TextView artist = findViewById(R.id.item_library_subtitle);
        ImageView art = findViewById(R.id.item_library_art);
        ImageButton more = findViewById(R.id.item_library_more);

        String info = item.getPlaylist().getNumOfSongs() + (item.getPlaylist().getNumOfSongs() > 1 ? " Tracks" : " Track");


        title.setText(item.getPlaylist().getPlaylist().getName());
        artist.setText(info);

        art.setImageResource(com.giga.tech1000.icons_pack.R.drawable.add_circle_24px);
        art.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        art.setColorFilter(itemView.getContext().getColor(R.color.hb_primary), PorterDuff.Mode.SRC_IN);

        if (item.getPlaylist().getPlaylist().getPlaylistId() == DefaultPlaylists.ALL_SONGS_ID) {
            more.setVisibility(View.GONE);
        } else {
            more.setVisibility(View.VISIBLE);
            more.setOnClickListener(v -> {
                PopupMenu popup = new PopupMenu(itemView.getContext(), more);
                popup.getMenu().add("Rename");
                popup.getMenu().add("Delete");
                popup.setOnMenuItemClickListener(menuItem -> {
                    if (menuItem.getTitle().equals("Rename")) {
                        navigation.renamePlaylist(item.getPlaylist().getPlaylist().getPlaylistId(), item.getPlaylist().getPlaylist().getName());
                    } else if (menuItem.getTitle().equals("Delete")) {
                        navigation.deletePlaylist(item.getPlaylist().getPlaylist());
                    }
                    return true;
                });
                popup.show();
            });
        }
    }

    @Override
    public void onPlayingStateViewHolder(PlayerCacheModel playingCache, BaseRecyclerViewItem viewItem) {
//        DummyBarVisualizer visualizer = findViewById(R.id.item_library_visualizer);
//        PlaylistViewItem item = (PlaylistViewItem) viewItem;
//        boolean isPlaybackPlaying = playingCache.getState() == isPlaying();
//        boolean isItemPlaying = playingCache.getItemId() == item.getId();
//
//        if (playingCache.getSource() == ItemSource.PLAYLISTS) {
//            visualizer.setVisibility(isItemPlaying ? View.VISIBLE : View.GONE);
//            visualizer.setPlaying(isPlaybackPlaying);
//        }

    }
}
