package com.giga.tech1000.heartbeatz.ui.adapters.models;

import com.giga.tech1000.media_player.models.cross_ref.GenreWithCount;
import com.giga.tech1000.utils.interfaces.Searchable;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class GenreViewItem extends BaseRecyclerViewItem {

private final GenreWithCount genreWithCount;
    public GenreViewItem(GenreWithCount genreWithCount) {
        super(genreWithCount.getGenre().getName(), ItemType.GENRE);
        this.genreWithCount = genreWithCount;
    }

    public GenreWithCount getGenreWithCount() {
        return genreWithCount;
    }

    @Override
    public int hashcode() {
        return genreWithCount.toString().hashCode();
    }

    @Override
    public int getId() {
        return (int) genreWithCount.getGenre().getId();
    }

    @Override
    public List<String> searchTokens() {
        return Collections.singletonList(
                genreWithCount.getGenre().getName()
        );
    }
}
