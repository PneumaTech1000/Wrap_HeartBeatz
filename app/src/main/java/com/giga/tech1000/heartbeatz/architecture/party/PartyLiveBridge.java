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
import com.giga.tech1000.heartbeatz.architecture.media.PartyTrackUploader;
import com.giga.tech1000.heartbeatz.architecture.repositories.PlaybackStateRepository;
import com.giga.tech1000.heartbeatz.architecture.timeengine.TimeAnchor;
import com.giga.tech1000.heartbeatz.architecture.timeengine.TimeEngine;
import com.giga.tech1000.heartbeatz.architecture.timeengine.TimeEnginePhase;
import com.giga.tech1000.heartbeatz.architecture.timeengine.TimeEnginePlayerBridge;
import com.giga.tech1000.media_player.models.Song;

import java.io.File;

/**
 * Host: upload track + publish TimeEngine anchors (5s lookahead, scheduleId, host mono).
 * Guest: {@link TimeEngine} + {@link TimeEnginePlayerBridge} (buffer → arm → release → lock).
 */
public final class PartyLiveBridge {

    private static final String TAG = "PartyLiveBridge";
    /** Host heartbeat; balance heat vs schedule freshness. */
    private static final long HEARTBEAT_MS = 2_000L;

    private final PartyTrackUploader uploader;
    private final PartyPlaybackSyncRepository syncRepo;
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
    /** Bump scheduleId on transport changes so guests re-arm cleanly. */
    private long forceScheduleId;

    private final MutableLiveData<PartyPlaybackSync> latestSync = new MutableLiveData<>(null);
    private final MutableLiveData<Boolean> guestLockedUi = new MutableLiveData<>(false);
    private final MutableLiveData<Long> idealPositionLive = new MutableLiveData<>(0L);

