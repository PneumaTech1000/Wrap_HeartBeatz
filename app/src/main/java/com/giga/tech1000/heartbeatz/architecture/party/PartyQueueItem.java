package com.giga.tech1000.heartbeatz.architecture.party;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.giga.tech1000.media_player.models.Song;

/**
 * Shared party queue row under {@code parties/{id}/queue/{itemId}}.
 * <p>
 * Lifecycle: {@link #STATUS_PENDING} (guest suggest) → host
 * {@link #STATUS_APPROVED} / {@link #STATUS_REJECTED} → {@link #STATUS_READY}
 * when mediaUrl is available → optional {@link #STATUS_PLAYING}.
 */
public class PartyQueueItem {

    public static final String STATUS_PENDING = "pending";
    public static final String STATUS_APPROVED = "approved";
    public static final String STATUS_READY = "ready";
    public static final String STATUS_PLAYING = "playing";
    public static final String STATUS_REJECTED = "rejected";
    public static final String STATUS_REMOVED = "removed";

    /** Firebase push key. */
    @Nullable public String itemId;
    @Nullable public String trackId;
    @Nullable public String title;
    @Nullable public String artist;
    @Nullable public String album;
    /** pending | approved | ready | playing | rejected | removed */
    @Nullable public String status;
    @Nullable public String suggestedByUid;
    @Nullable public String suggestedByName;
    /** Sort key; lower plays earlier. Host controls. */
    public long orderIndex;
    @Nullable public String mediaUrl;
    @Nullable public String objectKey;
    public long durationMs;
    public long createdAt;
    public long updatedAt;

    public PartyQueueItem() {}

    public boolean isPending() {
        return STATUS_PENDING.equals(status);
    }

    public boolean isReadyOrPlaying() {
        return STATUS_READY.equals(status) || STATUS_PLAYING.equals(status);
    }

    public boolean isVisibleInQueue() {
        return !STATUS_REJECTED.equals(status) && !STATUS_REMOVED.equals(status);
    }

    @NonNull
    public Song toDisplaySong() {
        Song s = new Song();
        try {
            if (trackId != null) {
                String digits = trackId.replaceAll("[^0-9]", "");
                if (!digits.isEmpty()) {
                    s.setId(Long.parseLong(digits));
                } else {
                    s.setId(trackId.hashCode() & 0x7fffffff);
                }
            }
        } catch (Exception ignored) { }
        s.setTitle(title != null ? title : "");
        s.setArtist(artist != null ? artist : "");
        if (album != null && !album.isEmpty()) {
            s.setAlbum(album);
        }
        s.setDuration(durationMs);
        if (mediaUrl != null && !mediaUrl.isEmpty()) {
            try {
                s.setData(mediaUrl);
            } catch (Exception ignored) { }
        }
        return s;
    }

    @NonNull
    public static PartyQueueItem fromSong(
            @NonNull Song song,
            @NonNull String status,
            @Nullable String suggestedByUid,
            @Nullable String suggestedByName,
            long orderIndex) {
        PartyQueueItem item = new PartyQueueItem();
        item.trackId = String.valueOf(song.getId());
        item.title = song.getTitle();
        item.artist = song.getArtist();
        try {
            item.album = song.getAlbum();
        } catch (Exception e) {
            item.album = null;
        }
        item.durationMs = song.getDuration();
        item.status = status;
        item.suggestedByUid = suggestedByUid;
        item.suggestedByName = suggestedByName;
        item.orderIndex = orderIndex;
        item.createdAt = System.currentTimeMillis();
        item.updatedAt = item.createdAt;
        return item;
    }
}
