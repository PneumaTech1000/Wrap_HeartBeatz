package com.giga.tech1000.heartbeatz.view_models.extended_models;

import com.giga.tech1000.heartbeatz.app_worker.HeartBeatzApp;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.OptIn;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.media3.common.util.UnstableApi;

import com.giga.tech1000.heartbeatz.architecture.repositories.PlaybackStateRepository;
import com.giga.tech1000.heartbeatz.ui.UIThread;
import com.giga.tech1000.media_player.engine.AudioEngine;
import com.giga.tech1000.media_player.utils.enums.EqPreset;
import com.giga.tech1000.heartbeatz.utils.Preset;
import com.giga.tech1000.heartbeatz.utils.PresetManager;
import com.giga.tech1000.heartbeatz.utils.audio.ParametricEQBand;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * ViewModel for EqualizerViewPanel
 *
 * Replaces direct HeartBeatzApp.container(getApplication()).requireUiThread() calls with injected repository access.
 * Manages playback controls (speed, pitch) and equalizer settings.
 *
 * This ViewModel encapsulates all equalizer panel functionality
 * and exposes it through clean LiveData interfaces.
 */
@OptIn(markerClass = UnstableApi.class)
public class EqualizerViewModel extends AndroidViewModel {

    private static final String TAG = "EqualizerViewModel";

// For configuration persistence
private static final String PREFS_NAME_LAST_STATE = "heartbeatz_last_state";
private static final String KEY_LAST_STATE_JSON = "last_state_json";

    private final PlaybackStateRepository playbackState;
    private AudioEngine audioEngine;
    private final PresetManager presetManager;

    // Equalizer state - Enhanced for parametric EQ
    private final MutableLiveData<Boolean> equalizerEnabled = new MutableLiveData<>(false);
    private final MutableLiveData<Preset> currentPreset = new MutableLiveData<>(new Preset());
    private final MutableLiveData<List<ParametricEQBand>> eqBands = new MutableLiveData<>(new ArrayList<>());

    // Effects state
    private final MutableLiveData<Boolean> bassEnabled = new MutableLiveData<>(false);
    private final MutableLiveData<Boolean> virtualizerEnabled = new MutableLiveData<>(false);
    private final MutableLiveData<Boolean> loudnessEnabled = new MutableLiveData<>(false);
    private final MutableLiveData<Boolean> reverbEnabled = new MutableLiveData<>(false);
    private final MutableLiveData<Integer> reverbRoomLevel = new MutableLiveData<>(0);
    private final MutableLiveData<Integer> reverbDecayTime = new MutableLiveData<>(1000);
    private final MutableLiveData<Boolean> stereoWideningEnabled = new MutableLiveData<>(false);
    private final MutableLiveData<Float> stereoWideningWidth = new MutableLiveData<>(0.5f);
    private final MutableLiveData<Boolean> exciterEnabled = new MutableLiveData<>(false);
    private final MutableLiveData<Float> exciterAmount = new MutableLiveData<>(0.0f);
    private final MutableLiveData<Float> exciterFrequency = new MutableLiveData<>(2000.0f);
    private final MutableLiveData<Boolean> compressorEnabled = new MutableLiveData<>(false);
    private final MutableLiveData<Float> compressorThreshold = new MutableLiveData<>(-20.0f);
    private final MutableLiveData<Float> compressorRatio = new MutableLiveData<>(4.0f);
    private final MutableLiveData<Float> compressorAttack = new MutableLiveData<>(10.0f);
    private final MutableLiveData<Float> compressorRelease = new MutableLiveData<>(100.0f);
    private final MutableLiveData<Boolean> limiterEnabled = new MutableLiveData<>(false);
    private final MutableLiveData<Float> limiterThreshold = new MutableLiveData<>(-3.0f);
    private final MutableLiveData<Boolean> noiseGateEnabled = new MutableLiveData<>(false);
    private final MutableLiveData<Float> noiseGateThreshold = new MutableLiveData<>(-60.0f);
    private final MutableLiveData<Boolean> deEsserEnabled = new MutableLiveData<>(false);
    private final MutableLiveData<Float> deEsserThreshold = new MutableLiveData<>(-20.0f);
    private final MutableLiveData<Float> deEsserFrequency = new MutableLiveData<>(5000.0f);

    // Playback control state
    private final MutableLiveData<Float> currentTempo = new MutableLiveData<>(1.0f);
    private final MutableLiveData<Float> currentPitch = new MutableLiveData<>(0.0f);

    // A/B comparison state
    private final MutableLiveData<Preset> presetPresetA = new MutableLiveData<>(new Preset());
    private final MutableLiveData<Preset> presetPresetB = new MutableLiveData<>(new Preset());
    private final MutableLiveData<Boolean> abComparisonEnabled = new MutableLiveData<>(false);

    public EqualizerViewModel(@NonNull Application application) {
        super(application);
        PlaybackStateRepository repo = null;
        try {
            UIThread ui = HeartBeatzApp.container(getApplication()).uiThreadOrNull();
            if (ui != null) {
                repo = ui.getPlaybackStateRepository();
            }
        } catch (Exception ignored) {
        }
        this.playbackState = repo;
        this.presetManager = new PresetManager(application);
        // Initialize with default parametric bands
        initializeParametricBands();
        // Load the default preset (flat) as current
        loadDefaultPreset();
        // Try to load last state from SharedPreferences
        loadStateFromSharedPreferences();
    }

