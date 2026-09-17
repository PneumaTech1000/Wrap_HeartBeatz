package com.giga.tech1000.media_player.scanners;

import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Context;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.ImageDecoder;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.text.TextUtils;
import android.util.Log;
import android.util.Size;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.giga.tech1000.media_player.models.Album;
import com.giga.tech1000.media_player.models.Artist;
import com.giga.tech1000.media_player.models.Folder;
import com.giga.tech1000.media_player.models.Genre;
import com.giga.tech1000.media_player.models.Playlist;
import com.giga.tech1000.media_player.models.Song;
import com.giga.tech1000.media_player.repository.SongRepository;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Robust, production-grade MediaStore scanner.
 * Centralizes all media scanning operations into a single class to reduce redundant queries.
 */
public class LibraryScanner {

    private final Context context;
    private final ContentResolver resolver;

    private static final String UNKNOWN = "<Unknown>";
    private static final Uri ALBUM_ART_URI = Uri.parse("content://media/external/audio/albumart");

    public LibraryScanner(Context context) {
        this.context = context.getApplicationContext();
        this.resolver = this.context.getContentResolver();
    }

    @NonNull
    public static List<Folder> groupByFolder(List<Song> songs) {
        Map<String, Folder> folderMap = new HashMap<>();

        for (Song song : songs) {
            String data = song.getData(); // full file path of the song
            if (TextUtils.isEmpty(data)) continue;

            // get folder path
            int idx = data.lastIndexOf('/');
            if (idx <= 0) continue;
            String folderPath = data.substring(0, idx);

            // get folder name
            String folderName = folderPath.substring(folderPath.lastIndexOf('/') + 1);

            // add or update folder
            if (folderMap.containsKey(folderPath)) {
                Folder folder = folderMap.get(folderPath);
                if (folder == null) continue;
                folder.setSongCount(folder.getSongCount() + 1);
            } else {
                Folder folder = new Folder(folderName, folderPath);
                folderMap.put(folderPath, folder);
            }
        }

        // convert map to list
        return new ArrayList<>(folderMap.values());
    }

    public TreeMap<Integer, Song> getTreeMapOfSongs(List<Song> songs) {
        TreeMap<Integer, Song> results = new TreeMap<>();
        if (songs == null) return results;

        for (Song song : songs) {
            results.put((int) song.getId(), song);
        }

        return results;
    }

    /**
     * Scans all songs from MediaStore.
     * This is the "Primary" scan that feeds the incremental update system.
     */
    @NonNull
    public List<Song> scanSongs() {
        return scanSongs(-1, 0);
    }

    /**
     * Scans songs from MediaStore with optional incremental filters.
     *
     * @param lastGeneration The last known MediaStore generation (API 30+).
     * @param lastScanTime   The last known scan timestamp (seconds).
     */
    @NonNull
    public List<Song> scanSongs(long lastGeneration, long lastScanTime) {
        List<Song> list = new ArrayList<>();
        Uri collection = getAudioCollection();

        String[] projection = {
                MediaStore.Audio.Media._ID,
                MediaStore.Audio.Media.TITLE,
                MediaStore.Audio.Media.ARTIST,
                MediaStore.Audio.Media.ARTIST_ID,
                MediaStore.Audio.Media.ALBUM,
                MediaStore.Audio.Media.ALBUM_ID,
                MediaStore.Audio.Media.DURATION,
                MediaStore.Audio.Media.MIME_TYPE,
                MediaStore.Audio.Media.DATA,
                MediaStore.Audio.Media.DATE_ADDED,
                MediaStore.Audio.Media.DATE_MODIFIED,
                MediaStore.Audio.Media.DISPLAY_NAME,
                MediaStore.Audio.Media.SIZE
        };

        StringBuilder selectionBuilder = new StringBuilder();
        selectionBuilder.append(MediaStore.Audio.Media.IS_MUSIC).append(" != 0");

        // Production-grade filtering: Exclude pending and trashed items to avoid "ghost" entries
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            selectionBuilder.append(" AND ").append(MediaStore.Audio.Media.IS_PENDING).append(" = 0");
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            selectionBuilder.append(" AND is_trashed = 0");
        }

