package com.giga.tech1000.heartbeatz.architecture;

import android.net.Uri;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.OptIn;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MediaMetadata;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.session.MediaController;

import com.giga.tech1000.heartbeatz.app_worker.HeartBeatzApp;

import com.giga.tech1000.heartbeatz.architecture.repositories.PlaybackStateRepository;
import com.giga.tech1000.heartbeatz.ui.MediaPlayerThread;
import com.giga.tech1000.media_player.interfaces.IPlaybackCallback;
import com.giga.tech1000.media_player.models.Song;
import com.giga.tech1000.media_player.utils.enums.ItemSource;
import com.giga.tech1000.party_mode.model.PartyHost;
import com.giga.tech1000.party_mode.model.SyncPacket;

import java.util.ArrayList;
import java.util.List;

/**
 * Concrete implementation of {@link PlaybackStateRepository}.
 *
 * Single shared instance is owned by {@link com.giga.tech1000.heartbeatz.ui.UIThread}
 * and obtained via {@code HeartBeatzApp.container(getContext()).requireUiThread().getPlaybackStateRepository()}.
 *
 * Bridges Media3 (CorePlayer / MediaController / MediaPlayerService) to LiveData
 * for the UI. Commands go through MediaPlayerThread → CorePlayer → MediaController.
 *
 * Do not construct additional instances from ViewModels — use the shared repository.
 */
@OptIn(markerClass = UnstableApi.class)
public class PlaybackStateManager implements PlaybackStateRepository {
    
    private static final String TAG = "PlaybackStateManager";
    
    // LiveData - single source of truth for UI
    private final MutableLiveData<Song> currentSong = new MutableLiveData<>(null);
    private final MutableLiveData<Long> currentPosition = new MutableLiveData<>(0L);
    private final MutableLiveData<Long> currentDuration = new MutableLiveData<>(0L);
    private final MutableLiveData<Boolean> isPlaying = new MutableLiveData<>(false);
    private final MutableLiveData<List<Integer>> queue = new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<Integer> currentQueueIndex = new MutableLiveData<>(0);
    private final MutableLiveData<Integer> repeatMode = new MutableLiveData<>(0);
    private final MutableLiveData<Boolean> shuffleEnabled = new MutableLiveData<>(false);
    private final MutableLiveData<Float> playbackSpeed = new MutableLiveData<>(1.0f);
    private final MutableLiveData<Float> playbackPitch = new MutableLiveData<>(1.0f);
    private final MutableLiveData<com.giga.tech1000.party_mode.model.PartyHost> partyHost = new MutableLiveData<>(null);
    
    // Legacy callback for state updates from CorePlayer
    private final IPlaybackCallback playbackListener = new IPlaybackCallback() {
        @Override
        public void onSongChanged(Song song) {
            currentSong.postValue(song);
        }
        
        @Override
        public void onPlaybackStateChanged(boolean playing, int playbackState) {
            isPlaying.postValue(playing);
        }
        
        @Override
        public void onQueueChanged(List<Integer> queueList, int index) {
            queue.postValue(new ArrayList<>(queueList != null ? queueList : new ArrayList<>()));
            currentQueueIndex.postValue(index);
        }
        
        @Override
        public void onRepeatModeChanged(int mode) {
            repeatMode.postValue(mode);
        }
        
        @Override
        public void onShuffleModeChanged(boolean enabled) {
            shuffleEnabled.postValue(enabled);
        }
        
        @Override
        public void onProgressUpdate(long position, long duration) {
            currentPosition.postValue(position);
            currentDuration.postValue(duration);
        }

        @Override
        public void onMediaItemTransition(@Nullable MediaItem mediaItem, int reason) {

        }

        @Override
        public void onPartyHostDiscovered(PartyHost host) {
            IPlaybackCallback.super.onPartyHostDiscovered(host);
        }

        @Override
        public void onPartyServiceRegistered(PartyHost host) {
            IPlaybackCallback.super.onPartyServiceRegistered(host);
        }

        @Override
        public void onPartyAuthSuccess() {
            // This signifies the guest has successfully joined and playback is ready
        }

        @Override
        public void onPartyMetadataReceived(SyncPacket sync) {
            IPlaybackCallback.super.onPartyMetadataReceived(sync);
        }

        @Override
        public void onPartyConnectionFailed() {
            IPlaybackCallback.super.onPartyConnectionFailed();
        }

        @Override
        public void onPartyDisconnected() {
            IPlaybackCallback.super.onPartyDisconnected();
        }

        @Override
        public void onPartyGuestsUpdated(java.util.List<String> guests) {
            // Host receives this when guest list changes
        }

        @Override
        public void onPartySetupRequired() {
            IPlaybackCallback.super.onPartySetupRequired();
        }

        @Override
        public void onSessionIdReady(int sessionId) {
            // Not exposed through LiveData
        }

        @Override
        public void onPartyHostCreated(com.giga.tech1000.party_mode.model.PartyHost host) {
            partyHost.postValue(host);
        }
    };
    
