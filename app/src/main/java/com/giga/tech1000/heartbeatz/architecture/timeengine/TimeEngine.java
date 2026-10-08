package com.giga.tech1000.heartbeatz.architecture.timeengine;

import android.os.SystemClock;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.giga.tech1000.heartbeatz.architecture.party.PartyServerClock;

import com.giga.tech1000.heartbeatz.architecture.party.PartyLog;
/**
 * Schedule-based party timeline (production).
 * <p>
 * Host publishes {@link TimeAnchor} schedules; guests compute ideal media position
 * and only output audio while {@link #shouldOutputAudio()} is true.
 * Lag / hard drift → {@link TimeEnginePhase#STALE} (silent) until re-arm.
 */
public final class TimeEngine {
    // ── thresholds (lightweight: prefer free-run, rare seeks = clean audio) ─
    /** Lookahead default when host omits it (release arm only). */
    public static final long DEFAULT_LOOKAHEAD_MS = 4_000L;
    /** Extra buffer past ideal before ARMED→LOCKED. */
    public static final long BUFFER_MARGIN_MS = 1_500L;
    /** Below this: in sync — no player action. */
    public static final long SOFT_DRIFT_MS = 180L;
    /** Rate-correct band upper bound (gentle speed only). */
    public static final long RATE_CORRECT_MS = 550L;
    /** Above this: one in-place seek while staying LOCKED. */
    public static final long HARD_DRIFT_MS = 1_200L;
    /** Above this: full mute + re-arm (track jump / scrub). */
    public static final long REARM_DRIFT_MS = 3_500L;
    /** Min time between hard seeks (prevents stutter). */
    public static final long MIN_SEEK_INTERVAL_MS = 5_000L;
    /** Host packet gap before schedule is soft-stale (guest keeps free-running). */
    public static final long STALE_TIMEOUT_MS = 25_000L;
    /** Very mild rate band — less pitch artifact. */
    public static final float RATE_SLOW = 0.992f;
    public static final float RATE_FAST = 1.008f;

    private final PartyServerClock serverClock = PartyServerClock.get();

    @Nullable private TimeAnchor latest;
    private long highestScheduleId;
    private TimeEnginePhase phase = TimeEnginePhase.IDLE;

    /** serverNow − deviceWall EMA from packets. */
    private long packetOffsetMs;
    private boolean hasPacketOffset;
    private long lagCompensationMs;

    private long sessionEpochMonoMs = -1L;
    private long lastSeekMonoMs;
    private long lastAnchorMonoMs;
    /** Local mono aligned so scheduleNow ≈ localMono - scheduleOffset. */
    private long scheduleOffsetMono;
    private boolean hasScheduleOffset;

    private final MutableLiveData<TimeEnginePhase> phaseLive =
            new MutableLiveData<>(TimeEnginePhase.IDLE);
    private final MutableLiveData<Long> idealLive = new MutableLiveData<>(0L);
    private final MutableLiveData<Long> driftLive = new MutableLiveData<>(0L);
    private final MutableLiveData<TimeAnchor> anchorLive = new MutableLiveData<>(null);
    private final MutableLiveData<Boolean> outputLive = new MutableLiveData<>(false);

    // ── session ─────────────────────────────────────────────────────────────

    public void startSession() {
        serverClock.start();
        sessionEpochMonoMs = SystemClock.elapsedRealtime();
        highestScheduleId = 0;
        latest = null;
        hasPacketOffset = false;
        packetOffsetMs = 0;
        lagCompensationMs = 0;
        lastSeekMonoMs = 0;
        lastAnchorMonoMs = 0;
        hasScheduleOffset = false;
        scheduleOffsetMono = 0;
        setPhase(TimeEnginePhase.IDLE);
        outputLive.postValue(false);
        idealLive.postValue(0L);
        driftLive.postValue(0L);
        PartyLog.i("TimeEngine", "session started");
    }

    public void stopSession() {
        latest = null;
        hasScheduleOffset = false;
        setPhase(TimeEnginePhase.IDLE);
        idealLive.postValue(0L);
        driftLive.postValue(0L);
        anchorLive.postValue(null);
        outputLive.postValue(false);
        PartyLog.i("TimeEngine", "session stopped");
    }

    /**
     * Apply a host schedule. Stale {@code scheduleId} values are ignored.
     */
    public void applySchedule(@NonNull TimeAnchor anchor) {
        feed(anchor);
    }

