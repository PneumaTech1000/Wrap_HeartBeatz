package com.giga.tech1000.heartbeatz.architecture.party;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Observer;

import com.giga.tech1000.heartbeatz.architecture.media.PartyMediaObject;
import com.giga.tech1000.heartbeatz.architecture.media.PartyTrackUploader;
import com.giga.tech1000.heartbeatz.architecture.repositories.PlaybackStateRepository;
import com.giga.tech1000.media_player.models.Song;

import java.io.File;
import java.util.Objects;

/**
 * Host: on each track change while hosting → background upload → write {@code parties/{id}/sync}.
 * Also heartbeats position while playing.
 * Guest: observes sync LiveData for UI / Media3 apply.
 */
public final class PartyLiveBridge {

    private static final String TAG = "PartyLiveBridge";
    private static final long HEARTBEAT_MS = 3500L; // was 2s — lower host CPU/network heat
    /** Guests schedule against this horizon (matches packet lookahead). */
    private static final long LOOKAHEAD_MS = PartyPlaybackSync.DEFAULT_LOOKAHEAD_MS;

    private final PartySyncTimeline guestTimeline = new PartySyncTimeline();
    private final MutableLiveData<Long> idealPositionLive = new MutableLiveData<>(0L);
    @Nullable private String lastAppliedMediaUrl;
    private long lastSeekAtDeviceMs;
    private long lastPublishedPos = -1;
    private boolean lastPublishedPlaying;



    private final PartyTrackUploader uploader;
    private final PartyPlaybackSyncRepository syncRepo;
    private final Handler main = new Handler(Looper.getMainLooper());

    @Nullable private PlaybackStateRepository playback;
    @Nullable private String activePartyId;
    private boolean hosting;

    @Nullable private String lastUploadedTrackId;
    @Nullable private String lastMediaUrl;
    @Nullable private String lastObjectKey;
    @Nullable private String lastTitle;
    @Nullable private String lastArtist;

    private final MutableLiveData<PartyPlaybackSync> latestSync = new MutableLiveData<>(null);
    private final MutableLiveData<Boolean> guestLockedUi = new MutableLiveData<>(false);

    private final Runnable heartbeat = new Runnable() {
        @Override
        public void run() {
            if (hosting && activePartyId != null && playback != null) {
                publishPositionOnly();
                main.postDelayed(this, HEARTBEAT_MS);
            }
        }
    };

    @Nullable private Observer<Song> songObserver;
    @Nullable private Observer<Boolean> playingObserver;
    @Nullable private Observer<PartyTrackUploader.Status> uploadObserver;

    public PartyLiveBridge(
            @NonNull PartyTrackUploader uploader,
            @NonNull PartyPlaybackSyncRepository syncRepo) {
        this.uploader = uploader;
        this.syncRepo = syncRepo;
    }

    @NonNull
    public LiveData<PartyPlaybackSync> getLatestSync() {
        return latestSync;
    }

    /** Guest: ideal track position from server timeline (updated on each sync packet). */
    @NonNull
    public LiveData<Long> getIdealPositionMs() {
        return idealPositionLive;
    }

    @NonNull
    public PartySyncTimeline getGuestTimeline() {
        return guestTimeline;
    }

    /** Guests should show blurred / non-seekable player chrome. */
    @NonNull
    public LiveData<Boolean> isGuestPlayerLocked() {
        return guestLockedUi;
    }

    public void attachPlayback(@Nullable PlaybackStateRepository playback) {
        this.playback = playback;
    }

    /** Call when entering HOSTING with party id. */
    public void startHost(@NonNull String partyId, @Nullable PlaybackStateRepository playbackRepo) {
        stopAll();
        this.activePartyId = partyId;
        this.hosting = true;
        this.playback = playbackRepo;
        guestLockedUi.postValue(false);
        syncRepo.stopObserving();
        bindHostObservers();
        // Process current song immediately
        if (playback != null) {
            Song song = playback.getCurrentSongSync();
            if (song != null) {
                onHostSong(song);
            }
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
        lastAppliedMediaUrl = null;
        lastSeekAtDeviceMs = 0;
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
        hosting = false;
        activePartyId = null;
        lastUploadedTrackId = null;
        guestLockedUi.postValue(false);
        latestSync.postValue(null);
        idealPositionLive.postValue(0L);
        guestTimeline.reset();
    }

    private void bindHostObservers() {
        if (playback == null) return;
        songObserver = this::onHostSong;
        playingObserver = playing -> {
            if (Boolean.TRUE.equals(playing)) {
                main.removeCallbacks(heartbeat);
                main.post(heartbeat);
            }
            publishPositionOnly();
        };
        playback.getCurrentSong().observeForever(songObserver);
        playback.isPlaying().observeForever(playingObserver);

        uploadObserver = status -> {
            if (status == null) return;
            if (status.state == PartyTrackUploader.State.SUCCESS && status.result != null) {
                PartyMediaObject obj = status.result;
                lastMediaUrl = obj.mediaUrl;
                lastObjectKey = obj.objectKey;
                publishFullSync(true);
            } else if (status.state == PartyTrackUploader.State.ERROR) {
                Log.e(TAG, "Upload error: " + status.errorMessage);
            }
        };
        uploader.getStatus().observeForever(uploadObserver);
    }

    private void unbindHostObservers() {
        if (playback != null) {
            if (songObserver != null) {
                try { playback.getCurrentSong().removeObserver(songObserver); } catch (Exception ignored) { }
            }
            if (playingObserver != null) {
                try { playback.isPlaying().removeObserver(playingObserver); } catch (Exception ignored) { }
            }
        }
        if (uploadObserver != null) {
            try { uploader.getStatus().removeObserver(uploadObserver); } catch (Exception ignored) { }
        }
        songObserver = null;
        playingObserver = null;
        uploadObserver = null;
    }

    private void onHostSong(@Nullable Song song) {
        if (!hosting || activePartyId == null || song == null) return;
        String trackId = String.valueOf(song.getId());
        lastTitle = song.getTitle();
        lastArtist = song.getArtist();
        if (Objects.equals(trackId, lastUploadedTrackId) && lastMediaUrl != null) {
            publishFullSync(playback != null && playback.isPlayingSync());
            return;
        }
        lastUploadedTrackId = trackId;
        lastMediaUrl = null;
        lastObjectKey = null;

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
            // Still publish metadata without URL so guests see title
            long pos = playback != null ? playback.getCurrentPositionSync() : 0;
            long dur = playback != null ? playback.getCurrentDurationSync() : 0;
            boolean playing = playback != null && playback.isPlayingSync();
            PartyPlaybackSync meta = PartyPlaybackSync.schedule(
                    null, null, trackId, lastTitle, lastArtist, pos, dur, playing);
            latestSync.postValue(meta);
            syncRepo.publishHostSync(activePartyId, meta);
            Log.w(TAG, "No file to upload for track " + trackId);
            return;
        }
        Log.d(TAG, "Uploading party track " + trackId + " " + file.getName());
        uploader.uploadAsync(activePartyId, trackId, file, "audio/*", null);
    }

