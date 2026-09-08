package com.giga.tech1000.media_player.utils;

import com.giga.tech1000.media_player.models.Song;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class DiffResult {
    public final List<Long> newSongIds;
    public final List<Long> updatedSongIds;
    public final List<Long> deletedSongIds;

    public DiffResult(
            List<Long> newSongIds,
            List<Long> updatedSongIds,
            List<Long> deletedSongIds
    ) {
        this.newSongIds = newSongIds;
        this.updatedSongIds = updatedSongIds;
        this.deletedSongIds = deletedSongIds;
    }

    public boolean hasAnyChange() {
        return !newSongIds.isEmpty()
                || !updatedSongIds.isEmpty()
                || !deletedSongIds.isEmpty();
    }

    public Set<Long> affectedSongIds() {
        Set<Long> ids = new HashSet<>(newSongIds);
        ids.addAll(updatedSongIds);
        return ids;
    }

    public List<Song> affectedSongs() {
        return null;
    }
}

