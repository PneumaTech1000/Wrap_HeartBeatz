package com.giga.tech1000.soundengine;

import androidx.annotation.Nullable;

/**
 * Process-wide owner for the native DSPark engine.
 * <p>
 * {@link #configure(int, int, int)} is used by the Media3 processor (authoritative sample rate).
 * {@link #getCurrent()} is used by {@code AudioEngine} UI/control path — never tears down
 * a running engine just because the control path assumes 48 kHz.
 */
public final class SoundEngineHolder {

    private static SoundEngine instance;
    private static int sampleRate;
    private static int maxBlockSize;
    private static int channelCount;

    private SoundEngineHolder() {
    }

    /**
     * Return the live engine, creating a default 48 kHz stereo instance if needed.
     * Does <b>not</b> recreate when an engine already exists with a different rate.
     */
    @Nullable
    public static synchronized SoundEngine getCurrent() {
        if (instance == null) {
            return configure(48000, 4096, 2);
        }
        return instance;
    }

    /**
     * Ensure an engine exists matching the audio pipeline format.
     * Only recreates when format actually changes.
     */
    public static synchronized SoundEngine getInstance(
            int sampleRate, int maxBlockSize, int channelCount) {
        return configure(sampleRate, maxBlockSize, channelCount);
    }

    public static synchronized SoundEngine configure(
            int sampleRate, int maxBlockSize, int channelCount) {
        if (sampleRate <= 0 || maxBlockSize <= 0 || channelCount <= 0) {
            throw new IllegalArgumentException("Audio format values must be positive");
        }
        if (instance != null
                && SoundEngineHolder.sampleRate == sampleRate
                && SoundEngineHolder.maxBlockSize == maxBlockSize
                && SoundEngineHolder.channelCount == channelCount) {
            return instance;
        }
        release();
        instance = new SoundEngine(sampleRate, maxBlockSize, channelCount);
        SoundEngineHolder.sampleRate = sampleRate;
        SoundEngineHolder.maxBlockSize = maxBlockSize;
        SoundEngineHolder.channelCount = channelCount;
        return instance;
    }

    public static synchronized int getSampleRate() {
        return sampleRate;
    }

    public static synchronized boolean isReady() {
        return instance != null;
    }

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