    /**
     * Constructor with dependency injection (for testing)
     */
    public EqualizerViewModel(@NonNull Application application,
                              @NonNull PlaybackStateRepository playbackStateRepo,
                              @NonNull AudioEngine audioEngineInstance) {
        super(application);
        this.playbackState = playbackStateRepo;
        this.audioEngine = audioEngineInstance;
        this.presetManager = new PresetManager(application);
        // Initialize with default parametric bands
        initializeParametricBands();
        // Load the default preset (flat) as current
        loadDefaultPreset();
        // Try to load last state from SharedPreferences
        loadStateFromSharedPreferences();
    }

    public void setAudioEngine(AudioEngine audioEngine) {
        this.audioEngine = audioEngine;
        if (audioEngine != null) {
            // Apply current state to the new engine
            Preset current = currentPreset.getValue();
            if (current != null) {
                setPreset(current);
            }
            
            // Ensure master switch state is applied
            if (Boolean.TRUE.equals(equalizerEnabled.getValue())) {
                audioEngine.enableEqualizer(true);
            }
        }
    }

    // ============ PARAMETRIC EQ STATE ============

    /**
     * Get equalizer enabled state
     */
    @NonNull
    public LiveData<Boolean> isEqualizerEnabled() {
        return equalizerEnabled;
    }

    /**
     * Set equalizer enabled state
     */
    public void setEqualizerEnabled(boolean enabled) {
        equalizerEnabled.setValue(enabled);
        if (audioEngine != null) {
            audioEngine.enableEqualizer(enabled);
        }
    }

    /**
     * Get current EQ preset
     */
    @NonNull
    public LiveData<Preset> getCurrentPreset() {
        return currentPreset;
    }

    /**
     * Set EQ preset from an EqPreset enum (legacy support)
     */
    public void setPreset(@NonNull EqPreset eqPreset) {
        // Map EqPreset to a Preset object
        Preset preset = new Preset();
        preset.setName(eqPreset.getLabel());
        preset.setCategory("Built-in");

        int[] dbLevels = eqPreset.getDbLevels();
        List<ParametricEQBand> bands = new ArrayList<>();

        // Map the fixed band gains to our parametric bands
        // Standard frequencies for parametric EQ (logarithmic spacing)
        float[] frequencies = {
            32, 64, 125, 250, 500, 1000, 2000, 4000, 8000, 16000
        };

        for (int i = 0; i < Math.min(frequencies.length, dbLevels.length); i++) {
            bands.add(new ParametricEQBand(i, frequencies[i], (float) dbLevels[i], 1.4f));
        }

        preset.setEqBands(bands);

        // Apply it
        setPreset(preset);
    }

    /**
     * Set EQ preset from a Preset object
     */
    public void setPreset(@NonNull Preset preset) {
        currentPreset.setValue(preset);
        // Update EQ bands to match the preset
        eqBands.setValue(preset.getEqBands());

        // Update basic effects states
        bassEnabled.setValue(preset.isBassEnabled());
        virtualizerEnabled.setValue(preset.isVirtualizerEnabled());
        loudnessEnabled.setValue(preset.isLoudnessEnabled());
        currentTempo.setValue(preset.getTempo());
        currentPitch.setValue(preset.getPitch());

        // Update advanced effects states
        reverbEnabled.setValue(preset.isReverbEnabled());
        reverbRoomLevel.setValue(preset.getReverbRoomLevel());
        reverbDecayTime.setValue(preset.getReverbDecayTime());
        stereoWideningEnabled.setValue(preset.isStereoWideningEnabled());
        stereoWideningWidth.setValue(preset.getStereoWideningWidth());
        exciterEnabled.setValue(preset.isExciterEnabled());
        exciterAmount.setValue(preset.getExciterAmount());
        exciterFrequency.setValue(preset.getExciterFrequency());
        compressorEnabled.setValue(preset.isCompressorEnabled());
        compressorThreshold.setValue(preset.getCompressorThreshold());
        compressorRatio.setValue(preset.getCompressorRatio());
        compressorAttack.setValue(preset.getCompressorAttack());
        compressorRelease.setValue(preset.getCompressorRelease());
        limiterEnabled.setValue(preset.isLimiterEnabled());
        limiterThreshold.setValue(preset.getLimiterThreshold());
        noiseGateEnabled.setValue(preset.isNoiseGateEnabled());
        noiseGateThreshold.setValue(preset.getNoiseGateThreshold());
        deEsserEnabled.setValue(preset.isDeEsserEnabled());
        deEsserThreshold.setValue(preset.getDeEsserThreshold());
        deEsserFrequency.setValue(preset.getDeEsserFrequency());

        if (audioEngine != null) {
            // Apply EQ bands to hardware
            applyParametricBandsToHardwareFromList(preset.getEqBands());

            // Apply basic effects
            audioEngine.enableBass(preset.isBassEnabled());
            audioEngine.setBassBoost(preset.isBassEnabled() ? (short) preset.getBassStrength() : (short) 0);

            audioEngine.enableVirtualizer(preset.isVirtualizerEnabled());
            audioEngine.setVirtualizer(preset.isVirtualizerEnabled() ? (short) preset.getVirtualizerStrength() : (short) 0);

            audioEngine.enableLoudness(preset.isLoudnessEnabled());
            audioEngine.setLoudnessGain(preset.isLoudnessEnabled() ? preset.getLoudnessGain() : 0);

            // Apply advanced effects
            audioEngine.setReverbEnabled(preset.isReverbEnabled());
            if (preset.isReverbEnabled()) {
                audioEngine.setReverbProperties(preset.getReverbRoomLevel(), preset.getReverbDecayTime());
            }

            audioEngine.setStereoWideningEnabled(preset.isStereoWideningEnabled());
            audioEngine.setStereoWideningWidth(preset.getStereoWideningWidth());

            audioEngine.setExciterEnabled(preset.isExciterEnabled());
            audioEngine.setExciterAmount(preset.getExciterAmount());
            audioEngine.setExciterFrequency(preset.getExciterFrequency());

            audioEngine.setCompressorEnabled(preset.isCompressorEnabled());
            audioEngine.setCompressorThreshold(preset.getCompressorThreshold());
            audioEngine.setCompressorRatio(preset.getCompressorRatio());
            audioEngine.setCompressorAttack(preset.getCompressorAttack());
            audioEngine.setCompressorRelease(preset.getCompressorRelease());

            audioEngine.setLimiterEnabled(preset.isLimiterEnabled());
            audioEngine.setLimiterThreshold(preset.getLimiterThreshold());

            audioEngine.setNoiseGateEnabled(preset.isNoiseGateEnabled());
            audioEngine.setNoiseGateThreshold(preset.getNoiseGateThreshold());

            audioEngine.setDeEsserEnabled(preset.isDeEsserEnabled());
            audioEngine.setDeEsserThreshold(preset.getDeEsserThreshold());
            audioEngine.setDeEsserFrequency(preset.getDeEsserFrequency());
        }
    }

