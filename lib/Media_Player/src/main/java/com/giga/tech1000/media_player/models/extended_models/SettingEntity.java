package com.giga.tech1000.media_player.models.extended_models;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

import com.giga.tech1000.media_player.utils.enums.ItemSource;
import com.giga.tech1000.media_player.utils.enums.RepeatMode;
import com.giga.tech1000.media_player.utils.enums.ShuffleMode;
import com.giga.tech1000.media_player.utils.enums.ThemeMode;

import java.util.List;

@Entity(tableName = "app_settings")
public class SettingEntity {

    @PrimaryKey
    @ColumnInfo(name = "id")
    public int id = 1; // single-row table

    // ===== Playback =====
    @ColumnInfo(name = "shuffle_value")
    public ShuffleMode shuffleValue;

    @ColumnInfo(name = "repeat_mode")
    public RepeatMode repeatMode;

    @ColumnInfo(name = "playback_speed")
    public float playbackSpeed;   // 0.5f – 2.0f

    @ColumnInfo(name = "playback_pitch")
    public float playbackPitch;   // ExoPlayer pitch multiplier (1.0f default)

    // ===== Audio effects =====
    @ColumnInfo(name = "equalizer_enabled")
    public boolean equalizerEnabled;

    @ColumnInfo(name = "eq_preset")
    @NonNull
    public String eqPreset;        // FLAT / ROCK / CUSTOM

    @ColumnInfo(name = "bass_strength")
    public int bassStrength;       // 0–100

    @ColumnInfo(name = "virtualizer_strength")
    public int virtualizerStrength;

    // ===== UI =====
    @ColumnInfo(name = "theme_mode")
    public ThemeMode themeMode;

    @ColumnInfo(name = "show_lyrics")
    public boolean showLyrics;

    @ColumnInfo(name = "keep_screen_on")
    public boolean keepScreenOn;

    // ===== Restore =====
    @ColumnInfo(name = "last_played_song_id")
    public long lastPlayedSongId;

    @ColumnInfo(name = "last_played_song_position")
    public long lastPlayedSongPosition;

    @ColumnInfo(name = "last_queue_source")
    public ItemSource lastQueueSource;

    @ColumnInfo(name = "last_queue_id_list")
    public List<Integer> lastQueueIdList;

    @ColumnInfo(name = "last_mediastore_generation")
    public long lastMediaStoreGeneration;

    @ColumnInfo(name = "last_scan_timestamp")
    public long lastScanTimestamp;


    // ===== Constructors =====

    /** Default constructor (Room + first install) */
    public SettingEntity() {
        applyDefaults();
    }

    /** Copy constructor (CRITICAL) */
    public SettingEntity(@NonNull SettingEntity other) {
        this.id = other.id;

        this.shuffleValue = other.shuffleValue;
        this.repeatMode = other.repeatMode;
        this.playbackSpeed = other.playbackSpeed;
        this.playbackPitch = other.playbackPitch;

        this.equalizerEnabled = other.equalizerEnabled;
        this.eqPreset = other.eqPreset;
        this.bassStrength = other.bassStrength;
        this.virtualizerStrength = other.virtualizerStrength;

        this.themeMode = other.themeMode;
        this.showLyrics = other.showLyrics;
        this.keepScreenOn = other.keepScreenOn;

        this.lastPlayedSongId = other.lastPlayedSongId;
        this.lastPlayedSongPosition = other.lastPlayedSongPosition;
        this.lastQueueSource = other.lastQueueSource;
        this.lastQueueIdList = other.lastQueueIdList;

        this.lastMediaStoreGeneration = other.lastMediaStoreGeneration;
        this.lastScanTimestamp = other.lastScanTimestamp;
    }

    /** Centralized defaults */
    private void applyDefaults() {
        shuffleValue = ShuffleMode.OFF;
        repeatMode = RepeatMode.OFF;

        playbackSpeed = 1.0f;
        playbackPitch = 1.0f;

        equalizerEnabled = false;
        eqPreset = "FLAT";

        bassStrength = 0;
        virtualizerStrength = 0;

        themeMode = ThemeMode.SYSTEM;
        showLyrics = true;
        keepScreenOn = false;

        lastPlayedSongId = -1;
        lastPlayedSongPosition = 0;
        lastQueueSource = ItemSource.NONE;

        lastMediaStoreGeneration = -1;
        lastScanTimestamp = 0;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public ShuffleMode getShuffleValue() {
        return shuffleValue;
    }

    public void setShuffleValue(ShuffleMode shuffleValue) {
        this.shuffleValue = shuffleValue;
    }

    public RepeatMode getRepeatMode() {
        return repeatMode;
    }

    public void setRepeatMode(RepeatMode repeatMode) {
        this.repeatMode = repeatMode;
    }

    public float getPlaybackSpeed() {
        return playbackSpeed;
    }

    public void setPlaybackSpeed(float playbackSpeed) {
        this.playbackSpeed = playbackSpeed;
    }

    public float getPlaybackPitch() {
        return playbackPitch;
    }

    public void setPlaybackPitch(float playbackPitch) {
        this.playbackPitch = playbackPitch;
    }

    public boolean isEqualizerEnabled() {
        return equalizerEnabled;
    }

    public void setEqualizerEnabled(boolean equalizerEnabled) {
        this.equalizerEnabled = equalizerEnabled;
    }

    @NonNull
    public String getEqPreset() {
        return eqPreset;
    }

    public void setEqPreset(@NonNull String eqPreset) {
        this.eqPreset = eqPreset;
    }

    public int getBassStrength() {
        return bassStrength;
    }

    public void setBassStrength(int bassStrength) {
        this.bassStrength = bassStrength;
    }

    public int getVirtualizerStrength() {
        return virtualizerStrength;
    }

    public void setVirtualizerStrength(int virtualizerStrength) {
        this.virtualizerStrength = virtualizerStrength;
    }

    public ThemeMode getThemeMode() {
        return themeMode;
    }

    public void setThemeMode(ThemeMode themeMode) {
        this.themeMode = themeMode;
    }

    public boolean isShowLyrics() {
        return showLyrics;
    }

    public void setShowLyrics(boolean showLyrics) {
        this.showLyrics = showLyrics;
    }

    public boolean isKeepScreenOn() {
        return keepScreenOn;
    }

    public void setKeepScreenOn(boolean keepScreenOn) {
        this.keepScreenOn = keepScreenOn;
    }

    public long getLastPlayedSongId() {
        return lastPlayedSongId;
    }

    public void setLastPlayedSongId(long lastPlayedSongId) {
        this.lastPlayedSongId = lastPlayedSongId;
    }

    public long getLastPlayedSongPosition() {
        return lastPlayedSongPosition;
    }

    public void setLastPlayedSongPosition(long lastPlayedSongPosition) {
        this.lastPlayedSongPosition = lastPlayedSongPosition;
    }

    public ItemSource getLastQueueSource() {
        return lastQueueSource;
    }

    public void setLastQueueSource(ItemSource lastQueueSource) {
        this.lastQueueSource = lastQueueSource;
    }

    public List<Integer> getLastQueueIdList() {
        return lastQueueIdList;
    }

    public void setLastQueueIdList(List<Integer> lastQueueIdList) {
        this.lastQueueIdList = lastQueueIdList;
    }
}
