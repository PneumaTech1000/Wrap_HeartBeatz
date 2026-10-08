package com.giga.tech1000.heartbeatz.architecture.timeengine;

import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;

import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.giga.tech1000.heartbeatz.architecture.repositories.PlaybackStateRepository;

import com.giga.tech1000.heartbeatz.architecture.party.PartyLog;
/**
 * Guest player bridge: BUFFER → ARMED (muted) → LOCKED (audible).
 * <p>
 * Join mid-track: seek to ideal, <b>wait</b> until player position is near ideal,
 * then release. Never thrash ARMED↔STALE while still loading/seeking.
 * Lag after lock → STALE (silent) → re-arm once, not every packet.
 */
public final class TimeEnginePlayerBridge {
    private static final long ARM_POLL_MS = 80L;
    /** Sparse correction — fewer seeks = less distortion / heat. */
    private static final long CORRECT_INTERVAL_MS = 1_500L;
    private static final long BUFFER_SETTLE_MS = 600L;
    /** After seek, ignore drift checks briefly (Media3 seek is async). */
    private static final long SEEK_SETTLE_MS = 500L;
    /** Accept coarse park at unlock; free-run handles the rest. */
    private static final long RELEASE_TOLERANCE_MS = 1_500L;
    /** Min gap between seeks while arming. */
    private static final long ARM_SEEK_COOLDOWN_MS = 1_200L;
    /** Max time waiting in ARMED before force-release attempt. */
    private static final long ARM_TIMEOUT_MS = 6_000L;
    /** Cap frozen wait so host heartbeats cannot push release forever. */
    private static final long MAX_FROZEN_WAIT_MS = 5_000L;
    /** After RELEASE, no seek/rate (decoder settle, clean audio). */
    private static final long LOCKED_GRACE_MS = 3_000L;

    private final TimeEngine engine;
    private final Handler main = new Handler(Looper.getMainLooper());

    @Nullable private PlaybackStateRepository playback;
    @Nullable private String lastMediaUrl;
    private long lastScheduleId = -1L;
    private boolean releasePosted;
    private boolean metaReadyNotified;
    private boolean loopsActive;

    private long lastArmSeekMonoMs;
    private long armedSinceMonoMs;
    private long lastStaleMonoMs;
    private long lockedSinceMonoMs;
    /**
     * One-shot release deadline in {@link SystemClock#elapsedRealtime()}.
     * Host heartbeats refresh targetServerTime (~lookahead into the future every
     * 2s), which would keep {@link TimeEngine#msUntilRelease()} positive forever.
     * We freeze the first deadline when entering ARMED.
     */
    private long frozenReleaseMonoMs = -1L;
    /** Last playback rate applied — skip redundant setPlaybackSpeed calls. */
    private float lastAppliedRate = 1.0f;

    private final MutableLiveData<Boolean> readyForUi = new MutableLiveData<>(false);

    private final Runnable armLoop = new Runnable() {
        @Override
        public void run() {
            if (!loopsActive || playback == null) return;
            TimeEnginePhase phase = engine.phase();
            if (phase != TimeEnginePhase.ARMED && phase != TimeEnginePhase.BUFFERING) {
                return;
            }
            TimeAnchor latest = engine.latestAnchor();
            // Host paused: stay buffered & silent — never force-release into play
            if (latest != null && !latest.isPlaying) {
                forceSilent();
                main.postDelayed(this, 500L);
                return;
            }
            long now = SystemClock.elapsedRealtime();
            long untilFrozen = frozenReleaseMonoMs > 0
                    ? frozenReleaseMonoMs - now
                    : engine.msUntilRelease();
            if (untilFrozen > 50) {
                long delay = Math.min(Math.max(untilFrozen / 2, ARM_POLL_MS), 250L);
                main.postDelayed(this, delay);
                return;
            }
            tryRelease();
        }
    };

    private final Runnable correctLoop = new Runnable() {
        @Override
        public void run() {
            if (!loopsActive || playback == null) return;
            if (engine.phase() != TimeEnginePhase.LOCKED) {
                return;
            }
            applyLockedCorrection();
            main.postDelayed(this, CORRECT_INTERVAL_MS);
        }
    };

    public TimeEnginePlayerBridge(@NonNull TimeEngine engine) {
        this.engine = engine;
    }

    public void attachPlayback(@Nullable PlaybackStateRepository repo) {
        this.playback = repo;
    }

