package com.giga.tech1000.media_player.database;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import com.giga.tech1000.media_player.models.extended_models.SettingEntity;
import com.giga.tech1000.media_player.utils.enums.ItemSource;
import com.giga.tech1000.media_player.utils.enums.RepeatMode;
import com.giga.tech1000.media_player.utils.enums.ShuffleMode;
import com.giga.tech1000.media_player.utils.enums.ThemeMode;

import java.util.List;

import kotlinx.coroutines.flow.Flow;

@Dao
public interface SettingDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void save(SettingEntity settings);

    @Query("SELECT * FROM app_settings WHERE id = 1 LIMIT 1")
    SettingEntity get();

    @Query("SELECT * FROM app_settings WHERE id = 1 LIMIT 1")
    Flow<SettingEntity> observe();
}

