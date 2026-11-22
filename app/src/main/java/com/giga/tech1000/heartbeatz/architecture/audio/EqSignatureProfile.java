package com.giga.tech1000.heartbeatz.architecture.audio;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Product signature EQ profiles (10 ISO bands, dB).
 * Safe defaults: Bass enables a soft limiter bias in the ViewModel.
 */
public enum EqSignatureProfile {
    FLAT("Flat", new float[]{0, 0, 0, 0, 0, 0, 0, 0, 0, 0}, false),
    /** Club / boom-box oriented low end. */
    BASS("Bass", new float[]{7.5f, 6.5f, 4.5f, 2f, 0.5f, -0.5f, 0f, 0f, 0.5f, 1f}, true),
    /** Late night: reduced harsh highs, mild low cut of extreme sub. */
    NIGHT("Night", new float[]{2f, 2.5f, 1.5f, 0.5f, 0f, 0f, -0.5f, -1.5f, -3f, -4f}, true),
    /** Speech / podcast intelligibility. */
    VOICE("Voice", new float[]{-2f, -1f, 0f, 1.5f, 3.5f, 4.5f, 3.5f, 1.5f, 0f, -1f}, true);

    public final String displayName;
    /** Length 10, dB. */
    public final float[] gainsDb;
    /** Recommend limiter on when applying. */
    public final boolean preferLimiter;

    EqSignatureProfile(String displayName, float[] gainsDb, boolean preferLimiter) {
        this.displayName = displayName;
        this.gainsDb = gainsDb;
        this.preferLimiter = preferLimiter;
    }

    @NonNull
    public static EqSignatureProfile fromChipLabel(@Nullable String name) {
        if (name == null) return FLAT;
        String n = name.trim().toLowerCase();
        if (n.equals("bass") || n.contains("bass boost")) return BASS;
        if (n.equals("night") || n.contains("late")) return NIGHT;
        if (n.equals("voice") || n.contains("vocal")) return VOICE;
        if (n.equals("flat") || n.contains("0db")) return FLAT;
        return FLAT;
    }

    @Nullable
    public static EqSignatureProfile matchExact(@Nullable String name) {
        if (name == null) return null;
        for (EqSignatureProfile p : values()) {
            if (p.displayName.equalsIgnoreCase(name.trim())) return p;
        }
        return null;
    }
}
