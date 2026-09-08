package com.giga.tech1000.media_player.models.extended_models;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

import com.giga.tech1000.media_player.utils.enums.FavoriteType;

@Entity(
        tableName = "favorites",
        indices = {
                @Index(value = {"mediaId"}, unique = true),
                @Index(value = {"type"})
        }
)
public class FavoriteEntity {

    @PrimaryKey(autoGenerate = true)
    public long id;

    /** MediaStore ID or your own internal mediaId */
    @NonNull
    public String mediaId;

    /** SONG, ALBUM, ARTIST, PLAYLIST (future-proof) */
    @NonNull
    public FavoriteType type;

    /** When user added it */
    public long addedAt;

    /** Optional user note / tag */
    @Nullable
    public String label;

    public FavoriteEntity(
            @NonNull String mediaId,
            @NonNull FavoriteType type
    ) {
        this.mediaId = mediaId;
        this.type = type;
        this.addedAt = System.currentTimeMillis();
    }
}
