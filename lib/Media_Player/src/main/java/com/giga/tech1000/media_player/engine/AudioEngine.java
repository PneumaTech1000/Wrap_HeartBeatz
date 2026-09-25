package com.giga.tech1000.media_player.engine;

import android.content.Context;

import com.giga.tech1000.soundengine.SoundEngine;
import com.giga.tech1000.soundengine.SoundEngineHolder;

/**
 * UI-facing audio effect controller.
 * All processing is delegated to the native {@link SoundEngine} (DSPark) via
 * {@link DspAudioProcessor} in the Media3 pipeline. No Android {@code audiofx} APIs.
 */
public final class AudioEngine {

    private static final int NUM_BANDS = 10;
    /** Standard 10-band center frequencies (Hz), matching Sound_Engine defaults. */
    private static final float[] CENTER_FREQS_HZ = {
            31f, 62f, 125f, 250f, 500f, 1000f, 2000f, 4000f, 8000f, 16000f
    };
    private static final float DEFAULT_Q = 0.707f;
    /** Band level range in millibels (compatible with old audiofx-style API). */
    private static final short MIN_BAND_MB = -1500;
    private static final short MAX_BAND_MB = 1500;

    private final Context context;

    private boolean equalizerEnabled;
    private final short[] bandLevelsMb = new short[NUM_BANDS];
    private final boolean[] bandEnabled = new boolean[NUM_BANDS];

    private boolean bassEnabled;
    private int bassStrength; // 0–1000 (legacy scale)

    private boolean virtualizerEnabled;
    private int virtualizerStrength; // 0–1000

    private boolean loudnessEnabled;
    private int loudnessGainMb;

    private boolean reverbEnabled;
    private int reverbRoomLevel;
    private int reverbDecayTime;

    private boolean stereoWideningEnabled;
    private float stereoWideningWidth;
    private boolean exciterEnabled;
    private float exciterAmount;
    private float exciterFrequency;
    private boolean compressorEnabled;
    private float compressorThreshold;
    private float compressorRatio;
    private float compressorAttack;
    private float compressorRelease;
    private boolean limiterEnabled;
    private float limiterThreshold;
    private boolean noiseGateEnabled;
    private float noiseGateThreshold;
    private float noiseGateHysteresis;
    private float noiseGateAttack;
    private float noiseGateHold;
    private float noiseGateRelease;
    private float noiseGateRange;
    private boolean noiseGateDuckMode;
    private boolean deEsserEnabled;
    private float deEsserThreshold;
    private float deEsserFrequency;

    /**
     * @param sessionId retained for call-site compatibility; unused (DSP is not session-bound).
     */
    public AudioEngine(int sessionId, Context context) {
        this.context = context != null ? context.getApplicationContext() : null;

        equalizerEnabled = false;
        for (int i = 0; i < NUM_BANDS; i++) {
            bandLevelsMb[i] = 0;
            bandEnabled[i] = true; // band slots armed; master switch gates processing
        }

        bassEnabled = false;
        bassStrength = 0;
        virtualizerEnabled = false;
        virtualizerStrength = 0;
        loudnessEnabled = false;
        loudnessGainMb = 0;
        reverbEnabled = false;
        reverbRoomLevel = 0;
        reverbDecayTime = 1000;

        stereoWideningEnabled = false;
        stereoWideningWidth = 0.5f;
        exciterEnabled = false;
        exciterAmount = 0.0f;
        exciterFrequency = 2000.0f;
        compressorEnabled = false;
        compressorThreshold = -20.0f;
        compressorRatio = 4.0f;
        compressorAttack = 10.0f;
        compressorRelease = 100.0f;
        limiterEnabled = false;
        limiterThreshold = -3.0f;
        noiseGateEnabled = false;
        noiseGateThreshold = -60.0f;
        noiseGateHysteresis = 6.0f;
        noiseGateAttack = 1.0f;
        noiseGateHold = 20.0f;
        noiseGateRelease = 100.0f;
        noiseGateRange = -80.0f;
        noiseGateDuckMode = false;
        deEsserEnabled = false;
        deEsserThreshold = -20.0f;
        deEsserFrequency = 5000.0f;
    }

    private SoundEngine getDspEngine() {
        // Always share the processor's engine — never force 48 kHz recreate.
        return SoundEngineHolder.getCurrent();
    }

    private static float mbToDb(short millibels) {
        return millibels / 100.0f;
    }

