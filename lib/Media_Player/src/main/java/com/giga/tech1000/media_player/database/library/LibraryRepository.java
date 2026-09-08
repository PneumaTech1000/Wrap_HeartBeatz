package com.giga.tech1000.media_player.database.library;

import android.app.PendingIntent;
import android.app.RecoverableSecurityException;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.Transformations;

import com.giga.tech1000.media_player.database.LibraryDao;
import com.giga.tech1000.media_player.models.Album;
import com.giga.tech1000.media_player.models.Artist;
import com.giga.tech1000.media_player.models.Folder;
import com.giga.tech1000.media_player.models.Genre;
import com.giga.tech1000.media_player.models.Playlist;
import com.giga.tech1000.media_player.models.Song;
import com.giga.tech1000.media_player.models.cross_ref.AlbumWithSongs;
import com.giga.tech1000.media_player.models.cross_ref.ArtistWithSongs;
import com.giga.tech1000.media_player.models.cross_ref.FolderWithSongs;
import com.giga.tech1000.media_player.models.cross_ref.GenreWithCount;
import com.giga.tech1000.media_player.models.cross_ref.GenreWithSongs;
import com.giga.tech1000.media_player.models.cross_ref.PlaylistSongCrossRef;
import com.giga.tech1000.media_player.models.cross_ref.PlaylistWithCount;
import com.giga.tech1000.media_player.models.cross_ref.PlaylistWithSongs;
import com.giga.tech1000.media_player.models.extended_models.DefaultPlaylists;
import com.giga.tech1000.media_player.models.extended_models.FavoriteEntity;
import com.giga.tech1000.media_player.scanners.LibraryScanner;
import com.giga.tech1000.media_player.utils.DiffResult;
import com.giga.tech1000.media_player.utils.enums.FavoriteType;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.concurrent.TimeUnit;

public class LibraryRepository {

    public final LibraryDao dao;
    private final LibraryDatabase db;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private final Context context;
    public static final String TAG = "LibraryRepository";

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


    public LibraryRepository(Context context) {
        if (context == null) {
            throw new IllegalArgumentException("Context cannot be null");
        }
        this.context = context.getApplicationContext();
        db = LibraryDatabase.getInstance(context);
        dao = db.libraryDao();
        ensureDefaultPlaylistExists();
    }

    private void ensureDefaultPlaylistExists() {
        executor.execute(() -> {
            try {
                Playlist defaultPlaylist = new Playlist(
                        DefaultPlaylists.ALL_SONGS_ID,
                        DefaultPlaylists.ALL_SONGS_NAME,
                        System.currentTimeMillis()
                );
                dao.insertPlaylist(defaultPlaylist);
            } catch (Exception e) {
                Log.e(TAG, "Error ensuring default playlist exists", e);
            }
        });
    }

    /* -------------------- SONGS -------------------- */

    public LiveData<List<Song>> getAllSongs() {
        return dao.getAllSongs();
    }


    public void insertSongs(List<Song> songs) {
        if (songs == null || songs.isEmpty()) {
            return;
        }
        executor.execute(() -> {
            try {
                dao.insertSongs(songs);
            } catch (Exception e) {
                Log.e(TAG, "Error inserting songs", e);
            }
        });
    }

    public void replaceSongs(List<Song> songs) {
        if (songs == null || songs.isEmpty()) {
            return;
        }
        executor.execute(() -> {
            try {
                dao.replaceSongs(songs);
            } catch (Exception e) {
                Log.e(TAG, "Error replacing songs", e);
            }
        });
    }

