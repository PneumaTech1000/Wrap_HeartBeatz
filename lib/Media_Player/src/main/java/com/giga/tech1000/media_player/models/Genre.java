package com.giga.tech1000.media_player.models;

import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;
import androidx.room.TypeConverters;

import com.giga.tech1000.media_player.utils.converters.UriConverter;

@Entity(
        tableName = "genres",
        indices = {@Index(value = "name", unique = true)}
)
public class Genre {

    @PrimaryKey
    @ColumnInfo(name = "genreId")
    private long id;

    private String name;

    @TypeConverters(UriConverter.class)
    private Uri artUri;

    public Genre(long id, String name, Uri uri) {
        this.id = id;
        this.name = name;
        this.artUri = uri;
    }

    public Genre() {}

    public long getId() { return id; }
    public String getName() { return name; }

    public void setId(long id) { this.id = id; }
    public void setName(String name) { this.name = name; }

    public Uri getArtUri() { return artUri; }
    public void setArtUri(Uri uri) { this.artUri = uri; }



    @NonNull
    @Override
    public String toString() {
        return "Genre{" +
                "id=" + id +
                ", name='" + name + '\'' +
                '}';
    }
}

