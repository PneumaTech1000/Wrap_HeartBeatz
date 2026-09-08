package com.giga.tech1000.heartbeatz.ui.adapters.models.playlist_item;

import com.giga.tech1000.heartbeatz.ui.adapters.models.BaseRecyclerViewItem;
import com.giga.tech1000.media_player.models.Playlist;
import com.giga.tech1000.media_player.models.cross_ref.PlaylistWithCount;

import java.util.Collections;
import java.util.List;

public class PlaylistViewItem extends BaseRecyclerViewItem {

    private final PlaylistWithCount playlist;

    public PlaylistViewItem(PlaylistWithCount playlist) {
        super(playlist.getPlaylist().getName(), ItemType.PLAYLIST);

        this.playlist = playlist;
    }

    public PlaylistWithCount getPlaylist() {
        return playlist;
    }



    @Override
    public int hashcode() {
        return playlist.toString().hashCode();
    }

    @Override
    public int getId() {
        return (int) playlist.getPlaylist().getPlaylistId();
    }

    @Override
    public List<String> searchTokens() {
        return Collections.singletonList(
                playlist.getPlaylist().getName()
        );
    }
}
