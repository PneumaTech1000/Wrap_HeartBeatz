package com.giga.tech1000.heartbeatz.architecture.timeengine;

import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.giga.tech1000.heartbeatz.architecture.repositories.PlaybackStateRepository;

/**
 * Guest-side: BUFFER → ARM → RELEASE using {@link TimeEngine}.
 * Production path: prepare early, wait for schedule, single play(), then soft lock.
 */
public final class TimeEnginePlayerBridge {

    private static final String TAG = "TimeEnginePlayer";
    private static final long ARM_POLL_MS = 15L;
    private static final long CORRECT_INTERVAL_MS = 500L;

    private final TimeEngine engine;
    private final Handler main = new Handler(Looper.getMainLooper());

    @Nullable private PlaybackStateRepository playback;
    @Nullable private String lastMediaUrl;
    private long lastScheduleId = -1L;
    private boolean releasePosted;
    private boolean metaReadyNotified;

    private final MutableLiveData<Boolean> readyForUi = new MutableLiveData<>(false);

    private final Runnable armLoop = new Runnable() {
        @Override
        public void run() {
            if (playback == null) return;
            TimeEnginePhase phase = engine.phase();
            if (phase != TimeEnginePhase.ARMED && phase != TimeEnginePhase.BUFFERING) {
                return;
            }
            long until = engine.msUntilRelease();
            if (until <= 0) {
                doRelease();
                return;
            }
            // High-rate poll near release for ms-level accuracy
            long delay = until > 100 ? Math.min(until / 2, 50) : ARM_POLL_MS;
            main.postDelayed(this, Math.max(ARM_POLL_MS, delay));
        }
    };

    private final Runnable correctLoop = new Runnable() {
        @Override
        public void run() {
            if (playback == null) return;
            if (engine.phase() != TimeEnginePhase.LOCKED
                    && engine.phase() != TimeEnginePhase.DRIFT_CORRECT) {
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

    public void reset() {
        main.removeCallbacks(armLoop);
        main.removeCallbacks(correctLoop);
        lastMediaUrl = null;
        lastScheduleId = -1L;
        releasePosted = false;
        metaReadyNotified = false;
        readyForUi.postValue(false);
        engine.stopSession();
    }

    public void onSessionStart() {
        reset();
        engine.startSession();
    }

    /**
     * Called on every host sync packet (guest).
     */
    public void onAnchor(@NonNull TimeAnchor anchor) {
        engine.feed(anchor);

        if (playback == null) return;

        // Metadata always
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

        if (newSchedule && engine.phase() == TimeEnginePhase.LOCKED) {
            lastScheduleId = anchor.scheduleId;
            // Stay locked; correction loop will track new ideal
            return;
        }

        if (engine.phase() == TimeEnginePhase.LOCKED
                || engine.phase() == TimeEnginePhase.DRIFT_CORRECT) {
            applyCorrection();
        }
    }

    private void beginBuffer(@NonNull TimeAnchor anchor) {
        engine.setPhase(TimeEnginePhase.LOADING);
        long parkAt = Math.max(0L, anchor.targetPositionMs);
        // If we're already past the target time, park at ideal now
        long until = engine.msUntilRelease();
        if (until <= 0) {
            long ideal = engine.idealTrackPositionMs();
            if (ideal >= 0) parkAt = ideal;
        }

        try {
            // Prepare paused at park position
            playback.playPartyStream(
                    anchor.mediaUrl,
                    anchor.trackId,
                    anchor.title,
                    anchor.artist,
                    anchor.album,
                    parkAt,
                    false // do not play yet
            );
        } catch (Exception e) {
            Log.e(TAG, "playPartyStream failed", e);
            return;
        }

        engine.setPhase(TimeEnginePhase.BUFFERING);

        // Short settle for Media3 buffer, then ARM
        main.postDelayed(() -> {
            if (playback == null) return;
            engine.setPhase(TimeEnginePhase.ARMED);
            if (anchor.hasTitle()) {
                metaReadyNotified = true;
            }
            long left = engine.msUntilRelease();
            if (left <= 0) {
                doRelease();
            } else {
                main.removeCallbacks(armLoop);
                main.post(armLoop);
            }
        }, 400);
    }

    private void doRelease() {
        if (releasePosted || playback == null) return;
        releasePosted = true;
        main.removeCallbacks(armLoop);

        long ideal = engine.idealTrackPositionMs();
        TimeAnchor a = engine.latestAnchor();
        boolean shouldPlay = a == null || a.isPlaying;

        try {
            if (ideal >= 0) {
                playback.seekTo(ideal);
            }
            playback.setPlaybackSpeed(1.0f);
            if (shouldPlay) {
                playback.play();
            } else {
                playback.pause();
            }
        } catch (Exception e) {
            Log.e(TAG, "release failed", e);
        }

        engine.setPhase(TimeEnginePhase.LOCKED);
        readyForUi.postValue(true);
        metaReadyNotified = true;
        main.removeCallbacks(correctLoop);
        main.post(correctLoop);
        Log.d(TAG, "RELEASE ideal=" + ideal + " play=" + shouldPlay
                + " mono=" + SystemClock.elapsedRealtime());
    }

    private void applyCorrection() {
        if (playback == null) return;
        long local;
        boolean playing;
        try {
            local = playback.getCurrentPositionSync();
            playing = playback.isPlayingSync();
        } catch (Exception e) {
            return;
        }

        TimeEngine.Correction c = engine.decideCorrection(local, playing);
        switch (c.kind) {
            case NONE:
                break;
            case RATE:
                try {
                    playback.setPlaybackSpeed(c.rate);
                } catch (Exception ignored) { }
                break;
            case SOFT_SEEK:
            case HARD_SEEK:
                engine.setPhase(TimeEnginePhase.DRIFT_CORRECT);
                try {
                    playback.seekTo(c.idealPositionMs);
                    playback.setPlaybackSpeed(1.0f);
                    engine.markSeekApplied();
                } catch (Exception ignored) { }
                engine.setPhase(TimeEnginePhase.LOCKED);
                Log.d(TAG, c.kind + " drift=" + c.driftMs + " → " + c.idealPositionMs);
                break;
            case TRANSPORT:
                try {
                    if (c.playWhenReady) playback.play();
                    else playback.pause();
                    if (c.idealPositionMs >= 0
                            && Math.abs(c.driftMs) > TimeEngine.SOFT_SEEK_MS) {
                        playback.seekTo(c.idealPositionMs);
                        engine.markSeekApplied();
                    }
                } catch (Exception ignored) { }
                break;
        }
    }

    /** True once first release completed (UI may expand full player). */
    @NonNull
    public LiveData<Boolean> getReadyForUi() {
        return readyForUi;
    }

    public boolean isMetaReady() {
        TimeAnchor a = engine.latestAnchor();
        return metaReadyNotified && a != null && a.hasTitle() && a.hasMedia();
    }
}