    /** @deprecated use {@link #applySchedule(TimeAnchor)} */
    public void feed(@NonNull TimeAnchor anchor) {
        if (anchor.scheduleId > 0 && anchor.scheduleId < highestScheduleId) {
            PartyLog.d("TimeEngine", "ignore stale scheduleId=" + anchor.scheduleId
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
                packetOffsetMs = (packetOffsetMs * 7 + sample) / 8;
            }
        }

        boolean trackOrSeek =
                latest == null
                        || (anchor.mediaUrl != null
                        && latest.mediaUrl != null
                        && !anchor.mediaUrl.equals(latest.mediaUrl))
                        || (anchor.scheduleId > highestScheduleId - 1
                        && Math.abs(anchor.positionMs
                        - (latest != null ? latest.positionMs : -1)) > 2_000);

        latest = anchor;
        lastAnchorMonoMs = SystemClock.elapsedRealtime();
        anchorLive.postValue(anchor);

        // Map schedule timeline using server target as schedule time origin
        long targetServer = anchor.targetServerTimeMs();
        if (targetServer > 0) {
            long localMono = SystemClock.elapsedRealtime();
            // scheduleNow = localMono - offset  ≈  serverNow mapped
            long estServer = estimatedServerNowMs();
            scheduleOffsetMono = localMono - estServer;
            hasScheduleOffset = true;
        }

        if (phase == TimeEnginePhase.IDLE && anchor.hasMedia()) {
            setPhase(TimeEnginePhase.LOADING);
        }

        // Transport change while locked → may need re-arm if pause/seek large
        if (phase == TimeEnginePhase.LOCKED && trackOrSeek) {
            // keep locked; bridge will soft-correct or go STALE
        }

        idealLive.postValue(idealTrackPositionMs());
        outputLive.postValue(shouldOutputAudio());
    }

    public void setLagCompensationMs(long oneWayMs) {
        this.lagCompensationMs = Math.max(0L, Math.min(oneWayMs, 1_500L));
    }

    public void setPhase(@NonNull TimeEnginePhase p) {
        if (phase == p) return;
        TimeEnginePhase prev = phase;
        phase = p;
        phaseLive.postValue(p);
        outputLive.postValue(shouldOutputAudio());
        PartyLog.d("TimeEngine", "phase " + prev + " → " + p);
    }

    @NonNull
    public TimeEnginePhase phase() {
        return phase;
    }

    @Nullable
    public TimeAnchor latestAnchor() {
        return latest;
    }

    public long nowSessionMs() {
        if (sessionEpochMonoMs < 0) return 0L;
        return SystemClock.elapsedRealtime() - sessionEpochMonoMs;
    }

    public long estimatedServerNowMs() {
        if (serverClock.isReady()) {
            return serverClock.serverNowMs() + lagCompensationMs;
        }
        return System.currentTimeMillis() + packetOffsetMs + lagCompensationMs;
    }

    /**
     * Shared schedule time ≈ estimated server timeline (ms).
     */
    public long scheduleNowMs() {
        if (hasScheduleOffset) {
            // Prefer mono mapping for smooth local waits
            return SystemClock.elapsedRealtime() - scheduleOffsetMono;
        }
        return estimatedServerNowMs();
    }

    /**
     * Ideal media position at this instant (ms into track).
     * <p>
     * While playing we extrapolate from the host's last <b>positionMs</b> plus
     * elapsed mono since the packet was received. We intentionally do <b>not</b>
     * chase the 4s lookahead target for continuous ideal — that raced ahead of
     * the decoder and forced constant seeks (distortion). Lookahead is only for
     * {@link #msUntilRelease()} arm timing.
     */
    public long idealTrackPositionMs() {
        TimeAnchor a = latest;
        if (a == null) return -1L;

        if (!a.isPlaying) {
            return clamp(a.positionMs, a.durationMs);
        }

        long sinceRecv = 0L;
        if (a.receivedAtMonoMs > 0) {
            sinceRecv = Math.max(0L, SystemClock.elapsedRealtime() - a.receivedAtMonoMs);
        } else if (a.serverWriteMs >= 0) {
            sinceRecv = Math.max(0L, scheduleNowMs() - a.serverWriteMs);
        }
        // Cap runaway if packets stop (stale path will mute)
        if (sinceRecv > STALE_TIMEOUT_MS) {
            sinceRecv = STALE_TIMEOUT_MS;
        }
        return clamp(a.positionMs + sinceRecv, a.durationMs);
    }

