package com.giga.tech1000.heartbeatz.architecture.party;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Guest-side: map Firebase server timeline → local seek position without trusting device UTC.
 * <p>
 * Clock offset is learned from each packet: {@code offset = updatedAt - receivedAtDeviceMs}.
 * Estimated server now: {@code System.currentTimeMillis() + offset}.
 */
public final class PartySyncTimeline {

    /** Smoothed offset: serverMs - deviceMs */
    private long serverMinusDeviceMs;
    private boolean hasOffset;

    /** Max correction jump per apply (avoid audible seeks on small drift). */
    public static final long SEEK_THRESHOLD_MS = 350L;

    public void onPacketReceived(@NonNull PartyPlaybackSync sync) {
        long serverWrite = sync.serverWriteTimeMs();
        if (serverWrite < 0) return;
        long deviceRecv = sync.receivedAtDeviceMs > 0
                ? sync.receivedAtDeviceMs
                : System.currentTimeMillis();
        long sample = serverWrite - deviceRecv;
        if (!hasOffset) {
            serverMinusDeviceMs = sample;
            hasOffset = true;
        } else {
            // Light EMA so one bad packet doesn't yank the clock
            serverMinusDeviceMs = (serverMinusDeviceMs * 3 + sample) / 4;
        }
    }

    public boolean hasServerClock() {
        return hasOffset;
    }

    /** Estimated Firebase server time (ms UTC) right now. */
    public long estimatedServerNowMs() {
        return System.currentTimeMillis() + serverMinusDeviceMs;
    }

    /**
     * Ideal track position at estimated server now, given the last sync packet.
     * @return position ms, or -1 if cannot compute
     */
    public long idealPositionMs(@Nullable PartyPlaybackSync sync) {
        if (sync == null) return -1L;
        long targetServer = sync.targetServerTimeMs();
        if (targetServer < 0) {
            // Fallback: position at write + elapsed if we have write time
            long write = sync.serverWriteTimeMs();
            if (write < 0 || !hasOffset) return sync.positionMs;
            long elapsed = estimatedServerNowMs() - write;
            if (!sync.isPlaying) return sync.positionMs;
            long pos = sync.positionMs + Math.max(0, elapsed);
            if (sync.durationMs > 0) pos = Math.min(pos, sync.durationMs);
            return pos;
        }
        if (!hasOffset) return sync.targetPositionMs;

        long serverNow = estimatedServerNowMs();
        long untilTarget = targetServer - serverNow;
        // At targetServerTime, position should be targetPositionMs
        // Before that: targetPosition - untilTarget (if playing)
        // After that: targetPosition + (-untilTarget)
        if (!sync.isPlaying) {
            return sync.targetPositionMs;
        }
        long pos = sync.targetPositionMs - untilTarget;
        if (pos < 0) pos = 0;
        if (sync.durationMs > 0) pos = Math.min(pos, sync.durationMs);
        return pos;
    }

    public void reset() {
        hasOffset = false;
        serverMinusDeviceMs = 0;
    }
}