    /**
     * Get current parametric EQ bands
     */
    @NonNull
    public LiveData<List<ParametricEQBand>> getEqBands() {
        return eqBands;
    }

    /**
     * Set parametric EQ band parameters
     */
    public void setEqBand(int bandIndex, float frequencyHz, float gainDb, float qFactor) {
        List<ParametricEQBand> bands = eqBands.getValue();
        if (bands != null && bandIndex >= 0 && bandIndex < bands.size()) {
            ParametricEQBand band = bands.get(bandIndex);
            band.setFrequencyHz(frequencyHz);
            band.setGainDb(gainDb);
            band.setQFactor(qFactor);

            if (audioEngine != null && bandIndex < 10) {
                audioEngine.setParametricEqualizerBand(
                        bandIndex, frequencyHz, gainDb, qFactor,
                        Boolean.TRUE.equals(equalizerEnabled.getValue()));
            }

            // Notify observers of change
            eqBands.setValue(new ArrayList<>(bands));

            // Apply to audio engine if available and equalizer is enabled
            if (audioEngine != null && Boolean.TRUE.equals(equalizerEnabled.getValue())) {
                applyBandToHardware(bandIndex, band);
            }

            // Save state to SharedPreferences
            saveStateToSharedPreferences();
        }
    }

    public void setStereoWidening(boolean enabled, float width) {
        stereoWideningEnabled.setValue(enabled);
        stereoWideningWidth.setValue(width);
        if (audioEngine != null) {
            audioEngine.setStereoWideningEnabled(enabled);
            audioEngine.setStereoWideningWidth(width);
        }
    }

    public void setExciter(boolean enabled, float amount, float frequency) {
        exciterEnabled.setValue(enabled);
        exciterAmount.setValue(amount);
        exciterFrequency.setValue(frequency);
        if (audioEngine != null) {
            audioEngine.setExciterEnabled(enabled);
            audioEngine.setExciterAmount(amount);
            audioEngine.setExciterFrequency(frequency);
        }
    }

    public void setCompressor(boolean enabled, float threshold, float ratio,
                               float attack, float release) {
        compressorEnabled.setValue(enabled);
        compressorThreshold.setValue(threshold);
        compressorRatio.setValue(ratio);
        compressorAttack.setValue(attack);
        compressorRelease.setValue(release);
        if (audioEngine != null) {
            audioEngine.setCompressorEnabled(enabled);
            audioEngine.setCompressorThreshold(threshold);
            audioEngine.setCompressorRatio(ratio);
            audioEngine.setCompressorAttack(attack);
            audioEngine.setCompressorRelease(release);
        }
    }

    public void setLimiter(boolean enabled, float threshold) {
        limiterEnabled.setValue(enabled);
        limiterThreshold.setValue(threshold);
        if (audioEngine != null) {
            audioEngine.setLimiterEnabled(enabled);
            audioEngine.setLimiterThreshold(threshold);
        }
    }

    public void setNoiseGate(boolean enabled, float threshold) {
        noiseGateEnabled.setValue(enabled);
        noiseGateThreshold.setValue(threshold);
        if (audioEngine != null) {
            audioEngine.setNoiseGateEnabled(enabled);
            audioEngine.setNoiseGateThreshold(threshold);
        }
    }

    public void setDeEsser(boolean enabled, float threshold, float frequency) {
        deEsserEnabled.setValue(enabled);
        deEsserThreshold.setValue(threshold);
        deEsserFrequency.setValue(frequency);
        if (audioEngine != null) {
            audioEngine.setDeEsserEnabled(enabled);
            audioEngine.setDeEsserThreshold(threshold);
            audioEngine.setDeEsserFrequency(frequency);
        }
    }

