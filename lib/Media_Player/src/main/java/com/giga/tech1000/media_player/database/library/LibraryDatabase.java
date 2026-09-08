package com.giga.tech1000.media_player.database.library;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.Transaction;
import androidx.room.TypeConverters;

import com.giga.tech1000.media_player.database.LibraryDao;
import com.giga.tech1000.media_player.models.Album;
import com.giga.tech1000.media_player.models.Artist;
import com.giga.tech1000.media_player.models.Folder;
import com.giga.tech1000.media_player.models.Genre;
import com.giga.tech1000.media_player.models.Playlist;
import com.giga.tech1000.media_player.models.Song;
import com.giga.tech1000.media_player.models.cross_ref.PlaylistSongCrossRef;
import com.giga.tech1000.media_player.models.extended_models.FavoriteEntity;
import com.giga.tech1000.media_player.utils.converters.UriConverter;

import java.util.List;

@Database(
        entities = {
                Song.class,
                Album.class,
                Artist.class,
                Folder.class,
                Genre.class,
                Playlist.class,
                PlaylistSongCrossRef.class,
                FavoriteEntity.class
        },
        version = 1,
        exportSchema = true
)
@TypeConverters({UriConverter.class})
public abstract class LibraryDatabase extends RoomDatabase {

    private static volatile LibraryDatabase INSTANCE;

    public abstract LibraryDao libraryDao();

    @Transaction
    public void applySongDiff(
            List<Song> toInsert,
            List<Song> toUpdate,
            List<Long> toDelete
    ) {
        if (!toInsert.isEmpty()) {
            libraryDao().insertSongs(toInsert);
        }

        if (!toUpdate.isEmpty()) {
            libraryDao().updateSongs(toUpdate);
        }

        if (!toDelete.isEmpty()) {
            libraryDao().deleteSongsByIds(toDelete);
            for (long id : toDelete) libraryDao().removeSongFromAllPlaylists(id);
        }
    }


    // ================= INSTANCE =================

    public static LibraryDatabase getInstance(Context context) {
        if (INSTANCE == null) {
            synchronized (LibraryDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(
                            context.getApplicationContext(),
                            LibraryDatabase.class,
                            "library.db"
                    ).build();
                }
            }
        }
        return INSTANCE;
    }
}