        if (lastScanTime > 0) {
            selectionBuilder.append(" AND ").append(MediaStore.Audio.Media.DATE_MODIFIED).append(" > ").append(lastScanTime);
        }

        Bundle queryArgs = null;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && lastGeneration >= 0) {
            queryArgs = new Bundle();
            queryArgs.putString(ContentResolver.QUERY_ARG_SQL_SELECTION, selectionBuilder.toString());
            queryArgs.putString(ContentResolver.QUERY_ARG_SQL_SORT_ORDER, MediaStore.Audio.Media.DATE_ADDED + " DESC");
            queryArgs.putLong("android.provider.extra.GENERATION", lastGeneration);
        }

        String selection = queryArgs == null ? selectionBuilder.toString() : null;
        String sortOrder = queryArgs == null ? MediaStore.Audio.Media.DATE_ADDED + " DESC" : null;

        try (Cursor cursor = resolver.query(collection, projection, queryArgs, null)) {
            
            if (cursor == null) return list;
            
            int idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID);
            int albumIdCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID);
            int titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE);
            int artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST);
            int artistIdCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST_ID);
            int albumCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM);
            int durCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION);
            int mimeCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.MIME_TYPE);
            int dataCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA);
            int dateCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED);
            int displayCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME);
            int sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE);

            while (cursor.moveToNext()) {
                long id = cursor.getLong(idCol);
                String title = safe(cursor.getString(titleCol), UNKNOWN);
                String artist = safe(cursor.getString(artistCol), UNKNOWN);
                long artistId = cursor.getLong(artistIdCol);
                String album = safe(cursor.getString(albumCol), UNKNOWN);
                long duration = cursor.getLong(durCol);
                String mime = safe(cursor.getString(mimeCol), "audio/*");
                String data = cursor.getString(dataCol);
                String displayName = safe(cursor.getString(displayCol), title);
                long dateAdded = cursor.getLong(dateCol);
                long albumId = cursor.getLong(albumIdCol);
                long size = cursor.getLong(sizeCol);

                Uri albumArtUri = getAlbumArtUri(albumId);
                Uri uri = ContentUris.withAppendedId(collection, id);

                String folder = extractFolderName(data);
                long folderId = folder.hashCode();

                Song s = new Song(id, title, displayName, artist, artistId, album, albumId, 
                        duration, mime, uri, data, albumArtUri, folder, folderId, dateAdded, size);
                list.add(s);
            }
        }
        
        return list;
    }

    /**
     * Scans Albums directly from MediaStore's Album table.
     */
    @NonNull
    public List<Album> scanAlbums() {
        List<Album> albums = new ArrayList<>();
        Uri collection = MediaStore.Audio.Albums.EXTERNAL_CONTENT_URI;

        String[] projection = {
                MediaStore.Audio.Albums._ID,
                MediaStore.Audio.Albums.ALBUM,
                MediaStore.Audio.Albums.ARTIST,
                MediaStore.Audio.Albums.NUMBER_OF_SONGS
        };

        try (Cursor cursor = resolver.query(collection, projection, null, null, MediaStore.Audio.Albums.ALBUM + " ASC")) {
            if (cursor == null) return albums;
            
            int idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Albums._ID);
            int nameCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Albums.ALBUM);
            int artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Albums.ARTIST);
            int songCountCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Albums.NUMBER_OF_SONGS);

            while (cursor.moveToNext()) {
                long id = cursor.getLong(idCol);
                String name = safe(cursor.getString(nameCol), UNKNOWN);
                String artist = safe(cursor.getString(artistCol), UNKNOWN);
                int songCount = cursor.getInt(songCountCol);

                albums.add(new Album(id, name, artist, getAlbumArtUri(id), songCount));
            }
        }
        return albums;
    }

    /**
     * Scans Artists directly from MediaStore's Artist table.
     */
    @NonNull
    public List<Artist> scanArtists() {
        List<Artist> artists = new ArrayList<>();
        Uri collection = MediaStore.Audio.Artists.EXTERNAL_CONTENT_URI;

        String[] projection = {
                MediaStore.Audio.Artists._ID,
                MediaStore.Audio.Artists.ARTIST,
                MediaStore.Audio.Artists.NUMBER_OF_ALBUMS,
                MediaStore.Audio.Artists.NUMBER_OF_TRACKS
        };

        try (Cursor cursor = resolver.query(collection, projection, null, null, MediaStore.Audio.Artists.ARTIST + " ASC")) {
            if (cursor == null) return artists;

            int idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Artists._ID);
            int nameCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Artists.ARTIST);
            int albumCountCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Artists.NUMBER_OF_ALBUMS);
            int trackCountCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Artists.NUMBER_OF_TRACKS);

            while (cursor.moveToNext()) {
                long id = cursor.getLong(idCol);
                String name = safe(cursor.getString(nameCol), UNKNOWN);
                int albumCount = cursor.getInt(albumCountCol);
                int trackCount = cursor.getInt(trackCountCol);

                artists.add(new Artist(id, name, albumCount, trackCount, getArtistArtUri(id)));
            }
        }
        return artists;
    }

    /**
     * Scans Genres directly from MediaStore's Genre table.
     */
    @NonNull
    public List<Genre> scanGenres() {
        List<Genre> genres = new ArrayList<>();
        Uri collection = MediaStore.Audio.Genres.EXTERNAL_CONTENT_URI;

        String[] projection = {
                MediaStore.Audio.Genres._ID,
                MediaStore.Audio.Genres.NAME
        };

        try (Cursor cursor = resolver.query(collection, projection, null, null, MediaStore.Audio.Genres.NAME + " ASC")) {
            if (cursor == null) return genres;

            int idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Genres._ID);
            int nameCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Genres.NAME);

            while (cursor.moveToNext()) {
                long id = cursor.getLong(idCol);
                String name = safe(cursor.getString(nameCol), UNKNOWN);
                genres.add(new Genre(id, name, getGenreArtUri(id)));
            }
        }
        return genres;
    }

    /**
     * Scans Playlists directly from MediaStore's Playlist table.
     */
    @NonNull
    public List<Playlist> scanPlaylists() {
        List<Playlist> playlists = new ArrayList<>();
        Uri collection = MediaStore.Audio.Playlists.EXTERNAL_CONTENT_URI;

        String[] projection = {
                MediaStore.Audio.Playlists._ID,
                MediaStore.Audio.Playlists.NAME,
                MediaStore.Audio.Playlists.DATE_ADDED
        };

        try (Cursor cursor = resolver.query(collection, projection, null, null, MediaStore.Audio.Playlists.NAME + " ASC")) {
            if (cursor == null) return playlists;
            
            int idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Playlists._ID);
            int nameCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Playlists.NAME);
            int dateCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Playlists.DATE_ADDED);

            while (cursor.moveToNext()) {
                long id = cursor.getLong(idCol);
                String name = safe(cursor.getString(nameCol), UNKNOWN);
                long dateAdded = cursor.getLong(dateCol);
                playlists.add(new Playlist(id, name, dateAdded));
            }
        }
        return playlists;
    }

    /**
     * Scans all songs belonging to all playlists.
     * Returns a map of PlaylistID -> List of SongIDs.
     */
    @NonNull
    public Map<Long, List<Long>> scanPlaylistMembers() {
        Map<Long, List<Long>> playlistMembers = new HashMap<>();
        List<Playlist> playlists = scanPlaylists();

        String volume = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
                ? MediaStore.VOLUME_EXTERNAL : "external";

        for (Playlist playlist : playlists) {
            long playlistId = playlist.getPlaylistId();
            List<Long> songIds = new ArrayList<>();
            Uri membersUri = MediaStore.Audio.Playlists.Members.getContentUri(volume, playlistId);
            String[] projection = { MediaStore.Audio.Playlists.Members.AUDIO_ID };

            try (Cursor cursor = resolver.query(membersUri, projection, null, null, MediaStore.Audio.Playlists.Members.PLAY_ORDER + " ASC")) {
                if (cursor != null) {
                    int audioIdCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Playlists.Members.AUDIO_ID);
                    while (cursor.moveToNext()) {
                        songIds.add(cursor.getLong(audioIdCol));
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            playlistMembers.put(playlistId, songIds);
        }
        return playlistMembers;
    }
    @NonNull
    public Map<Long, Long> resolveGenreMembers() {
        Map<Long, Long> songIdToGenreId = new HashMap<>();
        List<Genre> genres = scanGenres();

        String volume = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q 
                ? MediaStore.VOLUME_EXTERNAL : "external";

        for (Genre genre : genres) {
            long genreId = genre.getId();
            Uri membersUri = MediaStore.Audio.Genres.Members.getContentUri(volume, genreId);
            String[] projection = { MediaStore.Audio.Media._ID };

            try (Cursor cursor = resolver.query(membersUri, projection, null, null, null)) {
                if (cursor != null) {
                    while (cursor.moveToNext()) {
                        long songId = cursor.getLong(0);
                        songIdToGenreId.put(songId, genreId);
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return songIdToGenreId;
    }

    /**
     * Returns a map of GenreID -> Genre object.
     */
    @NonNull
    public Map<Long, Genre> resolveGenreMap() {
        Map<Long, Genre> map = new HashMap<>();
        for (Genre genre : scanGenres()) {
            map.put(genre.getId(), genre);
        }
        return map;
    }

    /**
     * Scans ONLY the IDs of all valid songs in MediaStore.
     * Used for efficient deletion detection.
     */
    @NonNull
    public Set<Long> scanSongIds() {
        Set<Long> ids = new java.util.HashSet<>();
        Uri collection = getAudioCollection();
        String[] projection = { MediaStore.Audio.Media._ID };

        StringBuilder selectionBuilder = new StringBuilder();
        selectionBuilder.append(MediaStore.Audio.Media.IS_MUSIC).append(" != 0");

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            selectionBuilder.append(" AND ").append(MediaStore.Audio.Media.IS_PENDING).append(" = 0");
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            selectionBuilder.append(" AND is_trashed = 0");
        }

        try (Cursor cursor = resolver.query(collection, projection, selectionBuilder.toString(), null, null)) {
            if (cursor != null) {
                int idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID);
                while (cursor.moveToNext()) {
                    ids.add(cursor.getLong(idCol));
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return ids;
    }

    private Uri getAudioCollection() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            return MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL);
        } else {
            return MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
        }
    }

    private Uri getAlbumArtUri(long albumId) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            return ContentUris.withAppendedId(MediaStore.Audio.Albums.EXTERNAL_CONTENT_URI, albumId);
        }
        return ContentUris.withAppendedId(ALBUM_ART_URI, albumId);
    }

    private Uri getArtistArtUri(long artistId) {
        String volume = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q 
                ? MediaStore.VOLUME_EXTERNAL : "external";
        Uri uri = MediaStore.Audio.Artists.Albums.getContentUri(volume, artistId);
        try (Cursor cursor = resolver.query(uri, new String[]{MediaStore.Audio.Albums._ID}, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                return getAlbumArtUri(cursor.getLong(0));
            }
        }
        return null;
    }

    private Uri getGenreArtUri(long genreId) {
        String volume = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q 
                ? MediaStore.VOLUME_EXTERNAL : "external";
        Uri uri = MediaStore.Audio.Genres.Members.getContentUri(volume, genreId);
        try (Cursor cursor = resolver.query(uri, new String[]{MediaStore.Audio.Media.ALBUM_ID}, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                return getAlbumArtUri(cursor.getLong(0));
            }
        }
        return null;
    }

    private String extractFolderName(String dataPath) {
        if (TextUtils.isEmpty(dataPath)) return UNKNOWN;
        int idx = dataPath.lastIndexOf('/');
        if (idx > 0) return dataPath.substring(0, idx);
        return UNKNOWN;
    }

    private String safe(String value, String fallback) {
        if (value == null) return fallback;
        value = value.trim();
        if (value.isEmpty() || value.equalsIgnoreCase("<unknown>")) {
            return fallback;
        }
        return value;
    }

    @Nullable
    public Bitmap getAlbumArtBitmap(Uri artUri) {
        if (artUri == null) return null;
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                try {
                    return resolver.loadThumbnail(artUri, new Size(500, 500), null);
                } catch (Exception e) {
                    // Fallback to other methods if loadThumbnail fails
                }
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                return ImageDecoder.decodeBitmap(ImageDecoder.createSource(resolver, artUri));
            } else {
                return MediaStore.Images.Media.getBitmap(resolver, artUri);
            }
        } catch (Exception e) {
            return null;
        }
    }
}
