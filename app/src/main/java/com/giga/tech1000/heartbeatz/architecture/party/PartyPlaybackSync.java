package com.giga.tech1000.heartbeatz.architecture.party;

import android.os.SystemClock;

import androidx.annotation.Nullable;

/**
 * Host-authoritative sync under {@code parties/{partyId}/sync}.
 * <p>
 * <b>Server Epoch Timeline (SET):</b> ideal media position is derived from
 * {@link #epochMediaMs} + {@link #epochServerMs}, not from chasing every
 * {@link #positionMs} heartbeat. See {@code SERVER_EPOCH_TIMELINE.md}.
 */
public class PartyPlaybackSync {

    public static final long DEFAULT_LOOKAHEAD_MS = 4_000L;

    @Nullable public String objectKey;
    @Nullable public String mediaUrl;
    @Nullable public String trackId;
    @Nullable public String title;
    @Nullable public String artist;
    @Nullable public String album;

    /** Monotonic schedule id; guests re-arm only when this increases for a new track/seek. */
    public long scheduleId;

    /** Diagnostic host sample at publish (not ideal authority when epoch is set). */
    public long positionMs;
    /** Arm window target only (lookahead); not continuous ideal chase. */
    public long targetPositionMs;
    public long lookaheadMs = DEFAULT_LOOKAHEAD_MS;
    public boolean isPlaying;
    public long durationMs;

    /**
     * Media position (ms) at {@link #epochServerMs}.
     * Set on force events; sticky across heartbeats in Firebase.
     */
    public long epochMediaMs = -1L;

    /**
     * Firebase server time (ms) of the epoch instant.
     * Written as {@code ServerValue.TIMESTAMP} on force; resolved on read.
     */
    public long epochServerMs = -1L;

    /** Host {@link SystemClock#elapsedRealtime()} at authoring (local only). */
    public long hostMonoMs;

    /** Host mono when targetPosition should be reached (arm helper). */
    public long targetHostMonoMs;

    /** Firebase ServerValue.TIMESTAMP (Long after read). */
    @Nullable public Object updatedAt;

    /** Guest-only: wall receive time. */
    public transient long receivedAtDeviceMs;

    /** Host-only: this publish should write new epoch keys to Firebase. */
    public transient boolean writeEpoch;

    public PartyPlaybackSync() {}

    public boolean hasEpoch() {
        return epochMediaMs >= 0 && epochServerMs >= 0;
    }

    /**
     * Build host packet. {@code forceNewEpoch} true → new scheduleId + epoch fields for RTDB.
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
            boolean isPlaying,
            long scheduleId,
            boolean forceNewEpoch,
            long previousEpochMediaMs,
            long previousEpochServerMs) {
        long look = isPlaying ? DEFAULT_LOOKAHEAD_MS : 0L;
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
        s.targetHostMonoMs = isPlaying ? (mono + look) : mono;
        s.writeEpoch = forceNewEpoch;

        if (forceNewEpoch) {
            // epochServerMs filled by ServerValue.TIMESTAMP on publish; optional estimate for local
            s.epochMediaMs = pos;
            long est = PartyServerClock.get().isReady()
                    ? PartyServerClock.get().serverNowMs()
                    : -1L;
            s.epochServerMs = est;
        } else {
            s.epochMediaMs = previousEpochMediaMs;
            s.epochServerMs = previousEpochServerMs;
        }
        return s;
    }

    /** Convenience: force new epoch + new schedule id. */
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
                positionMs, durationMs, isPlaying, nextScheduleId(), true, -1L, -1L);
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
        return schedule(objectKey, mediaUrl, trackId, title, artist, album,
                positionMs, durationMs, isPlaying, scheduleId, true, -1L, -1L);
    }

    private static long scheduleSeq = 1L;

    public static synchronized long nextScheduleId() {
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
