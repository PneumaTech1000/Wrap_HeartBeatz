package com.giga.tech1000.heartbeatz.architecture.party;

import androidx.annotation.Nullable;

/**
 * Host-authoritative sync under {@code parties/{partyId}/sync}.
 * <p>
 * <b>Timeline model (server clock only):</b>
 * <ul>
 *   <li>{@link #updatedAt} — Firebase {@code ServerValue.TIMESTAMP} (ms UTC) when the host wrote this packet</li>
 *   <li>{@link #positionMs} — host track position at that server instant (approx)</li>
 *   <li>{@link #lookaheadMs} — always 5000: how far ahead the target is scheduled</li>
 *   <li>{@link #targetPositionMs} — position the track <em>should</em> be at when
 *       {@code targetServerTimeMs = updatedAt + lookaheadMs}</li>
 * </ul>
 * Guests never trust device wall clocks for the schedule. They estimate server “now”
 * from the last packet: {@code serverNow ≈ deviceNow + (updatedAt - deviceReceiveTime)}.
 * Then: {@code pos = targetPositionMs - (targetServerTime - serverNow)} while playing.
 */
public class PartyPlaybackSync {

    public static final long DEFAULT_LOOKAHEAD_MS = 5_000L;

    @Nullable public String objectKey;
    @Nullable public String mediaUrl;
    @Nullable public String trackId;
    @Nullable public String title;
    @Nullable public String artist;

    /** Host position at the moment this packet was authored (ms into track). */
    public long positionMs;

    /**
     * Position the track should reach at {@code updatedAt + lookaheadMs}.
     * If playing: typically {@code positionMs + lookaheadMs} (clamped to duration if known).
     * If paused: same as {@code positionMs}.
     */
    public long targetPositionMs;

    /** Milliseconds ahead of {@link #updatedAt} for the target (default 5000). */
    public long lookaheadMs = DEFAULT_LOOKAHEAD_MS;

    public boolean isPlaying;

    /** Optional track duration for clamping (0 if unknown). */
    public long durationMs;

    /**
     * Firebase server time (ms UTC) when written — set only via {@code ServerValue.TIMESTAMP}.
     * After read, this is a {@link Long}.
     */
    @Nullable public Object updatedAt;

    /** Device-local receive time (not written to Firebase) — used only on guest clients. */
    public transient long receivedAtDeviceMs;

    public PartyPlaybackSync() {}

    /**
     * Build host packet: 5s lookahead target from current position.
     * {@code updatedAt} is filled by the repository with ServerValue.TIMESTAMP.
     */
    public static PartyPlaybackSync schedule(
            @Nullable String objectKey,
            @Nullable String mediaUrl,
            @Nullable String trackId,
            @Nullable String title,
            @Nullable String artist,
            long positionMs,
            long durationMs,
            boolean isPlaying) {
        PartyPlaybackSync s = new PartyPlaybackSync();
        s.objectKey = objectKey;
        s.mediaUrl = mediaUrl;
        s.trackId = trackId;
        s.title = title;
        s.artist = artist;
        s.positionMs = Math.max(0, positionMs);
        s.durationMs = Math.max(0, durationMs);
        s.isPlaying = isPlaying;
        s.lookaheadMs = DEFAULT_LOOKAHEAD_MS;
        if (isPlaying) {
            long target = s.positionMs + DEFAULT_LOOKAHEAD_MS;
            if (s.durationMs > 0) {
                target = Math.min(target, s.durationMs);
            }
            s.targetPositionMs = target;
        } else {
            s.targetPositionMs = s.positionMs;
        }
        return s;
    }

    /** @deprecated use {@link #schedule} */
    @Deprecated
    public static PartyPlaybackSync of(
            @Nullable String objectKey,
            @Nullable String mediaUrl,
            @Nullable String trackId,
            @Nullable String title,
            @Nullable String artist,
            long positionMs,
            boolean isPlaying) {
        return schedule(objectKey, mediaUrl, trackId, title, artist, positionMs, 0, isPlaying);
    }

    /** Server write time as long, or -1 if missing. */
    public long serverWriteTimeMs() {
        if (updatedAt instanceof Long) return (Long) updatedAt;
        if (updatedAt instanceof Number) return ((Number) updatedAt).longValue();
        return -1L;
    }

    /** Absolute server time (ms) when {@link #targetPositionMs} should be reached. */
    public long targetServerTimeMs() {
        long w = serverWriteTimeMs();
        if (w < 0) return -1L;
        return w + (lookaheadMs > 0 ? lookaheadMs : DEFAULT_LOOKAHEAD_MS);
    }
}
