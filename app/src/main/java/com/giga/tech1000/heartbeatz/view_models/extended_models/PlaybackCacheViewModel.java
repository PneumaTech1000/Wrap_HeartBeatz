package com.giga.tech1000.heartbeatz.view_models.extended_models;

import com.giga.tech1000.heartbeatz.app_worker.HeartBeatzApp;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.annotation.OptIn;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.media3.common.util.UnstableApi;

import com.giga.tech1000.heartbeatz.architecture.repositories.PlaybackStateRepository;
import com.giga.tech1000.heartbeatz.ui.UIThread;
import com.giga.tech1000.media_player.models.extended_models.PlayerCacheModel;
import com.giga.tech1000.media_player.utils.enums.ItemSource;
import com.giga.tech1000.party_mode.model.PartyHost;

import java.util.List;

/**
 * ViewModel for RootMediaDetailsPanel
 * 
 * Replaces direct HeartBeatzApp.container(getApplication()).requireUiThread() calls with injected repository access.
 * Manages playback cache information and current playing song display.
 * 
 * This ViewModel encapsulates all playback details panel functionality
 * and exposes it through clean LiveData interfaces.
 */
@OptIn(markerClass = UnstableApi.class)
public class PlaybackCacheViewModel extends AndroidViewModel {
    
    private static final String TAG = "PlaybackCacheViewModel";
    
    private final PlaybackStateRepository playbackState;
    
    public PlaybackCacheViewModel(@NonNull Application application) {
        super(application);
        PlaybackStateRepository repo = null;
        try {
            UIThread ui = HeartBeatzApp.container(getApplication()).uiThreadOrNull();
            if (ui != null) {
                repo = ui.getPlaybackStateRepository();
            }
        } catch (Exception ignored) {
        }
        this.playbackState = repo;
    }
    
    /**
     * Constructor with dependency injection (for testing)
     */
    public PlaybackCacheViewModel(@NonNull Application application,
                                   @NonNull PlaybackStateRepository playbackStateRepo) {
        super(application);
        this.playbackState = playbackStateRepo;
    }
    
    // ============ PLAYBACK STATE ============
    
    /**
     * Get current song being played
     */
    @NonNull
    public LiveData<?> getCurrentSong() {
        return playbackState.getCurrentSong();
    }
    
    /**
     * Get current playback position
     */
    @NonNull
    public LiveData<Long> getCurrentPosition() {
        return playbackState.getCurrentPosition();
    }
    
    /**
     * Get current song duration
     */
    @NonNull
    public LiveData<Long> getCurrentDuration() {
        return playbackState.getCurrentDuration();
    }
    
    /**
     * Get playback state (playing/paused)
     */
    @NonNull
    public LiveData<Boolean> isPlaying() {
        return playbackState.isPlaying();
    }
    
    /**
     * Get current queue
     */
    @NonNull
    public LiveData<java.util.List<Integer>> getQueue() {
        return playbackState.getQueue();
    }
    
    /**
     * Get current queue index
     */
    @NonNull
    public LiveData<Integer> getCurrentQueueIndex() {
        return playbackState.getCurrentQueueIndex();
    }
    
    /**
     * Get repeat mode
     */
    @NonNull
    public LiveData<Integer> getRepeatMode() {
        return playbackState.getRepeatMode();
    }
    
    /**
     * Get shuffle enabled state
     */
    @NonNull
    public LiveData<Boolean> isShuffleEnabled() {
        return playbackState.isShuffleEnabled();
    }
    
    /**
     * Get playback speed
     */
    @NonNull
    public LiveData<Float> getPlaybackSpeed() {
        return playbackState.getPlaybackSpeed();
    }
    
    /**
     * Get playback pitch
     */
    @NonNull
    public LiveData<Float> getPlaybackPitch() {
        return playbackState.getPlaybackPitch();
    }

    /**
     * Get active party host (contains QR code data)
     */
    @NonNull
    public LiveData<PartyHost> getPartyHost() {
        return playbackState.getPartyHost();
    }
    
    /**
     * Get player cache info (contains current playing info)
     */
    @NonNull
    public LiveData<PlayerCacheModel> getPlayerCacheInfo() {
        UIThread ui = HeartBeatzApp.container(getApplication()).uiThreadOrNull();
        if (ui == null || ui.getPlayingCache() == null) return new MutableLiveData<>(null);
        return ui.getPlayingCache().getPlayerCacheInfo();
    }
    
    // ============ PLAYBACK CONTROLS ============
    
    /**
     * Play current song
     */
    public void play() {
        playbackState.play();
    }

    /**
     * Play specific item from queue
     */
    public void play(int index, List<Integer> queue, ItemSource source) {
        playbackState.play(index, queue, source);
        UIThread ui = HeartBeatzApp.container(getApplication()).uiThreadOrNull();
        if (ui != null && ui.getPlayingCache() != null) {
            ui.getPlayingCache().cachePlayerItemSource(source);
        }
    }
    
    /**
     * Pause playback
     */
    public void pause() {
        if (blockGuestUserTransport("pause")) return;
        playbackState.pause();
    }
    
    /**
     * Toggle play/pause
     */
    public void togglePlayPause() {
        if (blockGuestUserTransport("togglePlayPause")) return;
        playbackState.togglePlayPause();
    }
    
    /**
     * Seek to position
     */
    public void seekTo(long positionMs) {
        if (blockGuestUserTransport("seek")) return;
        playbackState.seekTo(positionMs);
    }
    
    /**
     * Skip to next song
     */
    public void next() {
        if (blockGuestUserTransport("next")) return;
        playbackState.next();
    }
    
    /**
     * Skip to previous song
     */
    public void previous() {
        if (blockGuestUserTransport("previous")) return;
        playbackState.previous();
    }
    
    /**
     * Set repeat mode
     */
    public void setRepeatMode(int mode) {
        if (blockGuestUserTransport("repeat")) return;
        playbackState.setRepeatMode(mode);
    }
    
    /**
     * Toggle shuffle mode
     */
    public void toggleShuffle() {
        if (blockGuestUserTransport("shuffle")) return;
        playbackState.toggleShuffle();
    }

    /**
     * Party guest: UI-originated transport is blocked; TimeEngine still drives
     * {@link PlaybackStateRepository} directly and is unaffected.
     */
    private boolean blockGuestUserTransport(@NonNull String action) {
        try {
            Boolean locked = HeartBeatzApp.container(getApplication())
                    .partyLiveBridge()
                    .isGuestPlayerLocked()
                    .getValue();
            if (Boolean.TRUE.equals(locked)) {
                android.util.Log.d(TAG, "guest transport blocked: " + action);
                return true;
            }
        } catch (Exception ignored) {
        }
        return false;
    }
    
    /**
     * Set playback speed
     */
    public void setPlaybackSpeed(float speed) {
        playbackState.setPlaybackSpeed(speed);
    }
    
    /**
     * Set playback pitch
     */
    public void setPlaybackPitch(float pitch) {
        playbackState.setPlaybackPitch(pitch);
    }
    
    // ============ LIFECYCLE ============
    
    @Override
    protected void onCleared() {
        super.onCleared();
        // Do not release shared PlaybackStateRepository — owned by UIThread for app lifetime
    }
}
