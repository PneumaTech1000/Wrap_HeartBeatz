package com.giga.tech1000.party_mode.model;

import android.os.Parcel;
import android.os.Parcelable;
import android.text.TextUtils;

import java.io.Serial;
import java.io.Serializable;

/**
 * Represents a discovered Party Host.
 */
public class PartyHost implements Serializable, Parcelable {
    @Serial
    private static final long serialVersionUID = 1L;
    public String partyId;
    public String partyName;
    public String hostName;
    public String ipAddress;
    public int port;
    public String pin;
    public boolean isPasswordProtected;
    public String streamUri;

    public String ownerId;
    public long timestamp;


    public PartyHost() {}

    public PartyHost(String partyId, String partyName, String hostName, String ipAddress, String pin, int port, String ownerId, long timestamp) {
        this.partyId = partyId;
        this.partyName = partyName;
        this.hostName = hostName;
        this.ipAddress = ipAddress;
        this.pin = pin;
        this.port = port;
        this.ownerId = ownerId;
        this.timestamp = timestamp;
        this.streamUri = "http://" + ipAddress + ":" + port;
    }

    public void setPartyId(String partyId) {
        this.partyId = partyId;
    }

    public void setPartyName(String partyName) {
        this.partyName = partyName;
    }

    public void setHostName(String hostName) {
        this.hostName = hostName;
    }

    public void setIpAddress(String ipAddress) {
        this.ipAddress = ipAddress;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public void setPin(String pin) {
        this.pin = pin;
    }

    public void setPasswordProtected(boolean passwordProtected) {
        isPasswordProtected = passwordProtected;
    }

    public void setStreamUri(String streamUri) {
        this.streamUri = streamUri;
    }

    public String getOwnerId() {
        return ownerId;
    }

    public void setOwnerId(String ownerId) {
        this.ownerId = ownerId;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    public String getPartyId() {
        return partyId;
    }

    public String getPartyName() {
        return partyName;
    }

    public String getHostName() {
        return hostName;
    }

    public String getIpAddress() {
        return ipAddress;
    }

    public int getPort() {
        return port;
    }

    public String getPin() {
        return pin;
    }

    public boolean isPasswordProtected() {
        return isPasswordProtected;
    }

    public String getStreamUri() {
        return streamUri;
    }

    /**
     * Validates the provided PIN against the stored PIN for this party.
     *
     * @param pin The PIN to validate
     * @return true if the PIN is valid or if no PIN is required, false otherwise
     */
    public boolean validatePin(String pin) {
        if (!isPasswordProtected()) {
            return true; // No PIN required
        }
        return TextUtils.equals(this.pin, pin);
    }

    protected PartyHost(Parcel in) {
        partyId = in.readString();
        partyName = in.readString();
        hostName = in.readString();
        ipAddress = in.readString();
        port = in.readInt();
        pin = in.readString();
        isPasswordProtected = in.readByte() != 0;
        streamUri = in.readString();
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(partyId);
        dest.writeString(partyName);
        dest.writeString(hostName);
        dest.writeString(ipAddress);
        dest.writeInt(port);
        dest.writeString(pin);
        dest.writeByte((byte) (isPasswordProtected ? 1 : 0));
        dest.writeString(streamUri);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    public static final Creator<PartyHost> CREATOR = new Creator<>() {
        @Override
        public PartyHost createFromParcel(Parcel in) {
            return new PartyHost(in);
        }

        @Override
        public PartyHost[] newArray(int size) {
            return new PartyHost[size];
        }
    };
}
