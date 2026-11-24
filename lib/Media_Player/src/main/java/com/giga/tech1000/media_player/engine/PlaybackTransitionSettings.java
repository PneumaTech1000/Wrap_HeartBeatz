package com.giga.tech1000.media_player.engine;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;

/**
 * User preferences for local playlist transitions (gapless + crossfade).
 * Party guests never drive these — host timeline owns track boundaries.
 */
public final class PlaybackTransitionSettings {

    private static final String PREFS = "hb_playback_transitions_v1";
    private static final String KEY_GAPLESS = "gapless_enabled";
    private static final String KEY_CROSSFADE_MS = "crossfade_ms";

    public static final int CROSSFADE_MIN_MS = 0;
    public static final int CROSSFADE_MAX_MS = 12_000;
    public static final int CROSSFADE_DEFAULT_MS = 0;

    private final SharedPreferences prefs;

    public PlaybackTransitionSettings(@NonNull Context context) {
        this.prefs = context.getApplicationContext()
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    /** When true, optimize buffering/preload for seamless playlist advances. */
    public boolean isGaplessEnabled() {
        return prefs.getBoolean(KEY_GAPLESS, true);
    }

    public void setGaplessEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_GAPLESS, enabled).apply();
    }

    /**
     * Crossfade duration at end of track. {@code 0} = hard cut (pure gapless only).
     */
    public int getCrossfadeMs() {
        int v = prefs.getInt(KEY_CROSSFADE_MS, CROSSFADE_DEFAULT_MS);
        if (v < CROSSFADE_MIN_MS) return CROSSFADE_MIN_MS;
        if (v > CROSSFADE_MAX_MS) return CROSSFADE_MAX_MS;
        return v;
    }

    public void setCrossfadeMs(int ms) {
        int clamped = Math.max(CROSSFADE_MIN_MS, Math.min(CROSSFADE_MAX_MS, ms));
        prefs.edit().putInt(KEY_CROSSFADE_MS, clamped).apply();
    }
}