    /**
     * Apply all parametric bands to hardware
     */
    private void applyParametricBandsToHardware(EqPreset preset) {
        if (audioEngine == null) return;

        // For preset application, we still need to map to the fixed-band equalizer
        // since Android's Equalizer FX doesn't support true parametric EQ
        applyPresetToFixedBands(preset);
    }

    /**
     * Apply a single band to hardware (approximation for fixed-band equalizer)
     */
    private void applyBandToHardware(int bandIndex, ParametricEQBand band) {
        if (audioEngine == null || band == null) return;
        boolean on = Boolean.TRUE.equals(equalizerEnabled.getValue());
        audioEngine.setParametricEqualizerBand(
                bandIndex,
                band.getFrequencyHz(),
                band.getGainDb(),
                band.getQFactor(),
                on && band.isEnabled());
    }

    /**
     * Convert parametric bands to fixed-band equalizer gains
     */
    private short[] convertParametricToFixedBands(List<ParametricEQBand> parametricBands) {
        if (audioEngine == null) return new short[0];

        short bandCount = audioEngine.getNumberOfBands();
        short minLevel = audioEngine.getMinBandLevelRange();
        short maxLevel = audioEngine.getMaxBandLevelRange();
        short[] fixedGains = new short[bandCount];

        // Initialize all bands to 0dB (center position)
        int center = (maxLevel - minLevel) / 2;
        for (int i = 0; i < bandCount; i++) {
            fixedGains[i] = (short) center;
        }

        // Apply each parametric band's influence to the fixed bands
        for (ParametricEQBand paramBand : parametricBands) {
            float freq = paramBand.getFrequencyHz();
            float gain = paramBand.getGainDb();
            float q = paramBand.getQFactor();

            // Calculate influence of this parametric band on each fixed band
            for (int i = 0; i < bandCount; i++) {
                float fixedFreq = audioEngine.getEqualizerCenterFreq((short) i);
                float influence = calculateBandInfluence(freq, fixedFreq, q);
                int currentGain = fixedGains[i] - minLevel; // Convert to 0-based

                // Apply gain proportional to influence
                int newGain = Math.max(0, Math.min(maxLevel - minLevel,
                    currentGain + Math.round(influence * (gain / 15.0f) * (maxLevel - minLevel) / 2)));
                fixedGains[i] = (short) (minLevel + newGain);
            }
        }

        return fixedGains;
    }

    /**
     * Calculate the influence of a parametric band on a fixed frequency band
     * based on distance and Q-factor (bandwidth)
     */
    private float calculateBandInfluence(float paramFreq, float fixedFreq, float qFactor) {
        // Avoid division by zero
        if (fixedFreq <= 0) return 0f;

        // Calculate octave distance
        float octaves = (float) Math.log(fixedFreq / paramFreq) / (float) Math.log(2);

        // Calculate bandwidth in octaves based on Q-factor
        // Bandwidth in octaves = 1 / (Q * sqrt(2))
        float bandwidthOctaves = 1.0f / (qFactor * (float) Math.sqrt(2.0));

        // Calculate influence using Gaussian curve
        float influence = (float) Math.exp(-0.5f * Math.pow(octaves / bandwidthOctaves, 2));

        return influence;
    }

    /**
     * Apply preset to fixed-band equalizer (for compatibility with Android Equalizer FX)
     */
    private void applyPresetToFixedBands(EqPreset preset) {
        if (audioEngine == null) return;

        short bandCount = audioEngine.getNumberOfBands();
        short minLevel = audioEngine.getMinBandLevelRange();
        short maxLevel = audioEngine.getMaxBandLevelRange();

        int[] dbLevels = preset.getDbLevels();

        for (int i = 0; i < Math.min(bandCount, dbLevels.length); i++) {
            int levelMb = dbLevels[i] * 100;
            levelMb = Math.max(minLevel, Math.min(maxLevel, levelMb));

            int progress = levelMb - minLevel;
            audioEngine.setEqualizerBandLevel((short) i, (short) progress);
        }
    }

    /**
     * Initialize default parametric bands (20Hz - 20kHz, logarithmic spacing)
     */
    private void initializeParametricBands() {
        List<ParametricEQBand> bands = new ArrayList<>();

        // Standard frequencies for parametric EQ (can be adjusted by user)
        // ISO 10-band centers — matches DSPark UI and SoundEngine band indices 0..9
        float[] frequencies = {
            31.5f, 63f, 125f, 250f, 500f, 1000f, 2000f, 4000f, 8000f, 16000f
        };

        for (int i = 0; i < frequencies.length; i++) {
            bands.add(new ParametricEQBand(i, frequencies[i], 0f, 1.4f));
        }

        eqBands.setValue(bands);

        // Initialize A/B presets as copies
        Preset presetA = new Preset();
        presetA.setName("Preset A");
        presetA.setCategory("Temporary");
        presetA.setEqBands(new ArrayList<>(bands));
        presetPresetA.setValue(presetA);

        Preset presetB = new Preset();
        presetB.setName("Preset B");
        presetB.setCategory("Temporary");
        presetB.setEqBands(new ArrayList<>(bands));
        presetPresetB.setValue(presetB);
    }

