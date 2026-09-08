package com.giga.tech1000.heartbeatz.ui.adapters.models;

import androidx.annotation.Nullable;

import com.giga.tech1000.media_player.models.Album;
import com.giga.tech1000.utils.interfaces.Searchable;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class AlbumViewItem extends BaseRecyclerViewItem {

    private final Album album;

    public AlbumViewItem(Album album) {
        super(album.getName(), ItemType.ALBUM);
        this.album = album;
    }


    public Album getAlbum() {
        return album;
    }


    @Override
    public final int hashcode() {
        return album.toString().hashCode();
    }

    @Override
    public int getId() {
        return (int) album.getId();
    }

    @Override
    public List<String> searchTokens() {
        return Arrays.asList(
                album.getName(),
                album.getArtist()
        );
    }
}
