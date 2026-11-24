package com.giga.tech1000.media_player.scanners;

import android.content.Context;

import android.os.Build;
import android.provider.MediaStore;
import android.util.Log;

import androidx.annotation.NonNull;

import com.giga.tech1000.media_player.database.library.LibraryRepository;
import com.giga.tech1000.media_player.database.setting.SettingRepository;
import com.giga.tech1000.media_player.models.Album;
import com.giga.tech1000.media_player.models.Artist;
import com.giga.tech1000.media_player.models.Folder;
import com.giga.tech1000.media_player.models.Genre;
import com.giga.tech1000.media_player.models.Playlist;
import com.giga.tech1000.media_player.models.Song;
import com.giga.tech1000.media_player.models.cross_ref.PlaylistSongCrossRef;
import com.giga.tech1000.media_player.models.extended_models.SettingEntity;
import com.giga.tech1000.media_player.repository.SongRepository;
import com.giga.tech1000.media_player.utils.DiffResult;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class LocalMediaScannerManager {
    private final Context context;

    private final LibraryScanner scanner;

    private List<Song> songs = new ArrayList<>();
    private List<Album> albums = new ArrayList<>();
    private List<Artist> artists = new ArrayList<>();
    private List<Genre> genres = new ArrayList<>();
    private List<Playlist> playlists = new ArrayList<>();
    private List<Folder> folders = new ArrayList<>();


    public static final String TAG = "LocalMediaScannerManager";
    private final LibraryRepository repository;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();



    public LocalMediaScannerManager(Context context) {
        this.context = context.getApplicationContext();
        this.scanner = new LibraryScanner(context);
        this.repository = new LibraryRepository(context);
    }


    public void init() {
        // Perform initial scan on background thread to avoid blocking UI
        executor.execute(() -> {
            try {
                this.songs = scanner.scanSongs();
                runIncrementalMediaRefresh();
            } catch (Exception e) {
                Log.e(TAG, "Error during initial media scan", e);
                // Still try to run incremental refresh even if initial scan fails
                try {
                    runIncrementalMediaRefresh();
                } catch (Exception ex) {
                    Log.e(TAG, "Error during incremental media refresh after initial scan failure", ex);
                }
            }
        });
    }

    public LibraryRepository getRepository() {
        return repository;
    }

    public void loadSongs() {
        songs = scanner.scanSongs();
    }


    private void loadFolders() {
        folders = LibraryScanner.groupByFolder(songs);
    }

    private void loadArtists() {
        artists = scanner.scanArtists();
    }

    private void loadPlaylists() {
        playlists = scanner.scanPlaylists();
    }

    private void loadAlbums() {
        albums = scanner.scanAlbums();
    }

    private void loadGenres() {
        genres = scanner.scanGenres();
    }


    private void loadAllItems() {
        loadSongs();
        loadAlbums();
        loadArtists();
        loadGenres();
        loadFolders();
        loadPlaylists();
    }


    private void insertItems() {
        repository.insertSongs(songs);
        repository.insertAlbums(albums);
        repository.insertArtists(artists);
        repository.insertGenres(genres);
        repository.insertFolders(folders);
    }

    private void applyGenreToSongs(Map<Long, Long> songGenreMap, Map<Long, Genre> genreMap) {
        for (Song song : songs) {
            Long genreId = songGenreMap.get(song.getId());
            Genre genre = genreMap.get(genreId);
            if (genreId != null) song.setGenreId(genreId);
            if (genre != null) song.setGenreName(genre.getName());
        }
    }

    /**
     * User-initiated full library rebuild: ignores generation short-circuit,
     * rescans all music, updates Room + {@link SongRepository}.
     */
    public void forceFullRescan(@androidx.annotation.Nullable ScanCallback callback) {
        executor.execute(() -> {
            long t0 = System.currentTimeMillis();
            notifyScan(callback, ScanPhase.STARTED, 0, null);
            try {
                SettingRepository settings = SettingRepository.getInstance(context);
                // Clear markers so incremental logic cannot skip
                settings.update(s -> {
                    s.lastMediaStoreGeneration = -1;
                    s.lastScanTimestamp = 0;
                });

                // Full MediaStore pass (no DATE_MODIFIED / generation filter)
                List<Song> allSongs = scanner.scanSongs(-1, 0);
                Set<Long> allValidIds = scanner.scanSongIds();
                notifyScan(callback, ScanPhase.SCANNING, allSongs.size(), null);

                repository.diffUpdateSongs(allSongs, allValidIds, diff -> {
                    long newGen = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
                            ? MediaStore.getGeneration(context, MediaStore.VOLUME_EXTERNAL) : -1;
                    long newTime = System.currentTimeMillis() / 1000;
                    settings.update(s -> {
                        s.lastMediaStoreGeneration = newGen;
                        s.lastScanTimestamp = newTime;
                    });

                    executor.execute(() -> {
                        try {
                            repository.refreshSongGenres(diff.affectedSongIds(), scanner);
                            repository.refreshAlbums(diff);
                            repository.refreshArtists(diff);
                            repository.refreshFolders(diff);
                            repository.deleteEmptyFolders();
                            repository.deleteEmptyAlbums();
                            repository.deleteEmptyArtists();
                            repository.deleteEmptyGenres();
                            repository.cleanupPlaylists(diff.deletedSongIds);

                            List<Playlist> currentPlaylists = scanner.scanPlaylists();
                            List<Long> currentPlaylistIds = new ArrayList<>();
                            for (Playlist p : currentPlaylists) currentPlaylistIds.add(p.getPlaylistId());
                            repository.dao.insertPlaylists(currentPlaylists);
                            repository.dao.deletePlaylistsExcept(currentPlaylistIds);

                            Map<Long, List<Long>> playlistMembers = scanner.scanPlaylistMembers();
                            for (Map.Entry<Long, List<Long>> entry : playlistMembers.entrySet()) {
                                long playlistId = entry.getKey();
                                List<PlaylistSongCrossRef> refs = new ArrayList<>();
                                for (long songId : entry.getValue()) {
                                    refs.add(new PlaylistSongCrossRef(playlistId, songId));
                                }
                                repository.dao.syncPlaylistMembers(playlistId, refs);
                            }

                            List<Song> latestSongs = repository.dao.getAllSongsSync();
                            this.songs = latestSongs;
                            SongRepository.getInstance().loadSongs(scanner.getTreeMapOfSongs(latestSongs));

                            long ms = System.currentTimeMillis() - t0;
                            Log.i(TAG, "forceFullRescan done songs=" + latestSongs.size()
                                    + " updatedIds=" + (diff.updatedSongIds != null ? diff.updatedSongIds.size() : 0)
                                    + " deleted=" + (diff.deletedSongIds != null ? diff.deletedSongIds.size() : 0)
                                    + " ms=" + ms);
                            notifyScan(callback, ScanPhase.SUCCESS, latestSongs.size(), null);
                        } catch (Exception e) {
                            Log.e(TAG, "forceFullRescan post-process failed", e);
                            notifyScan(callback, ScanPhase.FAILED, 0, e.getMessage());
                        }
                    });
                });
            } catch (Exception e) {
                Log.e(TAG, "forceFullRescan failed", e);
                notifyScan(callback, ScanPhase.FAILED, 0, e.getMessage());
            }
        });
    }

    public enum ScanPhase {
        STARTED, SCANNING, SUCCESS, FAILED
    }

    public interface ScanCallback {
        void onScanPhase(@NonNull ScanPhase phase, int songCount, @androidx.annotation.Nullable String error);
    }

    private static void notifyScan(
            @androidx.annotation.Nullable ScanCallback callback,
            @NonNull ScanPhase phase,
            int count,
            @androidx.annotation.Nullable String error) {
        if (callback == null) return;
        try {
            callback.onScanPhase(phase, count, error);
        } catch (Exception e) {
            Log.w(TAG, "ScanCallback error", e);
        }
    }

    public void runIncrementalMediaRefresh() {
        executor.execute(() -> {
            SettingRepository settings = SettingRepository.getInstance(context);
            SettingEntity cachedSettings = settings.getCached();

            long lastGen = -1;
            long lastTime = 0;

            if (cachedSettings != null) {
                lastGen = cachedSettings.lastMediaStoreGeneration;
                lastTime = cachedSettings.lastScanTimestamp;
            }

            // API 30+ Optimization: Check if MediaStore has changed at all
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && lastGen != -1) {
                long currentGen = MediaStore.getGeneration(context, MediaStore.VOLUME_EXTERNAL);
                if (currentGen == lastGen) {
                    return; // No changes in MediaStore, skip scan
                }
            }

            // 1. Scan device for changes
            List<Song> changedSongs = scanner.scanSongs(lastGen, lastTime);
            
            // 2. Scan device for ALL valid IDs to detect deletions (Efficiently)
            // Even if no songs changed, some might have been deleted.
            Set<Long> allValidIds = scanner.scanSongIds();

            // 3. Diff + apply core song changes
            repository.diffUpdateSongs(changedSongs, allValidIds, diff -> {
                // Update markers
                long newGen = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
                        ? MediaStore.getGeneration(context, MediaStore.VOLUME_EXTERNAL) : -1;
                long newTime = System.currentTimeMillis() / 1000;

                settings.update(s -> {
                    s.lastMediaStoreGeneration = newGen;
                    s.lastScanTimestamp = newTime;
                });

                // 4. If nothing changed, stop early
                if (!diff.hasAnyChange()) return;

                executor.execute(() -> {
                    // 5. Resolve genre info ONLY for affected songs
                    repository.refreshSongGenres(diff.affectedSongIds(), scanner);

                    // 6. Refresh dependent entities (counts only)
                    repository.refreshAlbums(diff);
                    repository.refreshArtists(diff);
                    repository.refreshFolders(diff);

                    repository.deleteEmptyFolders();
                    repository.deleteEmptyAlbums();
                    repository.deleteEmptyArtists();
                    repository.deleteEmptyGenres();


                    // 7. Cleanup playlists if songs were deleted
                    repository.cleanupPlaylists(diff.deletedSongIds);

                    // 8. Sync Playlists from MediaStore
                    List<Playlist> currentPlaylists = scanner.scanPlaylists();
                    List<Long> currentPlaylistIds = new ArrayList<>();
                    for (Playlist p : currentPlaylists) currentPlaylistIds.add(p.getPlaylistId());
                    
                    repository.dao.insertPlaylists(currentPlaylists);
                    repository.dao.deletePlaylistsExcept(currentPlaylistIds);

                    // 9. Sync Playlist Members
                    Map<Long, List<Long>> playlistMembers = scanner.scanPlaylistMembers();
                    for (Map.Entry<Long, List<Long>> entry : playlistMembers.entrySet()) {
                        long playlistId = entry.getKey();
                        List<Long> songIds = entry.getValue();
                        List<PlaylistSongCrossRef> refs = new ArrayList<>();
                        for (long songId : songIds) {
                            refs.add(new PlaylistSongCrossRef(playlistId, songId));
                        }
                        repository.dao.syncPlaylistMembers(playlistId, refs);
                    }

                    // 10. Update Global Repository to reflect changes in UI
                    List<Song> latestSongs = repository.dao.getAllSongsSync();
                    SongRepository.getInstance().loadSongs(scanner.getTreeMapOfSongs(latestSongs));
                });
            });
        });
    }

    public List<Song> getSongs() {
        return songs != null ? songs : new ArrayList<>();
    }

    public List<Album> getAlbums() {
        return albums;
    }

    public List<Artist> getArtists() {
        return artists;
    }

    public List<Genre> getGenres() {
        return genres;
    }

    public List<Playlist> getPlaylists() {
        return playlists;
    }

    public List<Folder> getFolders() {
        return folders;
    }

    public void release() {
        if (executor != null) {
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
    }
}
