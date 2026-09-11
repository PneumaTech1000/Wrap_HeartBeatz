package com.giga.tech1000.soundengine;

import androidx.annotation.NonNull;

/** Native DSPark host and real-time effect chain. */
public final class SoundEngine implements AutoCloseable {

    static {
        System.loadLibrary("sound_engine");
    }

    private long nativeHandle;

    public SoundEngine(int sampleRate, int maxBlockSize, int channelCount) {
        if (sampleRate <= 0 || maxBlockSize <= 0 || channelCount <= 0) {
            throw new IllegalArgumentException("Audio format values must be positive");
        }
        nativeHandle = nativeCreate(sampleRate, maxBlockSize, channelCount);
        if (nativeHandle == 0) {
            throw new IllegalStateException("Unable to create native DSPark engine");
        }
    }

    public void process(@NonNull float[] samples) {
        process(samples, samples.length);
    }

    public void process(@NonNull float[] samples, int sampleCount) {
        if (sampleCount <= 0) return;
        if (sampleCount > samples.length) {
            throw new IllegalArgumentException("sampleCount exceeds sample array length");
        }
        long handle = nativeHandle;
        if (handle == 0) {
            throw new IllegalStateException("SoundEngine has been released");
        }
        nativeProcess(handle, samples, sampleCount);
    }

    public void setEqualizerEnabled(boolean enabled) {
        nativeSetEffectEnabled(requireHandle(), 0, enabled);
    }

    public void setEqualizerBand(int index, float frequency, float gainDb, float q, boolean enabled) {
        nativeSetEqBand(requireHandle(), index, frequency, gainDb, q, enabled);
    }

    public void setStereoWideningEnabled(boolean enabled) {
        nativeSetEffectEnabled(requireHandle(), 6, enabled);
    }

    public void setStereoWideningWidth(float width) {
        nativeSetStereoWidth(requireHandle(), width);
    }

    public void setExciterEnabled(boolean enabled) {
        nativeSetEffectEnabled(requireHandle(), 1, enabled);
    }

    public void setExciterAmount(float amount) {
        nativeSetExciterAmount(requireHandle(), amount);
    }

    public void setExciterFrequency(float frequency) {
        nativeSetExciterFrequency(requireHandle(), frequency);
    }

    public void setCompressorEnabled(boolean enabled) {
        nativeSetEffectEnabled(requireHandle(), 2, enabled);
    }

    public void setCompressorThreshold(float threshold) {
        nativeSetCompressorThreshold(requireHandle(), threshold);
    }

    public void setCompressorRatio(float ratio) {
        nativeSetCompressorRatio(requireHandle(), ratio);
    }

    public void setCompressorAttack(float attack) {
        nativeSetCompressorAttack(requireHandle(), attack);
    }

    public void setCompressorRelease(float release) {
        nativeSetCompressorRelease(requireHandle(), release);
    }

    public void setLimiterEnabled(boolean enabled) {
        nativeSetEffectEnabled(requireHandle(), 3, enabled);
    }

    public void setLimiterThreshold(float threshold) {
        nativeSetLimiterThreshold(requireHandle(), threshold);
    }

    public void setLimiterRelease(float release) {
        nativeSetLimiterRelease(requireHandle(), release);
    }

    public void setNoiseGateEnabled(boolean enabled) {
        nativeSetEffectEnabled(requireHandle(), 4, enabled);
    }

    public void setNoiseGateThreshold(float threshold) {
        nativeSetNoiseGateThreshold(requireHandle(), threshold);
    }

    public void setNoiseGateHysteresis(float hysteresis) {
        nativeSetNoiseGateHysteresis(requireHandle(), hysteresis);
    }

    public void setNoiseGateAttack(float attack) {
        nativeSetNoiseGateAttack(requireHandle(), attack);
    }

    public void setNoiseGateHold(float hold) {
        nativeSetNoiseGateHold(requireHandle(), hold);
    }

    public void setNoiseGateRelease(float release) {
        nativeSetNoiseGateRelease(requireHandle(), release);
    }

