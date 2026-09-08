package com.giga.tech1000.heartbeatz.ui.adapters.models;

import android.net.Uri;

import androidx.annotation.NonNull;

import com.giga.tech1000.media_player.models.Song;

import java.util.Arrays;
import java.util.List;

public class SongViewItem extends BaseRecyclerViewItem {

    private final Song song;

    public SongViewItem(Song song) {
        super(song.getTitle(), ItemType.SONG);

        this.song = song;
    }

    public Uri getUri() {
        return song.getUri();
    }


    public Song getSong() {
        return song;
    }

    @Override
    public int hashcode() {
        return toString().hashCode();
    }

    @Override
    public int getId() {
        return (int) song.getId();
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

    @NonNull
    @Override
    public String toString() {
        return "SongViewItem{" +
                "song=" + song +
                '}';
    }
}