    private static short clampBandMb(short millibels) {
        if (millibels < MIN_BAND_MB) return MIN_BAND_MB;
        if (millibels > MAX_BAND_MB) return MAX_BAND_MB;
        return millibels;
    }

    private void applyEqBandToDsp(int band) {
        SoundEngine dsp = getDspEngine();
        if (dsp == null || band < 0 || band >= NUM_BANDS) return;
        boolean on = equalizerEnabled && bandEnabled[band];
        dsp.setEqualizerBand(band, CENTER_FREQS_HZ[band], mbToDb(bandLevelsMb[band]), DEFAULT_Q, on);
    }

    private void applyAllEqBandsToDsp() {
        SoundEngine dsp = getDspEngine();
        if (dsp == null) return;
        dsp.setEqualizerEnabled(equalizerEnabled);
        for (int i = 0; i < NUM_BANDS; i++) {
            boolean on = equalizerEnabled && bandEnabled[i];
            dsp.setEqualizerBand(i, CENTER_FREQS_HZ[i], mbToDb(bandLevelsMb[i]), DEFAULT_Q, on);
        }
    }

    // --- Enable queries ---

    public boolean isBassEnabled() {
        return bassEnabled;
    }

    public boolean isVirtualizerEnabled() {
        return virtualizerEnabled;
    }

    public boolean isEqualizerEnabled() {
        return equalizerEnabled;
    }

