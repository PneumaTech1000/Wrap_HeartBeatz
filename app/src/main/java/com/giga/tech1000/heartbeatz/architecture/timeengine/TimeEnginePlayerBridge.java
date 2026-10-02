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
 * Lag / hard drift → {@link TimeEnginePhase#STALE}: pause (silent), seek, re-arm.
 * All Handler callbacks cleared on {@link #reset()} — no leaks.
 */
public final class TimeEnginePlayerBridge {

    private static final String TAG = "TimeEnginePlayer";
    private static final long ARM_POLL_MS = 16L;
    private static final long CORRECT_INTERVAL_MS = 400L;
    private static final long BUFFER_SETTLE_MS = 350L;

    private final TimeEngine engine;
    private final Handler main = new Handler(Looper.getMainLooper());

    @Nullable private PlaybackStateRepository playback;
    @Nullable private String lastMediaUrl;
    private long lastScheduleId = -1L;
    private boolean releasePosted;
    private boolean metaReadyNotified;
    private boolean loopsActive;

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
                // Paused schedule: stay armed/silent
                main.postDelayed(this, 200);
                return;
            }
            long until = engine.msUntilRelease();
            if (until <= 0) {
                tryRelease();
                return;
            }
            long delay = until > 120 ? Math.min(until / 2, 40) : ARM_POLL_MS;
            main.postDelayed(this, Math.max(ARM_POLL_MS, delay));
        }
    };

    private final Runnable correctLoop = new Runnable() {
        @Override
        public void run() {
            if (!loopsActive || playback == null) return;
            TimeEnginePhase phase = engine.phase();
            if (phase != TimeEnginePhase.LOCKED && phase != TimeEnginePhase.STALE) {
                return;
            }
            applyCorrection();
            main.postDelayed(this, CORRECT_INTERVAL_MS);
        }
    };

    public TimeEnginePlayerBridge(@NonNull TimeEngine engine) {
        this.engine = engine;
    }

    public void attachPlayback(@Nullable PlaybackStateRepository repo) {
        this.playback = repo;
    }

    /** Cancel all callbacks and stop session — call on leave party / destroy. */
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
        readyForUi.postValue(false);
        engine.stopSession();
    }

    public void onSessionStart() {
        reset();
        loopsActive = true;
        engine.startSession();
    }

    /**
     * Every host sync packet (guest path).
     */
    @MainThread
    public void onAnchor(@NonNull TimeAnchor anchor) {
        if (!loopsActive) {
            loopsActive = true;
            engine.startSession();
        }
        engine.applySchedule(anchor);

        if (playback == null) return;

        try {
            playback.updatePartyMetadata(
                    anchor.title, anchor.artist, anchor.album, anchor.durationMs);
        } catch (Exception e) {
            Log.w(TAG, "metadata: " + e.getMessage());
        }

        boolean newMedia = anchor.hasMedia()
                && (lastMediaUrl == null || !lastMediaUrl.equals(anchor.mediaUrl));
        boolean newSchedule = anchor.scheduleId > 0 && anchor.scheduleId != lastScheduleId;

        if (newMedia) {
            lastMediaUrl = anchor.mediaUrl;
            lastScheduleId = anchor.scheduleId;
            releasePosted = false;
            metaReadyNotified = false;
            readyForUi.postValue(false);
            beginBuffer(anchor);
            return;
        }

        if (newSchedule) {
            lastScheduleId = anchor.scheduleId;
            releasePosted = false;
            if (!anchor.isPlaying) {
                forceSilent();
                engine.setPhase(TimeEnginePhase.ARMED);
                return;
            }
            // Seek / resume: re-arm muted if not already locking tightly
            if (engine.phase() == TimeEnginePhase.LOCKED) {
                long local = safePos();
                long drift = Math.abs(engine.driftMs(local));
                if (drift > TimeEngine.HARD_DRIFT_MS) {
                    enterStaleAndRearm(anchor);
                    return;
                }
            } else if (engine.phase() != TimeEnginePhase.LOADING
                    && engine.phase() != TimeEnginePhase.BUFFERING) {
                enterStaleAndRearm(anchor);
                return;
            }
        }

        if (engine.phase() == TimeEnginePhase.LOCKED
                || engine.phase() == TimeEnginePhase.STALE) {
            applyCorrection();
        }
    }

    private void beginBuffer(@NonNull TimeAnchor anchor) {
        engine.setPhase(TimeEnginePhase.LOADING);
        forceSilent();

        long parkAt = Math.max(0L, anchor.targetPositionMs);
        long until = engine.msUntilRelease();
        if (until <= 0) {
            long ideal = engine.idealTrackPositionMs();
            if (ideal >= 0) parkAt = ideal;
        }

        try {
            playback.playPartyStream(
                    anchor.mediaUrl,
                    anchor.trackId,
                    anchor.title,
                    anchor.artist,
                    anchor.album,
                    parkAt,
                    false // never audible until release
            );
        } catch (Exception e) {
            Log.e(TAG, "playPartyStream failed", e);
            engine.setPhase(TimeEnginePhase.STALE);
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
            if (anchor.hasTitle()) metaReadyNotified = true;
            releasePosted = false;
            main.removeCallbacks(armLoop);
            main.post(armLoop);
        }, BUFFER_SETTLE_MS);
    }

    private void enterStaleAndRearm(@NonNull TimeAnchor anchor) {
        engine.setPhase(TimeEnginePhase.STALE);
        forceSilent();
        long ideal = engine.idealTrackPositionMs();
        if (ideal < 0) ideal = Math.max(0L, anchor.positionMs);
        try {
            playback.seekTo(ideal);
            playback.setPlaybackSpeed(1.0f);
            engine.markSeekApplied();
        } catch (Exception e) {
            Log.w(TAG, "stale seek: " + e.getMessage());
        }
        engine.setPhase(TimeEnginePhase.ARMED);
        releasePosted = false;
        main.removeCallbacks(armLoop);
        main.post(armLoop);
        Log.d(TAG, "STALE→ARMED ideal=" + ideal);
    }

    private void tryRelease() {
        if (releasePosted || playback == null) return;
        TimeAnchor a = engine.latestAnchor();
        if (a == null) return;

        if (!a.isPlaying) {
            forceSilent();
            engine.setPhase(TimeEnginePhase.ARMED);
            return;
        }

        long ideal = engine.idealTrackPositionMs();
        long local = safePos();
        if (ideal >= 0) {
            long drift = Math.abs(local - ideal);
            // If still far off at release, stay silent and wait next poll
            if (drift > TimeEngine.HARD_DRIFT_MS * 2) {
                try {
                    playback.seekTo(ideal);
                    engine.markSeekApplied();
                } catch (Exception ignored) { }
                main.postDelayed(armLoop, 50);
                return;
            }
        }

        releasePosted = true;
        main.removeCallbacks(armLoop);

        try {
            if (ideal >= 0) playback.seekTo(ideal);
            playback.setPlaybackSpeed(1.0f);
            playback.play();
        } catch (Exception e) {
            Log.e(TAG, "release failed", e);
            releasePosted = false;
            engine.setPhase(TimeEnginePhase.STALE);
            return;
        }

        engine.setPhase(TimeEnginePhase.LOCKED);
        readyForUi.postValue(true);
        metaReadyNotified = true;
        main.removeCallbacks(correctLoop);
        main.post(correctLoop);
        Log.i(TAG, "RELEASE ideal=" + ideal + " mono=" + SystemClock.elapsedRealtime());
    }

    private void applyCorrection() {
        if (playback == null) return;

        if (engine.isScheduleStale()) {
            forceSilent();
            engine.setPhase(TimeEnginePhase.STALE);
            return;
        }

        long local = safePos();
        boolean playing = safePlaying();
        TimeEngine.Correction c = engine.decideCorrection(local, playing);

        switch (c.kind) {
            case NONE:
                break;
            case RATE:
                if (!engine.shouldOutputAudio()) {
                    forceSilent();
                    break;
                }
                try {
                    playback.setPlaybackSpeed(c.rate);
                } catch (Exception ignored) { }
                // Ensure playing while locked
                if (engine.shouldOutputAudio() && !playing) {
                    try { playback.play(); } catch (Exception ignored) { }
                }
                break;
            case HARD_SEEK:
                engine.setPhase(TimeEnginePhase.STALE);
                forceSilent();
                try {
                    if (c.idealPositionMs >= 0) {
                        playback.seekTo(c.idealPositionMs);
                        engine.markSeekApplied();
                    }
                    playback.setPlaybackSpeed(1.0f);
                } catch (Exception ignored) { }
                releasePosted = false;
                engine.setPhase(TimeEnginePhase.ARMED);
                main.removeCallbacks(armLoop);
                main.post(armLoop);
                Log.d(TAG, "HARD_SEEK drift=" + c.driftMs + " → STALE→ARMED");
                break;
            case TRANSPORT:
                try {
                    if (c.playWhenReady) {
                        // Only play if we are allowed to output
                        if (engine.phase() == TimeEnginePhase.LOCKED) {
                            playback.play();
                        } else {
                            releasePosted = false;
                            engine.setPhase(TimeEnginePhase.ARMED);
                            main.removeCallbacks(armLoop);
                            main.post(armLoop);
                        }
                    } else {
                        forceSilent();
                    }
                    if (c.idealPositionMs >= 0
                            && Math.abs(c.driftMs) > TimeEngine.HARD_DRIFT_MS) {
                        playback.seekTo(c.idealPositionMs);
                        engine.markSeekApplied();
                    }
                } catch (Exception ignored) { }
                break;
            case STALE:
                forceSilent();
                engine.setPhase(TimeEnginePhase.STALE);
                break;
        }

        // Enforce mute policy every tick
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
    public LiveData<Boolean> getReadyForUi() {
        return readyForUi;
    }

    public boolean isMetaReady() {
        TimeAnchor a = engine.latestAnchor();
        return metaReadyNotified && a != null && a.hasTitle() && a.hasMedia();
    }

    /** Whether audio should be audible right now. */
    public boolean shouldOutputAudio() {
        return engine.shouldOutputAudio();
    }
}
