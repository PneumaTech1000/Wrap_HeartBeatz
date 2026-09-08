package com.giga.tech1000.heartbeatz.utils.audio;

import android.os.Parcel;
import android.os.Parcelable;

/**
 * Represents a single parametric equalizer band with frequency, gain, and Q-factor.
 * This class is used to define EQ settings that can be converted to fixed-band
 * equalizer settings for hardware acceleration.
 */
public class ParametricEQBand implements Parcelable {

    private final int bandId;
    private float frequencyHz;   // Center frequency in Hz
    private float gainDb;        // Gain in dB (-15 to +15)
    private float qFactor;       // Q-factor (bandwidth) (0.1 to 10.0)
    private boolean enabled;

    public ParametricEQBand(int bandId, float frequencyHz, float gainDb, float qFactor) {
        this.bandId = bandId;
        this.frequencyHz = frequencyHz;
        this.gainDb = gainDb;
        this.qFactor = qFactor;
        this.enabled = true;
    }

    // Parcelable implementation
    protected ParametricEQBand(Parcel in) {
        bandId = in.readInt();
        frequencyHz = in.readFloat();
        gainDb = in.readFloat();
        qFactor = in.readFloat();
        enabled = in.readByte() != 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(bandId);
        dest.writeFloat(frequencyHz);
        dest.writeFloat(gainDb);
        dest.writeFloat(qFactor);
        dest.writeByte((byte) (enabled ? 1 : 0));
    }

    @Override
    public int describeContents() {
        return 0;
    }

    public static final Creator<ParametricEQBand> CREATOR = new Creator<ParametricEQBand>() {
        @Override
        public ParametricEQBand createFromParcel(Parcel in) {
            return new ParametricEQBand(in);
        }

        @Override
        public ParametricEQBand[] newArray(int size) {
            return new ParametricEQBand[size];
        }
    };

    // Getters and Setters
    public int getBandId() {
        return bandId;
    }

    public float getFrequencyHz() {
        return frequencyHz;
    }

    public void setFrequencyHz(float frequencyHz) {
        // Limit frequency to reasonable range (20Hz - 20kHz)
        this.frequencyHz = Math.max(20f, Math.min(20000f, frequencyHz));
    }

    public float getGainDb() {
        return gainDb;
    }

    public void setGainDb(float gainDb) {
        // Limit gain to +/- 15dB
        this.gainDb = Math.max(-15f, Math.min(15f, gainDb));
    }

    public float getQFactor() {
        return qFactor;
    }

    public void setQFactor(float qFactor) {
        // Limit Q-factor to reasonable range
        this.qFactor = Math.max(0.1f, Math.min(10f, qFactor));
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
}