package com.giga.tech1000.heartbeatz.utils;

import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.NonNull;

import com.giga.tech1000.heartbeatz.utils.audio.ParametricEQBand;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Represents a user-configurable equalizer preset with metadata.
 * Supports parametric EQ bands, audio effects, and various metadata for organization.
 */
public class Preset implements Parcelable {

    // ======================
    // Fields
    // ======================

    private String id; // Unique identifier
    private String name; // Preset name (user-defined)
    private String category; // Category (e.g., "Music", "Gaming", "Movie")
    private String tags; // Comma-separated tags for search/filter
    private String description; // Optional description

    // EQ Settings
    private List<ParametricEQBand> eqBands;

    // Audio Effects Settings
    private boolean bassEnabled;
    private int bassStrength; // 0-1000
    private boolean virtualizerEnabled;
    private int virtualizerStrength; // 0-1000
    private boolean loudnessEnabled;
    private int loudnessGain; // in millibels
    private float tempo; // playback speed
    private float pitch; // in semitones

    // Advanced Audio Effects
    private boolean reverbEnabled;
    private int reverbRoomLevel; // -1000 to 0 (millibels)
    private int reverbDecayTime; // 0 to 5000 milliseconds
    private boolean stereoWideningEnabled;
    private float stereoWideningWidth; // 0.0 to 1.0
    private boolean exciterEnabled;
    private float exciterAmount; // 0.0 to 1.0
    private float exciterFrequency; // Hz
    private boolean compressorEnabled;
    private float compressorThreshold; // dB
    private float compressorRatio; // 1.0 to inf
    private float compressorAttack; // ms
    private float compressorRelease; // ms
    private boolean limiterEnabled;
    private float limiterThreshold; // dB
    private boolean noiseGateEnabled;
    private float noiseGateThreshold; // dB
    private boolean deEsserEnabled;
    private float deEsserThreshold; // dB
    private float deEsserFrequency; // Hz

    // Metadata
    private long createdTimestamp; // when preset was created
    private long modifiedTimestamp; // when preset was last modified
    private int usageCount; // how many times preset has been used
    private String deviceType; // e.g., "headphones", "speakers", "car"
    private String environment; // e.g., "indoor", "outdoor", "party"

    // ======================
    // Constructors
    // ======================

    public Preset() {
        this.id = java.util.UUID.randomUUID().toString();
        this.name = "New Preset";
        this.category = "Custom";
        this.tags = "";
        this.description = "";
        this.eqBands = new ArrayList<>();
        this.bassEnabled = false;
        this.bassStrength = 0;
        this.virtualizerEnabled = false;
        this.virtualizerStrength = 0;
        this.loudnessEnabled = false;
        this.loudnessGain = 0;
        this.tempo = 1.0f;
        this.pitch = 0.0f;
        // Advanced Audio Effects defaults
        this.reverbEnabled = false;
        this.reverbRoomLevel = 0;
        this.reverbDecayTime = 1000;
        this.stereoWideningEnabled = false;
        this.stereoWideningWidth = 0.5f;
        this.exciterEnabled = false;
        this.exciterAmount = 0.0f;
        this.exciterFrequency = 2000.0f;
        this.compressorEnabled = false;
        this.compressorThreshold = -20.0f;
        this.compressorRatio = 4.0f;
        this.compressorAttack = 10.0f;
        this.compressorRelease = 100.0f;
        this.limiterEnabled = false;
        this.limiterThreshold = -3.0f;
        this.noiseGateEnabled = false;
        this.noiseGateThreshold = -60.0f;
        this.deEsserEnabled = false;
        this.deEsserThreshold = -20.0f;
        this.deEsserFrequency = 5000.0f;
        this.createdTimestamp = new Date().getTime();
        this.modifiedTimestamp = this.createdTimestamp;
        this.usageCount = 0;
        this.deviceType = "unknown";
        this.environment = "unknown";
        initializeDefaultBands();
    }