    public boolean isReverbEnabled() {
        return reverbEnabled;
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

    // ========= Equalizer (fixed-band API → parametric DSP) =========

    public void setEqualizerBandLevel(short band, short millibels) {
        if (band < 0 || band >= NUM_BANDS) return;
        bandLevelsMb[band] = clampBandMb(millibels);
        bandEnabled[band] = true;
        applyEqBandToDsp(band);
    }

    public short getNumberOfBands() {
        return NUM_BANDS;
    }

    public short getMinBandLevelRange() {
        return MIN_BAND_MB;
    }

    public short getMaxBandLevelRange() {
        return MAX_BAND_MB;
    }

    /** Center frequency in millihertz (mHz), matching old audiofx {@code getCenterFreq} units. */
    public int getEqualizerCenterFreq(short band) {
        if (band < 0 || band >= NUM_BANDS) return 0;
        return Math.round(CENTER_FREQS_HZ[band] * 1000f);
    }

    public void enableEqualizer(boolean enable) {
        equalizerEnabled = enable;
        applyAllEqBandsToDsp();
    }

    public void setParametricEqualizerBand(
            int band, float frequencyHz, float gainDb, float qFactor, boolean enabled) {
        SoundEngine dsp = getDspEngine();
        if (dsp == null) return;
        if (band >= 0 && band < NUM_BANDS) {
            bandLevelsMb[band] = clampBandMb((short) Math.round(gainDb * 100f));
            bandEnabled[band] = enabled;
        }
        // `enabled` already includes master EQ from the ViewModel. Also keep field in sync
        // so applyAllEqBandsToDsp() stays correct.
        if (enabled) {
            equalizerEnabled = true;
        }
        boolean bandOn = enabled && equalizerEnabled;
        dsp.setEqualizerEnabled(equalizerEnabled);
        dsp.setEqualizerBand(band, frequencyHz, gainDb, qFactor, bandOn);
    }

    public boolean isSpectrumDataReady() {
        SoundEngine dsp = getDspEngine();
        return dsp != null && dsp.isSpectrumDataReady();
    }

    public int getSpectrumNumBins() {
        SoundEngine dsp = getDspEngine();
        return dsp == null ? 0 : dsp.getSpectrumNumBins();
    }

    public void getSpectrumMagnitudes(float[] destination) {
        SoundEngine dsp = getDspEngine();
        if (dsp != null) {
            dsp.getSpectrumMagnitudes(destination);
        }
    }

    public void getSpectrumPeakHold(float[] destination) {
        SoundEngine dsp = getDspEngine();
        if (dsp != null) {
            dsp.getSpectrumPeakHold(destination);
        }
    }

    // ========= Bass → low EQ bands on DSP =========

    public void setBassBoost(int strengthPercent) {
        bassStrength = Math.max(0, Math.min(1000, strengthPercent));
        if (bassEnabled) {
            applyBassToDsp();
        }
    }

    public void enableBass(boolean enable) {
        bassEnabled = enable;
        applyBassToDsp();
    }

    private void applyBassToDsp() {
        SoundEngine dsp = getDspEngine();
        if (dsp == null) return;
        // Map 0–1000 strength → 0–12 dB on 31 Hz and 62 Hz bands
        float gainDb = bassEnabled ? (bassStrength / 1000f) * 12f : 0f;
        boolean on = bassEnabled && gainDb != 0f;
        dsp.setEqualizerBand(0, CENTER_FREQS_HZ[0], gainDb, DEFAULT_Q, on);
        dsp.setEqualizerBand(1, CENTER_FREQS_HZ[1], gainDb * 0.75f, DEFAULT_Q, on);
        if (equalizerEnabled) {
            // Re-apply user EQ on those bands if EQ is on (bass stacks as override when enabled)
            if (!bassEnabled) {
                applyEqBandToDsp(0);
                applyEqBandToDsp(1);
            }
        }
        dsp.setEqualizerEnabled(equalizerEnabled || bassEnabled);
    }

    // ========= Virtualizer → stereo widening on DSP =========

    public void setVirtualizer(int strengthPercent) {
        virtualizerStrength = Math.max(0, Math.min(1000, strengthPercent));
        float width = virtualizerStrength / 1000f;
        stereoWideningWidth = width;
        SoundEngine dsp = getDspEngine();
        if (dsp != null) {
            dsp.setStereoWideningWidth(width);
        }
    }

    public void enableVirtualizer(boolean enable) {
        virtualizerEnabled = enable;
        stereoWideningEnabled = enable;
        SoundEngine dsp = getDspEngine();
        if (dsp != null) {
            dsp.setStereoWideningEnabled(enable);
            if (enable) {
                dsp.setStereoWideningWidth(virtualizerStrength / 1000f);
            }
        }
    }

    // ========= Loudness → mild broadband EQ lift on DSP =========

    public void setLoudnessGain(int gainMb) {
        loudnessGainMb = gainMb;
        if (loudnessEnabled) {
            applyLoudnessToDsp();
        }
    }

    public void enableLoudness(boolean enable) {
        loudnessEnabled = enable;
        applyLoudnessToDsp();
    }

    private void applyLoudnessToDsp() {
        SoundEngine dsp = getDspEngine();
        if (dsp == null) return;
        // Approximate loudness enhancer with a gentle presence lift (500 Hz–4 kHz)
        float gainDb = loudnessEnabled ? Math.max(-6f, Math.min(12f, loudnessGainMb / 100f)) : 0f;
        boolean on = loudnessEnabled && Math.abs(gainDb) > 0.01f;
        for (int band = 3; band <= 7; band++) {
            if (equalizerEnabled && bandEnabled[band]) {
                continue; // respect explicit EQ bands
            }
            dsp.setEqualizerBand(band, CENTER_FREQS_HZ[band], gainDb, 0.5f, on);
        }
        if (on) {
            dsp.setEqualizerEnabled(true);
        } else if (equalizerEnabled) {
            applyAllEqBandsToDsp();
        }
    }

    // ========= Reverb (no native reverb yet — state only for UI/presets) =========

    public void setReverbEnabled(boolean enable) {
        reverbEnabled = enable;
        // Sound_Engine has no reverb module yet; state kept for preset/UI compatibility.
    }

    public void setReverbProperties(int roomLevel, int decayTime) {
        reverbRoomLevel = roomLevel;
        reverbDecayTime = decayTime;
    }

    // ========= Stereo Widening =========

    public void setStereoWideningEnabled(boolean enabled) {
        stereoWideningEnabled = enabled;
        SoundEngine dsp = getDspEngine();
        if (dsp != null) {
            dsp.setStereoWideningEnabled(enabled);
        }
    }

    public void setStereoWideningWidth(float width) {
        stereoWideningWidth = Math.max(0.0f, Math.min(1.0f, width));
        SoundEngine dsp = getDspEngine();
        if (dsp != null) {
            dsp.setStereoWideningWidth(stereoWideningWidth);
        }
    }

    // ========= Exciter =========

    public void setExciterEnabled(boolean enabled) {
        exciterEnabled = enabled;
        SoundEngine dsp = getDspEngine();
        if (dsp != null) {
            dsp.setExciterEnabled(enabled);
        }
    }

    public void setExciterAmount(float amount) {
        exciterAmount = Math.max(0.0f, Math.min(1.0f, amount));
        SoundEngine dsp = getDspEngine();
        if (dsp != null) {
            dsp.setExciterAmount(exciterAmount);
        }
    }

    public void setExciterFrequency(float frequency) {
        exciterFrequency = Math.max(20.0f, Math.min(20000.0f, frequency));
        SoundEngine dsp = getDspEngine();
        if (dsp != null) {
            dsp.setExciterFrequency(exciterFrequency);
        }
    }

    // ========= Compressor =========

    public void setCompressorEnabled(boolean enabled) {
        compressorEnabled = enabled;
        SoundEngine dsp = getDspEngine();
        if (dsp != null) {
            dsp.setCompressorEnabled(enabled);
        }
    }

    public void setCompressorThreshold(float threshold) {
        compressorThreshold = threshold;
        SoundEngine dsp = getDspEngine();
        if (dsp != null) {
            dsp.setCompressorThreshold(threshold);
        }
    }

    public void setCompressorRatio(float ratio) {
        compressorRatio = Math.max(1.0f, ratio);
        SoundEngine dsp = getDspEngine();
        if (dsp != null) {
            dsp.setCompressorRatio(compressorRatio);
        }
    }

    public void setCompressorAttack(float attack) {
        compressorAttack = Math.max(0.0f, attack);
        SoundEngine dsp = getDspEngine();
        if (dsp != null) {
            dsp.setCompressorAttack(compressorAttack);
        }
    }

    public void setCompressorRelease(float release) {
        compressorRelease = Math.max(0.0f, release);
        SoundEngine dsp = getDspEngine();
        if (dsp != null) {
            dsp.setCompressorRelease(compressorRelease);
        }
    }

    // ========= Limiter =========

    public void setLimiterEnabled(boolean enabled) {
        limiterEnabled = enabled;
        SoundEngine dsp = getDspEngine();
        if (dsp != null) {
            dsp.setLimiterEnabled(enabled);
        }
    }

    public void setLimiterThreshold(float threshold) {
        limiterThreshold = threshold;
        SoundEngine dsp = getDspEngine();
        if (dsp != null) {
            dsp.setLimiterThreshold(threshold);
        }
    }

    // ========= Noise Gate =========

    public void setNoiseGateEnabled(boolean enabled) {
        noiseGateEnabled = enabled;
        SoundEngine dsp = getDspEngine();
        if (dsp != null) {
            dsp.setNoiseGateEnabled(enabled);
        }
    }

    public void setNoiseGateThreshold(float threshold) {
        noiseGateThreshold = threshold;
        SoundEngine dsp = getDspEngine();
        if (dsp != null) {
            dsp.setNoiseGateThreshold(threshold);
        }
    }

    // ========= De-esser =========

    public void setDeEsserEnabled(boolean enabled) {
        deEsserEnabled = enabled;
        SoundEngine dsp = getDspEngine();
        if (dsp != null) {
            dsp.setDeEsserEnabled(enabled);
        }
    }

    public void setDeEsserThreshold(float threshold) {
        deEsserThreshold = threshold;
        SoundEngine dsp = getDspEngine();
        if (dsp != null) {
            dsp.setDeEsserThreshold(threshold);
        }
    }

    public void setDeEsserFrequency(float frequency) {
        deEsserFrequency = Math.max(2000.0f, Math.min(20000.0f, frequency));
        SoundEngine dsp = getDspEngine();
        if (dsp != null) {
            dsp.setDeEsserFrequency(deEsserFrequency);
        }
    }

    public void setLimiterRelease(float releaseMs) {
        SoundEngine dsp = getDspEngine();
        if (dsp != null) {
            dsp.setLimiterRelease(releaseMs);
        }
    }

    public void setNoiseGateHysteresis(float hysteresis) {
        SoundEngine dsp = getDspEngine();
        if (dsp != null) {
            dsp.setNoiseGateHysteresis(hysteresis);
        }
    }

    public void setNoiseGateAttack(float attack) {
        SoundEngine dsp = getDspEngine();
        if (dsp != null) {
            dsp.setNoiseGateAttack(attack);
        }
    }

    public void setNoiseGateHold(float hold) {
        SoundEngine dsp = getDspEngine();
        if (dsp != null) {
            dsp.setNoiseGateHold(hold);
        }
    }

    public void setNoiseGateRelease(float release) {
        SoundEngine dsp = getDspEngine();
        if (dsp != null) {
            dsp.setNoiseGateRelease(release);
        }
    }

    // ========= Cleanup =========

    public void release() {
        // Native engine is shared via SoundEngineHolder / DspAudioProcessor; do not destroy here.
    }
}
