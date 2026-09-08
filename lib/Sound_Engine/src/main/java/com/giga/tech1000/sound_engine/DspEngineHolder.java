package com.giga.tech1000.sound_engine;

import android.content.Context;
import com.giga.tech1000.soundengine.SoundEngine;

/**
 * Holder for the Sound engine instance.
 * Uses application context to avoid leaks.
 */
public class DspEngineHolder {
    private static SoundEngine instance;

    /**
     * Gets the Sound engine instance, creating it if necessary.
     * @param context the application context
     * @return the Sound engine instance
     */
    public static SoundEngine getInstance(Context context) {
        if (instance == null) {
            instance = new SoundEngine(context.getApplicationContext());
        }
        return instance;
    }

    /**
     * Releases the Sound engine instance.
     * Call this when the Sound engine is no longer needed.
     */
    public static void release() {
        if (instance != null) {
            instance.release();
            instance = null;
        }
    }
}