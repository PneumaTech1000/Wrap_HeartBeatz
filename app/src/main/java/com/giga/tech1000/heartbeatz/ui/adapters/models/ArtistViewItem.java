package com.giga.tech1000.heartbeatz.ui.adapters.models;

import com.giga.tech1000.media_player.models.Artist;
import com.giga.tech1000.utils.interfaces.Searchable;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class ArtistViewItem extends BaseRecyclerViewItem {
private final Artist artist;

    public ArtistViewItem(Artist artist) {
        super(artist.getName(), ItemType.ARTIST);
        this.artist = artist;
    }

    public Artist getArtist() {
        return artist;
    }


    @Override
    public int hashcode() {
        return artist.toString().hashCode();
    }

    @Override
    public int getId() {
        return (int)artist.getId();
    }

    @Override
    public List<String> searchTokens() {
        return Collections.singletonList(
                artist.getName()
        );
    }
}
