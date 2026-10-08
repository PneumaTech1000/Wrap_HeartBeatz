package com.giga.tech1000.heartbeatz.architecture.party;

import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Observer;

import com.giga.tech1000.heartbeatz.architecture.media.PartyMediaObject;
import com.giga.tech1000.heartbeatz.architecture.media.PartyMediaLifecycle;
import com.giga.tech1000.heartbeatz.architecture.media.PartyTrackUploader;
import com.giga.tech1000.heartbeatz.architecture.repositories.PlaybackStateRepository;
import com.giga.tech1000.heartbeatz.architecture.timeengine.TimeAnchor;
import com.giga.tech1000.heartbeatz.architecture.timeengine.TimeEngine;
import com.giga.tech1000.heartbeatz.architecture.timeengine.TimeEnginePhase;
import com.giga.tech1000.heartbeatz.architecture.timeengine.TimeEnginePlayerBridge;
import com.giga.tech1000.media_player.models.Song;

import java.io.File;

import com.giga.tech1000.heartbeatz.architecture.party.PartyLog;
/**
 * Host: upload track + publish schedule anchors (4s lookahead, scheduleId, host mono).
 * Guest: {@link TimeEngine} + {@link TimeEnginePlayerBridge}
 * (LOADING → BUFFERING → ARMED muted → LOCKED; lag → STALE silent → re-arm).
 */
public final class PartyLiveBridge {
    /** Host position heartbeat — light load, not a tight loop. */
    private static final long HEARTBEAT_MS = 2_000L;
    /** Floor between non-forced Firebase writes (CPU / radio / heat). */
    private static final long MIN_PUBLISH_GAP_MS = 1_800L;

    private final PartyTrackUploader uploader;
    private final PartyPlaybackSyncRepository syncRepo;
    private final PartyMediaLifecycle mediaLifecycle;
    private final TimeEngine timeEngine = new TimeEngine();
    private final TimeEnginePlayerBridge playerBridge = new TimeEnginePlayerBridge(timeEngine);
    private final Handler main = new Handler(Looper.getMainLooper());

    @Nullable private PlaybackStateRepository playback;
    @Nullable private String activePartyId;
    private boolean hosting;

    @Nullable private String lastUploadedTrackId;
    @Nullable private String lastMediaUrl;
    @Nullable private String lastObjectKey;
    @Nullable private String lastTitle;
    @Nullable private String lastArtist;
    @Nullable private String lastAlbum;

    private long lastPublishedPos = -1;
    private boolean lastPublishedPlaying;
    private long lastPublishMonoMs;
    /** Bump scheduleId only on track change / pause / play / seek — not every heartbeat. */
    private long forceScheduleId;
    /** Guest: last known good stream URL (host packets may omit URL; never wipe this). */
    @Nullable private String guestStickyMediaUrl;
    @Nullable private String guestStickyObjectKey;

    private final MutableLiveData<PartyPlaybackSync> latestSync = new MutableLiveData<>(null);
    private final MutableLiveData<Boolean> guestLockedUi = new MutableLiveData<>(false);
    private final MutableLiveData<Long> idealPositionLive = new MutableLiveData<>(0L);

    @Nullable private Observer<Song> songObserver;
    @Nullable private Observer<Boolean> playingObserver;
    @Nullable private Observer<PartyTrackUploader.Status> uploadObserver;
    /** Stable observer instance — method refs cannot be removed from LiveData. */
    @Nullable private Observer<PartyPlaybackSync> guestSyncObserver;

    private final Runnable heartbeat = new Runnable() {
        @Override
        public void run() {
            if (hosting && activePartyId != null && playback != null) {
                publishPositionOnly(false);
                main.postDelayed(this, HEARTBEAT_MS);
            }
        }
    };

    public PartyLiveBridge(
            @NonNull PartyTrackUploader uploader,
            @NonNull PartyPlaybackSyncRepository syncRepo,
            @NonNull PartyMediaLifecycle mediaLifecycle) {
        this.uploader = uploader;
        this.syncRepo = syncRepo;
        this.mediaLifecycle = mediaLifecycle;
    }

    @NonNull
    public TimeEngine timeEngine() {
        return timeEngine;
    }

    @NonNull
    public TimeEnginePlayerBridge playerBridge() {
        return playerBridge;
    }

    @NonNull
    public LiveData<PartyPlaybackSync> getLatestSync() {
        return latestSync;
    }

    @NonNull
    public LiveData<Boolean> getGuestLockedUi() {
        return guestLockedUi;
    }

