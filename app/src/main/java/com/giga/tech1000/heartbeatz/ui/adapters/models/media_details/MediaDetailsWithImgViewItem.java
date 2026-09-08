package com.giga.tech1000.heartbeatz.ui.adapters.models.media_details;

import androidx.annotation.NonNull;

import com.giga.tech1000.heartbeatz.ui.adapters.models.BaseRecyclerViewItem;
import com.giga.tech1000.media_player.models.Song;

import java.util.Arrays;
import java.util.List;

public class MediaDetailsWithImgViewItem extends BaseRecyclerViewItem {
    private final Song song;

    public MediaDetailsWithImgViewItem(Song song) {
        super(song.getTitle(), ItemType.MEDIA_DETAILS);
        this.song = song;
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
        return toString().hashCode();
    }

    @Override
    public int getId() {
        return (int) getSong().getId();
    }

    @NonNull
    @Override
    public String toString() {
        return "MediaDetailsWithImgViewItem{" +
                "song=" + song +
                '}';
    }
}
