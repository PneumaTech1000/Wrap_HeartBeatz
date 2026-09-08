package com.giga.tech1000.media_player.database.setting;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.giga.tech1000.media_player.database.SettingDao;
import com.giga.tech1000.media_player.interfaces.SettingMutator;
import com.giga.tech1000.media_player.models.extended_models.SettingEntity;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.UnaryOperator;
import java.util.concurrent.TimeUnit;

import android.util.Log;

import kotlin.Unit;

public final class SettingRepository {

    private static volatile SettingRepository INSTANCE;

    private final SettingDao dao;
    private final ExecutorService executor;

    // Single source of truth (hot cache)
    private volatile SettingEntity cached;

    public SettingRepository(Context context) {
        if (context == null) {
            throw new IllegalArgumentException("Context cannot be null");
        }
        SettingDatabase db =
                SettingDatabase.getInstance(context.getApplicationContext());
        dao = db.settingDao();
        executor = Executors.newSingleThreadExecutor();

        // Bootstrap
        executor.execute(() -> {
            try {
                SettingEntity entity = dao.get();
                if (entity == null) {
                    entity = new SettingEntity();
                    dao.save(entity);
                }
                cached = entity;
            } catch (Exception e) {
                Log.e("SettingRepository", "Error during bootstrap", e);
                // Create a default setting as fallback
                cached = new SettingEntity();
            }
        });

        // DB → cache (Flow)
        SettingFlowBridge.INSTANCE.collect(
                dao.observe(),
                entity -> {
                    cached = entity;
                    return kotlin.Unit.INSTANCE;
                }
        );
    }

    /**
     * Shuts down the executor service to prevent resource leaks.
     * Should be called when the repository is no longer needed.
     */
    public void shutdown() {
        executor.shutdown();
        try {
            // Wait a moment for tasks to complete
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    public static SettingRepository getInstance(Context context) {
        if (INSTANCE == null) {
            synchronized (SettingRepository.class) {
                if (INSTANCE == null) {
                    INSTANCE = new SettingRepository(context);
                }
            }
        }
        return INSTANCE;
    }

    // ========= READ =========

    @Nullable
    public SettingEntity getCached() {
        return cached;
    }

    // ========= WRITE =========

    public void update(@NonNull SettingMutator mutator) {
        if (mutator == null) {
            Log.w("SettingRepository", "Mutator is null, skipping update");
            return;
        }
        executor.execute(() -> {
            try {
                if (cached == null) {
                    Log.w("SettingRepository", "Cached settings is null, initializing");
                    // Initialize with default settings if null
                    cached = new SettingEntity();
                }

                // Defensive copy
                SettingEntity updated = new SettingEntity(cached);

                // Apply mutation
                mutator.mutate(updated);

                // Persist
                dao.save(updated);
                cached = updated;
            } catch (Exception e) {
                Log.e("SettingRepository", "Error updating settings", e);
            }
        });
    }
}
