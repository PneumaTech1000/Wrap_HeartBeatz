package com.giga.tech1000.heartbeatz.architecture.party;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Guest: map host packet → ideal track position using Firebase server time.
 * <p>
 * Packet model:
 * <ul>
 *   <li>{@code updatedAt} = server time when host measured {@code positionMs}</li>
 *   <li>{@code targetPositionMs} = position at {@code updatedAt + lookaheadMs} (5s ahead if playing)</li>
 * </ul>
 * Ideal at server now:
 * {@code positionMs + (serverNow - updatedAt)} while playing (clamped),
 * which equals being at {@code targetPositionMs} exactly when {@code serverNow == updatedAt + lookahead}.
 */
public final class PartySyncTimeline {

    /** Seek if local position drifts more than this from ideal. */
    public static final long SEEK_THRESHOLD_MS = 120L;

    /** Hard resync (ignore soft throttle). */
    public static final long HARD_SEEK_THRESHOLD_MS = 500L;

    private final PartyServerClock clock = PartyServerClock.get();

    /** Fallback offset if .info/serverTimeOffset not ready yet (from last packet). */
    private long packetOffsetMs;
    private boolean hasPacketOffset;

    public void onPacketReceived(@NonNull PartyPlaybackSync sync) {
        long serverWrite = sync.serverWriteTimeMs();
        if (serverWrite < 0) return;
        long deviceRecv = sync.receivedAtDeviceMs > 0
                ? sync.receivedAtDeviceMs
                : System.currentTimeMillis();
        // Approximate: at receive, server is already slightly past write time.
        // Prefer Firebase offset; packet offset is backup only.
        long sample = serverWrite - deviceRecv;
        if (!hasPacketOffset) {
            packetOffsetMs = sample;
            hasPacketOffset = true;
        } else {
            packetOffsetMs = (packetOffsetMs * 3 + sample) / 4;
        }
    }

    public boolean hasServerClock() {
        return clock.isReady() || hasPacketOffset;
    }

    public long estimatedServerNowMs() {
        if (clock.isReady()) {
            return clock.serverNowMs();
        }
        return System.currentTimeMillis() + packetOffsetMs;
    }

    /**
     * Position the guest should be at <em>right now</em> (server timeline).
     * At {@code targetServerTime}, this equals {@code targetPositionMs}.
     */
    public long idealPositionMs(@Nullable PartyPlaybackSync sync) {
        if (sync == null) return -1L;

        long write = sync.serverWriteTimeMs();
        if (write < 0) {
            return sync.isPlaying ? sync.positionMs : sync.positionMs;
        }

        long serverNow = estimatedServerNowMs();
        long elapsed = serverNow - write;

        if (!sync.isPlaying) {
            return Math.max(0, sync.positionMs);
        }

        // Primary: linear from position at write
        long pos = sync.positionMs + Math.max(0, elapsed);

        // Cross-check against 5s target schedule when present
        long targetServer = sync.targetServerTimeMs();
        if (targetServer > 0 && sync.lookaheadMs > 0) {
            // At targetServer → targetPositionMs; interpolate/extrapolate
            long untilTarget = targetServer - serverNow;
            long fromTarget = sync.targetPositionMs - untilTarget;
            // Blend: prefer target-based once we have a full lookahead window
            pos = fromTarget;
        }

        if (pos < 0) pos = 0;
        if (sync.durationMs > 0) pos = Math.min(pos, sync.durationMs);
        return pos;
    }

    public void reset() {
        hasPacketOffset = false;
        packetOffsetMs = 0;
    }
}
