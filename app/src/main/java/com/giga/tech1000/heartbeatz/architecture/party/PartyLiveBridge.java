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
 * Host: upload track → hand off to same cloud URL → publish SET anchors.
 * Guest: {@link TimeEngine} + {@link TimeEnginePlayerBridge}
 * (LOADING → BUFFERING → ARMED → LOCKED free-run; bias learn; rare hard seek).
 * <p>
 * P0 harden: host plays uploaded URL, periodic epoch re-anchor, seek detect,
 * sticky media, concrete epochServerMs, sync-quality LiveData.
 */
public final class PartyLiveBridge {
    /** Host position heartbeat — light load, not a tight loop. */
    private static final long HEARTBEAT_MS = 2_000L;
    /** Floor between non-forced Firebase writes (CPU / radio / heat). */
    private static final long MIN_PUBLISH_GAP_MS = 1_800L;
    /**
     * While playing, force a fresh epoch (same scheduleId) so long tracks stay
     * anchored without full guest re-arm. ~25s balances radio vs accuracy.
     */
    private static final long REANCHOR_INTERVAL_MS = 25_000L;
    /** Media vs wall skew larger than this ⇒ treat as host seek/scrub. */
    private static final long SEEK_DETECT_JUMP_MS = 2_500L;
    /** After cloud handoff, wait for Media3 settle before force-epoch publish. */
    private static final long CLOUD_HANDOFF_SETTLE_MS = 400L;

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
    /** Bump scheduleId on track / pause / play / seek — not on quiet re-anchor. */
    private long forceScheduleId;
    /** Last epoch written (heartbeats reuse; force events replace). */
    private long lastEpochMediaMs = -1L;
    private long lastEpochServerMs = -1L;
    /** Mono time of last force-epoch write (re-anchor clock). */
    private long lastForceEpochMonoMs;
    /**
     * While true, ignore song LiveData (cloud handoff posts a synthetic Song
     * and must not trigger another upload).
     */
    private boolean suppressHostSongUpload;
    /** Track id for which cloud handoff already ran this party session. */
    @Nullable private String cloudHandoffTrackId;

    /** Guest: last known good stream URL (host packets may omit URL; never wipe this). */
    @Nullable private String guestStickyMediaUrl;
    @Nullable private String guestStickyObjectKey;

    private final MutableLiveData<PartyPlaybackSync> latestSync = new MutableLiveData<>(null);
    private final MutableLiveData<Boolean> guestLockedUi = new MutableLiveData<>(false);
    private final MutableLiveData<Long> idealPositionLive = new MutableLiveData<>(0L);
    private final MutableLiveData<PartySyncQuality> syncQualityLive =
            new MutableLiveData<>(PartySyncQuality.UNKNOWN);

    @Nullable private Observer<Song> songObserver;
    @Nullable private Observer<Boolean> playingObserver;
    @Nullable private Observer<PartyTrackUploader.Status> uploadObserver;
    /** Stable observer instance — method refs cannot be removed from LiveData. */
    @Nullable private Observer<PartyPlaybackSync> guestSyncObserver;

