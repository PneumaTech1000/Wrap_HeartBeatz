package com.giga.tech1000.media_player.models.cross_ref;

import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;

import com.giga.tech1000.media_player.models.Playlist;
import com.giga.tech1000.media_player.models.Song;

@Entity(
        tableName = "playlist_songs",
        primaryKeys = { "playlistId", "songId" },
        foreignKeys = {
                @ForeignKey(
                        entity = Playlist.class,
                        parentColumns = "playlistId",
                        childColumns = "playlistId",
                        onDelete = ForeignKey.CASCADE
                ),
                @ForeignKey(
                        entity = Song.class,
                        parentColumns = "songId",
                        childColumns = "songId",
                        onDelete = ForeignKey.CASCADE
                )
        },
        indices = {
                @Index("playlistId"),
                @Index("songId")
        }
)
public class PlaylistSongCrossRef {
    public long playlistId;
    public long songId;

    public PlaylistSongCrossRef(long playlistId, long songId) {
        this.playlistId = playlistId;
        this.songId = songId;
    }

    public long getPlaylistId() {
        return playlistId;
    }

    public void setPlaylistId(long playlistId) {
        this.playlistId = playlistId;
    }
}

