package com.giga.tech1000.media_player;

import android.content.Context;
import android.media.audiofx.BassBoost;
import android.media.audiofx.Equalizer;
import android.media.audiofx.LoudnessEnhancer;
import android.media.audiofx.Virtualizer;
import android.media.audiofx.EnvironmentalReverb;
import android.os.Build;

public final class AudioEngine {

    private final Equalizer equalizer;
    private final BassBoost bassBoost;
    private final Virtualizer virtualizer;
    private final LoudnessEnhancer loudnessEnhancer;
    private final EnvironmentalReverb reverb;

    private final Context context;

    // Advanced effects (implemented via DSP engine)
    private boolean stereoWideningEnabled;
    private float stereoWideningWidth; // 0.0 to 1.0
    private boolean exciterEnabled;
    private float exciterAmount; // 0.0 to 1.0
    private float exciterFrequency; // Hz
    private boolean compressorEnabled;
    private float compressorThreshold; // dB
    private float compressorRatio; // 1.0 to inf
    private float compressorAttack; // ms
    private float compressorRelease; // ms
    private boolean limiterEnabled;
    private float limiterThreshold; // dB
    private boolean noiseGateEnabled;
    private float noiseGateThreshold; // dB
    private float noiseGateHysteresis; // dB
    private float noiseGateAttack; // ms
    private float noiseGateHold; // ms
    private float noiseGateRelease; // ms
    private float noiseGateRange; // dB
    private boolean noiseGateDuckMode;
    private boolean deEsserEnabled;
    private float deEsserThreshold; // dB
    private float deEsserFrequency; // Hz

    // FFT/Spectrum Analysis


    public AudioEngine(int sessionId, Context context) {
        this.context = context.getApplicationContext();

        try {
            equalizer = new Equalizer(0, sessionId);
            equalizer.setEnabled(false);
        } catch (IllegalArgumentException e) {
            // Handle invalid session ID
            equalizer = null;
        }

        try {
            bassBoost = new BassBoost(0, sessionId);
            bassBoost.setEnabled(false);
        } catch (IllegalArgumentException e) {
            // Handle invalid session ID
            bassBoost = null;
        }

        try {
            virtualizer = new Virtualizer(0, sessionId);
            virtualizer.setEnabled(false);
        } catch (IllegalArgumentException e) {
            // Handle invalid session ID
            virtualizer = null;
        }

        // Initialize EnvironmentalReverb if available (API 9+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.GINGERBREAD) {
            try {
                reverb = new EnvironmentalReverb(0, sessionId);
                reverb.setEnabled(false);
            } catch (IllegalArgumentException e) {
                // Handle invalid session ID or missing feature
                reverb = null;
            }
        } else {
            reverb = null;
        }

        try {
            loudnessEnhancer = new LoudnessEnhancer(sessionId);
            loudnessEnhancer.setEnabled(false);
        } catch (IllegalArgumentException e) {
            // Handle invalid session ID
            loudnessEnhancer = null;
        }