    @Nullable private Observer<Song> songObserver;
    @Nullable private Observer<Boolean> playingObserver;
    @Nullable private Observer<PartyTrackUploader.Status> uploadObserver;

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
            @NonNull PartyPlaybackSyncRepository syncRepo) {
        this.uploader = uploader;
        this.syncRepo = syncRepo;
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

    @NonNull
    public LiveData<Long> getIdealPosition() {
        return idealPositionLive;
    }

    @NonNull
    public LiveData<Boolean> getGuestReadyForUi() {
        return playerBridge.getReadyForUi();
    }

    /** Call when host party is live. */
    public void startHost(@NonNull String partyId, @NonNull PlaybackStateRepository playbackRepo) {
        stopAll();
        this.activePartyId = partyId;
        this.hosting = true;
        this.playback = playbackRepo;
        guestLockedUi.postValue(false);
        PartyServerClock.get().start();
        timeEngine.startSession();
        playerBridge.attachPlayback(null);
        bindHostObservers();
        Song song = playback.getCurrentSongSync();
        if (song != null) {
            onHostSong(song);
        }
        main.postDelayed(heartbeat, HEARTBEAT_MS);
        Log.d(TAG, "Host bridge started party=" + partyId);
    }

    /** Call when guest JOINED. */
    public void startGuest(@NonNull String partyId) {
        stopAll();
        this.activePartyId = partyId;
        this.hosting = false;
        guestLockedUi.postValue(true);
        PartyServerClock.get().start();
        playerBridge.onSessionStart();
        playerBridge.attachPlayback(playback);
        syncRepo.observeParty(partyId);
        syncRepo.getSync().observeForever(this::onGuestSync);
        Log.d(TAG, "Guest bridge started party=" + partyId);
    }

    public void stopAll() {
        main.removeCallbacks(heartbeat);
        unbindHostObservers();
        syncRepo.stopObserving();
        try {
            syncRepo.getSync().removeObserver(this::onGuestSync);
        } catch (Exception ignored) { }
        playerBridge.reset();
        hosting = false;
        activePartyId = null;
        lastUploadedTrackId = null;
        lastMediaUrl = null;
        lastObjectKey = null;
        guestLockedUi.postValue(false);
        latestSync.postValue(null);
        idealPositionLive.postValue(0L);
    }

    public void attachPlaybackForGuest(@Nullable PlaybackStateRepository repo) {
        this.playback = repo;
        playerBridge.attachPlayback(repo);
    }

    private void bindHostObservers() {
        if (playback == null) return;
        songObserver = this::onHostSong;
        playingObserver = playing -> {
            if (Boolean.TRUE.equals(playing)) {
                main.removeCallbacks(heartbeat);
                main.post(heartbeat);
            }
            // Transport change → new schedule id so guests re-lock
            publishPositionOnly(true);
        };
        playback.getCurrentSong().observeForever(songObserver);
        playback.isPlaying().observeForever(playingObserver);

        uploadObserver = status -> {
            if (status == null) return;
            if (status.state == PartyTrackUploader.State.SUCCESS && status.result != null) {
                PartyMediaObject obj = status.result;
                lastMediaUrl = obj.mediaUrl;
                lastObjectKey = obj.objectKey;
                publishFullSync(true, true);
            } else if (status.state == PartyTrackUploader.State.ERROR) {
                Log.e(TAG, "Upload error: " + status.errorMessage);
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
        lastUploadedTrackId = trackId;
        lastTitle = song.getTitle();
        lastArtist = song.getArtist();
        try {
            lastAlbum = song.getAlbum();
        } catch (Exception e) {
            lastAlbum = null;
        }

        File file = null;
        try {
            String data = song.getData();
            if (data != null && !data.isEmpty()) {
                file = new File(data);
            }
        } catch (Exception e) {
            Log.w(TAG, "No local path for song", e);
        }
        if (file == null || !file.exists()) {
            long pos = playback != null ? playback.getCurrentPositionSync() : 0;
            long dur = playback != null ? playback.getCurrentDurationSync() : 0;
            boolean playing = playback != null && playback.isPlayingSync();
            PartyPlaybackSync meta = PartyPlaybackSync.schedule(
                    null, null, trackId, lastTitle, lastArtist, lastAlbum, pos, dur, playing);
            latestSync.postValue(meta);
            syncRepo.publishHostSync(activePartyId, meta);
            Log.w(TAG, "No file to upload for track " + trackId);
            return;
        }
        Log.d(TAG, "Uploading party track " + trackId + " " + file.getName());
        uploader.uploadAsync(activePartyId, trackId, file, "audio/*", null);
    }

    private void publishPositionOnly(boolean forceNewSchedule) {
        if (!hosting || activePartyId == null || playback == null) return;
        if (lastMediaUrl == null && lastUploadedTrackId == null) return;
        boolean playing = playback.isPlayingSync();
        long pos = playback.getCurrentPositionSync();
        if (!forceNewSchedule
                && playing == lastPublishedPlaying
                && Math.abs(pos - lastPublishedPos) < 350) {
            return;
        }
        lastPublishedPos = pos;
        lastPublishedPlaying = playing;
        publishFullSync(playing, forceNewSchedule);
    }

    private void publishFullSync(boolean isPlaying, boolean forceNewSchedule) {
        if (!hosting || activePartyId == null || playback == null) return;
        long pos = playback.getCurrentPositionSync();
        long dur = 0;
        try {
            dur = playback.getCurrentDurationSync();
        } catch (Exception ignored) { }

        long scheduleId = forceNewSchedule
                ? PartyPlaybackSync.nextScheduleId()
                : (forceScheduleId > 0 ? forceScheduleId : PartyPlaybackSync.nextScheduleId());
        if (forceNewSchedule) {
            forceScheduleId = scheduleId;
        } else if (forceScheduleId == 0) {
            forceScheduleId = scheduleId;
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
                scheduleId);
        latestSync.postValue(sync);
        syncRepo.publishHostSync(activePartyId, sync);
        Log.d(TAG, "sync scheduleId=" + scheduleId
                + " pos=" + pos
                + " target=" + sync.targetPositionMs
                + " mono=" + sync.hostMonoMs
                + " playing=" + isPlaying);
    }

    private void onGuestSync(@Nullable PartyPlaybackSync sync) {
        if (hosting) return;
        if (sync == null) {
            latestSync.postValue(null);
            return;
        }
        if (sync.receivedAtDeviceMs <= 0) {
            sync.receivedAtDeviceMs = System.currentTimeMillis();
        }

        TimeAnchor anchor = TimeAnchor.fromSync(sync, SystemClock.elapsedRealtime());
        playerBridge.onAnchor(anchor);

        long ideal = timeEngine.idealTrackPositionMs();
        if (ideal >= 0) {
            idealPositionLive.postValue(ideal);
        }
        latestSync.postValue(sync);

        Log.d(TAG, "guest feed scheduleId=" + anchor.scheduleId
                + " phase=" + timeEngine.phase()
                + " ideal=" + ideal
                + " untilRelease=" + timeEngine.msUntilRelease());
    }
}
