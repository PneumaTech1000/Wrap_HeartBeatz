package com.giga.tech1000.media_player.models;

import android.os.Parcel;
import android.os.Parcelable;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;
import androidx.room.TypeConverters;

import com.giga.tech1000.media_player.utils.converters.UriConverter;

import java.io.Serializable;

@Entity(
        tableName = "songs",
        indices = {
                @Index(value = {"title"}),
                @Index(value = {"artistId"}),
                @Index(value = {"albumId"}),
                @Index(value = {"folder"}),
                @Index(value = {"genreId"}),
                @Index(value = {"folderId"}) // Added index for the foreign key
        }
)
public class Song implements Serializable, Parcelable {

    @PrimaryKey
    @ColumnInfo(name = "songId")
    public long id;

    @ColumnInfo(name = "genreId")
    public long genreId;

    @ColumnInfo(name = "artistId")
    public long artistId;

    @ColumnInfo(name = "albumId")
    public long albumId;

    @ColumnInfo(name = "folderId")
    public long folderId; // Foreign key to link to the Folder table's primary key

    public String title;
    public String displayName;
    public String artist;
    public String album;
    public long duration; // ms
    public long size;
    public String mimeType;

    @TypeConverters(UriConverter.class)
    public Uri uri;
    @TypeConverters(UriConverter.class)
    public Uri albumArt; // String to album art
    public String folder; // parent folder path
    public long dateAdded; // seconds since epoch
    public String data;
    public String genreName;

    @ColumnInfo(name = "is_removed_from_app")
    public boolean isRemovedFromApp;
    @ColumnInfo(name = "is_hidden")
    public boolean isHidden;

    public Song(long id, String title, String displayName, String artist, long artistId, String album, long albumId,
                long duration, String mimeType, Uri uri, String data, Uri albumArt,
                String folder, long folderId, long dateAdded, long size) { // Added folderId to constructor
        this.id = id;
        this.title = title;
        this.displayName = displayName;
        this.artist = artist;
        this.artistId = artistId;
        this.album = album;
        this.albumId = albumId;
        this.duration = duration;
        this.mimeType = mimeType;
        this.uri = uri;
        this.data = data;
        this.albumArt = albumArt;
        this.folder = folder;
        this.folderId = folderId; // Added assignment
        this.dateAdded = dateAdded;
        this.genreId = 0;
        this.size = size;
    }

    public Song() {
    }

    // --- GETTERS AND SETTERS ---
    // You will also need to add a getter and setter for folderId

    public long getFolderId() {
        return folderId;
    }

    public void setFolderId(long folderId) {
        this.folderId = folderId;
    }

