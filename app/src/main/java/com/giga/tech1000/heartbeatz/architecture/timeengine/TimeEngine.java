package com.giga.tech1000.heartbeatz.architecture.timeengine;

import android.os.SystemClock;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.giga.tech1000.heartbeatz.architecture.party.PartyServerClock;

/**
 * Production TimeEngine — single app API for party (and future lyrics/UI) timeline math.
 * <p>
 * <b>Authority:</b> host-authored {@link TimeAnchor}s delivered via Firebase.<br>
 * <b>Coarse clock:</b> Firebase server time via {@link PartyServerClock}.<br>
 * <b>Local follower:</b> {@link SystemClock#elapsedRealtime()} only for intervals and arm waits.
 * <p>
 * Does not drive audio itself; callers apply {@link #idealTrackPositionMs()} and phase.
 */
public final class TimeEngine {

    private static final String TAG = "TimeEngine";

    /** |drift| below this → no correction. */
    public static final long DEAD_ZONE_MS = 35L;
    /** Prefer rate nudge up to this drift. */
    public static final long RATE_ZONE_MS = 750L;
    /** Soft seek threshold (throttled). */
    public static final long SOFT_SEEK_MS = 1_200L;
    /** Hard seek threshold. */
    public static final long HARD_SEEK_MS = 1_800L;
    /** Min interval between seeks. */
    public static final long MIN_SEEK_INTERVAL_MS = 3_500L;
    /** Rate when guest is behind (need to catch up). */
    public static final float RATE_CATCH_UP = 1.025f;
    /** Rate when guest is ahead. */
    public static final float RATE_SLOW = 0.975f;

    private final PartyServerClock serverClock = PartyServerClock.get();

    @Nullable private TimeAnchor latest;
    private long highestScheduleId;
    private TimeEnginePhase phase = TimeEnginePhase.IDLE;

    /** serverNow − deviceWall at last samples (EMA); used if PartyServerClock not ready. */
    private long packetOffsetMs;
    private boolean hasPacketOffset;

    /** Optional one-way lag compensation (ms), from RTT/2 when available. */
    private long lagCompensationMs;

    private long sessionEpochMonoMs = -1L;
    private long lastSeekMonoMs;

    private final MutableLiveData<TimeEnginePhase> phaseLive = new MutableLiveData<>(TimeEnginePhase.IDLE);
    private final MutableLiveData<Long> idealLive = new MutableLiveData<>(0L);
    private final MutableLiveData<Long> driftLive = new MutableLiveData<>(0L);
    private final MutableLiveData<TimeAnchor> anchorLive = new MutableLiveData<>(null);

    public void startSession() {
        serverClock.start();
        sessionEpochMonoMs = SystemClock.elapsedRealtime();
        highestScheduleId = 0;
        latest = null;
        hasPacketOffset = false;
        packetOffsetMs = 0;
        lagCompensationMs = 0;
        lastSeekMonoMs = 0;
        setPhase(TimeEnginePhase.IDLE);
        Log.d(TAG, "session started");
    }

    public void stopSession() {
        latest = null;
        setPhase(TimeEnginePhase.IDLE);
        idealLive.postValue(0L);
        driftLive.postValue(0L);
        anchorLive.postValue(null);
        Log.d(TAG, "session stopped");
    }

    /**
     * Feed a host anchor (guest path). Ignores stale {@code scheduleId}.
     */
    public void feed(@NonNull TimeAnchor anchor) {
        if (anchor.scheduleId > 0 && anchor.scheduleId < highestScheduleId) {
            Log.d(TAG, "ignore stale scheduleId=" + anchor.scheduleId
                    + " < " + highestScheduleId);
            return;
        }
        if (anchor.scheduleId > highestScheduleId) {
            highestScheduleId = anchor.scheduleId;
        }

        if (anchor.serverWriteMs >= 0 && anchor.receivedAtWallMs > 0) {
            long sample = anchor.serverWriteMs - anchor.receivedAtWallMs;
            if (!hasPacketOffset) {
                packetOffsetMs = sample;
                hasPacketOffset = true;
            } else {
                packetOffsetMs = (packetOffsetMs * 3 + sample) / 4;
            }
        }

        latest = anchor;
        anchorLive.postValue(anchor);

        if (phase == TimeEnginePhase.IDLE && anchor.hasMedia()) {
            setPhase(TimeEnginePhase.LOADING);
        }

        long ideal = idealTrackPositionMs();
        idealLive.postValue(ideal);
    }

    public void setLagCompensationMs(long oneWayMs) {
        this.lagCompensationMs = Math.max(0L, Math.min(oneWayMs, 2_000L));
    }

    public void setPhase(@NonNull TimeEnginePhase p) {
        if (phase == p) return;
        phase = p;
        phaseLive.postValue(p);
        Log.d(TAG, "phase → " + p);
    }

    @NonNull
    public TimeEnginePhase phase() {
        return phase;
    }

    @Nullable
    public TimeAnchor latestAnchor() {
        return latest;
    }

    /** Session-local ms since {@link #startSession()} (debug / UI). */
    public long nowSessionMs() {
        if (sessionEpochMonoMs < 0) return 0L;
        return SystemClock.elapsedRealtime() - sessionEpochMonoMs;
    }

    /** Estimated Firebase server now (ms UTC). */
    public long estimatedServerNowMs() {
        if (serverClock.isReady()) {
            return serverClock.serverNowMs();
        }
        return System.currentTimeMillis() + packetOffsetMs;
    }