    public void setNoiseGateRange(float range) {
        nativeSetNoiseGateRange(requireHandle(), range);
    }

    public void setNoiseGateDuckMode(boolean enabled) {
        nativeSetNoiseGateDuckMode(requireHandle(), enabled);
    }

    public void setDeEsserEnabled(boolean enabled) {
        nativeSetEffectEnabled(requireHandle(), 5, enabled);
    }

    public void setDeEsserThreshold(float threshold) {
        nativeSetDeEsserThreshold(requireHandle(), threshold);
    }

    public void setDeEsserFrequency(float frequency) {
        nativeSetDeEsserFrequency(requireHandle(), frequency);
    }

    public void setDeEsserAttack(float attack) {
        nativeSetDeEsserAttack(requireHandle(), attack);
    }

    public void setDeEsserRelease(float release) {
        nativeSetDeEsserRelease(requireHandle(), release);
    }

    public boolean isSpectrumDataReady() {
        return nativeIsSpectrumDataReady(requireHandle());
    }

    public int getSpectrumNumBins() {
        return nativeGetSpectrumNumBins(requireHandle());
    }

    public float getSpectrumBinFrequency(int binIndex) {
        return nativeGetSpectrumBinFrequency(requireHandle(), binIndex);
    }

    public void getSpectrumMagnitudes(@NonNull float[] destination) {
        nativeCopySpectrum(requireHandle(), destination, false);
    }

    public void getSpectrumPeakHold(@NonNull float[] destination) {
        nativeCopySpectrum(requireHandle(), destination, true);
    }

    private long requireHandle() {
        long handle = nativeHandle;
        if (handle == 0) {
            throw new IllegalStateException("SoundEngine has been released");
        }
        return handle;
    }

    @Override
    public void close() {
        long handle = nativeHandle;
        nativeHandle = 0;
        if (handle != 0) {
            nativeDestroy(handle);
        }
    }

    private static native long nativeCreate(int sampleRate, int maxBlockSize, int channelCount);

    private static native void nativeDestroy(long handle);

    private static native void nativeProcess(long handle, float[] samples, int sampleCount);

    private static native void nativeSetEffectEnabled(long handle, int effect, boolean enabled);

    private static native void nativeSetEqBand(
            long handle, int index, float frequency, float gainDb, float q, boolean enabled);

    private static native void nativeSetStereoWidth(long handle, float width);

    private static native void nativeSetExciterAmount(long handle, float amount);

    private static native void nativeSetExciterFrequency(long handle, float frequency);

    private static native void nativeSetCompressorThreshold(long handle, float threshold);

    private static native void nativeSetCompressorRatio(long handle, float ratio);

    private static native void nativeSetCompressorAttack(long handle, float attack);

    private static native void nativeSetCompressorRelease(long handle, float release);

    private static native void nativeSetLimiterThreshold(long handle, float threshold);

    private static native void nativeSetLimiterRelease(long handle, float release);

    private static native void nativeSetNoiseGateThreshold(long handle, float threshold);

    private static native void nativeSetNoiseGateHysteresis(long handle, float hysteresis);

    private static native void nativeSetNoiseGateAttack(long handle, float attack);

    private static native void nativeSetNoiseGateHold(long handle, float hold);

    private static native void nativeSetNoiseGateRelease(long handle, float release);

    private static native void nativeSetNoiseGateRange(long handle, float range);

    private static native void nativeSetNoiseGateDuckMode(long handle, boolean enabled);

    private static native void nativeSetDeEsserThreshold(long handle, float threshold);

    private static native void nativeSetDeEsserFrequency(long handle, float frequency);

    private static native void nativeSetDeEsserAttack(long handle, float attack);

    private static native void nativeSetDeEsserRelease(long handle, float release);

    private static native boolean nativeIsSpectrumDataReady(long handle);

    private static native int nativeGetSpectrumNumBins(long handle);

    private static native float nativeGetSpectrumBinFrequency(long handle, int binIndex);

    private static native void nativeCopySpectrum(long handle, float[] destination, boolean peakHold);
}