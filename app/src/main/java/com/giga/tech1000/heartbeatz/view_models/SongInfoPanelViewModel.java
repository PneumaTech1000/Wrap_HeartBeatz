package com.giga.tech1000.heartbeatz.view_models;

import com.giga.tech1000.heartbeatz.app_worker.HeartBeatzApp;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.giga.tech1000.heartbeatz.observers.LibraryObservers;
import com.giga.tech1000.media_player.database.library.LibraryRepository;
import com.giga.tech1000.media_player.models.Playlist;
import com.giga.tech1000.media_player.models.Song;
import com.giga.tech1000.media_player.models.cross_ref.PlaylistWithCount;

import java.util.List;
import java.util.TreeMap;

/**
 * ViewModel for SongInfoPanel
 * 
 * Replaces direct HeartBeatzApp.container(getApplication()).requireUiThread() calls with injected repository access.
 * Manages song details, favorites, playlists, and library operations.
 * 
 * This ViewModel encapsulates all song information panel functionality
 * and exposes it through clean LiveData interfaces.
 */
public class SongInfoPanelViewModel extends AndroidViewModel {
    
    private static final String TAG = "SongInfoPanelViewModel";
    
    private final LibraryRepository libraryRepository;
    private final LibraryObservers libraryObservers;
    
    // Song data
    private final MutableLiveData<Song> currentSong = new MutableLiveData<>(null);
    
    public SongInfoPanelViewModel(@NonNull Application application) {
        super(application);
        this.libraryRepository = new LibraryRepository(application);
        this.libraryObservers = new LibraryObservers();
    }
    
    // ============ SONG MANAGEMENT ============
    
    /**
     * Get TreeMap of all songs (for quick lookup by ID)
     */
    @NonNull
    public TreeMap<Integer, Song> getTreeMapOfSongs() {
        return libraryObservers.getTreeMapOfSongs();
    }
    
    /**
     * Set current song being displayed
     */
    public void setCurrentSong(@NonNull Song song) {
        currentSong.setValue(song);
    }
    
    /**
     * Get current song being displayed
     */
    @NonNull
    public LiveData<Song> getCurrentSong() {
        return currentSong;
    }
    
    // ============ LIBRARY OPERATIONS ============
    
    /**
     * Get all playlists
     */
    @NonNull
    public LiveData<List<PlaylistWithCount>> getPlaylists() {
        return libraryRepository.getPlaylists();
    }
    
    /**
     * Add song to playlist
     */
    public void addSongToPlaylist(long playlistId, long songId, 
                                   LibraryRepository.OnMetadataUpdateListener listener) {
        libraryRepository.addSongsToPlaylist(playlistId, 
            java.util.Collections.singletonList(songId), listener);
    }
    
    /**
     * Toggle song in favorites
     */
    public void toggleFavorite(@NonNull String songId, boolean currentlyFavorite) {
        libraryRepository.toggle(songId, com.giga.tech1000.media_player.utils.enums.FavoriteType.SONG, currentlyFavorite);
    }
    
    /**
     * Check if song is favorite
     */
    @NonNull
    public LiveData<Boolean> isFavoriteSong(@NonNull String songId) {
        return libraryRepository.isFavoriteSync(songId, com.giga.tech1000.media_player.utils.enums.FavoriteType.SONG);
    }
    
    // ============ SONG DELETION & HIDING ============
    
    /**
     * Temporarily delete song
     */
    public void tempDeleteSongById(long songId) {
        libraryRepository.tempDeleteSongById(songId);
    }
    
    /**
     * Restore deleted song
     */
    public void restoreDeletedSongById(long songId) {
        libraryRepository.restoreDeletedSongById(songId);
    }
    
    /**
     * Hide song from library
     */
    public void hideSongById(long songId) {
        libraryRepository.hideSongById(songId);
    }
    
    /**
     * Restore hidden song
     */
    public void restoreHiddenSongById(long songId) {
        libraryRepository.restoreHiddenSongById(songId);
    }
}