    /**
     * Ideal track position at this instant (ms).
     * Uses 5s schedule: at targetServerTime → targetPosition; then extrapolate if playing.
     */
    public long idealTrackPositionMs() {
        TimeAnchor a = latest;
        if (a == null) return -1L;

        long serverNow = estimatedServerNowMs() + lagCompensationMs;
        long targetServer = a.targetServerTimeMs();

        if (!a.isPlaying) {
            return clamp(a.positionMs, a.durationMs);
        }

        if (targetServer > 0) {
            // At targetServer → targetPosition; before/after linear in real time
            long ideal = a.targetPositionMs - (targetServer - serverNow);
            return clamp(ideal, a.durationMs);
        }

        // Fallback: from write time + position
        if (a.serverWriteMs >= 0) {
            long elapsed = serverNow - a.serverWriteMs;
            return clamp(a.positionMs + Math.max(0L, elapsed), a.durationMs);
        }

        // Last resort: extrapolate from receive using mono
        long monoNow = SystemClock.elapsedRealtime();
        long sinceRecv = monoNow - a.receivedAtMonoMs;
        return clamp(a.positionMs + Math.max(0L, sinceRecv), a.durationMs);
    }

    /**
     * Ms until scheduled release (target server time). Negative if already past.
     */
    public long msUntilRelease() {
        TimeAnchor a = latest;
        if (a == null) return Long.MIN_VALUE;
        long targetServer = a.targetServerTimeMs();
        if (targetServer < 0) return 0L;
        return targetServer - estimatedServerNowMs() - lagCompensationMs;
    }

    /** localPosition − ideal (positive = guest ahead). */
    public long driftMs(long localPositionMs) {
        long ideal = idealTrackPositionMs();
        if (ideal < 0) return 0L;
        long d = localPositionMs - ideal;
        driftLive.postValue(d);
        return d;
    }

    /**
     * Correction decision for the player controller.
     */
    @NonNull
    public Correction decideCorrection(long localPositionMs, boolean isLocalPlaying) {
        TimeAnchor a = latest;
        if (a == null) {
            return Correction.none();
        }

        long ideal = idealTrackPositionMs();
        if (ideal < 0) return Correction.none();

        long drift = localPositionMs - ideal;
        long abs = Math.abs(drift);
        long nowMono = SystemClock.elapsedRealtime();

        // Play/pause always honor host
        if (a.isPlaying != isLocalPlaying) {
            return Correction.transport(a.isPlaying, ideal, drift);
        }

        if (!a.isPlaying) {
            return Correction.none();
        }

        if (abs <= DEAD_ZONE_MS) {
            return Correction.rate(1.0f, ideal, drift);
        }

        if (abs <= RATE_ZONE_MS) {
            float rate = drift > 0 ? RATE_SLOW : RATE_CATCH_UP;
            return Correction.rate(rate, ideal, drift);
        }

        boolean seekAllowed = (nowMono - lastSeekMonoMs) >= MIN_SEEK_INTERVAL_MS;
        if (abs >= HARD_SEEK_MS && seekAllowed) {
            lastSeekMonoMs = nowMono;
            return Correction.hardSeek(ideal, drift);
        }
        if (abs >= SOFT_SEEK_MS && seekAllowed) {
            lastSeekMonoMs = nowMono;
            return Correction.softSeek(ideal, drift);
        }

        // In between: keep mild rate
        float rate = drift > 0 ? RATE_SLOW : RATE_CATCH_UP;
        return Correction.rate(rate, ideal, drift);
    }

    public void markSeekApplied() {
        lastSeekMonoMs = SystemClock.elapsedRealtime();
    }

    private static long clamp(long pos, long durationMs) {
        if (pos < 0) return 0L;
        if (durationMs > 0 && pos > durationMs) return durationMs;
        return pos;
    }

    @NonNull public LiveData<TimeEnginePhase> getPhase() { return phaseLive; }
    @NonNull public LiveData<Long> getIdealPosition() { return idealLive; }
    @NonNull public LiveData<Long> getDrift() { return driftLive; }
    @NonNull public LiveData<TimeAnchor> getAnchor() { return anchorLive; }

    /** Immutable correction command for the audio layer. */
    public static final class Correction {
        public enum Kind { NONE, RATE, SOFT_SEEK, HARD_SEEK, TRANSPORT }

        @NonNull public final Kind kind;
        public final float rate;
        public final long idealPositionMs;
        public final long driftMs;
        public final boolean playWhenReady;

        private Correction(Kind kind, float rate, long ideal, long drift, boolean play) {
            this.kind = kind;
            this.rate = rate;
            this.idealPositionMs = ideal;
            this.driftMs = drift;
            this.playWhenReady = play;
        }

        static Correction none() {
            return new Correction(Kind.NONE, 1f, -1, 0, false);
        }

        static Correction rate(float r, long ideal, long drift) {
            return new Correction(Kind.RATE, r, ideal, drift, true);
        }

        static Correction softSeek(long ideal, long drift) {
            return new Correction(Kind.SOFT_SEEK, 1f, ideal, drift, true);
        }

        static Correction hardSeek(long ideal, long drift) {
            return new Correction(Kind.HARD_SEEK, 1f, ideal, drift, true);
        }

        static Correction transport(boolean play, long ideal, long drift) {
            return new Correction(Kind.TRANSPORT, 1f, ideal, drift, play);
        }
    }
}