    public void diffUpdateSongs(@NonNull List<Song> deviceSongs, @Nullable Set<Long> allValidIds, @NonNull Consumer<DiffResult> onComplete) {

        executor.execute(() -> {
            List<Long> newIds = new ArrayList<>();
            List<Long> updatedIds = new ArrayList<>();
            List<Long> deletedIds = new ArrayList<>();

            List<Song> dbSongs = dao.getAllSongsSync();
            Map<Long, Song> dbMap = new HashMap<>();

            for (Song s : dbSongs) dbMap.put(s.getId(), s);

            // 1. Process changed/new songs
            for (Song deviceSong : deviceSongs) {
                Song dbSong = dbMap.get(deviceSong.getId());
                if (dbSong == null) {
                    newIds.add(deviceSong.getId());
                    dao.insertSong(deviceSong);
                } else if (!dbSong.semanticEquals(deviceSong)) {
                    updatedIds.add(deviceSong.getId());
                    dao.updateSong(deviceSong);
                }
            }

            // 2. Process deletions
            if (allValidIds != null) {
                // If we have a full set of valid IDs, any song in DB NOT in that set is deleted
                for (Song dbSong : dbSongs) {
                    if (!allValidIds.contains(dbSong.getId())) {
                        deletedIds.add(dbSong.getId());
                        dao.deleteSongsById(dbSong.getId());
                    }
                }
            }

            DiffResult result = new DiffResult(newIds, updatedIds, deletedIds);
            MAIN.post(() -> onComplete.accept(result));
        });
    }

    public void tempDeleteSongById(long songId) {
        executor.execute(() -> {
            try {
                dao.tempDeleteSongById(songId);
            } catch (Exception e) {
                Log.e(TAG, "Error temp deleting song by id: " + songId, e);
            }
        });
    }

    public void restoreDeletedSongById(long songId) {
        executor.execute(() -> {
            try {
                dao.restoreDeletedSongById(songId);
            } catch (Exception e) {
                Log.e(TAG, "Error restoring deleted song by id: " + songId, e);
            }
        });
    }

    public void hideSongById(long songId) {
        executor.execute(() -> {
            try {
                dao.hideSongById(songId);
            } catch (Exception e) {
                Log.e(TAG, "Error hiding song by id: " + songId, e);
            }
        });
    }

    public void restoreHiddenSongById(long songId) {
        executor.execute(() -> {
            try {
                dao.restoreHiddenSongById(songId);
            } catch (Exception e) {
                Log.e(TAG, "Error restoring hidden song by id: " + songId, e);
            }
        });
    }



    /* -------------------- ALBUMS -------------------- */

    public LiveData<List<Album>> getAlbums() {
        return dao.getAllAlbums();
    }

    public void insertAlbums(List<Album> albums) {
        if (albums == null || albums.isEmpty()) {
            return;
        }
        executor.execute(() -> {
            try {
                dao.insertAlbums(albums);
            } catch (Exception e) {
                Log.e(TAG, "Error inserting albums", e);
            }
        });
    }

    public LiveData<AlbumWithSongs> getAlbumWithSongs(long id) {
        return dao.getAlbumWithSongs(id);
    }

    public void refreshAlbums(DiffResult diff) {
        if (diff == null) {
            return;
        }
        if (diff.hasAnyChange()) {
            executor.execute(() -> {
                try {
                    dao.recalculateAlbumSongCounts();
                    insertAlbums(extractAlbums(dao.getSongsByIds(diff.affectedSongIds())));
                } catch (Exception e) {
                    Log.e(TAG, "Error refreshing albums", e);
                }
            });
        }
    }

    public void deleteEmptyAlbums() {
        executor.execute(() -> {
            try {
                dao.deleteEmptyAlbums();
            } catch (Exception e) {
                Log.e(TAG, "Error deleting empty albums", e);
            }
        });
    }



    /* -------------------- GENRES -------------------- */

    public LiveData<List<Genre>> getGenres() {
        return dao.getAllGenres();
    }

    public void insertGenres(List<Genre> genres) {
        if (genres == null || genres.isEmpty()) {
            return;
        }
        executor.execute(() -> {
            try {
                dao.insertGenres(genres);
            } catch (Exception e) {
                Log.e(TAG, "Error inserting genres", e);
            }
        });
    }

    public LiveData<GenreWithSongs> getGenreWithSongs(long id) {
        return dao.getGenreWithSongs(id);
    }

