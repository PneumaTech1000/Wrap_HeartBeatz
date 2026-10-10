package com.giga.tech1000.heartbeatz.architecture.timeengine;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.giga.tech1000.heartbeatz.architecture.party.PartyPlaybackSync;

/**
 * Host-authored schedule point. Single unit of truth for “what should play when.”
 * <p>
 * Prefer host mono + server time for target; guests never use wall clock alone.
 */
public final class TimeAnchor {

    public static final long DEFAULT_LOOKAHEAD_MS = 4_000L;

    /** Monotonic id; guests ignore stale schedules. */
    public final long scheduleId;

    @Nullable public final String mediaUrl;
    @Nullable public final String trackId;
    @Nullable public final String objectKey;
    @Nullable public final String title;
    @Nullable public final String artist;
    @Nullable public final String album;

    /** Host position at authoring (ms into track). */
    public final long positionMs;

    /** Position at release instant (typically positionMs + lookahead if playing). */
    public final long targetPositionMs;

    public final long lookaheadMs;
    public final boolean isPlaying;
    public final long durationMs;

    /**
     * Host {@link android.os.SystemClock#elapsedRealtime()} when this anchor was built.
     * Cross-device only via relative math after lag estimate; still useful on host.
     */
    public final long hostMonoMs;

    /** Host mono time at which {@link #targetPositionMs} should be reached. */
    public final long targetHostMonoMs;

    /**
     * Firebase server write time (ms UTC), or -1 if not yet resolved.
     * Target server time = serverWriteMs + lookaheadMs when serverWriteMs >= 0.
     */
    public final long serverWriteMs;

    /**
     * SET: media position at {@link #epochServerMs}. -1 if absent (legacy packet).
     */
    public final long epochMediaMs;

    /**
     * SET: Firebase server time of epoch. Ideal = epochMedia + (serverNow - epochServer).
     */
    public final long epochServerMs;

    /** Guest device elapsedRealtime when packet was received (local only). */
    public final long receivedAtMonoMs;

    /** Guest wall receive (debug only). */
    public final long receivedAtWallMs;

    private TimeAnchor(Builder b) {
        this.scheduleId = b.scheduleId;
        this.mediaUrl = b.mediaUrl;
        this.trackId = b.trackId;
        this.objectKey = b.objectKey;
        this.title = b.title;
        this.artist = b.artist;
        this.album = b.album;
        this.positionMs = b.positionMs;
        this.targetPositionMs = b.targetPositionMs;
        this.lookaheadMs = b.lookaheadMs;
        this.isPlaying = b.isPlaying;
        this.durationMs = b.durationMs;
        this.hostMonoMs = b.hostMonoMs;
        this.targetHostMonoMs = b.targetHostMonoMs;
        this.serverWriteMs = b.serverWriteMs;
        this.epochMediaMs = b.epochMediaMs;
        this.epochServerMs = b.epochServerMs;
        this.receivedAtMonoMs = b.receivedAtMonoMs;
        this.receivedAtWallMs = b.receivedAtWallMs;
    }

    public boolean hasEpoch() {
        return epochMediaMs >= 0 && epochServerMs >= 0;
    }

    public long targetServerTimeMs() {
        if (serverWriteMs < 0) return -1L;
        return serverWriteMs + Math.max(0L, lookaheadMs);
    }

    public boolean hasMedia() {
        return mediaUrl != null && !mediaUrl.isEmpty();
    }

    public boolean hasTitle() {
        return title != null && !title.isEmpty();
    }