    /**
     * Copy constructor
     */
    public Preset(@NonNull Preset other) {
        this.id = other.id;
        this.name = other.name;
        this.category = other.category;
        this.tags = other.tags;
        this.description = other.description;
        this.eqBands = deepCopyBands(other.eqBands);
        this.bassEnabled = other.bassEnabled;
        this.bassStrength = other.bassStrength;
        this.virtualizerEnabled = other.virtualizerEnabled;
        this.virtualizerStrength = other.virtualizerStrength;
        this.loudnessEnabled = other.loudnessEnabled;
        this.loudnessGain = other.loudnessGain;
        this.tempo = other.tempo;
        this.pitch = other.pitch;
        // Advanced Audio Effects
        this.reverbEnabled = other.reverbEnabled;
        this.reverbRoomLevel = other.reverbRoomLevel;
        this.reverbDecayTime = other.reverbDecayTime;
        this.stereoWideningEnabled = other.stereoWideningEnabled;
        this.stereoWideningWidth = other.stereoWideningWidth;
        this.exciterEnabled = other.exciterEnabled;
        this.exciterAmount = other.exciterAmount;
        this.exciterFrequency = other.exciterFrequency;
        this.compressorEnabled = other.compressorEnabled;
        this.compressorThreshold = other.compressorThreshold;
        this.compressorRatio = other.compressorRatio;
        this.compressorAttack = other.compressorAttack;
        this.compressorRelease = other.compressorRelease;
        this.limiterEnabled = other.limiterEnabled;
        this.limiterThreshold = other.limiterThreshold;
        this.noiseGateEnabled = other.noiseGateEnabled;
        this.noiseGateThreshold = other.noiseGateThreshold;
        this.deEsserEnabled = other.deEsserEnabled;
        this.deEsserThreshold = other.deEsserThreshold;
        this.deEsserFrequency = other.deEsserFrequency;
        this.createdTimestamp = other.createdTimestamp;
        this.modifiedTimestamp = other.modifiedTimestamp;
        this.usageCount = other.usageCount;
        this.deviceType = other.deviceType;
        this.environment = other.environment;
    }

    // ======================
    // Parcelable implementation
    // ======================

