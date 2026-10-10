package com.giga.tech1000.heartbeatz.architecture.party;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Guest: map host packet → ideal track position using Server Epoch Timeline.
 * <p>
 * Primary:
 * {@code ideal = epochMediaMs + (serverNow - epochServerMs)} while playing.
 * <p>
 * Fallback (legacy): {@code positionMs + (serverNow - updatedAt)}.
 */
public final class PartySyncTimeline {

    /** @deprecated Prefer TimeEngine thresholds. */
    public static final long SEEK_THRESHOLD_MS = 80L;

    /** @deprecated Prefer TimeEngine.HARD_DRIFT_MS. */
    public static final long HARD_SEEK_THRESHOLD_MS = 100L;

    private final PartyServerClock clock = PartyServerClock.get();

    /** Fallback offset if .info/serverTimeOffset not ready yet (from last packet). */
    private long packetOffsetMs;
    private boolean hasPacketOffset;

    public void onPacketReceived(@NonNull PartyPlaybackSync sync) {
        long serverWrite = sync.serverWriteTimeMs();
        if (serverWrite < 0) {
            if (sync.epochServerMs >= 0) {
                serverWrite = sync.epochServerMs;
            } else {
                return;
            }
        }
        long deviceRecv = sync.receivedAtDeviceMs > 0
                ? sync.receivedAtDeviceMs
                : System.currentTimeMillis();
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
     */
    public long idealPositionMs(@Nullable PartyPlaybackSync sync) {
        if (sync == null) return -1L;

        long serverNow = estimatedServerNowMs();

        // SET primary
        if (sync.hasEpoch()) {
            if (!sync.isPlaying) {
                return Math.max(0, sync.epochMediaMs);
            }
            long pos = sync.epochMediaMs + Math.max(0, serverNow - sync.epochServerMs);
            if (pos < 0) pos = 0;
            if (sync.durationMs > 0) pos = Math.min(pos, sync.durationMs);
            return pos;
        }

        // Legacy
        long write = sync.serverWriteTimeMs();
        if (write < 0) {
            return Math.max(0, sync.positionMs);
        }

        long elapsed = serverNow - write;
        if (!sync.isPlaying) {
            return Math.max(0, sync.positionMs);
        }

        long pos = sync.positionMs + Math.max(0, elapsed);
        if (pos < 0) pos = 0;
        if (sync.durationMs > 0) pos = Math.min(pos, sync.durationMs);
        return pos;
    }

    public void reset() {
        hasPacketOffset = false;
        packetOffsetMs = 0;
    }
}
