package com.giga.tech1000.heartbeatz.ui.party;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * UI model for a track in the party queue (backend wiring later).
 */
public final class PartyQueueItem {

    @NonNull public final String id;
    @NonNull public final String title;
    @Nullable public final String artist;
    @Nullable public final String addedByName;
    public final long localSongId;
    @Nullable public final String mediaUrl;
    @Nullable public final String objectKey;

    public PartyQueueItem(
            @NonNull String id,
            @NonNull String title,
            @Nullable String artist,
            @Nullable String addedByName,
            long localSongId,
            @Nullable String mediaUrl,
            @Nullable String objectKey) {
        this.id = id;
        this.title = title;
        this.artist = artist;
        this.addedByName = addedByName;
        this.localSongId = localSongId;
        this.mediaUrl = mediaUrl;
        this.objectKey = objectKey;
    }

    @NonNull
    public String subtitle() {
        String a = artist != null ? artist : "Unknown";
        if (addedByName != null && !addedByName.isEmpty()) {
            return a + " · " + addedByName;
        }
        return a;
    }
}
