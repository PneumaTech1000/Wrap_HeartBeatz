package com.giga.tech1000.media_player.database;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Transaction;
import androidx.room.Update;

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
import com.giga.tech1000.media_player.models.extended_models.FavoriteEntity;
import com.giga.tech1000.media_player.utils.enums.FavoriteType;

import java.util.List;
import java.util.Set;

@Dao
public interface LibraryDao {

    /* -------------------- SONGS -------------------- */

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    void insertSongs(List<Song> songs);

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    void insertSong(Song song);

    @Update
    void updateSong(Song song);

    @Update
    void updateSongs(List<Song> songs);

    @Transaction
    default void replaceSongs(List<Song> songs) {
        insertSongs(songs);
    }

    @Query("SELECT * FROM songs WHERE songId IN (:songIds)")
    List<Song> getSongsByIds(Set<Long> songIds);



    @Query("SELECT * FROM songs " +
            "WHERE is_removed_from_app = 0 " +
            "AND is_hidden = 0 " +
            "ORDER BY dateAdded DESC")
    LiveData<List<Song>> getAllSongs();

    @Query("SELECT * FROM songs ORDER BY dateAdded DESC")
    List<Song> getAllSongsSync();


    @Query("DELETE FROM songs WHERE songId IN (:songIds)")
    void deleteSongsByIds(List<Long> songIds);

    @Query("DELETE FROM songs WHERE songId = :songId")
    void deleteSongsById(long songId);

    @Query("UPDATE songs SET is_removed_from_app = 1 WHERE songId = :songId")
    void tempDeleteSongById(long songId);

    @Query("UPDATE songs SET is_removed_from_app = 0 WHERE songId = :songId")
    void restoreDeletedSongById(long songId);

    @Query("UPDATE songs SET is_hidden = 1 WHERE songId = :songId")
    void hideSongById(long songId);

    @Query("UPDATE songs SET is_hidden = 0 WHERE songId = :songId")
    void restoreHiddenSongById(long songId);


    @Query("SELECT songId FROM songs")
    List<Long> getAllSongIds();


    @Query("DELETE FROM songs")
    void deleteSongs();


    /* -------------------- ALBUMS -------------------- */

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertAlbums(List<Album> albums);

    @Query("SELECT * FROM albums ORDER BY name ASC")
    LiveData<List<Album>> getAllAlbums();

    @Query("DELETE FROM albums WHERE albumId NOT IN (SELECT DISTINCT albumId FROM songs)")
    void deleteEmptyAlbums();

    @Transaction
    @Query("SELECT * FROM albums WHERE albumId = :albumId")
    LiveData<AlbumWithSongs> getAlbumWithSongs(long albumId);

    @Query("""
                UPDATE albums
                SET songCount = (
                    SELECT COUNT(*)
                    FROM songs
                    WHERE songs.albumId = albums.albumId
                )
            """)
    void recalculateAlbumSongCounts();


    /* -------------------- GENRES -------------------- */

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertGenres(List<Genre> genres);

    @Query("SELECT * FROM genres ORDER BY name ASC")
    LiveData<List<Genre>> getAllGenres();

    @Transaction
    @Query("SELECT * FROM genres WHERE genreId = :genreId")
    LiveData<GenreWithSongs> getGenreWithSongs(long genreId);

    // Genre songs update
    @Query("""
                UPDATE songs
                SET genreId = :genreId,
                    genreName = :genreName
                WHERE songId = :songId
            """)
    void updateSongGenre(long songId, long genreId, String genreName);

    @Query("DELETE FROM genres WHERE genreId NOT IN (SELECT DISTINCT genreId FROM songs)")
    void deleteEmptyGenres();

    @Query("UPDATE genres SET name = name")
    void touchGenres();



    @Query("""
                UPDATE songs
                SET genreId = :genreId,
                    genreName = :genreName
                WHERE songId IN (:songIds)
            """)
    void updateSongsGenre(List<Long> songIds, long genreId, String genreName);

    // This is a song count for multiple genres, a list display
    @Query("""
            SELECT g.*, COUNT(s.songId) AS songCount
            FROM genres g
            LEFT JOIN songs s ON s.genreId = g.genreId
            GROUP BY g.genreId
            ORDER BY g.name COLLATE NOCASE
            """)
    LiveData<List<GenreWithCount>> observeGenresWithCount();

    // This is count per genre
    @Query("SELECT COUNT(*) FROM songs WHERE genreId = :genreId")
    int getGenreSongCount(long genreId);



    /* -------------------- ARTISTS -------------------- */

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertArtists(List<Artist> artists);