    public LiveData<List<GenreWithCount>> getGenresWithCount() {
        return dao.observeGenresWithCount();
    }


    // Genre songs Updates

    public void internalUpdateSongGenre(LibraryScanner scanner) {
        if (scanner == null) {
            Log.w(TAG, "Scanner is null in internalUpdateSongGenre");
            return;
        }
        executor.execute(() -> {
            try {
                Map<Long, Long> songGenreMap = scanner.resolveGenreMembers();
                Map<Long, Genre> genreMap = scanner.resolveGenreMap();

                for (Map.Entry<Long, Long> entry : songGenreMap.entrySet()) {
                    long songId = entry.getKey();
                    for (Genre genre : genreMap.values()) {
                        if (genre.getId() == entry.getValue()) {
                            updateSongGenre(songId, genre.getId(), genre.getName());
                            dao.touchGenres();
                            break;
                        }
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Error updating song genres", e);
            }
        });
    }

    public void refreshSongGenres(Set<Long> affectedSongIds, LibraryScanner scanner) {

        if (affectedSongIds == null || scanner == null) {
            return;
        }

        Map<Long, Long> songGenreMap = scanner.resolveGenreMembers();
        Map<Long, Genre> genreMap = scanner.resolveGenreMap();

        for (Long songId : affectedSongIds) {
            Long genreId = songGenreMap.get(songId);
            if (genreId == null) continue;

            Genre genre = genreMap.get(genreId);
            if (genre == null) continue;
            updateSongGenre(
                    songId,
                    genreId,
                    genre.getName()
            );
            dao.touchGenres();
            dao.insertGenres(extractGenres(dao.getSongsByIds(affectedSongIds)));
        }
    }


    public void updateSongGenre(long songId, long genreId, String genreName) {
        executor.execute(() -> {
            try {
                dao.updateSongGenre(songId, genreId, genreName);
            } catch (Exception e) {
                Log.e(TAG, "Error updating song genre", e);
            }
        });
    }

    public void deleteEmptyGenres() {
        executor.execute(() -> {
            try {
                dao.deleteEmptyGenres();
            } catch (Exception e) {
                Log.e(TAG, "Error deleting empty genres", e);
            }
        });
    }



    /* -------------------- ARTISTS -------------------- */

    public LiveData<List<Artist>> getArtists() {
        return dao.getAllArtists();
    }

    public void insertArtists(List<Artist> artists) {
        if (artists == null || artists.isEmpty()) {
            return;
        }
        executor.execute(() -> {
            try {
                dao.insertArtists(artists);
            } catch (Exception e) {
                Log.e(TAG, "Error inserting artists", e);
            }
        });
    }

    public LiveData<ArtistWithSongs> getArtistWithSongs(long id) {
        return dao.getArtistWithSongs(id);
    }

    public void refreshArtists(DiffResult diff) {
        if (diff == null) {
            return;
        }
        if (diff.hasAnyChange()) {
            executor.execute(() -> {
                try {
                    dao.recalculateArtistSongCounts();
                    insertArtists(extractArtists(dao.getSongsByIds(diff.affectedSongIds())));
                } catch (Exception e) {
                    Log.e(TAG, "Error refreshing artists", e);
                }
            });
        }
    }

    public void deleteEmptyArtists() {
        executor.execute(() -> {
            try {
                dao.deleteEmptyArtists();
            } catch (Exception e) {
                Log.e(TAG, "Error deleting empty artists", e);
            }
        });
    }




    /* -------------------- FOLDERS -------------------- */

    public LiveData<List<Folder>> getFolders() {
        return dao.getAllFolders();
    }

    public void insertFolders(List<Folder> folders) {
        if (folders == null || folders.isEmpty()) {
            return;
        }
        executor.execute(() -> {
            try {
                dao.insertFolders(folders);
            } catch (Exception e) {
                Log.e(TAG, "Error inserting folders", e);
            }
        });
    }

    public LiveData<FolderWithSongs> getSongsByFolder(String folder) {
        return dao.getSongsByFolder(folder);
    }

    public void refreshFolders(DiffResult diff) {
        if (diff == null) {
            return;
        }
        if (diff.hasAnyChange()) {
            executor.execute(() -> {
                try {
                    dao.recalculateFolderSongCounts();
                    // We use all songs to extract folders because folders are derived from songs' paths.
                    // Using only affected songs might miss folders that still have other songs.
                    List<Song> allSongs = dao.getAllSongsSync();
                    insertFolders(extractFolders(allSongs));
                } catch (Exception e) {
                    Log.e(TAG, "Error refreshing folders", e);
                }
            });
        }
    }

    public void deleteEmptyFolders() {
        executor.execute(() -> {
            try {
                dao.deleteEmptyFolders();
            } catch (Exception e) {
                Log.e(TAG, "Error deleting empty folders", e);
            }
        });
    }



    /* -------------------- PLAYLISTS -------------------- */

    public LiveData<List<PlaylistWithCount>> getPlaylists() {
        return Transformations.map(
                dao.observePlaylistsWithCount(),
                playlists -> {
                    List<PlaylistWithCount> result = new ArrayList<>();

                    PlaylistWithCount foundDefault = null;
                    List<PlaylistWithCount> others = new ArrayList<>();

                    if (playlists != null) {
                        for (PlaylistWithCount p : playlists) {
                            if (p.playlist.getPlaylistId() == DefaultPlaylists.ALL_SONGS_ID) {
                                foundDefault = p;
                            } else {
                                others.add(p);
                            }
                        }
                    }

                    // Always ensure default playlist is first
                    if (foundDefault != null) {
                        result.add(foundDefault);
                    } else {
                        result.add(defaultPlaylist());
                    }

                    result.addAll(others);
                    return result;
                }
        );
    }

    private PlaylistWithCount defaultPlaylist() {
        Playlist playlist = new Playlist(
                DefaultPlaylists.ALL_SONGS_ID,
                DefaultPlaylists.ALL_SONGS_NAME,
                System.currentTimeMillis()
        );

        PlaylistWithCount pwc = new PlaylistWithCount();
        pwc.playlist = playlist;
        pwc.numOfSongs = 0; // will be replaced later if you want

        return pwc;
    }


    public void createPlaylist(@NonNull String name, @NonNull Consumer<Long> onCreated) {
        executor.execute(() -> {
            ContentResolver resolver = context.getContentResolver();
            Uri collection = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
                    ? MediaStore.Audio.Playlists.getContentUri(MediaStore.VOLUME_EXTERNAL)
                    : MediaStore.Audio.Playlists.EXTERNAL_CONTENT_URI;

            ContentValues values = new ContentValues();
            values.put(MediaStore.Audio.Playlists.NAME, name);
            values.put(MediaStore.Audio.Playlists.DATE_ADDED, System.currentTimeMillis() / 1000);

            long mediaStoreId = -1;
            try {
                Uri newPlaylistUri = resolver.insert(collection, values);
                if (newPlaylistUri != null) {
                    mediaStoreId = Long.parseLong(newPlaylistUri.getLastPathSegment());
                }
            } catch (Exception e) {
                e.printStackTrace();
            }

            // Fallback if MediaStore fails or just use a local sequence if desired,
            // but for dual-write we prefer MediaStore ID.
            long finalId = mediaStoreId != -1 ? mediaStoreId : System.currentTimeMillis();

            Playlist playlist = new Playlist(
                    finalId,
                    name,
                    System.currentTimeMillis() / 1000
            );

            dao.insertPlaylist(playlist);

            MAIN.post(() -> onCreated.accept(finalId));
        });
    }


    public void cleanupPlaylists(List<Long> deletedSongIds) {
        if (deletedSongIds == null || deletedSongIds.isEmpty()) {
            return;
        }
        executor.execute(() -> {
            try {
                for (Long songId : deletedSongIds) {
                    dao.removeSongFromAllPlaylists(songId);
                }
            } catch (Exception e) {
                Log.e(TAG, "Error cleaning up playlists", e);
            }
        });
    }


    public LiveData<PlaylistWithSongs> getPlaylistWithSongs(long playlistId) {
        return dao.getPlaylistWithSongs(playlistId);
    }

    public void addSongsToPlaylist(long playlistId, List<Long> songIds, OnMetadataUpdateListener listener) {
        executor.execute(() -> {
            ContentResolver resolver = context.getContentResolver();
            String volume = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
                    ? MediaStore.VOLUME_EXTERNAL : "external";
            Uri membersUri = MediaStore.Audio.Playlists.Members.getContentUri(volume, playlistId);

            try {
                // 1. Get current max play order
                int baseOrder = 0;
                try (android.database.Cursor cursor = resolver.query(membersUri, new String[]{"MAX(" + MediaStore.Audio.Playlists.Members.PLAY_ORDER + ")"}, null, null, null)) {
                    if (cursor != null && cursor.moveToFirst()) {
                        baseOrder = cursor.getInt(0) + 1;
                    }
                }

                // 2. Add to MediaStore
                List<PlaylistSongCrossRef> refs = new ArrayList<>();
                for (int i = 0; i < songIds.size(); i++) {
                    long songId = songIds.get(i);
                    ContentValues values = new ContentValues();
                    values.put(MediaStore.Audio.Playlists.Members.AUDIO_ID, songId);
                    values.put(MediaStore.Audio.Playlists.Members.PLAY_ORDER, baseOrder + i);
                    resolver.insert(membersUri, values);
                    refs.add(new PlaylistSongCrossRef(playlistId, songId));
                }

                // 3. Sync to local DB
                dao.addSongsToPlaylist(refs);

                if (listener != null) {
                    MAIN.post(listener::onUpdateSuccess);
                }
            } catch (SecurityException securityException) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && securityException instanceof RecoverableSecurityException) {
                    RecoverableSecurityException recoverableSecurityException = (RecoverableSecurityException) securityException;
                    if (listener != null) {
                        MAIN.post(() -> listener.onPermissionRequired(recoverableSecurityException.getUserAction().getActionIntent()));
                    }
                } else {
                    if (listener != null) {
                        MAIN.post(() -> listener.onUpdateError(securityException));
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
                if (listener != null) {
                    MAIN.post(() -> listener.onUpdateError(e));
                }
            }
        });
    }


    public void removeSongsFromPlaylist(long playlistId, List<Long> songIds, OnMetadataUpdateListener listener) {
        if (songIds == null || songIds.isEmpty()) return;

        executor.execute(() -> {
            ContentResolver resolver = context.getContentResolver();
            String volume = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
                    ? MediaStore.VOLUME_EXTERNAL : "external";
            Uri membersUri = MediaStore.Audio.Playlists.Members.getContentUri(volume, playlistId);

            try {
                // 1. Remove from MediaStore
                for (long songId : songIds) {
                    resolver.delete(membersUri, MediaStore.Audio.Playlists.Members.AUDIO_ID + "=?", new String[]{String.valueOf(songId)});
                }

                // 2. Remove from local DB
                dao.removeSongsFromPlaylist(playlistId, songIds);

                if (listener != null) {
                    MAIN.post(listener::onUpdateSuccess);
                }
            } catch (SecurityException securityException) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && securityException instanceof RecoverableSecurityException) {
                    RecoverableSecurityException recoverableSecurityException = (RecoverableSecurityException) securityException;
                    if (listener != null) {
                        MAIN.post(() -> listener.onPermissionRequired(recoverableSecurityException.getUserAction().getActionIntent()));
                    }
                } else {
                    if (listener != null) {
                        MAIN.post(() -> listener.onUpdateError(securityException));
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
                if (listener != null) {
                    MAIN.post(() -> listener.onUpdateError(e));
                }
            }
        });
    }

    // ================== APP SETTINGS BELOW ============================

    // =========== FOR FAVORITE SONGS ===========

    public void renamePlaylist(long playlistId, String newName, OnMetadataUpdateListener listener) {
        executor.execute(() -> {
            ContentResolver resolver = context.getContentResolver();
            Uri collection = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
                    ? MediaStore.Audio.Playlists.getContentUri(MediaStore.VOLUME_EXTERNAL)
                    : MediaStore.Audio.Playlists.EXTERNAL_CONTENT_URI;

            try {
                // 1. Update MediaStore
                ContentValues values = new ContentValues();
                values.put(MediaStore.Audio.Playlists.NAME, newName);
                resolver.update(collection, values, MediaStore.Audio.Playlists._ID + "=?", new String[]{String.valueOf(playlistId)});

                // 2. Update local DB
                dao.updatePlaylistName(playlistId, newName);

                if (listener != null) {
                    MAIN.post(listener::onUpdateSuccess);
                }
            } catch (SecurityException securityException) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && securityException instanceof RecoverableSecurityException) {
                    RecoverableSecurityException recoverableSecurityException = (RecoverableSecurityException) securityException;
                    if (listener != null) {
                        MAIN.post(() -> listener.onPermissionRequired(recoverableSecurityException.getUserAction().getActionIntent()));
                    }
                } else {
                    if (listener != null) {
                        MAIN.post(() -> listener.onUpdateError(securityException));
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
                if (listener != null) {
                    MAIN.post(() -> listener.onUpdateError(e));
                }
            }
        });
    }

    public void deletePlaylist(Playlist playlist, OnMetadataUpdateListener listener) {
        executor.execute(() -> {
            ContentResolver resolver = context.getContentResolver();
            Uri collection = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
                    ? MediaStore.Audio.Playlists.getContentUri(MediaStore.VOLUME_EXTERNAL)
                    : MediaStore.Audio.Playlists.EXTERNAL_CONTENT_URI;

            try {
                // 1. Delete from MediaStore
                resolver.delete(collection, MediaStore.Audio.Playlists._ID + "=?", new String[]{String.valueOf(playlist.getPlaylistId())});

                // 2. Delete from local DB
                dao.deletePlaylist(playlist);

                if (listener != null) {
                    MAIN.post(listener::onUpdateSuccess);
                }
            } catch (SecurityException securityException) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && securityException instanceof RecoverableSecurityException) {
                    RecoverableSecurityException recoverableSecurityException = (RecoverableSecurityException) securityException;
                    if (listener != null) {
                        MAIN.post(() -> listener.onPermissionRequired(recoverableSecurityException.getUserAction().getActionIntent()));
                    }
                } else {
                    if (listener != null) {
                        MAIN.post(() -> listener.onUpdateError(securityException));
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
                if (listener != null) {
                    MAIN.post(() -> listener.onUpdateError(e));
                }
            }
        });
    }

    public void toggle(String mediaId, FavoriteType type, boolean isFavorite) {
        executor.execute(() -> {
            if (isFavorite) {
                dao.removeFromFavorite(mediaId, type);
            } else {
                dao.addToFavorite(new FavoriteEntity(mediaId, type));
            }
        });
    }

    public LiveData<Boolean> isFavoriteSync(String mediaId, FavoriteType type) {
        return dao.isFavorite(mediaId, type);
    }

    public List<String> getFavoriteSongIds() {
        return dao.getFavoriteIds(FavoriteType.SONG);
    }


    public static List<Album> extractAlbums(List<Song> songs) {
        Map<Long, Album> map = new HashMap<>();

        for (Song s : songs) {
            if (s.albumId <= 0) continue;

            Album album = map.get(s.albumId);
            if (album == null) {
                album = new Album(s.albumId, s.album, s.artist, s.albumArt, 1);
                map.put(s.albumId, album);
            } else {
                album.setSongCount(album.getSongCount() + 1);
            }
        }

        return new ArrayList<>(map.values());
    }

    public static List<Artist> extractArtists(List<Song> songs) {
        Map<Long, Artist> map = new HashMap<>();
        Map<Long, Set<Long>> artistAlbums = new HashMap<>();

        for (Song s : songs) {
            if (s.artistId <= 0) continue;

            Artist artist = map.get(s.artistId);
            if (artist == null) {
                artist = new Artist(s.artistId, s.artist, 0, 1, s.albumArt);
                map.put(s.artistId, artist);
            } else {
                artist.setTrackCount(artist.getTrackCount() + 1);
            }

            artistAlbums.computeIfAbsent(s.artistId, k -> new HashSet<>()).add(s.albumId);
        }

        // finalize album counts
        for (Artist artist : map.values()) {
            Set<Long> albums = artistAlbums.get(artist.getId());
            artist.setAlbumCount(albums == null ? 0 : albums.size());
        }

        return new ArrayList<>(map.values());
    }

    public static List<Folder> extractFolders(List<Song> songs) {
        Map<Long, Folder> map = new HashMap<>();

        for (Song s : songs) {
            if (s.folderId == 0 || s.folder == null) continue;

            Folder folder = map.get(s.folderId);
            if (folder == null) {
                String name = s.folder.lastIndexOf('/') >= 0
                        ? s.folder.substring(s.folder.lastIndexOf('/') + 1)
                        : s.folder;
                folder = new Folder(name, s.folder);
                folder.setId(s.folderId);
                folder.setSongCount(1);
                map.put(s.folderId, folder);
            } else {
                folder.setSongCount(folder.getSongCount() + 1);
            }
        }

        return new ArrayList<>(map.values());
    }

    public static List<Genre> extractGenres(List<Song> songs) {
        Map<Long, Genre> map = new HashMap<>();

        for (Song s : songs) {
            if (s.genreId <= 0) continue;

            map.putIfAbsent(s.genreId,
                    new Genre(s.genreId, s.getGenreName(), s.albumArt)
            );
        }

        return new ArrayList<>(map.values());
    }


    public interface OnMetadataUpdateListener {
        void onUpdateSuccess();
        void onPermissionRequired(PendingIntent pendingIntent);
        void onUpdateError(Exception e);
    }

    /**
     * Updates song metadata in both MediaStore and Room database.
     * This follows a "Dual-Write" strategy to ensure consistency.
     */
    public void updateSongMetadata(Song song, OnMetadataUpdateListener listener) {
        executor.execute(() -> {
            ContentResolver resolver = context.getContentResolver();
            ContentValues values = new ContentValues();
            values.put(MediaStore.Audio.Media.TITLE, song.getTitle());
            values.put(MediaStore.Audio.Media.ARTIST, song.getArtist());
            values.put(MediaStore.Audio.Media.ALBUM, song.getAlbum());

            try {
                // 1. Update MediaStore Core Metadata
                resolver.update(song.getUri(), values, null, null);

                // 2. Update Album Art if it was changed
                if (song.getAlbumArt() != null && !song.getAlbumArt().toString().startsWith("content://media/external/audio/albumart")) {
                    updateMediaStoreAlbumArt(song.albumId, song.getAlbumArt());
                }

                // 3. Update Genre in MediaStore (Complex operation)
                updateMediaStoreGenre(song.getId(), song.getGenreName());
                
                // 4. Update local Room database
                dao.updateSong(song);
                
                if (listener != null) {
                    MAIN.post(listener::onUpdateSuccess);
                }
            } catch (SecurityException securityException) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && securityException instanceof RecoverableSecurityException) {
                    RecoverableSecurityException recoverableSecurityException = (RecoverableSecurityException) securityException;
                    
                    // On Android 10+, if we fail to update the song, we must request permission for the specific URI.
                    // If we also tried to update album art, the security exception might be for the album art URI or the song URI.
                    // MediaStore.update usually handles the batch if we pass a list, but here we do them sequentially.

                    if (listener != null) {
                        MAIN.post(() -> listener.onPermissionRequired(recoverableSecurityException.getUserAction().getActionIntent()));
                    }
                } else {
                    if (listener != null) {
                        MAIN.post(() -> listener.onUpdateError(securityException));
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
                if (listener != null) {
                    MAIN.post(() -> listener.onUpdateError(e));
                }
            }
        });
    }

    /**
     * Helper to update Album Art in MediaStore.
     * Compresses the image to comply with standard album art sizes.
     */
    private void updateMediaStoreAlbumArt(long albumId, Uri imageUri) {
        try {
            ContentResolver resolver = context.getContentResolver();
            Uri albumArtUri = Uri.parse("content://media/external/audio/albumart/" + albumId);

            // 1. Load and compress bitmap
            Bitmap bitmap = null;
            try (InputStream is = resolver.openInputStream(imageUri)) {
                bitmap = BitmapFactory.decodeStream(is);
            }

            if (bitmap == null) return;

            // Resize if too large (standard is often 500x500 or 1000x1000)
            int maxSize = 1000;
            if (bitmap.getWidth() > maxSize || bitmap.getHeight() > maxSize) {
                float scale = (float) maxSize / Math.max(bitmap.getWidth(), bitmap.getHeight());
                Matrix matrix = new Matrix();
                matrix.postScale(scale, scale);
                bitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
            }

            // 2. Try direct write if possible (Legacy or granted permission)
            try (OutputStream os = resolver.openOutputStream(albumArtUri)) {
                if (os != null) {
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 85, os);
                }
            } catch (SecurityException e) {
                // On Android 10+, the exception will be caught in updateSongMetadata
                // which will then request permission for the song URI. 
                // Unfortunately, MediaStore doesn't easily allow requesting permission for the albumart URI directly via RecoverableSecurityException.
                // However, often granting permission for the song URI in the same session helps.
                throw e;
            }

            bitmap.recycle();
        } catch (Exception e) {
            if (e instanceof SecurityException) throw (SecurityException) e;
            throw new RuntimeException(e);
        }
    }

    /**
     * Helper to update Genre in MediaStore.
     */
    private void updateMediaStoreGenre(long songId, String genreName) {
        if (genreName == null || genreName.isEmpty()) return;
        ContentResolver resolver = context.getContentResolver();
        String volume = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
                ? MediaStore.VOLUME_EXTERNAL : "external";

        // 1. Find or Create Genre ID
        long targetGenreId = -1;
        Uri genreUri = MediaStore.Audio.Genres.getContentUri(volume);
        try (android.database.Cursor cursor = resolver.query(genreUri, new String[]{MediaStore.Audio.Genres._ID},
                MediaStore.Audio.Genres.NAME + "=?", new String[]{genreName}, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                targetGenreId = cursor.getLong(0);
            } else {
                ContentValues v = new ContentValues();
                v.put(MediaStore.Audio.Genres.NAME, genreName);
                Uri newGenre = resolver.insert(genreUri, v);
                if (newGenre != null) targetGenreId = Long.parseLong(newGenre.getLastPathSegment());
            }
        }

        if (targetGenreId == -1) return;

        // 2. Remove song from existing genres to avoid duplicates
        // We query all genres to find which ones contain this song
        try (android.database.Cursor cursor = resolver.query(genreUri, new String[]{MediaStore.Audio.Genres._ID}, null, null, null)) {
            if (cursor != null) {
                while (cursor.moveToNext()) {
                    long gId = cursor.getLong(0);
                    resolver.delete(MediaStore.Audio.Genres.Members.getContentUri(volume, gId),
                            MediaStore.Audio.Genres.Members.AUDIO_ID + "=?", new String[]{String.valueOf(songId)});
                }
            }
        }

        // 3. Add to the target genre
        ContentValues values = new ContentValues();
        values.put(MediaStore.Audio.Genres.Members.AUDIO_ID, songId);
        resolver.insert(MediaStore.Audio.Genres.Members.getContentUri(volume, targetGenreId), values);
    }
}