    private boolean isListenerRegistered = false;
    private final MediaPlayerThread playerThread;
    
    public PlaybackStateManager(MediaPlayerThread playerThread) {
        this.playerThread = playerThread;
        Log.d(TAG, "PlaybackStateManager created");
        registerPlaybackListener();
    }
    
    /**
     * Register listener with CorePlayer to receive updates
     */
    private void registerPlaybackListener() {
        try {
            if (!isListenerRegistered && playerThread != null) {
                if (playerThread.getCorePlayer() != null) {
                    playerThread.getCorePlayer().addListener(playbackListener);
                    isListenerRegistered = true;
                    Log.d(TAG, "Playback listener registered");
                    
                    // Sync initial state
                    syncInitialState();
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to register playback listener", e);
        }
    }
    
    /**
     * Sync initial playback state from current player
     */
    private void syncInitialState() {
        try {
            if (playerThread == null) return;
            
            Song current = playerThread.getCurrentSong();
            if (current != null) {
                currentSong.postValue(current);
            }
            
            // Trigger progress update to sync position/duration
            playerThread.getCallback().onSetSeekbar(0);
        } catch (Exception e) {
            Log.e(TAG, "Failed to sync initial state", e);
        }
    }
    
    // ============ REACTIVE STATE QUERIES ============
    
    @NonNull
    @Override
    public LiveData<Song> getCurrentSong() {
        return currentSong;
    }
    
    @NonNull
    @Override
    public LiveData<Long> getCurrentPosition() {
        return currentPosition;
    }
    
    @NonNull
    @Override
    public LiveData<Long> getCurrentDuration() {
        return currentDuration;
    }
    
    @NonNull
    @Override
    public LiveData<Boolean> isPlaying() {
        return isPlaying;
    }
    
    @NonNull
    @Override
    public LiveData<List<Integer>> getQueue() {
        return queue;
    }
    
    @NonNull
    @Override
    public LiveData<Integer> getCurrentQueueIndex() {
        return currentQueueIndex;
    }
    
    @NonNull
    @Override
    public LiveData<Integer> getRepeatMode() {
        return repeatMode;
    }
    
    @NonNull
    @Override
    public LiveData<Boolean> isShuffleEnabled() {
        return shuffleEnabled;
    }
    
    @NonNull
    @Override
    public LiveData<Float> getPlaybackSpeed() {
        return playbackSpeed;
    }
    
    @NonNull
    @Override
    public LiveData<Float> getPlaybackPitch() {
        return playbackPitch;
    }

    @NonNull
    @Override
    public LiveData<com.giga.tech1000.party_mode.model.PartyHost> getPartyHost() {
        return partyHost;
    }
    
    // ============ PLAYBACK CONTROL COMMANDS ============
    
    @Override
    public void play(int index, List<Integer> queue, ItemSource source) {
        try {
            if (playerThread != null) {
                Log.i("click", "fffffggggggfffff");
                playerThread.getCallback().onClickPlay(index, queue, source);
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to play queue", e);
        }
    }

    @Override
    public void play() {
        Log.i("click", "ffffffffffff");
        try {
            if (playerThread != null) {
                playerThread.getCallback().onClickPlayPause();
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to play", e);
        }
    }
    
    @Override
    public void pause() {
        try {
            if (playerThread != null && isPlayingSync()) {
                playerThread.getCallback().onClickPlayPause();
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to pause", e);
        }
    }
    
    @Override
    public void togglePlayPause() {
        Log.i(TAG, "togglePlayPause called");
        try {
            if (playerThread != null) {
                playerThread.getCallback().onClickPlayPause();
            } else {
                Log.w(TAG, "togglePlayPause failed: playerThread is null");
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to toggle play/pause", e);
        }
    }
    
    @Override
    public void playPartyStream(
            @NonNull String mediaUrl,
            @Nullable String mediaId,
            @Nullable String title,
            @Nullable String artist,
            @Nullable String album,
            long positionMs,
            boolean playWhenReady) {
        try {
            if (playerThread == null || playerThread.getCorePlayer() == null) return;
            MediaController c = playerThread.getCorePlayer().getMediaController();
            if (c == null) {
                Log.w(TAG, "playPartyStream: MediaController null — retry in 400ms");
                new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() ->
                        playPartyStream(mediaUrl, mediaId, title, artist, album, positionMs, playWhenReady), 400);
                return;
            }
            if (mediaUrl.contains("example.invalid")) {
                Log.w(TAG, "playPartyStream: stub URL: " + mediaUrl);
                updatePartyMetadata(title, artist, album, 0);
                return;
            }
            MediaMetadata.Builder meta = new MediaMetadata.Builder();
            if (title != null) meta.setTitle(title);
            if (artist != null) meta.setArtist(artist);
            if (album != null) meta.setAlbumTitle(album);
            MediaItem item = new MediaItem.Builder()
                    .setUri(Uri.parse(mediaUrl))
                    .setMediaId(mediaId != null ? mediaId : "party-stream")
                    .setMediaMetadata(meta.build())
                    .build();
            c.setMediaItem(item);
            c.prepare();
            if (positionMs > 0) c.seekTo(positionMs);
            if (playWhenReady) c.play();
            else c.pause();

            // Force UI metadata (mini + full) via LiveData + UIThread panels
            // Duration may arrive on later sync packets; start with 0 then updatePartyMetadata
            Song synthetic = buildPartySong(mediaId, title, artist, album, mediaUrl, 0);
            currentSong.postValue(synthetic);
            isPlaying.postValue(playWhenReady);
            currentPosition.postValue(Math.max(0, positionMs));
            notifyUiSongChanged(synthetic);
            // Media3 often reports duration after buffer — poll briefly
            pollPartyDuration(c, synthetic, 0);

            Log.d(TAG, "playPartyStream url=" + mediaUrl + " pos=" + positionMs + " play=" + playWhenReady);
        } catch (Exception e) {
            Log.e(TAG, "playPartyStream failed", e);
        }
    }

    @Override
    public void updatePartyMetadata(
            @Nullable String title,
            @Nullable String artist,
            @Nullable String album,
            long durationMs) {
        Song prev = currentSong.getValue();
        String url = prev != null && prev.getData() != null ? prev.getData() : null;
        String id = prev != null ? String.valueOf(prev.getId()) : "party-meta";
        Song synthetic = buildPartySong(id, title, artist, album, url, durationMs);
        if (durationMs <= 0 && prev != null && prev.getDuration() > 0) {
            synthetic.setDuration(prev.getDuration());
        }
        currentSong.postValue(synthetic);
        long d = synthetic.getDuration();
        if (d > 0) currentDuration.postValue(d);
        notifyUiSongChanged(synthetic);
    }

        private void pollPartyDuration(
            @NonNull MediaController c,
            @NonNull Song song,
            int attempt) {
        if (attempt > 8) return;
        new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
            try {
                long d = c.getDuration();
                if (d > 0 && d != androidx.media3.common.C.TIME_UNSET) {
                    song.setDuration(d);
                    currentDuration.postValue(d);
                    currentSong.postValue(song);
                    notifyUiSongChanged(song);
                    return;
                }
            } catch (Exception ignored) { }
            pollPartyDuration(c, song, attempt + 1);
        }, 250);
    }

    private void notifyUiSongChanged(@NonNull Song song) {
        try {
            new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
                try {
                    if (playerThread != null && playerThread.getActivity() != null) {
                        HeartBeatzApp.container(playerThread.getActivity())
                                .requireUiThread()
                                .onSongChanged(song);
                    }
                } catch (Exception e) {
                    Log.w(TAG, "notifyUiSongChanged: " + e.getMessage());
                }
            });
        } catch (Exception e) {
            Log.w(TAG, "notifyUiSongChanged failed", e);
        }
    }

    @NonNull
    private Song buildPartySong(
            @Nullable String mediaId,
            @Nullable String title,
            @Nullable String artist,
            @Nullable String album,
            @Nullable String mediaUrl,
            long durationMs) {
        Song s = new Song();
        try {
            if (mediaId != null) {
                try { s.setId(Long.parseLong(mediaId.replaceAll("[^0-9]", "").isEmpty()
                        ? "0" : mediaId.replaceAll("[^0-9]", ""))); } catch (Exception ignored) {
                    s.setId(mediaId.hashCode() & 0x7fffffff);
                }
            }
        } catch (Exception ignored) { }
        s.setTitle(title != null ? title : "");
        s.setArtist(artist != null ? artist : "");
        s.setAlbum(album != null && !album.isEmpty() ? album : null);
        s.setDuration(durationMs);
        if (mediaUrl != null && !mediaUrl.isEmpty()) {
            try {
                s.setUri(Uri.parse(mediaUrl));
                s.setData(mediaUrl);
            } catch (Exception ignored) { }
        }
        // albumArt left null → UI uses default album_launcher
        return s;
    }

    public void seekTo(long positionMs) {
        try {
            if (playerThread != null) {
                playerThread.getCallback().onSetSeekbar((int) positionMs);
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to seek", e);
        }
    }
    
    @Override
    public void next() {
        try {
            if (playerThread != null) {
                playerThread.getCallback().onClickPlayNext();
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to skip next", e);
        }
    }
    
    @Override
    public void previous() {
        try {
            if (playerThread != null) {
                playerThread.getCallback().onClickPlayPrev();
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to skip previous", e);
        }
    }
    
    @Override
    public void setRepeatMode(int mode) {
        try {
            if (playerThread != null) {
                playerThread.getCallback().onSetRepeatState(mode);
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to set repeat mode", e);
        }
    }
    
    @Override
    public void toggleShuffle() {
        try {
            if (playerThread != null) {
                playerThread.getCallback().onSetShuffleMode(Boolean.FALSE.equals(shuffleEnabled.getValue()));
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to toggle shuffle", e);
        }
    }
    
    @Override
    public void setPlaybackSpeed(float speed) {
        try {
            if (playerThread != null) {
                playerThread.getCallback().onSetPlaybackSpeed(speed);
            }
            playbackSpeed.postValue(speed);
        } catch (Exception e) {
            Log.e(TAG, "Failed to set playback speed", e);
        }
    }
    
    @Override
    public void setPlaybackPitch(float pitch) {
        try {
            if (playerThread != null) {
                playerThread.getCallback().onSetPlaybackPitch(pitch);
            }
            playbackPitch.postValue(pitch);
        } catch (Exception e) {
            Log.e(TAG, "Failed to set playback pitch", e);
        }
    }
    
    // ============ SYNCHRONOUS STATE QUERIES ============
    
    @Nullable
    @Override
    public Song getCurrentSongSync() {
        return currentSong.getValue();
    }
    
    @Override
    public long getCurrentDurationSync() {
        try {
            Long d = getCurrentDuration().getValue();
            return d != null ? d : 0L;
        } catch (Exception e) {
            return 0L;
        }
    }

    public long getCurrentPositionSync() {
        Long pos = currentPosition.getValue();
        return pos != null ? pos : 0L;
    }
    
    @NonNull
    @Override
    public List<Integer> getQueueSync() {
        List<Integer> q = queue.getValue();
        return q != null ? q : new ArrayList<>();
    }
    
    @Override
    public boolean isPlayingSync() {
        Boolean playing = isPlaying.getValue();
        return playing != null && playing;
    }
    
    /**
     * Cleanup when ViewModel is destroyed
     */
    public void release() {
        try {
            if (isListenerRegistered && playerThread != null) {
                if (playerThread.getCorePlayer() != null) {
                    playerThread.getCorePlayer().removeListener(playbackListener);
                    isListenerRegistered = false;
                    Log.d(TAG, "Playback listener unregistered");
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to cleanup", e);
        }
    }
}