    /** Ms until scheduled release (target server time). Negative if past. */
    public long msUntilRelease() {
        TimeAnchor a = latest;
        if (a == null) return Long.MIN_VALUE;
        long targetServer = a.targetServerTimeMs();
        if (targetServer < 0) {
            // No server stamp yet: release ASAP after buffer
            return 0L;
        }
        return targetServer - scheduleNowMs();
    }

    /** localPosition − ideal (positive = guest ahead of schedule). */
    public long driftMs(long localPositionMs) {
        long ideal = idealTrackPositionMs();
        if (ideal < 0) return 0L;
        long d = localPositionMs - ideal;
        driftLive.postValue(d);
        return d;
    }

    /**
     * True only in {@link TimeEnginePhase#LOCKED} while host wants playback.
     * All other phases ⇒ silent (architecture lag policy).
     */
    public boolean shouldOutputAudio() {
        if (phase != TimeEnginePhase.LOCKED) return false;
        TimeAnchor a = latest;
        return a != null && a.isPlaying;
    }

    /** True if schedule is too old to trust. */
    public boolean isScheduleStale() {
        if (latest == null) return true;
        if (lastAnchorMonoMs <= 0) return false;
        return SystemClock.elapsedRealtime() - lastAnchorMonoMs > STALE_TIMEOUT_MS;
    }

    /**
     * Locked-state correction. Heartbeats that still match → {@link Correction.Kind#NONE}
     * (no seek, no rate change, no buffer interrupt).
     * <ul>
     *   <li>|drift| ≤ soft → NONE (aligned)</li>
     *   <li>soft &lt; |drift| ≤ rate → gentle rate only</li>
     *   <li>rate &lt; |drift| ≤ rearm → HARD_SEEK in place (stay LOCKED)</li>
     *   <li>|drift| &gt; rearm → STALE path (full re-arm)</li>
     * </ul>
     */
    @NonNull
    public Correction decideCorrection(long localPositionMs, boolean isLocalPlaying) {
        TimeAnchor a = latest;
        if (a == null) return Correction.none();

        if (isScheduleStale()) {
            return Correction.stale(idealTrackPositionMs());
        }

        long ideal = idealTrackPositionMs();
        if (ideal < 0) return Correction.none();

        long drift = localPositionMs - ideal;
        long abs = Math.abs(drift);
        long nowMono = SystemClock.elapsedRealtime();

        // Play/pause mismatch always wins
        if (a.isPlaying != isLocalPlaying) {
            return Correction.transport(a.isPlaying, ideal, drift);
        }

        if (!a.isPlaying) {
            // Frozen timeline — only nudge position if badly off while paused
            if (abs > HARD_DRIFT_MS
                    && (nowMono - lastSeekMonoMs) >= MIN_SEEK_INTERVAL_MS) {
                lastSeekMonoMs = nowMono;
                return Correction.hardSeek(ideal, drift);
            }
            return Correction.none();
        }

        // Free-run by default. Rate changes cause pitch distortion on many
        // devices — we only seek on large drift, never pitch-bend.
        if (abs < HARD_DRIFT_MS) {
            return Correction.none();
        }

        // ── Major jump (host seek / long stall): full re-arm ──
        if (abs >= REARM_DRIFT_MS
                && (nowMono - lastSeekMonoMs) >= MIN_SEEK_INTERVAL_MS) {
            lastSeekMonoMs = nowMono;
            return Correction.stale(ideal);
        }

        // ── Hard but recoverable: one in-place seek, stay LOCKED ──
        if ((nowMono - lastSeekMonoMs) >= MIN_SEEK_INTERVAL_MS) {
            lastSeekMonoMs = nowMono;
            return Correction.hardSeek(ideal, drift);
        }

        // Seek throttled — free-run until next window
        return Correction.none();
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
    @NonNull public LiveData<Boolean> getShouldOutputAudio() { return outputLive; }

    // ── Correction ──────────────────────────────────────────────────────────

    public static final class Correction {
        public enum Kind { NONE, RATE, HARD_SEEK, TRANSPORT, STALE }

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

        static Correction hardSeek(long ideal, long drift) {
            return new Correction(Kind.HARD_SEEK, 1f, ideal, drift, false);
        }

        static Correction transport(boolean play, long ideal, long drift) {
            return new Correction(Kind.TRANSPORT, 1f, ideal, drift, play);
        }

        static Correction stale(long ideal) {
            return new Correction(Kind.STALE, 1f, ideal, 0, false);
        }
    }
}