    /** Alias used by RootMediaPlayerPanel. */
    @NonNull
    public LiveData<Boolean> isGuestPlayerLocked() {
        return guestLockedUi;
    }

    @NonNull
    public LiveData<Long> getIdealPosition() {
        return idealPositionLive;
    }

    @NonNull
    public LiveData<Boolean> getGuestReadyForUi() {
        return playerBridge.getReadyForUi();
    }

    /**
     * Call when host party is live. Idempotent for the same partyId — LiveData
     * re-emits of HOSTING must not tear down session / clear mediaUrl.
     */
    public void startHost(@NonNull String partyId, @NonNull PlaybackStateRepository playbackRepo) {
        if (hosting && partyId.equals(activePartyId)) {
            this.playback = playbackRepo;
            PartyLog.d("PartyLiveBridge", "Host bridge already active party=" + partyId
                    + " url=" + (lastMediaUrl != null));
            return;
        }

        stopAll();
        this.activePartyId = partyId;
        this.hosting = true;
        this.playback = playbackRepo;
        guestLockedUi.postValue(false);
        PartyServerClock.get().start();
        timeEngine.startSession();
        // Host does not drive local player through TimeEngine
        playerBridge.attachPlayback(null);
        bindHostObservers();
        Song song = playback.getCurrentSongSync();
        if (song != null) {
            onHostSong(song);
        }
        main.removeCallbacks(heartbeat);
        main.postDelayed(heartbeat, HEARTBEAT_MS);
        PartyLog.i("PartyLiveBridge", "Host bridge started party=" + partyId);
    }

    /**
     * Call when guest JOINED. Idempotent for the same partyId so auth LiveData
     * re-emits do not tear down an already-running session.
     */
    public void startGuest(@NonNull String partyId) {
        if (!hosting
                && partyId.equals(activePartyId)
                && guestSyncObserver != null) {
            // Already wired for this party — only refresh playback binding
            if (playback != null) {
                playerBridge.attachPlayback(playback);
            }
            PartyLog.d("PartyLiveBridge", "Guest bridge already active party=" + partyId
                    + " phase=" + timeEngine.phase());
            return;
        }

        stopAll();
        this.activePartyId = partyId;
        this.hosting = false;
        guestLockedUi.postValue(true);
        PartyServerClock.get().start();

        if (playback == null) {
            PartyLog.e("PartyLiveBridge", "startGuest: PlaybackStateRepository is null — "
                    + "call attachPlaybackForGuest() first");
        }
        playerBridge.attachPlayback(playback);
        playerBridge.onSessionStart();

        guestSyncObserver = this::onGuestSync;
        syncRepo.observeParty(partyId);
        syncRepo.getSync().observeForever(guestSyncObserver);

        // If Firebase already has a cached sync value, LiveData may not re-fire
        PartyPlaybackSync existing = syncRepo.getSync().getValue();
        if (existing != null) {
            main.post(() -> onGuestSync(existing));
        }

        PartyLog.i("PartyLiveBridge", "Guest bridge started party=" + partyId
                + " playback=" + (playback != null)
                + " hasCachedSync=" + (existing != null));
    }

    public void stopAll() {
        main.removeCallbacks(heartbeat);
        unbindHostObservers();
        syncRepo.stopObserving();
        if (guestSyncObserver != null) {
            try {
                syncRepo.getSync().removeObserver(guestSyncObserver);
            } catch (Exception ignored) { }
            guestSyncObserver = null;
        }
        playerBridge.reset();
        hosting = false;
        activePartyId = null;
        lastUploadedTrackId = null;
        lastMediaUrl = null;
        lastObjectKey = null;
        lastTitle = null;
        lastArtist = null;
        lastAlbum = null;
        forceScheduleId = 0;
        lastPublishedPos = -1;
        lastPublishMonoMs = 0;
        guestStickyMediaUrl = null;
        guestStickyObjectKey = null;
        guestLockedUi.postValue(false);
        latestSync.postValue(null);
        idealPositionLive.postValue(0L);
    }

    public void attachPlaybackForGuest(@Nullable PlaybackStateRepository repo) {
        this.playback = repo;
        playerBridge.attachPlayback(repo);
        PartyLog.d("PartyLiveBridge", "attachPlaybackForGuest repo=" + (repo != null));
    }