    /**
     * Load the default preset (flat) as the current preset
     */
    private void loadDefaultPreset() {
        Preset defaultPreset = new Preset();
        defaultPreset.setName("Flat");
        defaultPreset.setCategory("Reference");
        currentPreset.setValue(defaultPreset);

        // Also update the EQ bands to match the preset
        eqBands.setValue(new ArrayList<>(defaultPreset.getEqBands()));
    }

    /**
     * Deep copy of parametric EQ bands
     */
    private List<ParametricEQBand> deepCopyBands(List<ParametricEQBand> original) {
        if (original == null) return new ArrayList<>();
        List<ParametricEQBand> copy = new ArrayList<>();
        for (ParametricEQBand band : original) {
            copy.add(new ParametricEQBand(
                band.getBandId(),
                band.getFrequencyHz(),
                band.getGainDb(),
                band.getQFactor()
            ));
        }
        return copy;
    }

    // ============ A/B COMPARISON ============

    // A/B comparison methods
    /**
     * Save current state to preset A
     */
    public void saveToPresetA() {
        Preset current = new Preset();
        current.setName("Preset A");
        current.setCategory("Temporary");

        // Copy EQ bands
        List<ParametricEQBand> currentBands = eqBands.getValue();
        if (currentBands != null) {
            current.setEqBands(new ArrayList<>(currentBands));
        }

        // Copy basic effects states
        current.setBassEnabled(bassEnabled.getValue());
        current.setVirtualizerEnabled(virtualizerEnabled.getValue());
        current.setLoudnessEnabled(loudnessEnabled.getValue());
        current.setTempo(currentTempo.getValue());
        current.setPitch(currentPitch.getValue());

        // Copy advanced effects states
        reverbEnabled.observeForever(current::setReverbEnabled);
        reverbRoomLevel.observe((LifecycleOwner) this, current::setReverbRoomLevel);
        current.setReverbDecayTime(reverbDecayTime.getValue());
        current.setStereoWideningEnabled(stereoWideningEnabled.getValue());
        current.setStereoWideningWidth(stereoWideningWidth.getValue());
        current.setExciterEnabled(exciterEnabled.getValue());
        current.setExciterAmount(exciterAmount.getValue());
        current.setExciterFrequency(exciterFrequency.getValue());
        current.setCompressorEnabled(compressorEnabled.getValue());
        current.setCompressorThreshold(compressorThreshold.getValue());
        current.setCompressorRatio(compressorRatio.getValue());
        current.setCompressorAttack(compressorAttack.getValue());
        current.setCompressorRelease(compressorRelease.getValue());
        current.setLimiterEnabled(limiterEnabled.getValue());
        current.setLimiterThreshold(limiterThreshold.getValue());
        current.setNoiseGateEnabled(noiseGateEnabled.getValue());
        current.setNoiseGateThreshold(noiseGateThreshold.getValue());
        current.setDeEsserEnabled(deEsserEnabled.getValue());
        current.setDeEsserThreshold(deEsserThreshold.getValue());
        current.setDeEsserFrequency(deEsserFrequency.getValue());

        presetPresetA.setValue(current);
    }

    /**
     * Save current state to preset B
     */
    public void saveToPresetB() {
        Preset current = new Preset();
        current.setName("Preset B");
        current.setCategory("Temporary");

        // Copy EQ bands
        List<ParametricEQBand> currentBands = eqBands.getValue();
        if (currentBands != null) {
            current.setEqBands(new ArrayList<>(currentBands));
        }

        // Copy basic effects states
        current.setBassEnabled(bassEnabled.getValue());
        current.setVirtualizerEnabled(virtualizerEnabled.getValue());
        current.setLoudnessEnabled(loudnessEnabled.getValue());
        current.setTempo(currentTempo.getValue());
        current.setPitch(currentPitch.getValue());

        // Copy advanced effects states
        current.setReverbEnabled(reverbEnabled.getValue());
        current.setReverbRoomLevel(reverbRoomLevel.getValue());
        current.setReverbDecayTime(reverbDecayTime.getValue());
        current.setStereoWideningEnabled(stereoWideningEnabled.getValue());
        current.setStereoWideningWidth(stereoWideningWidth.getValue());
        current.setExciterEnabled(exciterEnabled.getValue());
        current.setExciterAmount(exciterAmount.getValue());
        current.setExciterFrequency(exciterFrequency.getValue());
        current.setCompressorEnabled(compressorEnabled.getValue());
        current.setCompressorThreshold(compressorThreshold.getValue());
        current.setCompressorRatio(compressorRatio.getValue());
        current.setCompressorAttack(compressorAttack.getValue());
        current.setCompressorRelease(compressorRelease.getValue());
        current.setLimiterEnabled(limiterEnabled.getValue());
        current.setLimiterThreshold(limiterThreshold.getValue());
        current.setNoiseGateEnabled(noiseGateEnabled.getValue());
        current.setNoiseGateThreshold(noiseGateThreshold.getValue());
        current.setDeEsserEnabled(deEsserEnabled.getValue());
        current.setDeEsserThreshold(deEsserThreshold.getValue());
        current.setDeEsserFrequency(deEsserFrequency.getValue());

        presetPresetB.setValue(current);
    }

    /**
     * Load state from preset A
     */
    public void loadFromPresetA() {
        Preset preset = presetPresetA.getValue();
        if (preset != null) {
            setPreset(preset);
        }
    }

    /**
     * Load state from preset B
     */
    public void loadFromPresetB() {
        Preset preset = presetPresetB.getValue();
        if (preset != null) {
            setPreset(preset);
        }
    }