    private final Runnable heartbeat = new Runnable() {
        @Override
        public void run() {
            if (hosting && activePartyId != null && playback != null) {
                hostHeartbeatTick();
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

    /** Guest sync quality for UI chip (tight / soft / drifting). */
    @NonNull
    public LiveData<PartySyncQuality> getSyncQuality() {
        return syncQualityLive;
    }

    /**
     * Call when host party is live. Idempotent for the same partyId — LiveData
     * re-emits of HOSTING must not tear down session / clear mediaUrl.
     */
    public void startHost(@NonNull String partyId, @NonNull PlaybackStateRepository playbackRepo) {
        if (hosting && partyId.equals(activePartyId)) {
            this.playback = playbackRepo;
            return; // silent — avoid LiveData re-emit log spam
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
        lastEpochMediaMs = -1L;
        lastEpochServerMs = -1L;
        lastForceEpochMonoMs = 0;
        suppressHostSongUpload = false;
        cloudHandoffTrackId = null;
        lastPublishedPos = -1;
        lastPublishMonoMs = 0;
        guestStickyMediaUrl = null;
        guestStickyObjectKey = null;
        guestLockedUi.postValue(false);
        latestSync.postValue(null);
        idealPositionLive.postValue(0L);
        syncQualityLive.postValue(PartySyncQuality.UNKNOWN);
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
                onHostUploadSuccess(status.result);
            } else if (status.state == PartyTrackUploader.State.ERROR) {
                PartyLog.e("PartyLiveBridge", "Upload error: " + status.errorMessage);
            }
        };
        uploader.getStatus().observeForever(uploadObserver);
    }

    /**
     * Register object, hand host playback to the same public URL guests will use,
     * then force a new epoch so the timeline matches the cloud decoder path.
     */
    private void onHostUploadSuccess(@NonNull PartyMediaObject obj) {
        if (obj.mediaUrl != null && !obj.mediaUrl.isEmpty()) {
            lastMediaUrl = obj.mediaUrl;
        }
        if (obj.objectKey != null && !obj.objectKey.isEmpty()) {
            lastObjectKey = obj.objectKey;
        }
        if (activePartyId != null && lastObjectKey != null) {
            mediaLifecycle.registerUploaded(activePartyId, lastObjectKey, lastUploadedTrackId);
        }
        PartyLog.i("PartyLiveBridge", "Upload ready url sticky=" + (lastMediaUrl != null)
                + " key=" + lastObjectKey);

        if (lastMediaUrl == null || lastMediaUrl.isEmpty() || playback == null) {
            publishFullSync(playback != null && playback.isPlayingSync(), true, true);
            return;
        }

        final String url = lastMediaUrl;
        final String trackId = lastUploadedTrackId;
        final long pos = Math.max(0L, playback.getCurrentPositionSync());
        final boolean playing = playback.isPlayingSync();

        // Avoid re-entrant upload when synthetic Song is posted
        suppressHostSongUpload = true;
        cloudHandoffTrackId = trackId;
        try {
            playback.playPartyStream(
                    url,
                    trackId,
                    lastTitle,
                    lastArtist,
                    lastAlbum,
                    pos,
                    playing);
            PartyLog.i("SET", "hostCloudHandoff url=… trackId=" + trackId
                    + " pos=" + pos + " play=" + playing);
        } catch (Exception e) {
            PartyLog.e("PartyLiveBridge", "host cloud handoff failed", e);
            suppressHostSongUpload = false;
            publishFullSync(playing, true, true);
            return;
        }

        // Settle Media3 prepare/seek, then publish authoritative epoch from stream position
        main.postDelayed(() -> {
            suppressHostSongUpload = false;
            if (!hosting || activePartyId == null || playback == null) return;
            if (lastMediaUrl == null || !lastMediaUrl.equals(url)) return;
            boolean stillPlaying = playback.isPlayingSync();
            publishFullSync(stillPlaying, true, true);
            lastForceEpochMonoMs = SystemClock.elapsedRealtime();
        }, CLOUD_HANDOFF_SETTLE_MS);
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
        if (suppressHostSongUpload) {
            PartyLog.d("PartyLiveBridge", "onHostSong suppressed (cloud handoff)");
            return;
        }

        String trackId = String.valueOf(song.getId());
        String dataPath = null;
        try {
            dataPath = song.getData();
        } catch (Exception ignored) { }

        // Already playing the party cloud URL for this track — do not re-upload
        if (lastMediaUrl != null
                && dataPath != null
                && dataPath.equals(lastMediaUrl)) {
            return;
        }
        if (trackId.equals(cloudHandoffTrackId)
                && lastMediaUrl != null
                && !lastMediaUrl.isEmpty()) {
            return;
        }

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
            lastEpochMediaMs = -1L;
            lastEpochServerMs = -1L;
            lastForceEpochMonoMs = 0;
            cloudHandoffTrackId = null;
        } else if (lastMediaUrl != null && !lastMediaUrl.isEmpty()) {
            // Same track already uploaded this session
            return;
        }

        File file = null;
        try {
            if (dataPath != null && !dataPath.isEmpty() && !dataPath.startsWith("http")) {
                file = new File(dataPath);
            }
        } catch (Exception e) {
            PartyLog.w("PartyLiveBridge", "No local path for song", e);
        }
        if (file == null || !file.exists()) {
            PartyLog.w("PartyLiveBridge", "No file to upload for track " + trackId
                    + " data=" + dataPath);
            return;
        }
        String mime = resolveAudioMime(file);
        PartyLog.d("PartyLiveBridge", "Uploading party track " + trackId
                + " " + file.getName() + " mime=" + mime);
        uploader.uploadAsync(activePartyId, trackId, file, mime, null);
    }

    /**
     * Supabase rejects wildcard {@code audio/*}. Map extension → concrete type.
     */
    @NonNull
    private static String resolveAudioMime(@NonNull File file) {
        String name = file.getName();
        int dot = name.lastIndexOf('.');
        String ext = dot >= 0 ? name.substring(dot + 1).toLowerCase() : "";
        switch (ext) {
            case "mp3":
            case "mpga":
            case "mp2":
                return "audio/mpeg";
            case "m4a":
            case "m4b":
                return "audio/mp4";
            case "aac":
                return "audio/aac";
            case "ogg":
            case "oga":
            case "opus":
                return "audio/ogg";
            case "wav":
                return "audio/wav";
            case "flac":
                return "audio/flac";
            case "wma":
                return "audio/x-ms-wma";
            case "webm":
                return "audio/webm";
            default:
                // Unknown extension — still send a concrete type Supabase accepts
                return "audio/mpeg";
        }
    }

    /**
     * Heartbeat: quiet position publish, seek detection, or periodic epoch re-anchor.
     */
    private void hostHeartbeatTick() {
        if (!hosting || activePartyId == null || playback == null) return;
        if (lastMediaUrl == null || lastMediaUrl.isEmpty()) return;

        boolean playing = playback.isPlayingSync();
        long pos = playback.getCurrentPositionSync();
        long now = SystemClock.elapsedRealtime();

        // Host scrub/seek while playing → new schedule + epoch (guests may re-arm)
        if (playing && lastPublishedPos >= 0 && lastPublishMonoMs > 0) {
            long wall = now - lastPublishMonoMs;
            if (wall > 200L && wall < 8_000L) {
                long expected = lastPublishedPos + wall;
                long skew = Math.abs(pos - expected);
                if (skew >= SEEK_DETECT_JUMP_MS) {
                    PartyLog.i("SET", "hostSeekDetect skew=" + skew
                            + " pos=" + pos + " expected≈" + expected);
                    publishFullSync(playing, true, true);
                    lastForceEpochMonoMs = now;
                    return;
                }
            }
        }

        // Periodic quiet re-anchor: new epochMedia/epochServer, same scheduleId
        if (playing
                && lastForceEpochMonoMs > 0
                && (now - lastForceEpochMonoMs) >= REANCHOR_INTERVAL_MS) {
            PartyLog.i("SET", "hostReanchor intervalMs=" + (now - lastForceEpochMonoMs));
            publishFullSync(playing, true, false);
            lastForceEpochMonoMs = now;
            return;
        }

        publishPositionOnly(false);
    }

    private void publishPositionOnly(boolean forceTransport) {
        if (!hosting || activePartyId == null || playback == null) return;
        if (lastMediaUrl == null || lastMediaUrl.isEmpty()) {
            return;
        }
        boolean playing = playback.isPlayingSync();
        long pos = playback.getCurrentPositionSync();
        long now = SystemClock.elapsedRealtime();

        if (!forceTransport) {
            if (now - lastPublishMonoMs < MIN_PUBLISH_GAP_MS) {
                return;
            }
            if (playing == lastPublishedPlaying && Math.abs(pos - lastPublishedPos) < 400) {
                return;
            }
        }

        // Play/pause/seek-class transport → force epoch + new scheduleId
        if (forceTransport) {
            publishFullSync(playing, true, true);
            lastForceEpochMonoMs = now;
            return;
        }

        // Quiet heartbeat — position only, sticky epoch
        publishFullSync(playing, false, false);
    }

    /**
     * @param forceEpoch   write new epochMedia/epochServer (and sample server clock now)
     * @param bumpSchedule new scheduleId so guests may re-arm (track / seek / play-pause)
     */
    private void publishFullSync(boolean isPlaying, boolean forceEpoch, boolean bumpSchedule) {
        if (!hosting || activePartyId == null || playback == null) return;
        if (lastMediaUrl == null || lastMediaUrl.isEmpty()) {
            PartyLog.d("PartyLiveBridge", "skip publish — no mediaUrl yet");
            return;
        }

        // Sample position and server clock as close together as possible
        long pos = playback.getCurrentPositionSync();
        long dur = 0;
        try {
            dur = playback.getCurrentDurationSync();
        } catch (Exception ignored) { }

        boolean needEpoch = forceEpoch || forceScheduleId == 0;
        long scheduleId;
        if (needEpoch && bumpSchedule) {
            scheduleId = PartyPlaybackSync.nextScheduleId();
            forceScheduleId = scheduleId;
        } else if (forceScheduleId == 0) {
            scheduleId = PartyPlaybackSync.nextScheduleId();
            forceScheduleId = scheduleId;
            needEpoch = true;
        } else {
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
                scheduleId,
                needEpoch,
                lastEpochMediaMs,
                lastEpochServerMs);
        if (needEpoch) {
            lastEpochMediaMs = sync.epochMediaMs;
            lastEpochServerMs = sync.epochServerMs;
            lastForceEpochMonoMs = SystemClock.elapsedRealtime();
        }
        lastPublishMonoMs = SystemClock.elapsedRealtime();
        lastPublishedPos = pos;
        lastPublishedPlaying = isPlaying;
        latestSync.postValue(sync);
        syncRepo.publishHostSync(activePartyId, sync);

        PartyServerClock clock = PartyServerClock.get();
        PartyLog.i("SET", "hostPub"
                + " scheduleId=" + scheduleId
                + " forceEpoch=" + needEpoch
                + " bumpSched=" + bumpSchedule
                + " playing=" + isPlaying
                + " pos=" + pos
                + " dur=" + dur
                + " epochMedia=" + sync.epochMediaMs
                + " epochServerEst=" + sync.epochServerMs
                + " look=" + sync.lookaheadMs
                + " targetPos=" + sync.targetPositionMs
                + " clockReady=" + clock.isReady()
                + " offsetMs=" + clock.getOffsetMs()
                + " serverNowEst=" + (clock.isReady() ? clock.serverNowMs() : -1)
                + " mono=" + SystemClock.elapsedRealtime()
                + " trackId=" + (lastUploadedTrackId != null ? lastUploadedTrackId : "-"));
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

        if (!hasUrl) {
            PartyLog.d("SET", "guestRx scheduleId=" + sync.scheduleId
                    + " hasUrl=false playing=" + sync.isPlaying
                    + " pos=" + sync.positionMs);
            latestSync.postValue(sync);
            return;
        }

        if (playback == null) {
            PartyLog.e("PartyLiveBridge", "guest sync: playback still null");
            latestSync.postValue(sync);
            return;
        }

        long monoRecv = SystemClock.elapsedRealtime();
        TimeAnchor anchor = TimeAnchor.fromSync(sync, monoRecv);
        playerBridge.onAnchor(anchor);

        long ideal = timeEngine.idealTrackPositionMs();
        long local = -1L;
        try {
            local = playback.getCurrentPositionSync();
        } catch (Exception ignored) { }
        PartyServerClock clock = PartyServerClock.get();
        long serverNow = clock.isReady() ? clock.serverNowMs() : -1L;
        long epochAge = (sync.hasEpoch() && serverNow > 0)
                ? (serverNow - sync.epochServerMs) : -1L;
        long delta = (ideal >= 0 && local >= 0) ? (local - ideal) : 0L;

        TimeEnginePhase phase = timeEngine.phase();
        boolean buffering = phase == TimeEnginePhase.LOADING
                || phase == TimeEnginePhase.BUFFERING
                || phase == TimeEnginePhase.ARMED
                || phase == TimeEnginePhase.IDLE;
        PartySyncQuality q = PartySyncQuality.fromDeltaMs(delta, sync.isPlaying, buffering);
        syncQualityLive.postValue(q);

        // SET guest receive — same scheduleId as hostPub for cross-device compare
        PartyLog.i("SET", "guestRx"
                + " scheduleId=" + sync.scheduleId
                + " playing=" + sync.isPlaying
                + " pos=" + sync.positionMs
                + " epochMedia=" + sync.epochMediaMs
                + " epochServer=" + sync.epochServerMs
                + " epochAgeMs=" + epochAge
                + " hasEpoch=" + sync.hasEpoch()
                + " updatedAt=" + sync.serverWriteTimeMs()
                + " ideal=" + ideal
                + " local=" + local
                + " delta=" + delta
                + " quality=" + q
                + " phase=" + phase
                + " clockReady=" + clock.isReady()
                + " offsetMs=" + clock.getOffsetMs()
                + " serverNow=" + serverNow
                + " mono=" + monoRecv
                + " sticky=" + (guestStickyMediaUrl != null));

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
