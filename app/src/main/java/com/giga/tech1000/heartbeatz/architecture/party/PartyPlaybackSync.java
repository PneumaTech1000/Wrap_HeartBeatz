package com.giga.tech1000.heartbeatz.architecture.party;

import android.os.SystemClock;

import androidx.annotation.Nullable;

/**
 * Host-authoritative sync under {@code parties/{partyId}/sync}.
 * <p>
 * Aligned with {@link com.giga.tech1000.heartbeatz.architecture.timeengine.TimeEngine}:
 * scheduleId, host mono, 5s lookahead targets, Firebase server write time.
 */
public class PartyPlaybackSync {

    public static final long DEFAULT_LOOKAHEAD_MS = 5_000L;

    @Nullable public String objectKey;
    @Nullable public String mediaUrl;
    @Nullable public String trackId;
    @Nullable public String title;
    @Nullable public String artist;
    @Nullable public String album;

    /** Monotonic schedule id; guests ignore lower ids. */
    public long scheduleId;

    public long positionMs;
    public long targetPositionMs;
    public long lookaheadMs = DEFAULT_LOOKAHEAD_MS;
    public boolean isPlaying;
    public long durationMs;

    /** Host {@link SystemClock#elapsedRealtime()} at authoring. */
    public long hostMonoMs;

    /** Host mono when targetPosition should be reached. */
    public long targetHostMonoMs;

    /** Firebase ServerValue.TIMESTAMP (Long after read). */
    @Nullable public Object updatedAt;

    /** Guest-only: wall receive time. */
    public transient long receivedAtDeviceMs;

    public PartyPlaybackSync() {}

    /**
     * Build host packet with 5s lookahead and host mono stamps.
     */
    public static PartyPlaybackSync schedule(
            @Nullable String objectKey,
            @Nullable String mediaUrl,
            @Nullable String trackId,
            @Nullable String title,
            @Nullable String artist,
            @Nullable String album,
            long positionMs,
            long durationMs,
            boolean isPlaying) {
        return schedule(objectKey, mediaUrl, trackId, title, artist, album,
                positionMs, durationMs, isPlaying, nextScheduleId());
    }

    public static PartyPlaybackSync schedule(
            @Nullable String objectKey,
            @Nullable String mediaUrl,
            @Nullable String trackId,
            @Nullable String title,
            @Nullable String artist,
            @Nullable String album,
            long positionMs,
            long durationMs,
            boolean isPlaying,
            long scheduleId) {
        long look = DEFAULT_LOOKAHEAD_MS;
        long mono = SystemClock.elapsedRealtime();
        long pos = Math.max(0L, positionMs);
        long target = isPlaying ? pos + look : pos;
        if (durationMs > 0 && target > durationMs) {
            target = durationMs;
        }

        PartyPlaybackSync s = new PartyPlaybackSync();
        s.objectKey = objectKey;
        s.mediaUrl = mediaUrl;
        s.trackId = trackId;
        s.title = title;
        s.artist = artist;
        s.album = album;
        s.scheduleId = scheduleId;
        s.positionMs = pos;
        s.targetPositionMs = target;
        s.lookaheadMs = look;
        s.isPlaying = isPlaying;
        s.durationMs = Math.max(0L, durationMs);
        s.hostMonoMs = mono;
        s.targetHostMonoMs = mono + look;
        return s;
    }

    private static long scheduleSeq = 1L;

    public static synchronized long nextScheduleId() {
        // Mix wall + seq so ids increase and stay unique across process restarts
        return (System.currentTimeMillis() << 10) | (scheduleSeq++ & 0x3FF);
    }

    public long serverWriteTimeMs() {
        if (updatedAt instanceof Long) {
            return (Long) updatedAt;
        }
        if (updatedAt instanceof Double) {
            return ((Double) updatedAt).longValue();
        }
        if (updatedAt instanceof Number) {
            return ((Number) updatedAt).longValue();
        }
        return -1L;
    }

    public long targetServerTimeMs() {
        long w = serverWriteTimeMs();
        if (w < 0) return -1L;
        return w + Math.max(0L, lookaheadMs > 0 ? lookaheadMs : DEFAULT_LOOKAHEAD_MS);
    }
}