    /**
     * Get preset A
     */
    @NonNull
    public LiveData<Preset> getPresetA() {
        return presetPresetA;
    }

    /**
     * Get preset B
     */
    @NonNull
    public LiveData<Preset> getPresetB() {
        return presetPresetB;
    }

    /**
     * Get A/B comparison enabled state
     */
    @NonNull
    public LiveData<Boolean> isAbComparisonEnabled() {
        return abComparisonEnabled;
    }

    /**
     * Set A/B comparison enabled state
     */
    public void setAbComparisonEnabled(boolean enabled) {
        abComparisonEnabled.setValue(enabled);
        if (enabled) {
            // When enabling A/B, show preset B
            loadFromPresetB();
        } else {
            // When disabling, return to current (preset A) state
            loadFromPresetA();
        }
    }

    // ============ PRESET MANAGEMENT ============

    /**
     * Save the current state as a named preset
     */
    public void saveCurrentAsPreset(@NonNull String name, @NonNull String category, @NonNull String tags) {
        Preset preset = new Preset();
        preset.setName(name);
        preset.setCategory(category);
        preset.setTags(tags);
        preset.setDescription("Saved from current equalizer state");

        // Copy EQ bands
        List<ParametricEQBand> currentBands = eqBands.getValue();
        if (currentBands != null) {
            preset.setEqBands(new ArrayList<>(currentBands));
        }

        // Copy basic effects states
        preset.setBassEnabled(bassEnabled.getValue());
        preset.setBassStrength((int) (bassEnabled.getValue() ? 500 : 0)); // Default strength if enabled
        preset.setVirtualizerEnabled(virtualizerEnabled.getValue());
        preset.setVirtualizerStrength((int) (virtualizerEnabled.getValue() ? 500 : 0));
        preset.setLoudnessEnabled(loudnessEnabled.getValue());
        preset.setLoudnessGain((int) (loudnessEnabled.getValue() ? 0 : 0)); // Default gain
        preset.setTempo(currentTempo.getValue());
        preset.setPitch(currentPitch.getValue());

        // Copy advanced effects states
        preset.setReverbEnabled(reverbEnabled.getValue());
        preset.setReverbRoomLevel(reverbRoomLevel.getValue());
        preset.setReverbDecayTime(reverbDecayTime.getValue());
        preset.setStereoWideningEnabled(stereoWideningEnabled.getValue());
        preset.setStereoWideningWidth(stereoWideningWidth.getValue());
        preset.setExciterEnabled(exciterEnabled.getValue());
        preset.setExciterAmount(exciterAmount.getValue());
        preset.setExciterFrequency(exciterFrequency.getValue());
        preset.setCompressorEnabled(compressorEnabled.getValue());
        preset.setCompressorThreshold(compressorThreshold.getValue());
        preset.setCompressorRatio(compressorRatio.getValue());
        preset.setCompressorAttack(compressorAttack.getValue());
        preset.setCompressorRelease(compressorRelease.getValue());
        preset.setLimiterEnabled(limiterEnabled.getValue());
        preset.setLimiterThreshold(limiterThreshold.getValue());
        preset.setNoiseGateEnabled(noiseGateEnabled.getValue());
        preset.setNoiseGateThreshold(noiseGateThreshold.getValue());
        preset.setDeEsserEnabled(deEsserEnabled.getValue());
        preset.setDeEsserThreshold(deEsserThreshold.getValue());
        preset.setDeEsserFrequency(deEsserFrequency.getValue());

        // Save to preset manager
        presetManager.savePreset(preset);
    }

    /**
     * Load a preset by ID and apply it
     */
    public void loadPresetById(String presetId) {
        Preset preset = presetManager.loadPreset(presetId);
        if (preset != null) {
            setPreset(preset);
        }
    }

    /**
     * Get all saved presets
     */
    @NonNull
    public LiveData<List<Preset>> getAllPresetsLiveData() {
        // Note: For a complete implementation, we'd use LiveData or Flow to observe changes
        // For now, we'll return a wrapper that gets the current list
        // In a real app, you'd want to use MediatorLiveData or similar to update when presets change
        return new MutableLiveData<>(presetManager.getAllPresets());
    }

    /**
     * Get suggested presets based on audio analysis
     * This is a placeholder - in a real implementation, this would use actual audio analysis
     */
    @NonNull
    public List<Preset> getSuggestedPresets() {
        // For now, return presets sorted by usage count
        // In a real implementation, this would analyze the current audio and suggest presets
        List<Preset> allPresets = presetManager.getAllPresets();
        // Sort by usage count (most used first)
        Collections.sort(allPresets, Comparator.comparingInt(Preset::getUsageCount).reversed());
        // Return top 5
        int count = Math.min(5, allPresets.size());
        return allPresets.subList(0, count);
    }

    /**
     * Delete a preset by ID
     */
    public boolean deletePreset(String presetId) {
        return presetManager.deletePreset(presetId);
    }

    /**
     * Export a preset to a file
     */
    public boolean exportPreset(String presetId, String fileName) {
        Preset preset = presetManager.loadPreset(presetId);
        if (preset != null) {
            return presetManager.exportPreset(preset, fileName);
        }
        return false;
    }

    /**
     * Import a preset from a file
     */
    public Preset importPreset(String filePath) {
        return presetManager.importPreset(filePath);
    }

