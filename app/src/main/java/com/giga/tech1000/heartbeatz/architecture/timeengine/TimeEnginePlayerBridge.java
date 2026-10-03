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

/**
 * Guest player bridge: BUFFER → ARMED (muted) → LOCKED (audible).
 * <p>
 * Join mid-track: seek to ideal, <b>wait</b> until player position is near ideal,
 * then release. Never thrash ARMED↔STALE while still loading/seeking.
 * Lag after lock → STALE (silent) → re-arm once, not every packet.
 */
public final class TimeEnginePlayerBridge {

    private static final String TAG = "TimeEnginePlayer";

    private static final long ARM_POLL_MS = 50L;
    private static final long CORRECT_INTERVAL_MS = 500L;
    private static final long BUFFER_SETTLE_MS = 500L;
    /** After seek, ignore drift checks briefly (Media3 seek is async). */
    private static final long SEEK_SETTLE_MS = 400L;
    /** First unlock tolerance (join mid-track). */
    private static final long RELEASE_TOLERANCE_MS = 750L;
    /** Min gap between seeks while arming. */
    private static final long ARM_SEEK_COOLDOWN_MS = 800L;
    /** Max time waiting in ARMED before force-release attempt. */
    private static final long ARM_TIMEOUT_MS = 12_000L;

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
            if (latest != null && !latest.isPlaying) {
                forceSilent();
                main.postDelayed(this, 250);
                return;
            }
            long until = engine.msUntilRelease();
            if (until > 50) {
                long delay = Math.min(Math.max(until / 2, ARM_POLL_MS), 200L);
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
        readyForUi.postValue(false);
        engine.stopSession();
    }

    public void onSessionStart() {
        reset();
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
            Log.w(TAG, "onAnchor: playback repo null — attachPlayback first");
            return;
        }

        try {
            playback.updatePartyMetadata(
                    anchor.title, anchor.artist, anchor.album, anchor.durationMs);
        } catch (Exception e) {
            Log.w(TAG, "metadata: " + e.getMessage());
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
                forceSilent();
                return;
            }
            // Large intentional seek from host (schedule advanced + big jump)
            if (scheduleAdvanced) {
                long local = safePos();
                long drift = Math.abs(engine.driftMs(local));
                if (drift > TimeEngine.HARD_DRIFT_MS * 3) {
                    rearmOnce(anchor, "host-seek");
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

        Log.i(TAG, "beginBuffer url=" + shortUrl(anchor.mediaUrl)
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
            Log.e(TAG, "playPartyStream failed", e);
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
            armedSinceMonoMs = SystemClock.elapsedRealtime();
            if (anchor.hasTitle()) metaReadyNotified = true;
            releasePosted = false;
            main.removeCallbacks(armLoop);
            main.post(armLoop);
            Log.i(TAG, "ARMED waiting release until=" + engine.msUntilRelease()
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
                Log.w(TAG, "rearm seek: " + e.getMessage());
            }
        }

        releasePosted = false;
        engine.setPhase(TimeEnginePhase.ARMED);
        armedSinceMonoMs = SystemClock.elapsedRealtime();
        main.removeCallbacks(armLoop);
        main.postDelayed(armLoop, SEEK_SETTLE_MS);
        Log.i(TAG, "rearm (" + reason + ") ideal=" + ideal);
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

        // Player still at 0 / far from ideal: seek once (cooldown) and wait
        if (drift > RELEASE_TOLERANCE_MS) {
            if (now - lastArmSeekMonoMs >= ARM_SEEK_COOLDOWN_MS) {
                try {
                    // Lead the ideal slightly so we land closer after async seek
                    long seekTo = ideal + Math.min(200L, RELEASE_TOLERANCE_MS / 2);
                    if (a.durationMs > 0) {
                        seekTo = Math.min(seekTo, a.durationMs);
                    }
                    playback.seekTo(seekTo);
                    lastArmSeekMonoMs = now;
                    engine.markSeekApplied();
                    Log.d(TAG, "arm seek local=" + local + " → " + seekTo
                            + " ideal=" + ideal);
                } catch (Exception e) {
                    Log.w(TAG, "arm seek failed: " + e.getMessage());
                }
            }
            // Force unlock after timeout even if position API is sticky
            if (armedFor > ARM_TIMEOUT_MS) {
                Log.w(TAG, "arm timeout — force release local=" + local + " ideal=" + ideal);
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
            if (ideal >= 0) {
                playback.seekTo(ideal);
            }
            playback.setPlaybackSpeed(1.0f);
            playback.play();
        } catch (Exception e) {
            Log.e(TAG, "release play failed", e);
            releasePosted = false;
            engine.setPhase(TimeEnginePhase.STALE);
            lastStaleMonoMs = SystemClock.elapsedRealtime();
            main.postDelayed(armLoop, 1_000L);
            return;
        }

        engine.setPhase(TimeEnginePhase.LOCKED);
        readyForUi.postValue(true);
        metaReadyNotified = true;
        main.removeCallbacks(correctLoop);
        main.post(correctLoop);
        Log.i(TAG, "RELEASE ok ideal=" + ideal
                + " local=" + safePos()
                + " mono=" + SystemClock.elapsedRealtime());
    }

    /** Drift correction only while LOCKED — never during ARMED. */
    private void applyLockedCorrection() {
        if (playback == null) return;
        if (engine.phase() != TimeEnginePhase.LOCKED) return;

        if (engine.isScheduleStale()) {
            Log.w(TAG, "schedule stale while locked");
            forceSilent();
            engine.setPhase(TimeEnginePhase.STALE);
            lastStaleMonoMs = SystemClock.elapsedRealtime();
            return;
        }

        TimeAnchor a = engine.latestAnchor();
        if (a == null) return;

        if (!a.isPlaying) {
            forceSilent();
            return;
        }

        long local = safePos();
        boolean playing = safePlaying();
        TimeEngine.Correction c = engine.decideCorrection(local, playing);

        switch (c.kind) {
            case NONE:
            case RATE:
                try {
                    playback.setPlaybackSpeed(c.rate);
                    if (!playing) playback.play();
                } catch (Exception ignored) { }
                break;
            case HARD_SEEK:
                // One re-arm, not a thrash loop
                if (a != null) {
                    rearmOnce(a, "hard-drift=" + c.driftMs);
                }
                break;
            case TRANSPORT:
                try {
                    if (c.playWhenReady) playback.play();
                    else forceSilent();
                } catch (Exception ignored) { }
                break;
            case STALE:
                forceSilent();
                engine.setPhase(TimeEnginePhase.STALE);
                lastStaleMonoMs = SystemClock.elapsedRealtime();
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
