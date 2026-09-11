package com.giga.tech1000.soundengine;

/** Process-wide owner for the native engine used by the audio pipeline. */
public final class SoundEngineHolder {

    private static SoundEngine instance;
    private static int sampleRate;
    private static int maxBlockSize;
    private static int channelCount;

    private SoundEngineHolder() {
    }

    public static synchronized SoundEngine getInstance(
            int sampleRate, int maxBlockSize, int channelCount) {
        if (instance == null
                || SoundEngineHolder.sampleRate != sampleRate
                || SoundEngineHolder.maxBlockSize != maxBlockSize
                || SoundEngineHolder.channelCount != channelCount) {
            release();
            instance = new SoundEngine(sampleRate, maxBlockSize, channelCount);
            SoundEngineHolder.sampleRate = sampleRate;
            SoundEngineHolder.maxBlockSize = maxBlockSize;
            SoundEngineHolder.channelCount = channelCount;
        }
        return instance;
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