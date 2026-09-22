package com.giga.tech1000.heartbeatz.architecture.party;

import androidx.annotation.Nullable;

/**
 * Host-authoritative playback snapshot under {@code parties/{partyId}/sync}.
 * Guests apply this to Media3 (URL + play/pause + seek + drift correction).
 * <p>
 * Firebase-friendly POJO (public fields / empty ctor for deserialization).
 */
public class PartyPlaybackSync {

    /** Object storage key */
    @Nullable public String objectKey;

    /** Playable media URL (signed or public) */
    @Nullable public String mediaUrl;

    /** App / library track id */
    @Nullable public String trackId;

    @Nullable public String title;
    @Nullable public String artist;

    /** Host position when this snapshot was written */
    public long positionMs;

    public boolean isPlaying;

    /** Client wall-clock at host write (ms); prefer serverTimeOffset when available */
    public long updatedAtClientMs;

    /** Firebase ServerValue.TIMESTAMP when written (Long) */
    @Nullable public Object updatedAt;

    public PartyPlaybackSync() {}

    public static PartyPlaybackSync of(
            @Nullable String objectKey,
            @Nullable String mediaUrl,
            @Nullable String trackId,
            @Nullable String title,
            @Nullable String artist,
            long positionMs,
            boolean isPlaying) {
        PartyPlaybackSync s = new PartyPlaybackSync();
        s.objectKey = objectKey;
        s.mediaUrl = mediaUrl;
        s.trackId = trackId;
        s.title = title;
        s.artist = artist;
        s.positionMs = positionMs;
        s.isPlaying = isPlaying;
        s.updatedAtClientMs = System.currentTimeMillis();
        return s;
    }
}
