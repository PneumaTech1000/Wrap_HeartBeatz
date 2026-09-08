package com.giga.tech1000.heartbeatz.app_worker;

import android.app.Application;

import androidx.appcompat.app.AppCompatDelegate;

import com.google.firebase.FirebaseApp;
import com.google.firebase.database.FirebaseDatabase;
import com.giga.tech1000.media_player.database.setting.SettingRepository;
import com.giga.tech1000.media_player.models.extended_models.SettingEntity;
import com.giga.tech1000.media_player.utils.enums.ThemeMode;

public class HeartBeatzApp extends Application {

    private static SettingRepository settingRepository;
    private static SettingEntity cachedSettings;

    @Override
    public void onCreate() {
        super.onCreate();

        // Initialize Firebase
        FirebaseApp.initializeApp(this);
        // Enable persistence for offline capabilities
        FirebaseDatabase.getInstance().setPersistenceEnabled(true);

        settingRepository = new SettingRepository(this);

        // ⚠️ Blocking read is OK here ONCE at startup
        if (settingRepository.getCached() == null) return;
        cachedSettings = settingRepository.getCached();

        applyTheme(cachedSettings.themeMode);
    }

    private void applyTheme(ThemeMode mode) {
        switch (mode) {
            case LIGHT ->
                    AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
            case DARK ->
                    AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
            case SYSTEM ->
                    AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
        }
    }

    public static SettingRepository settings() {
        return settingRepository;
    }

    public static SettingEntity settingsSnapshot() {
        return cachedSettings;
    }
}
