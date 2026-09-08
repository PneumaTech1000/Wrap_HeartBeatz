package com.giga.tech1000.heartbeatz.ui.adapters.models;

import androidx.annotation.NonNull;

import com.giga.tech1000.media_player.models.Song;

import java.util.Arrays;
import java.util.List;

public class SelectSongViewItem extends BaseRecyclerViewItem {
    private final Song song;
    private boolean selected;

    public SelectSongViewItem(Song song) {
        super(song.getTitle(), ItemType.SELECTION_SONG);
        this.song = song;
    }

    public Song getSong() {
        return song;
    }

    public boolean isSelected() {
        return selected;
    }

    public void setSelected(boolean selected) {
        this.selected = selected;
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
        return (int) song.getId();
    }

    @NonNull
    @Override
    public String toString() {
        return "SelectSongViewItem{" +
                "song=" + song +
                '}';
    }
}