    public void reset() {
        loopsActive = false;
        main.removeCallbacks(armLoop);
        main.removeCallbacks(correctLoop);
        main.removeCallbacksAndMessages(null);
        forceSilent();
        lastMediaUrl = null;
        lastScheduleId = -1L;
        releasePosted = false;
        metaReadyNotified = false;
        lastArmSeekMonoMs = 0;
        armedSinceMonoMs = 0;
        lastStaleMonoMs = 0;
        lockedSinceMonoMs = 0;
        frozenReleaseMonoMs = -1L;
        lastAppliedRate = 1.0f;
        readyForUi.postValue(false);
        engine.stopSession();
    }

    public void onSessionStart() {
        // reset() already stops session; start clean once
        if (loopsActive) {
            reset();
        } else {
            main.removeCallbacks(armLoop);
            main.removeCallbacks(correctLoop);
            main.removeCallbacksAndMessages(null);
            lastMediaUrl = null;
            lastScheduleId = -1L;
            releasePosted = false;
            metaReadyNotified = false;
            lastArmSeekMonoMs = 0;
            armedSinceMonoMs = 0;
            lastStaleMonoMs = 0;
            lockedSinceMonoMs = 0;
            frozenReleaseMonoMs = -1L;
            lastAppliedRate = 1.0f;
            readyForUi.postValue(false);
            engine.stopSession();
        }
        loopsActive = true;
        engine.startSession();
    }

    @MainThread
    public void onAnchor(@NonNull TimeAnchor anchor) {
        if (!loopsActive) {
            loopsActive = true;
            engine.startSession();
        }
        engine.applySchedule(anchor);

        if (playback == null) {
            PartyLog.w("TimeEnginePlayerBridge", "onAnchor: playback repo null — attachPlayback first");
            return;
        }

        try {
            playback.updatePartyMetadata(
                    anchor.title, anchor.artist, anchor.album, anchor.durationMs);
        } catch (Exception e) {
            PartyLog.w("TimeEnginePlayerBridge", "metadata: " + e.getMessage());
        }

        boolean newMedia = anchor.hasMedia()
                && (lastMediaUrl == null || !lastMediaUrl.equals(anchor.mediaUrl));
        boolean scheduleAdvanced = anchor.scheduleId > 0 && anchor.scheduleId != lastScheduleId;

        if (newMedia) {
            lastMediaUrl = anchor.mediaUrl;
            lastScheduleId = anchor.scheduleId;
            releasePosted = false;
            metaReadyNotified = false;
            readyForUi.postValue(false);
            beginBuffer(anchor);
            return;
        }

        if (scheduleAdvanced) {
            lastScheduleId = anchor.scheduleId;
        }

        TimeEnginePhase phase = engine.phase();

        // Still preparing — only refresh schedule math, never STALE-thrash
        if (phase == TimeEnginePhase.LOADING
                || phase == TimeEnginePhase.BUFFERING
                || phase == TimeEnginePhase.ARMED) {
            if (!anchor.isPlaying) {
                forceSilent();
            }
            // Keep arm loop alive
            if (phase == TimeEnginePhase.ARMED) {
                main.removeCallbacks(armLoop);
                main.post(armLoop);
            }
            return;
        }

        if (phase == TimeEnginePhase.IDLE && anchor.hasMedia()) {
            beginBuffer(anchor);
            return;
        }

        if (phase == TimeEnginePhase.LOCKED) {
            if (!anchor.isPlaying) {
                // Host paused — hard stop, no seeks
                forceSilent();
                return;
            }
            // Host resumed while we were paused
            if (!safePlaying()) {
                try {
                    playback.play();
                    PartyLog.i("TimeEnginePlayerBridge", "LOCKED resume play");
                } catch (Exception e) {
                    PartyLog.w("TimeEnginePlayerBridge", "resume play failed: " + e.getMessage());
                }
            }
            // Heartbeat only refreshes ideal math. correctLoop no-ops when aligned.
            // Full re-arm only on huge host jump (scrub / track change residue).
            if (scheduleAdvanced) {
                long local = safePos();
                long drift = Math.abs(engine.driftMs(local));
                if (drift >= TimeEngine.REARM_DRIFT_MS) {
                    rearmOnce(anchor, "host-jump drift=" + drift);
                }
            }
            return;
        }

        if (phase == TimeEnginePhase.STALE) {
            // Single re-arm path; cooldown prevents packet storms
            if (SystemClock.elapsedRealtime() - lastStaleMonoMs > 1_000L) {
                rearmOnce(anchor, "stale-recovery");
            }
        }
    }

