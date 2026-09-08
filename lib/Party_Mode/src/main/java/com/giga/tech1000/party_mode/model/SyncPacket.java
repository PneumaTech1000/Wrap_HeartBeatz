package com.giga.tech1000.party_mode.model;

import android.os.Parcel;
import android.os.Parcelable;

import java.io.Serializable;

/**
 * Data structure for authoritative synchronization heartbeats.
 */
public class SyncPacket implements Serializable, Parcelable {
    private static final long serialVersionUID = 1L;
    public String state; // PLAYING, PAUSED
    public long positionMs;
    public long durationMs;
    public float playbackSpeed;
    public String mediaId;
    public String title;
    public String artist;
    public String album;
    public long sentAt; // SystemClock.elapsedRealtime()

    public SyncPacket() {}

    protected SyncPacket(Parcel in) {
        state = in.readString();
        positionMs = in.readLong();
        durationMs = in.readLong();
        playbackSpeed = in.readFloat();
        mediaId = in.readString();
        title = in.readString();
        artist = in.readString();
        album = in.readString();
        sentAt = in.readLong();
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(state);
        dest.writeLong(positionMs);
        dest.writeLong(durationMs);
        dest.writeFloat(playbackSpeed);
        dest.writeString(mediaId);
        dest.writeString(title);
        dest.writeString(artist);
        dest.writeString(album);
        dest.writeLong(sentAt);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    public static final Creator<SyncPacket> CREATOR = new Creator<SyncPacket>() {
        @Override
        public SyncPacket createFromParcel(Parcel in) {
            return new SyncPacket(in);
        }

        @Override
        public SyncPacket[] newArray(int size) {
            return new SyncPacket[size];
        }
    };
}
