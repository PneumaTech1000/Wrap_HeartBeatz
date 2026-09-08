package com.giga.tech1000.media_player.repository;

import android.content.Context;

import androidx.lifecycle.MutableLiveData;

import com.giga.tech1000.media_player.models.Song;
import com.giga.tech1000.media_player.scanners.LibraryScanner;

import java.util.List;
import java.util.TreeMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import android.util.Log;

/**
 * Singleton repository to hold the global list of songs.
 * This ensures a single source of truth for the entire application.
 */
public class SongRepository {

    private static volatile SongRepository INSTANCE;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    // The single source of truth for all songs, keyed by their ID.
    private final MutableLiveData<TreeMap<Integer, Song>> allSongs;
    public static final String TAG = "SongRepository";
    private SongRepository() {
        allSongs = new MutableLiveData<>();
    }

    /**
     * Shuts down the executor service to prevent resource leaks.
     * Should be called when the repository is no longer needed.
     */
    public void shutdown() {
        executor.shutdown();
        try {
            // Wait a moment for tasks to complete
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    public static SongRepository getInstance() {
        if (INSTANCE == null) {
            synchronized (SongRepository.class) {
                if (INSTANCE == null) {
                    INSTANCE = new SongRepository();
                }
            }
        }
        return INSTANCE;
    }

    /**
     * Asynchronously loads or re-loads all songs from a list.
     * @param songs The list of songs to load.
     */
    public void setSongs(List<Song> songs) {
        executor.execute(() -> {
            try {
                TreeMap<Integer, Song> songTreeMap = new TreeMap<>();
                if (songs != null) {
                    for (Song song : songs) {
                        if (song != null) { // Null check for each song
                            songTreeMap.put((int) song.getId(), song);
                        }
                    }
                }
                allSongs.postValue(songTreeMap);
            } catch (Exception e) {
                Log.e(TAG, "Error setting songs", e);
            }
        });
    }

    /**
     * Asynchronously loads or re-loads all songs from the provided TreeMap.
     * Call this from your UI thread (e.g., in your UIThread class).
     * The update will be reflected globally.
     */
    public void loadSongs(TreeMap<Integer, Song> songTreeMap) {
        executor.execute(() -> {
            try {
                allSongs.postValue(songTreeMap);
            } catch (Exception e) {
                Log.e(TAG, "Error loading songs", e);
            }
        });
    }


    /**
     * Gets the currently cached TreeMap of all songs.
     * @return The global TreeMap of songs.
     */

    public TreeMap<Integer, Song> getCachedSongs() {
        TreeMap<Integer, Song> value = allSongs.getValue();
        return value != null ? value : new TreeMap<>();
    }

    public MutableLiveData<TreeMap<Integer, Song>> getSongs() {
        return allSongs;
    }


}