    private void beginBuffer(@NonNull TimeAnchor anchor) {
        engine.setPhase(TimeEnginePhase.LOADING);
        forceSilent();
        releasePosted = false;

        long ideal = engine.idealTrackPositionMs();
        long parkAt = ideal >= 0 ? ideal : Math.max(0L, anchor.targetPositionMs);
        // If release is still in the future, park at target; else at live ideal
        long until = engine.msUntilRelease();
        if (until > 200 && anchor.targetPositionMs > 0) {
            parkAt = anchor.targetPositionMs;
        }

        PartyLog.i("TimeEnginePlayerBridge", "beginBuffer url=" + shortUrl(anchor.mediaUrl)
                + " parkAt=" + parkAt
                + " ideal=" + ideal
                + " untilRelease=" + until
                + " playing=" + anchor.isPlaying);

        try {
            playback.playPartyStream(
                    anchor.mediaUrl,
                    anchor.trackId,
                    anchor.title,
                    anchor.artist,
                    anchor.album,
                    parkAt,
                    false
            );
            lastArmSeekMonoMs = SystemClock.elapsedRealtime();
        } catch (Exception e) {
            PartyLog.e("TimeEnginePlayerBridge", "playPartyStream failed", e);
            engine.setPhase(TimeEnginePhase.STALE);
            lastStaleMonoMs = SystemClock.elapsedRealtime();
            return;
        }

        engine.setPhase(TimeEnginePhase.BUFFERING);
        main.postDelayed(() -> {
            if (!loopsActive || playback == null) return;
            if (engine.phase() != TimeEnginePhase.BUFFERING
                    && engine.phase() != TimeEnginePhase.LOADING) {
                return;
            }
            engine.setPhase(TimeEnginePhase.ARMED);
            long now = SystemClock.elapsedRealtime();
            armedSinceMonoMs = now;
            // Freeze deadline once — host heartbeats must not push it forever
            long liveUntil = engine.msUntilRelease();
            if (liveUntil < 0 || liveUntil > MAX_FROZEN_WAIT_MS) {
                liveUntil = Math.min(MAX_FROZEN_WAIT_MS,
                        Math.max(800L, anchor.lookaheadMs > 0
                                ? Math.min(anchor.lookaheadMs, MAX_FROZEN_WAIT_MS)
                                : 3_000L));
            }
            frozenReleaseMonoMs = now + liveUntil;
            if (anchor.hasTitle()) metaReadyNotified = true;
            releasePosted = false;
            main.removeCallbacks(armLoop);
            main.post(armLoop);
            PartyLog.i("TimeEnginePlayerBridge", "ARMED frozenReleaseIn=" + liveUntil
                    + "ms liveUntil=" + engine.msUntilRelease()
                    + " ideal=" + engine.idealTrackPositionMs());
        }, BUFFER_SETTLE_MS);
    }

    /**
     * One controlled re-arm (not per-frame).
     */
    private void rearmOnce(@NonNull TimeAnchor anchor, @NonNull String reason) {
        long now = SystemClock.elapsedRealtime();
        if (now - lastStaleMonoMs < 800L && engine.phase() == TimeEnginePhase.STALE) {
            return;
        }
        lastStaleMonoMs = now;
        engine.setPhase(TimeEnginePhase.STALE);
        forceSilent();

        long ideal = engine.idealTrackPositionMs();
        if (ideal < 0) ideal = Math.max(0L, anchor.positionMs);

        long sinceSeek = now - lastArmSeekMonoMs;
        if (sinceSeek >= ARM_SEEK_COOLDOWN_MS) {
            try {
                playback.seekTo(ideal);
                playback.setPlaybackSpeed(1.0f);
                lastArmSeekMonoMs = now;
                engine.markSeekApplied();
            } catch (Exception e) {
                PartyLog.w("TimeEnginePlayerBridge", "rearm seek: " + e.getMessage());
            }
        }

        releasePosted = false;
        engine.setPhase(TimeEnginePhase.ARMED);
        long now2 = SystemClock.elapsedRealtime();
        armedSinceMonoMs = now2;
        // Short re-arm window after recovery (do not wait full lookahead again)
        frozenReleaseMonoMs = now2 + SEEK_SETTLE_MS;
        main.removeCallbacks(armLoop);
        main.postDelayed(armLoop, SEEK_SETTLE_MS);
        PartyLog.i("TimeEnginePlayerBridge", "rearm (" + reason + ") ideal=" + ideal);
    }