    protected Preset(Parcel in) {
        id = in.readString();
        name = in.readString();
        category = in.readString();
        tags = in.readString();
        description = in.readString();
        eqBands = in.createTypedArrayList(ParametricEQBand.CREATOR);
        bassEnabled = in.readByte() != 0;
        bassStrength = in.readInt();
        virtualizerEnabled = in.readByte() != 0;
        virtualizerStrength = in.readInt();
        loudnessEnabled = in.readByte() != 0;
        loudnessGain = in.readInt();
        tempo = in.readFloat();
        pitch = in.readFloat();
        createdTimestamp = in.readLong();
        modifiedTimestamp = in.readLong();
        usageCount = in.readInt();
        deviceType = in.readString();
        environment = in.readString();
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(id);
        dest.writeString(name);
        dest.writeString(category);
        dest.writeString(tags);
        dest.writeString(description);
        dest.writeTypedList(eqBands);
        dest.writeByte((byte) (bassEnabled ? 1 : 0));
        dest.writeInt(bassStrength);
        dest.writeByte((byte) (virtualizerEnabled ? 1 : 0));
        dest.writeInt(virtualizerStrength);
        dest.writeByte((byte) (loudnessEnabled ? 1 : 0));
        dest.writeInt(loudnessGain);
        dest.writeFloat(tempo);
        dest.writeFloat(pitch);
        dest.writeLong(createdTimestamp);
        dest.writeLong(modifiedTimestamp);
        dest.writeInt(usageCount);
        dest.writeString(deviceType);
        dest.writeString(environment);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    public static final Creator<Preset> CREATOR = new Creator<Preset>() {
        @Override
        public Preset createFromParcel(Parcel in) {
            return new Preset(in);
        }

        @Override
        public Preset[] newArray(int size) {
            return new Preset[size];
        }
    };

    // ======================
    // Public Methods
    // ======================

    /**
     * Initialize with default flat EQ bands
     */
    public void initializeDefaultBands() {
        eqBands.clear();
        // Standard frequencies for parametric EQ (20Hz - 20kHz, logarithmic spacing)
        float[] frequencies = {
                20, 25, 31.5f, 40, 50, 63, 80, 100, 125, 160,
                200, 250, 315, 400, 500, 630, 800, 1000, 1250, 1600,
                2000, 2500, 3150, 4000, 5000, 6300, 8000, 10000, 12500, 16000, 20000
        };

        for (int i = 0; i < frequencies.length; i++) {
            eqBands.add(new ParametricEQBand(i, frequencies[i], 0f, 1.0f));
        }
    }

    /**
     * Update the modified timestamp
     */
    public void touch() {
        this.modifiedTimestamp = new Date().getTime();
    }

    /**
     * Increment usage count
     */
    public void use() {
        this.usageCount++;
        touch();
    }

    /**
     * Get a deep copy of this preset
     */
    public Preset clone() {
        return new Preset(this);
    }

    // ======================
    // JSON Serialization
    // ======================

    /**
     * Convert preset to JSON object
     */
    public JSONObject toJson() throws JSONException {
        JSONObject json = new JSONObject();
        json.put("id", id);
        json.put("name", name);
        json.put("category", category);
        json.put("tags", tags);
        json.put("description", description);

        // EQ Bands
        JSONArray eqBandsArray = new JSONArray();
        for (ParametricEQBand band : eqBands) {
            JSONObject bandObj = new JSONObject();
            bandObj.put("bandId", band.getBandId());
            bandObj.put("frequencyHz", band.getFrequencyHz());
            bandObj.put("gainDb", band.getGainDb());
            bandObj.put("qFactor", band.getQFactor());
            bandObj.put("enabled", band.isEnabled());
            eqBandsArray.put(bandObj);
        }
        json.put("eqBands", eqBandsArray);

        // Audio Effects
        json.put("bassEnabled", bassEnabled);
        json.put("bassStrength", bassStrength);
        json.put("virtualizerEnabled", virtualizerEnabled);
        json.put("virtualizerStrength", virtualizerStrength);
        json.put("loudnessEnabled", loudnessEnabled);
        json.put("loudnessGain", loudnessGain);
        json.put("tempo", tempo);
        json.put("pitch", pitch);

        // Metadata
        json.put("createdTimestamp", createdTimestamp);
        json.put("modifiedTimestamp", modifiedTimestamp);
        json.put("usageCount", usageCount);
        json.put("deviceType", deviceType);
        json.put("environment", environment);

        return json;
    }

    /**
     * Create preset from JSON object
     */
    public static Preset fromJson(JSONObject json) throws JSONException {
        Preset preset = new Preset();
        preset.id = json.optString("id", java.util.UUID.randomUUID().toString());
        preset.name = json.optString("name", "New Preset");
        preset.category = json.optString("category", "Custom");
        preset.tags = json.optString("tags", "");
        preset.description = json.optString("description", "");

        // EQ Bands
        preset.eqBands = new ArrayList<>();
        JSONArray eqBandsArray = json.optJSONArray("eqBands");
        if (eqBandsArray != null) {
            for (int i = 0; i < eqBandsArray.length(); i++) {
                JSONObject bandObj = eqBandsArray.getJSONObject(i);
                ParametricEQBand band = new ParametricEQBand(
                        bandObj.optInt("bandId", i),
                        (float) bandObj.optDouble("frequencyHz", 0),
                        (float) bandObj.optDouble("gainDb", 0),
                        (float) bandObj.optDouble("qFactor", 1.0)
                );
                band.setEnabled(bandObj.optBoolean("enabled", true));
                preset.eqBands.add(band);
            }
        }

        // Audio Effects
        preset.bassEnabled = json.optBoolean("bassEnabled", false);
        preset.bassStrength = json.optInt("bassStrength", 0);
        preset.virtualizerEnabled = json.optBoolean("virtualizerEnabled", false);
        preset.virtualizerStrength = json.optInt("virtualizerStrength", 0);
        preset.loudnessEnabled = json.optBoolean("loudnessEnabled", false);
        preset.loudnessGain = json.optInt("loudnessGain", 0);
        preset.tempo = (float) json.optDouble("tempo", 1.0);
        preset.pitch = (float) json.optDouble("pitch", 0.0);

        // Metadata
        preset.createdTimestamp = json.optLong("createdTimestamp", new Date().getTime());
        preset.modifiedTimestamp = json.optLong("modifiedTimestamp", new Date().getTime());
        preset.usageCount = json.optInt("usageCount", 0);
        preset.deviceType = json.optString("deviceType", "unknown");
        preset.environment = json.optString("environment", "unknown");

        return preset;
    }

    /**
     * Convert preset to JSON string
     */
    public String toJsonString() {
        try {
            return toJson().toString();
        } catch (JSONException e) {
            e.printStackTrace();
            return "{}";
        }
    }

    /**
     * Create preset from JSON string
     */
    public static Preset fromJsonString(String jsonString) {
        try {
            return fromJson(new JSONObject(jsonString));
        } catch (JSONException e) {
            e.printStackTrace();
            return new Preset(); // Return default preset on error
        }
    }

    // ======================
    // Getters and Setters
    // ======================

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
        touch();
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
        touch();
    }

