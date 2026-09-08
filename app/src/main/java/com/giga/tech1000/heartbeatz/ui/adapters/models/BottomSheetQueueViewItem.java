package com.giga.tech1000.heartbeatz.ui.adapters.models;

import android.net.Uri;

import com.giga.tech1000.media_player.models.Song;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class BottomSheetQueueViewItem extends BaseRecyclerViewItem {

    private Song song;

    public BottomSheetQueueViewItem(Song song) {
        super(song.getTitle(), ItemType.BOTTOM_SHEET_SONG);
        this.song = song;
    }

    public Uri getUri() {
        return song.getUri();
    }


    public Song getSong() {
        return song;
    }

    @Override
    public List<String> searchTokens() {
        return Arrays.asList(
                song.getTitle(),
                song.getArtist(),
                song.getAlbum(),
                song.getDisplayName()
        );
    }

    @Override
    public int hashcode() {
        return getUri().hashCode() + getTitle().hashCode();
    }

    @Override
    public int getId() {
        return (int) song.getId();
    }

}