    // ============ AUDIO EFFECTS ============

    /**
     * Get bass boost enabled state
     */
    @NonNull
    public LiveData<Boolean> isBassBoosted() {
        return bassEnabled;
    }

    /**
     * Set bass boost
     */
    public void setBassBoosted(boolean enabled, short strength) {
        bassEnabled.setValue(enabled);
        if (audioEngine != null) {
            audioEngine.enableBass(enabled);
            if (enabled) {
                audioEngine.setBassBoost(strength);
            }
        }
    }

    /**
     * Get virtualizer enabled state
     */
    @NonNull
    public LiveData<Boolean> isVirtualizerEnabled() {
        return virtualizerEnabled;
    }

    /**
     * Set virtualizer
     */
    public void setVirtualizer(boolean enabled, short strength) {
        virtualizerEnabled.setValue(enabled);
        if (audioEngine != null) {
            audioEngine.enableVirtualizer(enabled);
            if (enabled) {
                audioEngine.setVirtualizer(strength);
            }
        }
    }

    /**
     * Get loudness enabled state
     */
    @NonNull
    public LiveData<Boolean> isLoudnessEnabled() {
        return loudnessEnabled;
    }

    /**
     * Set loudness
     */
    public void setLoudness(boolean enabled, int gain) {
        loudnessEnabled.setValue(enabled);
        if (audioEngine != null) {
            audioEngine.enableLoudness(enabled);
            if (enabled) {
                audioEngine.setLoudnessGain(gain);
            }
        }
    }

    // ============ PLAYBACK CONTROL ============

    /**
     * Get current playback speed (tempo)
     */
    @NonNull
    public LiveData<Float> getCurrentTempo() {
        return currentTempo;
    }

    /**
     * Set playback speed
     */
    public void setTempo(float tempo) {
        currentTempo.setValue(tempo);
        if (playbackState != null) {
            playbackState.setPlaybackSpeed(tempo);
        }
    }

    /**
     * Get current pitch
     */
    @NonNull
    public LiveData<Float> getCurrentPitch() {
        return currentPitch;
    }

    /**
     * Set audio pitch
     */
    public void setPitch(float pitch) {
        currentPitch.setValue(pitch);
        if (playbackState != null) {
            playbackState.setPlaybackPitch(pitch);
        }
    }

    /**
     * Get playback state (from playback state repository)
     */
    @NonNull
    public PlaybackStateRepository getPlaybackState() {
        return playbackState;
    }

    // ============ RESET / DEFAULTS ============

    /**
     * Reset all effects to neutral/default
     */
    public void resetAllEffects() {
        setEqualizerEnabled(false);
        setBassBoosted(false, (short) 0);
        setVirtualizer(false, (short) 0);
        setLoudness(false, 0);
        setTempo(1.0f);
        setPitch(0.0f);
        resetAllBands();

        // Also reset to default preset
        loadDefaultPreset();
    }

    /**
     * Reset all EQ bands to flat (0dB gain) with default frequency and Q factor
     */
    public void resetAllBands() {
        List<ParametricEQBand> bands = eqBands.getValue();
        if (bands != null) {
            for (ParametricEQBand band : bands) {
                band.setGainDb(0f);
                band.setQFactor(1.0f);
                // Keep frequency as-is for reset (could also reset to default frequencies if desired)
            }
            eqBands.setValue(new ArrayList<>(bands)); // Notify observers

            // Apply to hardware if equalizer is enabled
            if (audioEngine != null && equalizerEnabled.getValue()) {
                applyParametricBandsToHardwareFromList(bands);
            }
        }
    }

    /**
     * Apply a list of parametric bands to hardware
     */
    private void applyParametricBandsToHardwareFromList(List<ParametricEQBand> bands) {
        if (audioEngine == null || bands == null) return;

        // Convert parametric bands to fixed-band equalizer gains and apply
        short[] fixedBandGains = convertParametricToFixedBands(bands);
        if (fixedBandGains != null) {
            short bandCount = audioEngine.getNumberOfBands();
            int bandsToApply = Math.min(bandCount, fixedBandGains.length);
            for (int i = 0; i < bandsToApply; i++) {
                audioEngine.setEqualizerBandLevel((short) i, fixedBandGains[i]);
            }
        }
    }