    public String getTags() {
        return tags;
    }

    public void setTags(String tags) {
        this.tags = tags;
        touch();
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
        touch();
    }

    public List<ParametricEQBand> getEqBands() {
        return eqBands;
    }

    public void setEqBands(List<ParametricEQBand> eqBands) {
        this.eqBands = eqBands != null ? new ArrayList<>(eqBands) : new ArrayList<>();
        touch();
    }

    public boolean isBassEnabled() {
        return bassEnabled;
    }

    public void setBassEnabled(boolean bassEnabled) {
        this.bassEnabled = bassEnabled;
        touch();
    }

    public int getBassStrength() {
        return bassStrength;
    }

    public void setBassStrength(int bassStrength) {
        this.bassStrength = Math.max(0, Math.min(1000, bassStrength));
        touch();
    }

    public boolean isVirtualizerEnabled() {
        return virtualizerEnabled;
    }

    public void setVirtualizerEnabled(boolean virtualizerEnabled) {
        this.virtualizerEnabled = virtualizerEnabled;
        touch();
    }

    public int getVirtualizerStrength() {
        return virtualizerStrength;
    }

    public void setVirtualizerStrength(int virtualizerStrength) {
        this.virtualizerStrength = Math.max(0, Math.min(1000, virtualizerStrength));
        touch();
    }

    public boolean isLoudnessEnabled() {
        return loudnessEnabled;
    }

    public void setLoudnessEnabled(boolean loudnessEnabled) {
        this.loudnessEnabled = loudnessEnabled;
        touch();
    }

    public int getLoudnessGain() {
        return loudnessGain;
    }

    public void setLoudnessGain(int loudnessGain) {
        this.loudnessGain = Math.clamp(loudnessGain, -1000, 1000);
        touch();
    }

    public float getTempo() {
        return tempo;
    }

    public void setTempo(float tempo) {
        this.tempo = Math.clamp(tempo, 0.5f, 2.0f);
        touch();
    }

    public float getPitch() {
        return pitch;
    }

    public void setPitch(float pitch) {
        this.pitch = Math.clamp(pitch, -12.0f, 12.0f);
        touch();
    }

    // Advanced Audio Effects Getters and Setters
    public boolean isReverbEnabled() {
        return reverbEnabled;
    }

    public void setReverbEnabled(boolean reverbEnabled) {
        this.reverbEnabled = reverbEnabled;
        touch();
    }

    public int getReverbRoomLevel() {
        return reverbRoomLevel;
    }

    public void setReverbRoomLevel(int reverbRoomLevel) {
        this.reverbRoomLevel = Math.max(-1000, Math.min(0, reverbRoomLevel));
        touch();
    }

    public int getReverbDecayTime() {
        return reverbDecayTime;
    }

    public void setReverbDecayTime(int reverbDecayTime) {
        this.reverbDecayTime = Math.max(0, Math.min(5000, reverbDecayTime));
        touch();
    }

    public boolean isStereoWideningEnabled() {
        return stereoWideningEnabled;
    }

    public void setStereoWideningEnabled(boolean stereoWideningEnabled) {
        this.stereoWideningEnabled = stereoWideningEnabled;
        touch();
    }

    public float getStereoWideningWidth() {
        return stereoWideningWidth;
    }

    public void setStereoWideningWidth(float stereoWideningWidth) {
        this.stereoWideningWidth = Math.max(0.0f, Math.min(1.0f, stereoWideningWidth));
        touch();
    }

    public boolean isExciterEnabled() {
        return exciterEnabled;
    }

    public void setExciterEnabled(boolean exciterEnabled) {
        this.exciterEnabled = exciterEnabled;
        touch();
    }

    public float getExciterAmount() {
        return exciterAmount;
    }

    public void setExciterAmount(float exciterAmount) {
        this.exciterAmount = Math.max(0.0f, Math.min(1.0f, exciterAmount));
        touch();
    }

    public float getExciterFrequency() {
        return exciterFrequency;
    }

