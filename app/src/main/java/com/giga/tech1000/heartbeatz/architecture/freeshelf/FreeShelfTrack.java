package com.giga.tech1000.heartbeatz.architecture.freeshelf;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * One curated legal stream entry (never scraped commercial catalogs).
 */
public final class FreeShelfTrack {

    @NonNull public final String id;
    @NonNull public final String title;
    @NonNull public final String artist;
    @Nullable public final String album;
    @NonNull public final String streamUrl;
    @Nullable public final String artworkUrl;
    @NonNull public final FreeShelfLicense license;
    /** Human-readable credit line shown in UI / notification. */
    @NonNull public final String attribution;
    @Nullable public final String sourceName;
    @Nullable public final String sourcePageUrl;
    public final long durationMs;
    public final boolean remote;

    public FreeShelfTrack(
            @NonNull String id,
            @NonNull String title,
            @NonNull String artist,
            @Nullable String album,
            @NonNull String streamUrl,
            @Nullable String artworkUrl,
            @NonNull FreeShelfLicense license,
            @NonNull String attribution,
            @Nullable String sourceName,
            @Nullable String sourcePageUrl,
            long durationMs,
            boolean remote) {
        this.id = id;
        this.title = title;
        this.artist = artist;
        this.album = album;
        this.streamUrl = streamUrl;
        this.artworkUrl = artworkUrl;
        this.license = license;
        this.attribution = attribution;
        this.sourceName = sourceName;
        this.sourcePageUrl = sourcePageUrl;
        this.durationMs = durationMs;
        this.remote = remote;
    }

    public boolean isPlayable() {
        return streamUrl.startsWith("https://") || streamUrl.startsWith("http://");
    }
}