    private void tryRelease() {
        if (releasePosted || playback == null) return;
        TimeAnchor a = engine.latestAnchor();
        if (a == null) {
            main.postDelayed(armLoop, ARM_POLL_MS);
            return;
        }

        if (!a.isPlaying) {
            forceSilent();
            main.postDelayed(armLoop, 250);
            return;
        }

        long now = SystemClock.elapsedRealtime();
        long sinceSeek = now - lastArmSeekMonoMs;
        long ideal = engine.idealTrackPositionMs();
        long local = safePos();

        // Seek still settling — wait
        if (sinceSeek < SEEK_SETTLE_MS) {
            main.postDelayed(armLoop, SEEK_SETTLE_MS - sinceSeek);
            return;
        }

        if (ideal < 0) {
            main.postDelayed(armLoop, ARM_POLL_MS);
            return;
        }

        long drift = Math.abs(local - ideal);
        long armedFor = armedSinceMonoMs > 0 ? now - armedSinceMonoMs : 0;

        // local==0 often means Media3 has not reported position yet — wait, do not spam seek
        if (local <= 0 && armedFor < 2_500L) {
            main.postDelayed(armLoop, ARM_POLL_MS);
            return;
        }

        // Far from ideal: at most one seek every ARM_SEEK_COOLDOWN, then wait
        if (drift > RELEASE_TOLERANCE_MS) {
            if (local > 0 && now - lastArmSeekMonoMs >= ARM_SEEK_COOLDOWN_MS) {
                try {
                    long seekTo = ideal;
                    if (a.durationMs > 0) {
                        seekTo = Math.min(seekTo, a.durationMs);
                    }
                    playback.seekTo(Math.max(0L, seekTo));
                    lastArmSeekMonoMs = now;
                    engine.markSeekApplied();
                    PartyLog.d("TimeEnginePlayerBridge", "arm seek local=" + local + " → " + seekTo
                            + " ideal=" + ideal);
                } catch (Exception e) {
                    PartyLog.w("TimeEnginePlayerBridge", "arm seek failed: " + e.getMessage());
                }
            }
            // Only force-release if host is playing and we have waited long enough
            if (a.isPlaying && armedFor > ARM_TIMEOUT_MS) {
                PartyLog.w("TimeEnginePlayerBridge", "arm timeout — force release local="
                        + local + " ideal=" + ideal);
                doRelease(ideal);
                return;
            }
            main.postDelayed(armLoop, ARM_POLL_MS);
            return;
        }

        doRelease(ideal);
    }

    private void doRelease(long ideal) {
        if (releasePosted || playback == null) return;
        releasePosted = true;
        main.removeCallbacks(armLoop);

        try {
            // Only seek if clearly off; avoid double-seek click at unlock
            long local = safePos();
            if (ideal >= 0 && (local <= 0 || Math.abs(local - ideal) > 400L)) {
                playback.seekTo(ideal);
                lastArmSeekMonoMs = SystemClock.elapsedRealtime();
            }
            playback.setPlaybackSpeed(1.0f);
            lastAppliedRate = 1.0f;
            playback.play();
        } catch (Exception e) {
            PartyLog.e("TimeEnginePlayerBridge", "release play failed", e);
            releasePosted = false;
            engine.setPhase(TimeEnginePhase.STALE);
            lastStaleMonoMs = SystemClock.elapsedRealtime();
            main.postDelayed(armLoop, 1_000L);
            return;
        }

        lockedSinceMonoMs = SystemClock.elapsedRealtime();
        engine.setPhase(TimeEnginePhase.LOCKED);
        readyForUi.postValue(true);
        metaReadyNotified = true;
        main.removeCallbacks(correctLoop);
        // Start correction after grace so first seconds are clean audio
        main.postDelayed(correctLoop, LOCKED_GRACE_MS);
        PartyLog.i("TimeEnginePlayerBridge", "RELEASE ok ideal=" + ideal
                + " local=" + safePos()
                + " mono=" + SystemClock.elapsedRealtime());
    }

