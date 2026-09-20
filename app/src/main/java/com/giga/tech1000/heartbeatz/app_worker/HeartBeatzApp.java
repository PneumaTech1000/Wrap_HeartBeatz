package com.giga.tech1000.heartbeatz.app_worker;

import android.app.Application;
import android.content.Context;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatDelegate;

import com.giga.tech1000.heartbeatz.architecture.di.AppContainer;
import com.google.firebase.FirebaseApp;
import com.google.firebase.database.FirebaseDatabase;
import com.giga.tech1000.media_player.database.setting.SettingRepository;
import com.giga.tech1000.media_player.models.extended_models.SettingEntity;
import com.giga.tech1000.media_player.utils.enums.ThemeMode;

/**
 * Process-wide Application. Owns {@link AppContainer} — the single composition root.
 * Obtain via {@link #get(Context)}; do not use scattered service singletons.
 */
public class HeartBeatzApp extends Application {

    private AppContainer appContainer;
    private SettingRepository settingRepository;
    private SettingEntity cachedSettings;

    @Override
    public void onCreate() {
        super.onCreate();

        FirebaseApp.initializeApp(this);
        FirebaseDatabase.getInstance().setPersistenceEnabled(true);

        settingRepository = new SettingRepository(this);
        if (settingRepository.getCached() != null) {
            cachedSettings = settingRepository.getCached();
            applyTheme(cachedSettings.themeMode);
        }

        appContainer = new AppContainer(this);
    }

    @NonNull
    public AppContainer getContainer() {
        return appContainer;
    }

    /** Preferred access from any Context (Activity, View, Service). */
    @NonNull
    public static HeartBeatzApp get(@NonNull Context context) {
        return (HeartBeatzApp) context.getApplicationContext();
    }

    @NonNull
    public static AppContainer container(@NonNull Context context) {
        return get(context).getContainer();
    }

    public SettingRepository settings() {
        return settingRepository;
    }

    public SettingEntity settingsSnapshot() {
        return cachedSettings;
    }

    /** @deprecated use instance {@link #settings()} via {@link #get(Context)} */
    @Deprecated
    public static SettingRepository settingsStatic() {
        // Kept only if legacy call sites remain; prefer get(context).settings()
        throw new UnsupportedOperationException("Use HeartBeatzApp.get(context).settings()");
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
}