    /**
     * Save current state to SharedPreferences
     */
    private void saveStateToSharedPreferences() {
        try {
            Preset preset = new Preset();
            preset.setName("Last Session State");

            // Copy EQ bands
            List<ParametricEQBand> currentBands = eqBands.getValue();
            if (currentBands != null) {
                preset.setEqBands(new ArrayList<>(currentBands));
            }

            // Copy basic effects states
            preset.setBassEnabled(Boolean.TRUE.equals(bassEnabled.getValue()));
            preset.setVirtualizerEnabled(Boolean.TRUE.equals(virtualizerEnabled.getValue()));
            preset.setLoudnessEnabled(Boolean.TRUE.equals(loudnessEnabled.getValue()));
            preset.setTempo(currentTempo.getValue() != null ? currentTempo.getValue() : 1.0f);
            preset.setPitch(currentPitch.getValue() != null ? currentPitch.getValue() : 0.0f);

            // Advanced effects
            preset.setReverbEnabled(Boolean.TRUE.equals(reverbEnabled.getValue()));
            preset.setReverbRoomLevel(reverbRoomLevel.getValue() != null ? reverbRoomLevel.getValue() : 0);
            preset.setReverbDecayTime(reverbDecayTime.getValue() != null ? reverbDecayTime.getValue() : 1000);
            preset.setStereoWideningEnabled(Boolean.TRUE.equals(stereoWideningEnabled.getValue()));
            preset.setStereoWideningWidth(stereoWideningWidth.getValue() != null ? stereoWideningWidth.getValue() : 0.5f);
            preset.setExciterEnabled(Boolean.TRUE.equals(exciterEnabled.getValue()));
            preset.setExciterAmount(exciterAmount.getValue() != null ? exciterAmount.getValue() : 0.0f);
            preset.setExciterFrequency(exciterFrequency.getValue() != null ? exciterFrequency.getValue() : 2000.0f);
            preset.setCompressorEnabled(Boolean.TRUE.equals(compressorEnabled.getValue()));
            preset.setCompressorThreshold(compressorThreshold.getValue() != null ? compressorThreshold.getValue() : -20.0f);
            preset.setCompressorRatio(compressorRatio.getValue() != null ? compressorRatio.getValue() : 4.0f);
            preset.setCompressorAttack(compressorAttack.getValue() != null ? compressorAttack.getValue() : 10.0f);
            preset.setCompressorRelease(compressorRelease.getValue() != null ? compressorRelease.getValue() : 100.0f);
            preset.setLimiterEnabled(Boolean.TRUE.equals(limiterEnabled.getValue()));
            preset.setLimiterThreshold(limiterThreshold.getValue() != null ? limiterThreshold.getValue() : -3.0f);
            preset.setNoiseGateEnabled(Boolean.TRUE.equals(noiseGateEnabled.getValue()));
            preset.setNoiseGateThreshold(noiseGateThreshold.getValue() != null ? noiseGateThreshold.getValue() : -60.0f);
            preset.setDeEsserEnabled(Boolean.TRUE.equals(deEsserEnabled.getValue()));
            preset.setDeEsserThreshold(deEsserThreshold.getValue() != null ? deEsserThreshold.getValue() : -20.0f);
            preset.setDeEsserFrequency(deEsserFrequency.getValue() != null ? deEsserFrequency.getValue() : 5000.0f);

            String json = preset.toJsonString();
            getApplication().getSharedPreferences(PREFS_NAME_LAST_STATE, Context.MODE_PRIVATE)
                    .edit()
                    .putString(KEY_LAST_STATE_JSON, json)
                    .apply();
        } catch (Exception e) {
            Log.e(TAG, "Error saving state", e);
        }
    }

    /**
     * Load state from SharedPreferences
     */
    private void loadStateFromSharedPreferences() {
        try {
            String json = getApplication().getSharedPreferences(PREFS_NAME_LAST_STATE, Context.MODE_PRIVATE)
                    .getString(KEY_LAST_STATE_JSON, null);
            if (json != null) {
                Preset preset = Preset.fromJsonString(json);
                setPreset(preset);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error loading state", e);
        }
    }

    /**
     * Cleanup resources
     */

    /** Exposed for EqualizerViewPanel → SoundEngine path. */
    @Nullable
    public com.giga.tech1000.media_player.engine.AudioEngine getAudioEngineOrNull() {
        return audioEngine;
    }

    public void setBassBoost(boolean enabled, int strengthPercent) {
        setBassBoosted(enabled, (short) Math.max(0, Math.min(1000, strengthPercent * 10)));
        bassEnabled.setValue(enabled);
    }

    public void setVirtualizer(boolean enabled, int strengthPercent) {
        setVirtualizer(enabled, (short) Math.max(0, Math.min(1000, strengthPercent * 10)));
        virtualizerEnabled.setValue(enabled);
    }

    public void switchToPresetA() {
        saveToPresetB(); // stash B side current? keep simple: load A
        loadFromPresetA();
    }

    public void switchToPresetB() {
        loadFromPresetB();
    }

    public void saveCurrentAsUserPreset(@NonNull String name) {
        saveCurrentAsPreset(name, "User", "");
    }

    public void setLimiter(boolean enabled, float threshold, float releaseMs) {
        setLimiter(enabled, threshold);
        if (audioEngine != null) {
            try {
                audioEngine.setLimiterRelease(releaseMs);
            } catch (Exception ignored) { }
        }
    }

    public void setNoiseGate(boolean enabled, float threshold, float hysteresis,
                             float attack, float hold, float release) {
        setNoiseGate(enabled, threshold);
        if (audioEngine != null) {
            try {
                audioEngine.setNoiseGateHysteresis(hysteresis);
                audioEngine.setNoiseGateAttack(attack);
                audioEngine.setNoiseGateHold(hold);
                audioEngine.setNoiseGateRelease(release);
            } catch (Exception ignored) { }
        }
    }

    public void setPlaybackSpeed(float speed) {
        if (playbackState != null) {
            try { playbackState.setPlaybackSpeed(speed); } catch (Exception ignored) { }
        }
    }

    public void setPlaybackPitch(float pitch) {
        if (playbackState != null) {
            try { playbackState.setPlaybackPitch(pitch); } catch (Exception ignored) { }
        }
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        // Save state before clearing
        saveStateToSharedPreferences();
        // Do not release shared PlaybackStateRepository — owned by UIThread for app lifetime
    }

}