    public void setExciterFrequency(float exciterFrequency) {
        this.exciterFrequency = Math.max(20.0f, Math.min(20000.0f, exciterFrequency));
        touch();
    }

    public boolean isCompressorEnabled() {
        return compressorEnabled;
    }

    public void setCompressorEnabled(boolean compressorEnabled) {
        this.compressorEnabled = compressorEnabled;
        touch();
    }

    public float getCompressorThreshold() {
        return compressorThreshold;
    }

    public void setCompressorThreshold(float compressorThreshold) {
        this.compressorThreshold = compressorThreshold;
        touch();
    }

    public float getCompressorRatio() {
        return compressorRatio;
    }

    public void setCompressorRatio(float compressorRatio) {
        this.compressorRatio = Math.max(1.0f, compressorRatio);
        touch();
    }

    public float getCompressorAttack() {
        return compressorAttack;
    }

    public void setCompressorAttack(float compressorAttack) {
        this.compressorAttack = Math.max(0.0f, compressorAttack);
        touch();
    }

    public float getCompressorRelease() {
        return compressorRelease;
    }

    public void setCompressorRelease(float compressorRelease) {
        this.compressorRelease = Math.max(0.0f, compressorRelease);
        touch();
    }

    public boolean isLimiterEnabled() {
        return limiterEnabled;
    }

    public void setLimiterEnabled(boolean limiterEnabled) {
        this.limiterEnabled = limiterEnabled;
        touch();
    }

    public float getLimiterThreshold() {
        return limiterThreshold;
    }

    public void setLimiterThreshold(float limiterThreshold) {
        this.limiterThreshold = limiterThreshold;
        touch();
    }

    public boolean isNoiseGateEnabled() {
        return noiseGateEnabled;
    }

    public void setNoiseGateEnabled(boolean noiseGateEnabled) {
        this.noiseGateEnabled = noiseGateEnabled;
        touch();
    }

    public float getNoiseGateThreshold() {
        return noiseGateThreshold;
    }

    public void setNoiseGateThreshold(float noiseGateThreshold) {
        this.noiseGateThreshold = Math.max(-60.0f, Math.min(0.0f, noiseGateThreshold));
        touch();
    }

    public boolean isDeEsserEnabled() {
        return deEsserEnabled;
    }

    public void setDeEsserEnabled(boolean deEsserEnabled) {
        this.deEsserEnabled = deEsserEnabled;
        touch();
    }

    public float getDeEsserThreshold() {
        return deEsserThreshold;
    }

    public void setDeEsserThreshold(float deEsserThreshold) {
        this.deEsserThreshold = deEsserThreshold;
        touch();
    }

    public float getDeEsserFrequency() {
        return deEsserFrequency;
    }

    public void setDeEsserFrequency(float deEsserFrequency) {
        this.deEsserFrequency = Math.max(2000.0f, Math.min(20000.0f, deEsserFrequency));
        touch();
    }

    public long getCreatedTimestamp() {
        return createdTimestamp;
    }

    public void setCreatedTimestamp(long createdTimestamp) {
        this.createdTimestamp = createdTimestamp;
    }

    public long getModifiedTimestamp() {
        return modifiedTimestamp;
    }

    public void setModifiedTimestamp(long modifiedTimestamp) {
        this.modifiedTimestamp = modifiedTimestamp;
    }

    public int getUsageCount() {
        return usageCount;
    }

    public void setUsageCount(int usageCount) {
        this.usageCount = Math.max(0, usageCount);
    }

    public String getDeviceType() {
        return deviceType;
    }

    public void setDeviceType(String deviceType) {
        this.deviceType = deviceType;
        touch();
    }

    public String getEnvironment() {
        return environment;
    }

    public void setEnvironment(String environment) {
        this.environment = environment;
        touch();
    }

    // ======================
    // Private Helper Methods
    // ======================

    /**
     * Create a deep copy of the EQ bands list
     */
    private List<ParametricEQBand> deepCopyBands(List<ParametricEQBand> original) {
        if (original == null) return new ArrayList<>();
        List<ParametricEQBand> copy = new ArrayList<>();
        for (ParametricEQBand band : original) {
            copy.add(new ParametricEQBand(
                    band.getBandId(),
                    band.getFrequencyHz(),
                    band.getGainDb(),
                    band.getQFactor()
            ));
        }
        return copy;
    }
}