        // Initialize advanced effects with default values
        stereoWideningEnabled = false;
        stereoWideningWidth = 0.5f;
        exciterEnabled = false;
        exciterAmount = 0.0f;
        exciterFrequency = 2000.0f; // 2kHz
        compressorEnabled = false;
        compressorThreshold = -20.0f; // dB
        compressorRatio = 4.0f;
        compressorAttack = 10.0f; // ms
        compressorRelease = 100.0f; // ms
        limiterEnabled = false;
        limiterThreshold = -3.0f; // dB
        noiseGateEnabled = false;
        noiseGateThreshold = -60.0f; // dB
        deEsserEnabled = false;
        deEsserThreshold = -20.0f; // dB
        deEsserFrequency = 5000.0f; // 5kHz

    }

    /**
     * Gets the DSP engine instance from the holder.
     * @return the DSP engine, or null if context is not available.
     */
    private SoundEngine getDspEngine() {
        if (context == null) {
            return null;
        }
        return DspEngineHolder.getInstance(context);
    }

    // Helper to apply effect to both Android audiofx (if applicable) and DSP engine
    private void setStereoWideningEnabledInternal(boolean enabled) {
        this.stereoWideningEnabled = enabled;
        SoundEngine dsp = getDspEngine();
        if (dsp != null) {
            // TODO: Implement effect enable/disable in SoundEngine
        }
    }

    private void setStereoWideningWidthInternal(float width) {
        this.stereoWideningWidth = width;
        SoundEngine dsp = getDspEngine();
        if (dsp != null) {
            // TODO: Implement effect parameter in SoundEngine
        }
    }

    private void setExciterEnabledInternal(boolean enabled) {
        this.exciterEnabled = enabled;
        SoundEngine dsp = getDspEngine();
        if (dsp != null) {
            dsp.setExciterEnabled(enabled);
        }
    }

    private void setExciterAmountInternal(float amount) {
        this.exciterAmount = amount;
        SoundEngine dsp = getDspEngine();
        if (dsp != null) {
            dsp.setExciterAmount(amount);
        }
    }

    private void setExciterFrequencyInternal(float frequency) {
        this.exciterFrequency = frequency;
        SoundEngine dsp = getDspEngine();
        if (dsp != null) {
            dsp.setExciterFrequency(frequency);
        }
    }

    private void setCompressorEnabledInternal(boolean enabled) {
        this.compressorEnabled = enabled;
        SoundEngine dsp = getDspEngine();
        if (dsp != null) {
            dsp.setCompressorEnabled(enabled);
        }
    }

    private void setCompressorThresholdInternal(float threshold) {
        this.compressorThreshold = threshold;
        SoundEngine dsp = getDspEngine();
        if (dsp != null) {
            dsp.setCompressorThreshold(threshold);
        }
    }

    private void setCompressorRatioInternal(float ratio) {
        this.compressorRatio = ratio;
        SoundEngine dsp = getDspEngine();
        if (dsp != null) {
            dsp.setCompressorRatio(ratio);
        }
    }

    private void setCompressorAttackInternal(float attack) {
        this.compressorAttack = attack;
        SoundEngine dsp = getDspEngine();
        if (dsp != null) {
            dsp.setCompressorAttack(attack);
        }
    }

    private void setCompressorReleaseInternal(float release) {
        this.compressorRelease = release;
        SoundEngine dsp = getDspEngine();
        if (dsp != null) {
            dsp.setCompressorRelease(release);
        }
    }

    private void setLimiterEnabledInternal(boolean enabled) {
        this.limiterEnabled = enabled;
        SoundEngine dsp = getDspEngine();
        if (dsp != null) {
            dsp.setLimiterEnabled(enabled);
        }
    }

    private void setLimiterThresholdInternal(float threshold) {
        this.limiterThreshold = threshold;
        SoundEngine dsp = getDspEngine();
        if (dsp != null) {
            dsp.setLimiterThreshold(threshold);
        }
    }

    private void setLimiterReleaseInternal(float release) {
        // Note: we don't have a release parameter in the limiter? Actually we do in DSP.
        // But we don't have a liveData for limiter release in ViewModel; we have only threshold.
        // We'll ignore for now, or we could add later.
    }

    private void setNoiseGateEnabledInternal(boolean enabled) {
        this.noiseGateEnabled = enabled;
        SoundEngine dsp = getDspEngine();
        if (dsp != null) {
            dsp.setNoiseGateEnabled(enabled);
        }
    }

    private void setNoiseGateThresholdInternal(float threshold) {
        this.noiseGateThreshold = threshold;
        SoundEngine dsp = getDspEngine();
        if (dsp != null) {
            dsp.setNoiseGateThreshold(threshold);
        }
    }

    private void setNoiseGateHysteresisInternal(float hysteresis) {
        this.noiseGateHysteresis = hysteresis;
        SoundEngine dsp = getDspEngine();
        if (dsp != null) {
            dsp.setNoiseGateHysteresis(hysteresis);
        }
    }

    private void setNoiseGateAttackInternal(float attack) {
        this.noiseGateAttack = attack;
        SoundEngine dsp = getDspEngine();
        if (dsp != null) {
            dsp.setNoiseGateAttack(attack);
        }
    }

    private void setNoiseGateHoldInternal(float hold) {
        this.noiseGateHold = hold;
        SoundEngine dsp = getDspEngine();
        if (dsp != null) {
            dsp.setNoiseGateHold(hold);
        }
    }

    private void setNoiseGateReleaseInternal(float release) {
        this.noiseGateRelease = release;
        SoundEngine dsp = getDspEngine();
        if (dsp != null) {
            dsp.setNoiseGateRelease(release);
        }
    }

    private void setNoiseGateRangeInternal(float range) {
        this.noiseGateRange = range;
        SoundEngine dsp = getDspEngine();
        if (dsp != null) {
            dsp.setNoiseGateRange(range);
        }
    }

    private void setNoiseGateDuckModeInternal(boolean duckMode) {
        this.noiseGateDuckMode = duckMode;
        SoundEngine dsp = getDspEngine();
        if (dsp != null) {
            dsp.setNoiseGateDuckMode(duckMode);
        }
    }

    private void setDeEsserEnabledInternal(boolean enabled) {
        this.deEsserEnabled = enabled;
        SoundEngine dsp = getDspEngine();
        if (dsp != null) {
            dsp.setDeEsserEnabled(enabled);
        }
    }

    private void setDeEsserThresholdInternal(float threshold) {
        this.deEsserThreshold = threshold;
        SoundEngine dsp = getDspEngine();
        if (dsp != null) {
            dsp.setDeEsserThreshold(threshold);
        }
    }

    private void setDeEsserFrequencyInternal(float frequency) {
        this.deEsserFrequency = frequency;
        SoundEngine dsp = getDspEngine();
        if (dsp != null) {
            dsp.setDeEsserFrequency(frequency);
        }
    }

    // ========= FFT/Spectrum Analysis =========
    private boolean isSpectrumDataReadyInternal() {
        SoundEngine dsp = getDspEngine();
        if (dsp != null) {
            return dsp.isSpectrumDataReady();
        }
        return false;
    }

    private int getSpectrumNumBinsInternal() {
        SoundEngine dsp = getDspEngine();
        if (dsp != null) {
            return dsp.getSpectrumNumBins();
        }
        return 0;
    }

    private float getSpectrumBinFrequencyInternal(int binIndex) {
        SoundEngine dsp = getDspEngine();
        if (dsp != null) {
            return dsp.getSpectrumBinFrequency(binIndex);
        }
        return 0.0f;
    }

    private void getSpectrumMagnitudesInternal(float[] magnitudeArray) {
        SoundEngine dsp = getDspEngine();
        if (dsp != null) {
            dsp.getSpectrumMagnitudes(magnitudeArray);
        }
    }

    private void getSpectrumPeakHoldInternal(float[] peakHoldArray) {
        SoundEngine dsp = getDspEngine();
        if (dsp != null) {
            dsp.getSpectrumPeakHold(peakHoldArray);
        }
    }

    public boolean isBassEnabled() {
        return bassBoost.getEnabled();
    }

    public boolean isVirtualizerEnabled() {
        return virtualizer.getEnabled();
    }

    public boolean isEqualizerEnabled() {
        return equalizer.getEnabled();
    }

    public boolean isReverbEnabled() {
        return reverb != null && reverb.getEnabled();
    }

    public boolean isStereoWideningEnabled() {
        return stereoWideningEnabled;
    }

    public boolean isExciterEnabled() {
        return exciterEnabled;
    }

    public boolean isCompressorEnabled() {
        return compressorEnabled;
    }

    public boolean isLimiterEnabled() {
        return limiterEnabled;
    }

    public boolean isNoiseGateEnabled() {
        return noiseGateEnabled;
    }

    public boolean isDeEsserEnabled() {
        return deEsserEnabled;
    }

    // ========= Equalizer =========
    public void setEqualizerBandLevel(short band, short millibels) {
        if (equalizer == null) return;

        try {
            short min = equalizer.getBandLevelRange()[0];
            short max = equalizer.getBandLevelRange()[1];

            millibels = (short) Math.max(min, Math.min(max, millibels));
            equalizer.setBandLevel(band, millibels);
        } catch (IllegalArgumentException e) {
            // Handle invalid band index
        }
    }

    public short getNumberOfBands() {
        return equalizer.getNumberOfBands();
    }

    public short getMinBandLevelRange() {
        return equalizer.getBandLevelRange()[0];
    }

    public short getMaxBandLevelRange() {
        return equalizer.getBandLevelRange()[1];
    }

    public int getEqualizerCenterFreq(short band) {
        return equalizer.getCenterFreq(band);
    }

    public void enableEqualizer(boolean enable) {
        if (equalizer == null) return;

        try {
            equalizer.setEnabled(enable);
        } catch (IllegalArgumentException e) {
            // Handle if equalizer is not available
        }
    }

    // ========= Bass =========
    public void setBassBoost(int strengthPercent) {
        if (bassBoost == null) return;

        try {
            bassBoost.setStrength((short) Math.min(1000, strengthPercent * 10));
        } catch (IllegalArgumentException e) {
            // Handle invalid strength value
        }
    }

    public void enableBass(boolean enable) {
        if (bassBoost == null) return;

        try {
            bassBoost.setEnabled(enable);
        } catch (IllegalArgumentException e) {
            // Handle if bass boost is not available
        }
    }

    // ========= Virtualizer (legacy) =========
    public void setVirtualizer(int strengthPercent) {
        virtualizer.setStrength((short) Math.min(1000, strengthPercent * 10));
    }

    public void enableVirtualizer(boolean enable) {
        virtualizer.setEnabled(enable);
    }

    // ========= Loudness Enhancer =========
    public void setLoudnessGain(int gainMb) {
        loudnessEnhancer.setTargetGain(gainMb);
    }

    public void enableLoudness(boolean enable) {
        loudnessEnhancer.setEnabled(enable);
    }

    // ========= Reverb =========
    public void setReverbEnabled(boolean enable) {
        if (reverb != null) {
            reverb.setEnabled(enable);
        }
    }

    public void setReverbProperties(int roomLevel, int decayTime) {
        if (reverb != null) {
            // These are simplified - in practice you'd use EnvironmentalReverb.Settings
            try {
                reverb.setRoomLevel((short) roomLevel);
                reverb.setDecayTime((short) decayTime);
            } catch (IllegalArgumentException e) {
                // Invalid settings
            }
        }
    }

    // ========= Stereo Widening =========
    public void setStereoWideningEnabled(boolean enabled) {
        setStereoWideningEnabledInternal(enabled);
    }

    public void setStereoWideningWidth(float width) {
        float clampedWidth = Math.max(0.0f, Math.min(1.0f, width));
        setStereoWideningWidthInternal(clampedWidth);
    }

    // ========= Exciter =========
    public void setExciterEnabled(boolean enabled) {
        setExciterEnabledInternal(enabled);
    }

    public void setExciterAmount(float amount) {
        float clampedAmount = Math.max(0.0f, Math.min(1.0f, amount));
        setExciterAmountInternal(clampedAmount);
    }

    public void setExciterFrequency(float frequency) {
        float clampedFrequency = Math.max(20.0f, Math.min(20000.0f, frequency));
        setExciterFrequencyInternal(clampedFrequency);
    }

    // ========= Compressor =========
    public void setCompressorEnabled(boolean enabled) {
        setCompressorEnabledInternal(enabled);
    }

    public void setCompressorThreshold(float threshold) {
        setCompressorThresholdInternal(threshold);
    }

    public void setCompressorRatio(float ratio) {
        float clampedRatio = Math.max(1.0f, ratio);
        setCompressorRatioInternal(clampedRatio);
    }

    public void setCompressorAttack(float attack) {
        float clampedAttack = Math.max(0.0f, attack);
        setCompressorAttackInternal(clampedAttack);
    }

    public void setCompressorRelease(float release) {
        float clampedRelease = Math.max(0.0f, release);
        setCompressorReleaseInternal(clampedRelease);
    }

    // ========= Limiter =========
    public void setLimiterEnabled(boolean enabled) {
        setLimiterEnabledInternal(enabled);
    }

    public void setLimiterThreshold(float threshold) {
        setLimiterThresholdInternal(threshold);
    }

    // ========= Noise Gate =========
    public void setNoiseGateEnabled(boolean enabled) {
        setNoiseGateEnabledInternal(enabled);
    }

    public void setNoiseGateThreshold(float threshold) {
        setNoiseGateThresholdInternal(threshold);
    }

    // ========= De-esser =========
    public void setDeEsserEnabled(boolean enabled) {
        setDeEsserEnabledInternal(enabled);
    }

    public void setDeEsserThreshold(float threshold) {
        setDeEsserThresholdInternal(threshold);
    }

    public void setDeEsserFrequency(float frequency) {
        float clampedFrequency = Math.max(2000.0f, Math.min(20000.0f, frequency));
        setDeEsserFrequencyInternal(clampedFrequency);
    }

    // ========= Cleanup =========
    public void release() {
        equalizer.release();
        bassBoost.release();
        virtualizer.release();
        loudnessEnhancer.release();
        if (reverb != null) {
            reverb.release();
        }
    }

}
