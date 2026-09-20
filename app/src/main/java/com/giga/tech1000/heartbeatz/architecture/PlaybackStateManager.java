package com.giga.tech1000.heartbeatz.architecture;

import com.giga.tech1000.heartbeatz.app_worker.HeartBeatzApp;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.OptIn;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.media3.common.MediaItem;
import androidx.media3.common.util.UnstableApi;

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
