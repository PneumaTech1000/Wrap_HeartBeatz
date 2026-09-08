package com.giga.tech1000.heartbeatz.view_models.extended_models;

import android.app.Application;

import androidx.annotation.Nullable;
import androidx.lifecycle.AndroidViewModel;

import com.giga.tech1000.media_player.database.setting.SettingRepository;
import com.giga.tech1000.media_player.models.extended_models.SettingEntity;
import com.giga.tech1000.media_player.utils.enums.ItemSource;
import com.giga.tech1000.media_player.utils.enums.RepeatMode;
import com.giga.tech1000.media_player.utils.enums.ShuffleMode;
import com.giga.tech1000.media_player.utils.enums.ThemeMode;

import androidx.annotation.NonNull;

import java.util.List;

public final class SettingViewModel extends AndroidViewModel {

    private final SettingRepository repository;

    public SettingViewModel(@NonNull Application application) {
        super(application);
        repository = SettingRepository.getInstance(application);
    }

    // ========= READ =========

    @Nullable
    public SettingEntity getCached() {
        return repository.getCached();
    }

    // ========= PLAYBACK =========

    public void setShuffle(ShuffleMode mode) {
        repository.update(settings -> {
            settings.setShuffleValue(mode);
        });
    }

    public void setRepeatMode(RepeatMode mode) {
        repository.update(settings -> {
            settings.setRepeatMode(mode);
        });
    }

    public void setPlaybackSpeed(float speed) {
        repository.update(settings -> {
            settings.setPlaybackSpeed(speed);
        });
    }

    public void setPlaybackPitch(float pitch) {
        repository.update(settings -> {
            settings.setPlaybackPitch(pitch);
        });
    }

    // ========= AUDIO =========

    public void setEqualizerEnabled(boolean enabled) {
        repository.update(settings -> {
            settings.setEqualizerEnabled(enabled);
        });
    }

    public void setEqPreset(String preset) {
        repository.update(settings -> {
            settings.setEqPreset(preset);
        });
    }

    public void setBassStrength(int value) {
        repository.update(settings -> {
            settings.setBassStrength(value);
        });
    }

    public void setVirtualizerStrength(int value) {
        repository.update(settings -> {
            settings.setVirtualizerStrength(value);
        });
    }

    // ========= UI =========

    public void setThemeMode(ThemeMode mode) {
        repository.update(settings -> {
            settings.setThemeMode(mode);
        });
    }

    public void setShowLyrics(boolean enabled) {
        repository.update(settings -> {
            settings.setShowLyrics(enabled);
        });
    }

    public void setKeepScreenOn(boolean enabled) {
        repository.update(settings -> {
            settings.setKeepScreenOn(enabled);
        });
    }

    // ========= RESTORE =========

    public void updateRestoreState(
            long songId,
            long position,
            ItemSource source,
            List<Integer> queue
    ) {
        repository.update(settings -> {
            settings.setLastPlayedSongId(songId);
            settings.setLastPlayedSongPosition(position);
            settings.setLastQueueSource(source);
            settings.setLastQueueIdList(queue);
        });
    }
}