    private void bindHostObservers() {
        if (playback == null) return;
        songObserver = this::onHostSong;
        playingObserver = playing -> {
            // Pause / play only — new schedule id; guests must pause immediately
            boolean now = Boolean.TRUE.equals(playing);
            if (now == lastPublishedPlaying && lastPublishMonoMs > 0) {
                return; // ignore duplicate LiveData emissions
            }
            publishPositionOnly(true);
            if (now) {
                main.removeCallbacks(heartbeat);
                main.postDelayed(heartbeat, HEARTBEAT_MS);
            }
        };
        playback.getCurrentSong().observeForever(songObserver);
        playback.isPlaying().observeForever(playingObserver);

        uploadObserver = status -> {
            if (status == null) return;
            if (status.state == PartyTrackUploader.State.SUCCESS && status.result != null) {
                PartyMediaObject obj = status.result;
                if (obj.mediaUrl != null && !obj.mediaUrl.isEmpty()) {
                    lastMediaUrl = obj.mediaUrl;
                }
                if (obj.objectKey != null && !obj.objectKey.isEmpty()) {
                    lastObjectKey = obj.objectKey;
                }
                if (activePartyId != null && lastObjectKey != null) {
                    mediaLifecycle.registerUploaded(activePartyId, lastObjectKey, lastUploadedTrackId);
                }
                boolean playing = playback != null && playback.isPlayingSync();
                publishFullSync(playing, true);
                PartyLog.i("PartyLiveBridge", "Upload ready url sticky=" + (lastMediaUrl != null));
            } else if (status.state == PartyTrackUploader.State.ERROR) {
                PartyLog.e("PartyLiveBridge", "Upload error: " + status.errorMessage);
            }
        };
        uploader.getStatus().observeForever(uploadObserver);
    }

    private void unbindHostObservers() {
        if (playback != null) {
            if (songObserver != null) {
                try {
                    playback.getCurrentSong().removeObserver(songObserver);
                } catch (Exception ignored) { }
            }
            if (playingObserver != null) {
                try {
                    playback.isPlaying().removeObserver(playingObserver);
                } catch (Exception ignored) { }
            }
        }
        if (uploadObserver != null) {
            try {
                uploader.getStatus().removeObserver(uploadObserver);
            } catch (Exception ignored) { }
        }
        songObserver = null;
        playingObserver = null;
        uploadObserver = null;
    }

    private void onHostSong(@Nullable Song song) {
        if (!hosting || activePartyId == null || song == null) return;
        String trackId = String.valueOf(song.getId());
        boolean trackChanged = lastUploadedTrackId == null || !lastUploadedTrackId.equals(trackId);
        lastUploadedTrackId = trackId;
        lastTitle = song.getTitle();
        lastArtist = song.getArtist();
        try {
            lastAlbum = song.getAlbum();
        } catch (Exception e) {
            lastAlbum = null;
        }

        // New track → clear previous URL until upload finishes (do not publish null URL)
        if (trackChanged) {
            lastMediaUrl = null;
            lastObjectKey = null;
            forceScheduleId = 0;
        }

        File file = null;
        try {
            String data = song.getData();
            if (data != null && !data.isEmpty()) {
                file = new File(data);
            }
        } catch (Exception e) {
            PartyLog.w("PartyLiveBridge", "No local path for song", e);
        }
        if (file == null || !file.exists()) {
            PartyLog.w("PartyLiveBridge", "No file to upload for track " + trackId);
            return;
        }
        PartyLog.d("PartyLiveBridge", "Uploading party track " + trackId + " " + file.getName());
        uploader.uploadAsync(activePartyId, trackId, file, "audio/*", null);
    }

    private void publishPositionOnly(boolean forceNewSchedule) {
        if (!hosting || activePartyId == null || playback == null) return;
        // Wait until at least one successful upload for this party session
        if (lastMediaUrl == null || lastMediaUrl.isEmpty()) {
            return;
        }
        boolean playing = playback.isPlayingSync();
        long pos = playback.getCurrentPositionSync();
        long now = SystemClock.elapsedRealtime();

        if (!forceNewSchedule) {
            if (now - lastPublishMonoMs < MIN_PUBLISH_GAP_MS) {
                return;
            }
            if (playing == lastPublishedPlaying && Math.abs(pos - lastPublishedPos) < 400) {
                return;
            }
        }

        lastPublishedPos = pos;
        lastPublishedPlaying = playing;
        lastPublishMonoMs = now;
        publishFullSync(playing, forceNewSchedule);
    }