    @Query("SELECT * FROM artists ORDER BY name ASC")
    LiveData<List<Artist>> getAllArtists();

    @Query("DELETE FROM artists WHERE artistId NOT IN (SELECT DISTINCT artistId FROM songs)")
    void deleteEmptyArtists();

    @Transaction
    @Query("SELECT * FROM artists WHERE artistId = :artistId")
    LiveData<ArtistWithSongs> getArtistWithSongs(long artistId);

    @Query("""
                UPDATE artists
                SET trackCount = (
                    SELECT COUNT(*)
                    FROM songs
                    WHERE songs.artistId = artists.artistId
                )
            """)
    void recalculateArtistSongCounts();



    /* -------------------- FOLDERS -------------------- */

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertFolders(List<Folder> folders);

    @Query("SELECT * FROM folders ORDER BY name ASC")
    LiveData<List<Folder>> getAllFolders();

    @Query("DELETE FROM folders WHERE path NOT IN (SELECT DISTINCT folder FROM songs)")
    void deleteEmptyFolders();

    @Transaction
    @Query("SELECT * FROM folders WHERE path = :folder")
    LiveData<FolderWithSongs> getSongsByFolder(String folder);

    @Query("""
                UPDATE folders
                SET songCount = (
                    SELECT COUNT(*)
                    FROM songs
                    WHERE songs.folder = folders.path
                      AND is_removed_from_app = 0
                      AND is_hidden = 0
                )
            """)
    void recalculateFolderSongCounts();



    /* -------------------- PLAYLISTS -------------------- */

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertPlaylist(Playlist playlist);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertPlaylists(List<Playlist> playlists);

    @Update
    void updatePlaylist(Playlist playlist);

    @Query("UPDATE playlists SET name = :name WHERE playlistId = :id")
    void updatePlaylistName(long id, String name);

    @Query("DELETE FROM playlists WHERE playlistId NOT IN (:ids) AND playlistId > 0")
    void deletePlaylistsExcept(List<Long> ids);

    @Query("DELETE FROM playlist_songs WHERE playlistId = :playlistId")
    void clearPlaylistMembers(long playlistId);

    @Transaction
    default void syncPlaylistMembers(long playlistId, List<PlaylistSongCrossRef> refs) {
        clearPlaylistMembers(playlistId);
        addSongsToPlaylist(refs);
    }

    @Query("SELECT * FROM playlists ORDER BY dateAdded DESC")
    LiveData<List<Playlist>> getAllPlaylists();

    @Delete
    void deletePlaylist(Playlist playlist);


    /* -------- PLAYLIST SONG MAPPING -------- */

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    void addSongsToPlaylist(List<PlaylistSongCrossRef> refs);

    @Transaction
    @Query("SELECT * FROM playlists WHERE playlistId = :playlistId")
    LiveData<PlaylistWithSongs> getPlaylistWithSongs(long playlistId);

    @Query("""
                DELETE FROM playlist_songs
                WHERE playlistId = :playlistId
                  AND songId IN (:songIds)
            """)
    void removeSongsFromPlaylist(long playlistId, List<Long> songIds);

    @Query("""
            SELECT p.*, COUNT(ps.songId) AS numOfSongs
            FROM playlists p
            LEFT JOIN playlist_songs ps ON ps.playlistId = p.playlistId
            GROUP BY p.playlistId
            ORDER BY p.dateAdded DESC
            """)
    LiveData<List<PlaylistWithCount>> observePlaylistsWithCount();

    @Query("DELETE FROM playlist_songs WHERE songId = :songId")
    void removeSongFromAllPlaylists(long songId);



    // =============== BELOW ARE THE SETTINGS FOR THE APP, MIGHT BE MIGRATED LATER ====================

    // ========== FAVORITE ITEMS =================

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    void addToFavorite(FavoriteEntity favorite);

    @Delete
    void remove(FavoriteEntity favorite);

    @Query("DELETE FROM favorites WHERE mediaId = :mediaId AND type = :type")
    void removeFromFavorite(String mediaId, FavoriteType type);

    @Query("SELECT EXISTS(SELECT 1 FROM favorites WHERE mediaId = :mediaId AND type = :type)")
    LiveData<Boolean> isFavorite(String mediaId, FavoriteType type);

    @Query("SELECT * FROM favorites WHERE type = :type ORDER BY addedAt DESC")
    List<FavoriteEntity> getFavorites(FavoriteType type);

    @Query("SELECT mediaId FROM favorites WHERE type = :type")
    List<String> getFavoriteIds(FavoriteType type);

}