    private void publishPositionOnly() {
        if (!hosting || activePartyId == null || playback == null) return;
        if (lastMediaUrl == null && lastUploadedTrackId == null) return;
        boolean playing = playback.isPlayingSync();
        long pos = playback.getCurrentPositionSync();
        // Skip redundant Firebase writes to reduce host heat / radio use
        if (playing == lastPublishedPlaying && Math.abs(pos - lastPublishedPos) < 400) {
            return;
        }
        lastPublishedPos = pos;
        lastPublishedPlaying = playing;
        publishFullSync(playing);
    }

    private void publishFullSync(boolean isPlaying) {
        if (!hosting || activePartyId == null || playback == null) return;
        long pos = playback.getCurrentPositionSync();
        long dur = 0;
        try {
            dur = playback.getCurrentDurationSync();
        } catch (Exception ignored) { }
        // positionMs = now; targetPositionMs = position 5s ahead (if playing)
        PartyPlaybackSync sync = PartyPlaybackSync.schedule(
                lastObjectKey,
                lastMediaUrl,
                lastUploadedTrackId,
                lastTitle,
                lastArtist,
                pos,
                dur,
                isPlaying);
        latestSync.postValue(sync);
        // updatedAt filled by repo with ServerValue.TIMESTAMP (not phone clock)
        syncRepo.publishHostSync(activePartyId, sync);
        Log.d(TAG, "sync published pos=" + pos + " targetPos=" + sync.targetPositionMs
                + " lookahead=" + sync.lookaheadMs + " playing=" + isPlaying);
    }

    private void onGuestSync(@Nullable PartyPlaybackSync sync) {
        if (hosting) return;
        if (sync != null) {
            if (sync.receivedAtDeviceMs <= 0) {
                sync.receivedAtDeviceMs = System.currentTimeMillis();
            }
            guestTimeline.onPacketReceived(sync);
            long ideal = guestTimeline.idealPositionMs(sync);
            idealPositionLive.postValue(ideal);
            applyGuestPlayback(sync, ideal);
        }
        latestSync.postValue(sync);
    }

    private void applyGuestPlayback(@NonNull PartyPlaybackSync sync, long idealPos) {
        if (playback == null) {
            try {
                // Lazy attach may happen after JOINED
            } catch (Exception ignored) { }
        }
        if (playback == null) return;

        String url = sync.mediaUrl;
        boolean urlChanged = url != null && !url.isEmpty()
                && !url.equals(lastAppliedMediaUrl);
        if (urlChanged) {
            lastAppliedMediaUrl = url;
            playback.playPartyStream(
                    url,
                    sync.trackId,
                    sync.title,
                    sync.artist,
                    Math.max(0, idealPos),
                    sync.isPlaying);
            lastSeekAtDeviceMs = System.currentTimeMillis();
            return;
        }
        // Same track: correct drift + play/pause
        if (url != null && !url.isEmpty()) {
            long now = System.currentTimeMillis();
            if (now - lastSeekAtDeviceMs > 1500L && idealPos >= 0) {
                try {
                    long local = playback.getCurrentPositionSync();
                    if (Math.abs(local - idealPos) > PartySyncTimeline.SEEK_THRESHOLD_MS) {
                        playback.seekTo(idealPos);
                        lastSeekAtDeviceMs = now;
                    }
                } catch (Exception ignored) { }
            }
            try {
                boolean localPlaying = playback.isPlayingSync();
                if (sync.isPlaying && !localPlaying) playback.play();
                else if (!sync.isPlaying && localPlaying) playback.pause();
            } catch (Exception ignored) { }
        }
    }

    public void attachPlaybackForGuest(@Nullable PlaybackStateRepository repo) {
        this.playback = repo;
    }
}
