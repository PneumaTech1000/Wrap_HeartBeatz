package com.giga.tech1000.media_player.database.setting;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.TypeConverters;

import com.giga.tech1000.media_player.database.SettingDao;
import com.giga.tech1000.media_player.models.extended_models.SettingEntity;
import com.giga.tech1000.media_player.utils.converters.SettingConverter;

@Database(
        entities = { SettingEntity.class },
        version = 1,
        exportSchema = false
)
@TypeConverters(SettingConverter.class)
public abstract class SettingDatabase extends RoomDatabase {
    public abstract SettingDao settingDao();
    private static volatile SettingDatabase INSTANCE;

    public static SettingDatabase getInstance(Context context) {
        if (INSTANCE == null) {
            synchronized (SettingDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(
                            context.getApplicationContext(),
                            SettingDatabase.class,
                            "settings.db"
                    ).build();
                }
            }
        }
        return INSTANCE;
    }
}