    /**
     * Locked-state sync tick. When guest matches host ideal → no player calls.
     * Only rate / in-place seek / re-arm when drift exceeds thresholds.
     */
    private void applyLockedCorrection() {
        if (playback == null) return;
        if (engine.phase() != TimeEnginePhase.LOCKED) return;

        TimeAnchor a = engine.latestAnchor();
        if (a == null) return;

        // Host paused — stop and stay LOCKED (do not STALE-thrash)
        if (!a.isPlaying) {
            forceSilent();
            return;
        }

        // Grace after unlock: free-run only
        long now = SystemClock.elapsedRealtime();
        if (lockedSinceMonoMs > 0 && now - lockedSinceMonoMs < LOCKED_GRACE_MS) {
            if (!safePlaying()) {
                try { playback.play(); } catch (Exception ignored) { }
            }
            return;
        }

        if (engine.isScheduleStale()) {
            // Only stale if host also claims playing; otherwise stay paused
            PartyLog.w("TimeEnginePlayerBridge", "schedule stale while locked");
            forceSilent();
            engine.setPhase(TimeEnginePhase.STALE);
            lastStaleMonoMs = now;
            return;
        }

        long local = safePos();
        // Position not ready yet — do not correct
        if (local <= 0) return;

        boolean playing = safePlaying();
        TimeEngine.Correction c = engine.decideCorrection(local, playing);

        switch (c.kind) {
            case NONE:
                // Aligned with host — leave buffering and playback alone
                if (lastAppliedRate != 1.0f) {
                    try {
                        playback.setPlaybackSpeed(1.0f);
                        lastAppliedRate = 1.0f;
                    } catch (Exception ignored) { }
                }
                if (!playing) {
                    try { playback.play(); } catch (Exception ignored) { }
                }
                break;

            case RATE:
                // Pitch-bend disabled — causes audible distortion on many devices.
                // Free-run at 1.0 until a HARD_SEEK window opens.
                if (lastAppliedRate != 1.0f) {
                    try {
                        playback.setPlaybackSpeed(1.0f);
                        lastAppliedRate = 1.0f;
                    } catch (Exception ignored) { }
                }
                if (!playing) {
                    try { playback.play(); } catch (Exception ignored) { }
                }
                break;

            case HARD_SEEK:
                // In-place seek — stay LOCKED, do not mute/re-buffer whole stream
                try {
                    if (c.idealPositionMs >= 0) {
                        playback.seekTo(c.idealPositionMs);
                        engine.markSeekApplied();
                    }
                    playback.setPlaybackSpeed(1.0f);
                    lastAppliedRate = 1.0f;
                    if (!playing) playback.play();
                    PartyLog.i("TimeEnginePlayerBridge", "in-place seek drift=" + c.driftMs
                            + " → " + c.idealPositionMs);
                } catch (Exception e) {
                    PartyLog.w("TimeEnginePlayerBridge", "in-place seek failed: " + e.getMessage());
                }
                break;

            case TRANSPORT:
                try {
                    if (c.playWhenReady) playback.play();
                    else forceSilent();
                } catch (Exception ignored) { }
                break;

            case STALE:
                // Only true major desync — full silent re-arm
                rearmOnce(a, "stale-desync");
                break;
        }

        if (!engine.shouldOutputAudio() && playing) {
            forceSilent();
        }
    }

    private void forceSilent() {
        if (playback == null) return;
        try {
            playback.pause();
            playback.setPlaybackSpeed(1.0f);
        } catch (Exception ignored) { }
    }

    private long safePos() {
        try {
            return playback != null ? playback.getCurrentPositionSync() : 0L;
        } catch (Exception e) {
            return 0L;
        }
    }

    private boolean safePlaying() {
        try {
            return playback != null && playback.isPlayingSync();
        } catch (Exception e) {
            return false;
        }
    }

    @NonNull
    private static String shortUrl(@Nullable String url) {
        if (url == null) return "null";
        int n = url.length();
        return n <= 48 ? url : ("…" + url.substring(n - 40));
    }

    @NonNull
    public LiveData<Boolean> getReadyForUi() {
        return readyForUi;
    }

    public boolean isMetaReady() {
        TimeAnchor a = engine.latestAnchor();
        return metaReadyNotified && a != null && a.hasTitle() && a.hasMedia();
    }

    public boolean shouldOutputAudio() {
        return engine.shouldOutputAudio();
    }
}