    @NonNull
    public static TimeAnchor fromSync(@NonNull PartyPlaybackSync s, long receivedAtMonoMs) {
        long serverWrite = s.serverWriteTimeMs();
        long look = s.lookaheadMs > 0 ? s.lookaheadMs : DEFAULT_LOOKAHEAD_MS;
        long hostMono = s.hostMonoMs > 0 ? s.hostMonoMs : -1L;
        long targetHostMono = s.targetHostMonoMs > 0
                ? s.targetHostMonoMs
                : (hostMono > 0 ? hostMono + look : -1L);
        long scheduleId = s.scheduleId > 0 ? s.scheduleId : (
                (serverWrite > 0 ? serverWrite : System.currentTimeMillis())
                        ^ (s.trackId != null ? s.trackId.hashCode() : 0)
        );
        long epochMedia = s.epochMediaMs;
        long epochServer = s.epochServerMs;
        // Legacy packets: synthesize epoch from position + server write
        if (epochMedia < 0 || epochServer < 0) {
            if (serverWrite >= 0) {
                epochMedia = Math.max(0L, s.positionMs);
                epochServer = serverWrite;
            }
        }

        return new Builder()
                .scheduleId(scheduleId)
                .mediaUrl(s.mediaUrl)
                .trackId(s.trackId)
                .objectKey(s.objectKey)
                .title(s.title)
                .artist(s.artist)
                .album(s.album)
                .positionMs(Math.max(0L, s.positionMs))
                .targetPositionMs(Math.max(0L, s.targetPositionMs))
                .lookaheadMs(look)
                .isPlaying(s.isPlaying)
                .durationMs(Math.max(0L, s.durationMs))
                .hostMonoMs(hostMono)
                .targetHostMonoMs(targetHostMono)
                .serverWriteMs(serverWrite)
                .epochMediaMs(epochMedia)
                .epochServerMs(epochServer)
                .receivedAtMonoMs(receivedAtMonoMs)
                .receivedAtWallMs(s.receivedAtDeviceMs > 0
                        ? s.receivedAtDeviceMs
                        : System.currentTimeMillis())
                .build();
    }

    public static final class Builder {
        long scheduleId;
        @Nullable String mediaUrl;
        @Nullable String trackId;
        @Nullable String objectKey;
        @Nullable String title;
        @Nullable String artist;
        @Nullable String album;
        long positionMs;
        long targetPositionMs;
        long lookaheadMs = DEFAULT_LOOKAHEAD_MS;
        boolean isPlaying;
        long durationMs;
        long hostMonoMs = -1L;
        long targetHostMonoMs = -1L;
        long serverWriteMs = -1L;
        long epochMediaMs = -1L;
        long epochServerMs = -1L;
        long receivedAtMonoMs;
        long receivedAtWallMs;

        public Builder scheduleId(long v) { scheduleId = v; return this; }
        public Builder mediaUrl(@Nullable String v) { mediaUrl = v; return this; }
        public Builder trackId(@Nullable String v) { trackId = v; return this; }
        public Builder objectKey(@Nullable String v) { objectKey = v; return this; }
        public Builder title(@Nullable String v) { title = v; return this; }
        public Builder artist(@Nullable String v) { artist = v; return this; }
        public Builder album(@Nullable String v) { album = v; return this; }
        public Builder positionMs(long v) { positionMs = v; return this; }
        public Builder targetPositionMs(long v) { targetPositionMs = v; return this; }
        public Builder lookaheadMs(long v) { lookaheadMs = v; return this; }
        public Builder isPlaying(boolean v) { isPlaying = v; return this; }
        public Builder durationMs(long v) { durationMs = v; return this; }
        public Builder hostMonoMs(long v) { hostMonoMs = v; return this; }
        public Builder targetHostMonoMs(long v) { targetHostMonoMs = v; return this; }
        public Builder serverWriteMs(long v) { serverWriteMs = v; return this; }
        public Builder epochMediaMs(long v) { epochMediaMs = v; return this; }
        public Builder epochServerMs(long v) { epochServerMs = v; return this; }
        public Builder receivedAtMonoMs(long v) { receivedAtMonoMs = v; return this; }
        public Builder receivedAtWallMs(long v) { receivedAtWallMs = v; return this; }

        @NonNull
        public TimeAnchor build() {
            return new TimeAnchor(this);
        }
    }
}
