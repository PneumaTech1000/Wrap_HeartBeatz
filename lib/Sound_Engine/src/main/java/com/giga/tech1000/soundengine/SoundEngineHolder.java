package com.giga.tech1000.soundengine;

import androidx.annotation.Nullable;

/**
 * Process-wide owner for the native DSPark engine.
 * Media3 DspAudioProcessor is sample-rate authority via {@link #configure}.
 * UI uses {@link #getCurrent()} and never forces a rate that would wipe the live engine
 * without re-applying state — see {@link #setOnEngineRecreated}.
 */
public final class SoundEngineHolder {

    private static SoundEngine instance;
    private static int sampleRate;
    private static int maxBlockSize;
    private static int channelCount;
    @Nullable private static volatile Runnable onEngineRecreated;

    private SoundEngineHolder() {}

    public static synchronized void setOnEngineRecreated(@Nullable Runnable callback) {
        onEngineRecreated = callback;
    }

    @Nullable
    public static synchronized SoundEngine getCurrent() {
        if (instance == null) {
            return configure(48000, 4096, 2);
        }
        return instance;
    }

    public static synchronized SoundEngine getInstance(int sampleRate, int maxBlockSize, int channelCount) {
        return configure(sampleRate, maxBlockSize, channelCount);
    }

    public static synchronized SoundEngine configure(int sampleRate, int maxBlockSize, int channelCount) {
        if (sampleRate <= 0 || maxBlockSize <= 0 || channelCount <= 0) {
            throw new IllegalArgumentException("Audio format values must be positive");
        }
        if (instance != null
                && SoundEngineHolder.sampleRate == sampleRate
                && SoundEngineHolder.maxBlockSize == maxBlockSize
                && SoundEngineHolder.channelCount == channelCount) {
            return instance;
        }
        boolean wasLive = instance != null;
        if (instance != null) {
            instance.close();
            instance = null;
        }
        instance = new SoundEngine(sampleRate, maxBlockSize, channelCount);
        SoundEngineHolder.sampleRate = sampleRate;
        SoundEngineHolder.maxBlockSize = maxBlockSize;
        SoundEngineHolder.channelCount = channelCount;
        if (wasLive) {
            Runnable cb = onEngineRecreated;
            if (cb != null) {
                try { cb.run(); } catch (Exception ignored) { }
            }
        }
        return instance;
    }

    public static synchronized int getSampleRate() { return sampleRate; }
    public static synchronized boolean isReady() { return instance != null; }

    public static synchronized void release() {
        if (instance != null) {
            instance.close();
            instance = null;
        }
        sampleRate = 0;
        maxBlockSize = 0;
        channelCount = 0;
    }
}