    // (All of your other getters and setters remain here)

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }


    public String getArtist() {
        return artist;
    }

    public void setArtist(String artist) {
        this.artist = artist;
    }

    public long getArtistId() {
        return artistId;
    }

    public void setArtistId(long artistId) {
        this.artistId = artistId;
    }


    public String getAlbum() {
        return album;
    }

    public void setAlbum(String album) {
        this.album = album;
    }

    public long getAlbumId() {
        return albumId;
    }

    public void setAlbumId(long albumId) {
        this.albumId = albumId;
    }


    public long getDuration() {
        return duration;
    }

    public void setDuration(long duration) {
        this.duration = duration;
    }

    public String getMimeType() {
        return mimeType;
    }

    public void setMimeType(String mimeType) {
        this.mimeType = mimeType;
    }

    public Uri getUri() {
        return uri;
    }

    public void setUri(Uri uri) {
        this.uri = uri;
    }

    public String getData() {
        return data;
    }

    public void setData(String data) {
        this.data = data;
    }


    public Uri getAlbumArt() {
        return albumArt;
    }

    public void setAlbumArt(Uri albumArt) {
        this.albumArt = albumArt;
    }

    public String getFolder() {
        return folder;
    }

    public void setFolder(String folder) {
        this.folder = folder;
    }

    public long getDateAdded() {
        return dateAdded;
    }

    public void setDateAdded(long dateAdded) {
        this.dateAdded = dateAdded;
    }

    public long getGenreId() {
        return genreId;
    }

    public void setGenreId(long genreId) {
        this.genreId = genreId;
    }

    public String getGenreName() {
        return genreName;
    }

    public void setGenreName(String genreName) {
        this.genreName = genreName;
    }

    public long getSize() {
        return size;
    }

    public void setSize(long size) {
        this.size = size;
    }


    public boolean semanticEquals(@NonNull Song other) {
        if (this == other) return true;
        if (other == null) return false;

        return id == other.id
                && duration == other.duration
                && artistId == other.artistId
                && albumId == other.albumId
                && folderId == other.folderId
                && safeEquals(title, other.title)
                && safeEquals(displayName, other.displayName)
                && safeEquals(artist, other.artist)
                && safeEquals(album, other.album)
                && safeEquals(mimeType, other.mimeType)
                && safeEquals(data, other.data)
                && safeEqualsUri(uri, other.uri);
    }


    public static Song copy(Song other) {
        if (other == null) return null;
        Song s = new Song();
        s.id = other.id;
        s.genreId = other.genreId;
        s.artistId = other.artistId;
        s.albumId = other.albumId;
        s.folderId = other.folderId;
        s.title = other.title;
        s.displayName = other.displayName;
        s.artist = other.artist;
        s.album = other.album;
        s.duration = other.duration;
        s.size = other.size;
        s.mimeType = other.mimeType;
        s.uri = other.uri;
        s.albumArt = other.albumArt;
        s.folder = other.folder;
        s.dateAdded = other.dateAdded;
        s.data = other.data;
        s.genreName = other.genreName;
        s.isRemovedFromApp = other.isRemovedFromApp;
        s.isHidden = other.isHidden;
        return s;
    }


    private static boolean safeEquals(String a, String b) {
        if (a == null) return b == null;
        return a.equals(b);
    }

    private static boolean safeEqualsUri(Uri a, Uri b) {
        if (a == null) return b == null;
        return a.equals(b);
    }


    @NonNull
    @Override
    public String toString() {
        return "Song{" +
                "id=" + id +
                ", genreId=" + genreId +
                ", artistId=" + artistId +
                ", albumId=" + albumId +
                ", folderId=" + folderId +
                ", title='" + title + '\'' +
                ", displayName='" + displayName + '\'' +
                ", artist='" + artist + '\'' +
                ", album='" + album + '\'' +
                ", duration=" + duration +
                ", size=" + size +
                ", mimeType='" + mimeType + '\'' +
                ", uri=" + uri +
                ", albumArt=" + albumArt +
                ", folder='" + folder + '\'' +
                ", dateAdded=" + dateAdded +
                ", data='" + data + '\'' +
                ", genreName='" + genreName + '\'' +
                '}';
    }

    protected Song(Parcel in) {
        id = in.readLong();
        genreId = in.readLong();
        artistId = in.readLong();
        albumId = in.readLong();
        folderId = in.readLong();
        title = in.readString();
        displayName = in.readString();
        artist = in.readString();
        album = in.readString();
        duration = in.readLong();
        size = in.readLong();
        mimeType = in.readString();
        uri = in.readParcelable(Uri.class.getClassLoader());
        albumArt = in.readParcelable(Uri.class.getClassLoader());
        folder = in.readString();
        dateAdded = in.readLong();
        data = in.readString();
        genreName = in.readString();
        isRemovedFromApp = in.readByte() != 0;
        isHidden = in.readByte() != 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeLong(id);
        dest.writeLong(genreId);
        dest.writeLong(artistId);
        dest.writeLong(albumId);
        dest.writeLong(folderId);
        dest.writeString(title);
        dest.writeString(displayName);
        dest.writeString(artist);
        dest.writeString(album);
        dest.writeLong(duration);
        dest.writeLong(size);
        dest.writeString(mimeType);
        dest.writeParcelable(uri, flags);
        dest.writeParcelable(albumArt, flags);
        dest.writeString(folder);
        dest.writeLong(dateAdded);
        dest.writeString(data);
        dest.writeString(genreName);
        dest.writeByte((byte) (isRemovedFromApp ? 1 : 0));
        dest.writeByte((byte) (isHidden ? 1 : 0));
    }

    @Override
    public int describeContents() {
        return 0;
    }

    public static final Creator<Song> CREATOR = new Creator<Song>() {
        @Override
        public Song createFromParcel(Parcel in) {
            return new Song(in);
        }

        @Override
        public Song[] newArray(int size) {
            return new Song[size];
        }
    };
}