    private void publishFullSync(boolean isPlaying, boolean forceNewSchedule) {
        if (!hosting || activePartyId == null || playback == null) return;
        if (lastMediaUrl == null || lastMediaUrl.isEmpty()) {
            PartyLog.d("PartyLiveBridge", "skip publish — no mediaUrl yet");
            return;
        }

        long pos = playback.getCurrentPositionSync();
        long dur = 0;
        try {
            dur = playback.getCurrentDurationSync();
        } catch (Exception ignored) { }

        long scheduleId;
        if (forceNewSchedule || forceScheduleId == 0) {
            scheduleId = PartyPlaybackSync.nextScheduleId();
            forceScheduleId = scheduleId;
        } else {
            // Heartbeats keep the same scheduleId so guests do not re-arm
            scheduleId = forceScheduleId;
        }

        PartyPlaybackSync sync = PartyPlaybackSync.schedule(
                lastObjectKey,
                lastMediaUrl,
                lastUploadedTrackId,
                lastTitle,
                lastArtist,
                lastAlbum,
                pos,
                dur,
                isPlaying,
                scheduleId);
        lastPublishMonoMs = SystemClock.elapsedRealtime();
        lastPublishedPos = pos;
        lastPublishedPlaying = isPlaying;
        latestSync.postValue(sync);
        syncRepo.publishHostSync(activePartyId, sync);
        PartyLog.d("PartyLiveBridge", "sync scheduleId=" + scheduleId
                + " pos=" + pos
                + " playing=" + isPlaying
                + " hasUrl=true"
                + " force=" + forceNewSchedule);
    }

    private void onGuestSync(@Nullable PartyPlaybackSync sync) {
        if (hosting) return;
        if (activePartyId == null) return;
        if (sync == null) {
            latestSync.postValue(null);
            return;
        }
        if (sync.receivedAtDeviceMs <= 0) {
            sync.receivedAtDeviceMs = System.currentTimeMillis();
        }

        // Sticky URL: host may omit mediaUrl on some packets; never lose a good URL mid-track
        if (sync.mediaUrl != null && !sync.mediaUrl.isEmpty()) {
            guestStickyMediaUrl = sync.mediaUrl;
        }
        if (sync.objectKey != null && !sync.objectKey.isEmpty()) {
            guestStickyObjectKey = sync.objectKey;
        }
        if ((sync.mediaUrl == null || sync.mediaUrl.isEmpty()) && guestStickyMediaUrl != null) {
            sync.mediaUrl = guestStickyMediaUrl;
            if (sync.objectKey == null || sync.objectKey.isEmpty()) {
                sync.objectKey = guestStickyObjectKey;
            }
        }

        boolean hasUrl = sync.mediaUrl != null && !sync.mediaUrl.isEmpty();
        PartyLog.d("PartyLiveBridge", "guest sync scheduleId=" + sync.scheduleId
                + " hasUrl=" + hasUrl
                + " playing=" + sync.isPlaying
                + " pos=" + sync.positionMs
                + " sticky=" + (guestStickyMediaUrl != null));

        if (!hasUrl) {
            // Still uploading on host — wait; do not thrash player
            latestSync.postValue(sync);
            return;
        }

        if (playback == null) {
            PartyLog.e("PartyLiveBridge", "guest sync: playback still null");
            latestSync.postValue(sync);
            return;
        }

        TimeAnchor anchor = TimeAnchor.fromSync(sync, SystemClock.elapsedRealtime());
        playerBridge.onAnchor(anchor);

        long ideal = timeEngine.idealTrackPositionMs();
        if (ideal >= 0) {
            idealPositionLive.postValue(ideal);
        }
        latestSync.postValue(sync);
    }

    public void onHostRemovedTrack(@Nullable String objectKey) {
        if (objectKey == null || objectKey.isEmpty()) return;
        if (activePartyId != null) {
            mediaLifecycle.deleteTrackObject(activePartyId, objectKey);
        } else {
            mediaLifecycle.deleteTrackObject(objectKey);
        }
        if (objectKey.equals(lastObjectKey)) {
            lastObjectKey = null;
            lastMediaUrl = null;
        }
    }

    public void onPartyClosed(@Nullable String partyId) {
        if (partyId == null || partyId.isEmpty()) return;
        mediaLifecycle.purgeParty(partyId);
        if (partyId.equals(activePartyId)) {
            lastObjectKey = null;
            lastMediaUrl = null;
            lastUploadedTrackId = null;
        }
        PartyLog.i("PartyLiveBridge", "Party media purge scheduled for " + partyId);
    }

}
