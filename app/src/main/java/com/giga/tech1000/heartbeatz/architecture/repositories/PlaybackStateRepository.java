package com.giga.tech1000.heartbeatz.architecture.repositories;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;

import com.giga.tech1000.media_player.models.Song;
import com.giga.tech1000.media_player.utils.enums.ItemSource;
import com.giga.tech1000.party_mode.model.PartyHost;

import java.util.List;

/**
 * Abstraction for playback state management.
 * 
 * Separates UI layer from direct dependency on MediaPlayerThread/CorePlayer.
 * All playback state is exposed through LiveData for reactive UI updates.
 * 
 * This is the SINGLE SOURCE OF TRUTH for playback state in the app.
 * UI should NEVER access MediaPlayerThread or CorePlayer directly.
 */
public interface PlaybackStateRepository {
    
    // ============ CURRENT PLAYBACK STATE ============
    
    /**
     * Current song being played
     */
    @NonNull
    LiveData<Song> getCurrentSong();
    
    /**
     * Current playback position in milliseconds
     */
    @NonNull
    LiveData<Long> getCurrentPosition();
    
    /**
     * Total duration of current song in milliseconds
     */
    @NonNull
    LiveData<Long> getCurrentDuration();
    
    /**
     * Whether playback is currently active
     */
    @NonNull
    LiveData<Boolean> isPlaying();
    
    /**
     * Current queue of song IDs
     */
    @NonNull
    LiveData<java.util.List<Integer>> getQueue();
    
    /**
     * Current index in queue
     */
    @NonNull
    LiveData<Integer> getCurrentQueueIndex();
    
    // ============ PLAYBACK PARAMETERS ============
    
    /**
     * Current repeat mode (NONE, ONE, ALL)
     */
    @NonNull
    LiveData<Integer> getRepeatMode();
    
    /**
     * Whether shuffle is enabled
     */
    @NonNull
    LiveData<Boolean> isShuffleEnabled();
    
    /**
     * Playback speed (1.0 = normal)
     */
    @NonNull
    LiveData<Float> getPlaybackSpeed();
    
    /**
     * Audio pitch (1.0 = normal)
     */
    @NonNull
    LiveData<Float> getPlaybackPitch();

    // ============ PARTY MODE STATE ============

    /**
     * Called when a party is created successfully
     */
    @NonNull
    LiveData<PartyHost> getPartyHost();

    // ============ PLAYBACK CONTROL COMMANDS ============
    
    /**
     * Play a specific item from a queue
     */
    void play(int index, List<Integer> queue, ItemSource source);

    /**
     * Play the current song or resume if paused
     */
    void play();
    
    /**
     * Pause playback
     */
    void pause();
    
    /**
     * Toggle play/pause
     */
    void togglePlayPause();
    
    /**
     * Seek to specific position in current song
     */
    void seekTo(long positionMs);
    
    /**
     * Play next song in queue
     */
    void next();
    
    /**
     * Play previous song in queue
     */
    void previous();
    
    /**
     * Set repeat mode
     */
    void setRepeatMode(int repeatMode);
    
    /**
     * Toggle shuffle mode
     */
    void toggleShuffle();
    
    /**
     * Set playback speed
     */
    void setPlaybackSpeed(float speed);
    
    /**
     * Set audio pitch
     */
    void setPlaybackPitch(float pitch);
    
    // ============ STATE QUERIES (Synchronous) ============
    
    /**
     * Get current song immediately (for initial UI setup)
     */
    @Nullable
    Song getCurrentSongSync();
    
    /**
     * Get current position immediately
     */
    long getCurrentPositionSync();
    
    /**
     * Get current queue immediately
     */
    @NonNull
    java.util.List<Integer> getQueueSync();
    
    /**
     * Check if currently playing
     */
    boolean isPlayingSync();
}
