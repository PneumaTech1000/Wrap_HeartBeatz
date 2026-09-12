package com.giga.tech1000.heartbeatz.views.panels.sub_panels;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.widget.AppCompatImageButton;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.content.ContextCompat;
import androidx.core.widget.NestedScrollView;
import androidx.media3.common.util.UnstableApi;

import com.giga.tech1000.extensions.VerticalSeekBar;
import com.giga.tech1000.heartbeatz.R;
import com.giga.tech1000.heartbeatz.ui.fragments.FragmentHome;
import com.giga.tech1000.heartbeatz.utils.audio.ParametricEQBand;
import com.giga.tech1000.heartbeatz.view_models.extended_models.EqualizerViewModel;
import com.giga.tech1000.heartbeatz.views.SpectrumView;
import com.giga.tech1000.heartbeatz.views.knobs.extended.BassKnob;
import com.giga.tech1000.heartbeatz.views.knobs.extended.PitchKnob;
import com.giga.tech1000.heartbeatz.views.knobs.extended.TempoKnob;
import com.giga.tech1000.heartbeatz.views.knobs.extended.VisualizerKnob;
import com.giga.tech1000.media_player.engine.AudioEngine;
import com.giga.tech1000.media_player.utils.enums.EqPreset;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.slider.Slider;

import android.view.Gravity;
import android.widget.Toast;

import java.util.function.Consumer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Stack;
import java.util.concurrent.atomic.AtomicBoolean;

@UnstableApi
public final class EqualizerViewPanel {

    // =========================
    // Core
    // =========================

    private AudioEngine audioEngine;
    private final Context context;
    private final View rootView;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final EqualizerViewModel equalizerViewModel;
    private Runnable applyRunnable;
    private Runnable spectrumUpdateRunnable;

    private int sessionId;
    private short bandCount;
    private short minLevel;
    private short maxLevel;

    private static final int UI_BANDS = 10;
    // Spectrum update rate variables for battery efficiency
    private static final int SPECTRUM_UPDATE_FAST = 30;   // ~33 FPS when actively changing
    private static final int SPECTRUM_UPDATE_NORMAL = 50; // ~20 FPS when idle but visible
    private static final int SPECTRUM_UPDATE_SLOW = 200;  // ~5 FPS when not visible or no changes
    private int spectrumUpdateInterval = SPECTRUM_UPDATE_NORMAL;
    private long lastChangeTime = 0;
    private static final long CHANGE_TIMEOUT = 1000; // Consider changes "recent" within 1 second

    private final AtomicBoolean isVisible = new AtomicBoolean(false);
    private boolean internalUpdate = false;

    // =========================
    // Views
    // =========================

    private NestedScrollView parentView;
    private View chipsContainer;
    private View eqCard;

    private MaterialSwitch eqSwitch;
    private MaterialSwitch bassSwitch;
    private MaterialSwitch virtualizerSwitch;
    private MaterialSwitch tempoSwitch;
    private MaterialSwitch pitchSwitch;
    private MaterialSwitch loudnessSwitch;

    // Advanced Effects Views
    private MaterialSwitch reverbSwitch;
    private MaterialSwitch stereoWideningSwitch;
    private MaterialSwitch exciterSwitch;
    private MaterialSwitch compressorSwitch;
    private MaterialSwitch limiterSwitch;
    private MaterialSwitch noiseGateSwitch;
    private MaterialSwitch deEsserSwitch;

    // Containers for simple knobs (we'll add VerticalSeekBar instances programmatically)
    private LinearLayout reverbKnobContainer;
    private LinearLayout stereoWideningKnobContainer;
    private LinearLayout exciterKnobContainer;
    private LinearLayout compressorKnobContainer;
    private LinearLayout limiterKnobContainer;
    private LinearLayout noiseGateKnobContainer;
    private LinearLayout deEsserKnobContainer;

    // Simple knob values for advanced effects
    private float reverbRoomLevelValue = 0f; // -1000 to 0 mB
    private float reverbDecayTimeValue = 1000f; // 0 to 5000 ms
    private float stereoWideningWidthValue = 0.5f; // 0.0 to 1.0
    private float exciterAmountValue = 0.0f; // 0.0 to 1.0
    private float exciterFrequencyValue = 2000.0f; // 20-20000 Hz
    private float compressorThresholdValue = -20.0f; // dB, typically -60 to 0
    private float compressorRatioValue = 4.0f; // 1.0 to inf
    private float compressorAttackValue = 10.0f; // ms
    private float compressorReleaseValue = 100.0f; // ms
    private float limiterThresholdValue = -3.0f; // dB, typically -20 to 0
    private float noiseGateThresholdValue = -60.0f; // dB, typically -80 to -20
    private float deEsserThresholdValue = -20.0f; // dB, typically -40 to 0
    private float deEsserFrequencyValue = 5000.0f; // Hz, typically 2k-20kHz

    // =========================
    // KnobViews
    // =========================

    private BassKnob bassKnob;
    private VisualizerKnob virtualizerKnob;
    private TempoKnob tempoKnob;
    private PitchKnob pitchKnob;

    // =========================
    // KnobViews values
    // =========================

    private float pitchValue = 0f;
    private float tempoValue = 1.0f;
    private float virtualizerValue = 0f;
    private float bassValue = 0f;


    private View loudnessIcon;
    private View closeButton;
    private View eqReset;

    private ChipGroup presetGroup;

    // Spectrum Analyzer
    private SpectrumView spectrumView;

    // Updated: Store UI levels for each band (0-100% progress)
    private final View[] bandViews = new View[UI_BANDS];
    private final List<Integer> uiLevels = new ArrayList<>(UI_BANDS);
    // Store frequency values for each band (Hz)
    private final List<Float> freqValues = new ArrayList<>(UI_BANDS);
    // Store Q factor values for each band
    private final List<Float> qValues = new ArrayList<>(UI_BANDS);
    // Store dB value TextViews for each band
    private final TextView[] dbValueViews = new TextView[UI_BANDS];
    // Store frequency value TextViews for each band
    private final TextView[] freqValueViews = new TextView[UI_BANDS];
    // Store Q factor value TextViews for each band
    private final TextView[] qValueViews = new TextView[UI_BANDS];
    // Store frequency seekbars for each band
    private final VerticalSeekBar[] freqSeekBars = new VerticalSeekBar[UI_BANDS];
    // Store Q factor seekbars for each band
    private final VerticalSeekBar[] qSeekBars = new VerticalSeekBar[UI_BANDS];

    // Undo/Redo functionality
    private final Stack<StateSnapshot> undoStack = new Stack<>();
    private final Stack<StateSnapshot> redoStack = new Stack<>();
    private static final int MAX_HISTORY_SIZE = 50;
    private View undoButton;
    private View redoButton;

    private Slider stereoWidthSlider;
    private Slider exciterAmountSlider;
    private Slider compressorThresholdSlider;
    private Slider limiterCeilingSlider;
    private Slider noiseGateThresholdSlider;
    private Slider deEsserThresholdSlider;

    private final Map<Integer, EqPreset> presetMap = new HashMap<>();

    // =========================
    // Constructor
    // =========================

    public EqualizerViewPanel(
            @NonNull FragmentHome fragment,
            @NonNull ViewGroup parent,
            @NonNull EqualizerViewModel equalizerViewModel
    ) {
        context = fragment.requireContext();
        rootView = LayoutInflater.from(context).inflate(R.layout.media_equalizer_view, parent, false);
        this.equalizerViewModel = equalizerViewModel;

        bindViews(rootView);
        initUiState();
        mapPresets();
        setupListeners(fragment);

        // Initialize spectrum analyzer
        initSpectrumAnalyzer();

        // Observe changes to EQ bands from ViewModel
        setupEqBandObserver();
    }

    // =========================
    // Initialization
    // =========================

    private void bindViews(View v) {
        parentView = v.findViewById(R.id.eq_workspace_root);

        closeButton = v.findViewById(R.id.equalizer_view_close);
        presetGroup = v.findViewById(R.id.sound_profile_chip_group);
        eqReset = v.findViewById(R.id.eq_reset);
        undoButton = v.findViewById(R.id.eq_undo_button);
        redoButton = v.findViewById(R.id.eq_redo_button);

        ViewGroup faders = v.findViewById(R.id.faders_container);
        for (int i = 0; i < UI_BANDS; i++) {
            bandViews[i] = faders.getChildAt(i);
        }

        chipsContainer = presetGroup;
        eqCard = faders;

        eqSwitch = v.findViewById(R.id.eq_enable_disable);
        bassSwitch = v.findViewById(R.id.bass_enable_disable);
        virtualizerSwitch = v.findViewById(R.id.virtualizer_enable_disable);
        tempoSwitch = v.findViewById(R.id.tempo_enable_disable);
        pitchSwitch = v.findViewById(R.id.pitch_enable_disable);

        bassKnob = v.findViewById(R.id.knob_bass_boost);
        virtualizerKnob = v.findViewById(R.id.knob_virtualizer);
        tempoKnob = v.findViewById(R.id.knob_tempo_speed);
        pitchKnob = v.findViewById(R.id.knob_pitch_shift);
        loudnessSwitch = new MaterialSwitch(context);
        loudnessIcon = new AppCompatImageButton(context);



        // Initialize spectrum view
        spectrumView = v.findViewById(R.id.spectrum_canvas);

        // Initialize advanced effects views
        reverbSwitch = new MaterialSwitch(context);
        stereoWideningSwitch = v.findViewById(R.id.switch_stereo_enabled);
        exciterSwitch = v.findViewById(R.id.switch_exciter_enabled);
        compressorSwitch = v.findViewById(R.id.switch_compressor_enabled);
        limiterSwitch = v.findViewById(R.id.switch_limiter_enabled);
        noiseGateSwitch = v.findViewById(R.id.switch_gate_enabled);
        deEsserSwitch = v.findViewById(R.id.switch_deesser_enabled);

        reverbKnobContainer = v.findViewById(R.id.rack_stereo_content);
        stereoWideningKnobContainer = v.findViewById(R.id.rack_stereo_content);
        exciterKnobContainer = v.findViewById(R.id.rack_exciter_content);
        compressorKnobContainer = v.findViewById(R.id.rack_compressor_content);
        limiterKnobContainer = v.findViewById(R.id.rack_limiter_content);
        noiseGateKnobContainer = v.findViewById(R.id.rack_gate_content);
        deEsserKnobContainer = v.findViewById(R.id.rack_deesser_content);

        stereoWidthSlider = v.findViewById(R.id.slider_stereo_width);
        exciterAmountSlider = v.findViewById(R.id.slider_exciter_amount);
        compressorThresholdSlider = v.findViewById(R.id.slider_comp_threshold);
        limiterCeilingSlider = v.findViewById(R.id.slider_limiter_ceiling);
        noiseGateThresholdSlider = v.findViewById(R.id.slider_gate_threshold);
        deEsserThresholdSlider = v.findViewById(R.id.slider_deesser_threshold);



        // Initialize frequency value TextViews
        freqValueViews[0] = bandViews[0].findViewById(R.id.eqDbValue);
        freqValueViews[1] = bandViews[1].findViewById(R.id.eqDbValue);
        freqValueViews[2] = bandViews[2].findViewById(R.id.eqDbValue);
        freqValueViews[3] = bandViews[3].findViewById(R.id.eqDbValue);
        freqValueViews[4] = bandViews[4].findViewById(R.id.eqDbValue);
        freqValueViews[5] = bandViews[5].findViewById(R.id.eqDbValue);
        freqValueViews[6] = bandViews[6].findViewById(R.id.eqDbValue);
        freqValueViews[7] = bandViews[7].findViewById(R.id.eqDbValue);
        freqValueViews[8] = bandViews[8].findViewById(R.id.eqDbValue);
        freqValueViews[9] = bandViews[9].findViewById(R.id.eqDbValue);

        // The redesigned fader controls gain. Frequency and Q remain ViewModel values.
        freqSeekBars[0] = bandViews[0].findViewById(R.id.eqFreqSeekBar);
        freqSeekBars[1] = bandViews[1].findViewById(R.id.eqFreqSeekBar);
        freqSeekBars[2] = bandViews[2].findViewById(R.id.eqFreqSeekBar);
        freqSeekBars[3] = bandViews[3].findViewById(R.id.eqFreqSeekBar);
        freqSeekBars[4] = bandViews[4].findViewById(R.id.eqFreqSeekBar);
        freqSeekBars[5] = bandViews[5].findViewById(R.id.eqFreqSeekBar);
        freqSeekBars[6] = bandViews[6].findViewById(R.id.eqFreqSeekBar);
        freqSeekBars[7] = bandViews[7].findViewById(R.id.eqFreqSeekBar);
        freqSeekBars[8] = bandViews[8].findViewById(R.id.eqFreqSeekBar);
        freqSeekBars[9] = bandViews[9].findViewById(R.id.eqFreqSeekBar);

        // Initialize Q factor value TextViews
        qValueViews[0] = bandViews[0].findViewById(R.id.eqQValue);
        qValueViews[1] = bandViews[1].findViewById(R.id.eqQValue);
        qValueViews[2] = bandViews[2].findViewById(R.id.eqQValue);
        qValueViews[3] = bandViews[3].findViewById(R.id.eqQValue);
        qValueViews[4] = bandViews[4].findViewById(R.id.eqQValue);
        qValueViews[5] = bandViews[5].findViewById(R.id.eqQValue);
        qValueViews[6] = bandViews[6].findViewById(R.id.eqQValue);
        qValueViews[7] = bandViews[7].findViewById(R.id.eqQValue);
        qValueViews[8] = bandViews[8].findViewById(R.id.eqQValue);
        qValueViews[9] = bandViews[9].findViewById(R.id.eqQValue);

        // Initialize Q factor seekbars
        qSeekBars[0] = null;
        qSeekBars[1] = null;
        qSeekBars[2] = null;
        qSeekBars[3] = null;
        qSeekBars[4] = null;
        qSeekBars[5] = null;
        qSeekBars[6] = null;
        qSeekBars[7] = null;
        qSeekBars[8] = null;
        qSeekBars[9] = null;

        // Initialize dB value TextViews
        dbValueViews[0] = bandViews[0].findViewById(R.id.eqDbValue);
        dbValueViews[1] = bandViews[1].findViewById(R.id.eqDbValue);
        dbValueViews[2] = bandViews[2].findViewById(R.id.eqDbValue);
        dbValueViews[3] = bandViews[3].findViewById(R.id.eqDbValue);
        dbValueViews[4] = bandViews[4].findViewById(R.id.eqDbValue);
        dbValueViews[5] = bandViews[5].findViewById(R.id.eqDbValue);
        dbValueViews[6] = bandViews[6].findViewById(R.id.eqDbValue);
        dbValueViews[7] = bandViews[7].findViewById(R.id.eqDbValue);
        dbValueViews[8] = bandViews[8].findViewById(R.id.eqDbValue);
        dbValueViews[9] = bandViews[9].findViewById(R.id.eqDbValue);
    }

    private void initUiState() {
        // Initialize UI levels list
        uiLevels.clear();
        freqValues.clear();
        qValues.clear();
        for (int i = 0; i < UI_BANDS; i++) {
            uiLevels.add(50); // Default 50% (0dB)
            freqValues.add(1000f);
            qValues.add(1.4f);
        }

        // Load initial state from ViewModel
        updateUiFromParametricBands();
    }

    private void updateUiFromParametricBands() {
        List<ParametricEQBand> bands = equalizerViewModel.getEqBands().getValue();
        if (bands == null) return;

        internalUpdate = true;
        int count = Math.min(bands.size(), UI_BANDS);
        for (int i = 0; i < count; i++) {
            ParametricEQBand band = bands.get(i);
            float gainDb = band.getGainDb();
            // Map -15..+15 to 0..100
            int progress = Math.round(((gainDb + 15f) / 30f) * 100);

            uiLevels.set(i, progress);
            freqValues.set(i, band.getFrequencyHz());
            qValues.set(i, band.getQFactor());

            VerticalSeekBar bar = getSeekBar(i);
            if (bar != null) {
                bar.setProgress(progress);
            }

            if (dbValueViews[i] != null) {
                dbValueViews[i].setText(String.format(Locale.getDefault(), "%.1f dB", gainDb));
            }
            updateFrequencyDisplay(i);
        }
        internalUpdate = false;
    }

    private void initSpectrumAnalyzer() {
        // Generate mock FFT data that responds to EQ settings
        float[] magnitudes = new float[512]; // 1024 FFT size / 2

        // Create a baseline spectrum (pink noise-like)
        for (int i = 0; i < magnitudes.length; i++) {
            // 1/f frequency response (pink noise) with some randomness
            float freq = (float) i / magnitudes.length * 22050; // Nyquist frequency
            float baseLevel = 0.3f / (float) Math.sqrt(Math.max(1, freq / 100)); // Pink noise

            // Add some variation
            float random = (float) Math.random() * 0.2f;
            magnitudes[i] = Math.min(1.0f, baseLevel + random);
        }

        // Apply EQ curve to the mock data
        if (equalizerViewModel != null) {
            List<ParametricEQBand> bands = equalizerViewModel.getEqBands().getValue();
            if (bands != null) {
                // Apply each band's effect to the spectrum
                for (ParametricEQBand band : bands) {
                    float freq = band.getFrequencyHz();
                    float gainDb = band.getGainDb();
                    float qFactor = band.getQFactor();

                    // Apply band effect to frequency bins
                    for (int i = 0; i < magnitudes.length; i++) {
                        float binFreq = (float) i / magnitudes.length * 22050;
                        float influence = calculateBandInfluence(freq, binFreq, qFactor);
                        float gainFactor = (float) Math.pow(10, gainDb / 20f);
                        magnitudes[i] *= (1f + influence * (gainFactor - 1f) * 0.5f); // Moderate effect
                    }
                }
            }
        }

        // Normalize and set data
        float max = 0f;
        for (float mag : magnitudes) {
            if (mag > max) max = mag;
        }
        if (max > 0) {
            for (int i = 0; i < magnitudes.length; i++) {
                magnitudes[i] /= max;
            }
        }

        spectrumView.setFftData(magnitudes);
    }

    private float calculateBandInfluence(float centerFreq, float targetFreq, float qFactor) {
        if (targetFreq <= 0 || centerFreq <= 0) return 0f;

        // Calculate octave distance
        float octaves = (float) Math.log(targetFreq / centerFreq) / (float) Math.log(2);

        // Calculate bandwidth in octaves based on Q-factor
        float bandwidthOctaves = 1.0f / (qFactor * (float) Math.sqrt(2.0));

        // Calculate influence using Gaussian curve
        return (float) Math.exp(-0.5f * Math.pow(octaves / bandwidthOctaves, 2));
    }

    private void updateSpectrumEqCurve(List<ParametricEQBand> bands) {
        if (spectrumView == null || bands == null) return;

        // Create EQ curve data for spectrum display
        float[] eqCurve = new float[512]; // Match FFT size

        // Start with flat (0dB = 0.5 in normalized 0-1 range where 0.5 is flat)
        for (int i = 0; i < eqCurve.length; i++) {
            eqCurve[i] = 0.5f;
        }

        // Apply each parametric band
        for (ParametricEQBand band : bands) {
            float freq = band.getFrequencyHz();
            float gainDb = band.getGainDb();
            float qFactor = band.getQFactor();

            // Apply band to EQ curve
            for (int i = 0; i < eqCurve.length; i++) {
                float binFreq = (float) i / eqCurve.length * 22050; // Nyquist
                float influence = calculateBandInfluence(freq, binFreq, qFactor);
                float gainFactor = (float) Math.pow(10, gainDb / 20f);
                // Blend the gain with current curve value
                eqCurve[i] = 0.5f + (eqCurve[i] - 0.5f) * (1f - influence) +
                        (gainFactor * 0.5f) * influence;
            }
        }

        // Normalize to 0-1 range (though it should already be close)
        spectrumView.setEqCurveData(eqCurve);
    }

// =========================
// EQ Band Observation
// =========================

    private void setupEqBandObserver() {
        equalizerViewModel.getEqBands().observeForever(eqBands -> {
            if (eqBands != null && !internalUpdate) {
                // Update UI based on EQ band parameters (frequency, gain, Q factor)
                // We'll map the first UI_BANDS parametric bands to our UI controls
                int bandsToShow = Math.min(eqBands.size(), UI_BANDS);
                for (int i = 0; i < bandsToShow; i++) {
                    ParametricEQBand band = eqBands.get(i);

                    if (freqSeekBars[i] != null) {
                        freqValues.set(i, band.getFrequencyHz());
                        updateFrequencyDisplay(i);
                    }

                    // Update frequency seekbar (0-100 maps to 20Hz-20000Hz on logarithmic scale)
                    float freqProgress = (float) ((Math.log10(band.getFrequencyHz()) - Math.log10(20)) /
                                                (Math.log10(20000) - Math.log10(20)) * 100);

                    // Update gain value and UI
                    float gainDb = band.getGainDb();
                    // Convert gain from -15..+15 dB to 0..100 progress
                    int gainProgress = Math.round(((gainDb + 15f) / 30f) * 100);
                    gainProgress = Math.max(0, Math.min(100, gainProgress));
                    uiLevels.set(i, gainProgress);

                    // Update gain seekbar if not currently updating internally
                    if (!internalUpdate) {
                        VerticalSeekBar bar = getSeekBar(i);
                        bar.setProgress(gainProgress);
                    }

                    // Update dB value display
                    dbValueViews[i].setText(String.format(Locale.getDefault(), "%.1f dB", gainDb));

                    // Update Q factor value and UI
                    float qFactor = band.getQFactor();
                    qValues.set(i, qFactor);

                    // Update Q factor display
                    String qText = String.format(Locale.getDefault(), "Q: %.1f", qFactor);
                    qValueViews[i].setText(qText);

                    // Update Q factor seekbar (0-100 maps to 0.1-10.0 Q factor on logarithmic scale)
                    float qProgress = (float) ((Math.log10(qFactor) - Math.log10(0.1f)) /
                            (Math.log10(10.0f) - Math.log10(0.1f)) * 100);
                    if (qSeekBars[i] != null) {
                        qSeekBars[i].setProgress(Math.round(qProgress));
                    }
                }

                // Update spectrum analyzer with EQ curve
                updateSpectrumEqCurve(eqBands);
            }
        });
    }

// =========================
// Session / Engine
// =========================

    public void setSessionId(int id) {
        if (id == -1) {
            if (audioEngine != null) {
                audioEngine.release();
                audioEngine = null;
            }
            sessionId = -1;
            setEnabledTotal(false);
            return;
        }

        // If it's the same ID and engine exists, don't recreate but ensure state
        if (id == this.sessionId && audioEngine != null) {
            syncHardwareWithUi();
            return;
        }

        // Clean up old engine
        if (audioEngine != null) {
            audioEngine.release();
        }

        sessionId = id;
        audioEngine = new AudioEngine(sessionId, context);
        equalizerViewModel.setAudioEngine(audioEngine);

        // Fetch hardware constraints
        bandCount = audioEngine.getNumberOfBands();
        minLevel = audioEngine.getMinBandLevelRange();
        maxLevel = audioEngine.getMaxBandLevelRange();

        // Initialize UI components for the new engine
        setupBands();
        setupEqPresets();

        // Initialize spectrum analyzer
        initSpectrumAnalyzer();

        // Sync the engine with the current UI switches/sliders
        syncHardwareWithUi();

        // Finally, enable UI interaction
        setEnabledTotal(true);
    }

    private void syncHardwareWithUi() {
        if (audioEngine == null) return;

        boolean masterOn = eqSwitch.isChecked();
        audioEngine.enableEqualizer(masterOn);

        if (masterOn) {
            applyUiToHardware();

            // Bass
            boolean bOn = bassSwitch.isChecked();
            audioEngine.enableBass(bOn);
            audioEngine.setBassBoost((short) (bOn ? Math.min(bassKnob.getValue(), 1000) : 0));

            // Virtualizer
            boolean vOn = virtualizerSwitch.isChecked();
            audioEngine.enableVirtualizer(vOn);
            audioEngine.setVirtualizer((short) (vOn ? virtualizerKnob.getValue() : 0));

            // Tempo
            float tVal = tempoSwitch.isChecked() ? tempoKnob.getValue() : 1.0f;
            equalizerViewModel.setTempo(tVal);

            // Pitch
            float pVal = pitchSwitch.isChecked() ? pitchKnob.getValue() : 0f;
            equalizerViewModel.setPitch(progressToPitch(pVal));

            // Loudness
            boolean lOn = loudnessSwitch.isChecked();
            audioEngine.enableLoudness(lOn);
            audioEngine.setLoudnessGain(lOn ? 1000 : 0); // 1000mB = 1dB
        } else {
            // Master OFF: Reset hardware/player effects to neutral
            audioEngine.enableBass(false);
            audioEngine.enableVirtualizer(false);
            audioEngine.enableLoudness(false);
            equalizerViewModel.setTempo(1.0f);
            equalizerViewModel.setPitch(0f);
        }
    }

// =========================
// Band setup
// =========================

    private void setupBands() {
        int range = maxLevel - minLevel;
        int center = range / 2;

        internalUpdate = true;

        for (int i = 0; i < UI_BANDS; i++) {
            // Initialize to center position (0dB gain equivalent)
            uiLevels.set(i, center);
            freqValues.add(0f);
            qValues.add(1.0f); // Default Q factor of 1.0

            VerticalSeekBar bar = getSeekBar(i);
            bar.setMax(range);
            bar.setProgress(center);

            final int index = i;
            bar.setOnSeekBarChangeListener(new VerticalSeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(VerticalSeekBar sb, int progress, boolean fromUser) {
                    if (!fromUser || internalUpdate) return;

                    presetGroup.check(R.id.eq_custom);
                    uiLevels.set(index, progress);

                    // Update the corresponding parametric band's gain
                    updateParametricBandFromUi(index, progress);

                    // Update dB value display
                    float gainDb = ((progress / 100f) * 30f) - 15f;
                    dbValueViews[index].setText(String.format(Locale.getDefault(), "%.1f dB", gainDb));

                    // Update last change time for spectrum update rate adjustment
                    lastChangeTime = System.currentTimeMillis();

                    scheduleApply();
                }

                @Override
                public void onStartTrackingTouch(VerticalSeekBar sb) {
                }

                @Override
                public void onStopTrackingTouch(VerticalSeekBar sb) {
                }
            });

        }

        updateFrequencyLabels();
        internalUpdate = false;
    }

    /**
     * Update a parametric band's gain based on UI seekbar progress
     */
    private void updateParametricBandFromUi(int bandIndex, int progress) {
        // Convert progress (0-100) to gain (-15 to +15 dB)
        float gainDb = ((progress / 100f) * 30f) - 15f;

        equalizerViewModel.setEqBand(bandIndex,
                equalizerViewModel.getEqBands().getValue().get(bandIndex).getFrequencyHz(),
                gainDb,
                equalizerViewModel.getEqBands().getValue().get(bandIndex).getQFactor());
    }

// =========================
// Presets
// =========================

    private void setupEqPresets() {
        presetGroup.setOnCheckedStateChangeListener((group, checkedIds) -> {
            if (checkedIds.isEmpty()) return;

            EqPreset preset = presetMap.get(checkedIds.get(0));
            if (preset != null) applyPreset(preset);
        });
    }

    public void applyPreset(EqPreset preset) {
        internalUpdate = true;

        // Apply preset to parametric bands in ViewModel
        equalizerViewModel.setPreset(preset);

        // Update UI to reflect the preset
        updateUiFromParametricBands();

        internalUpdate = false;
        applyUiToHardware();
    }

    /**
     * Update UI controls to match current parametric bands
     */
    private void updateSpectrumData() {
        if (spectrumView == null || audioEngine == null || !audioEngine.isSpectrumDataReady())
            return;

        int bins = audioEngine.getSpectrumNumBins();
        if (bins <= 0) return;

        float[] magnitudesDb = new float[bins];
        audioEngine.getSpectrumMagnitudes(magnitudesDb);
        float[] normalized = new float[bins];
        for (int i = 0; i < bins; i++) {
            normalized[i] = Math.max(0.0f, Math.min(1.0f,
                    (magnitudesDb[i] + 96.0f) / 96.0f));
        }
        spectrumView.setFftData(normalized);
    }

    private void mapPresets() {
        presetMap.put(R.id.eq_flat, EqPreset.FLAT);
        presetMap.put(R.id.eq_normal, EqPreset.NORMAL);
        presetMap.put(R.id.eq_rock, EqPreset.ROCK);
        presetMap.put(R.id.eq_pop, EqPreset.POP);
        presetMap.put(R.id.eq_dance, EqPreset.DANCE);
        presetMap.put(R.id.eq_hip_hop, EqPreset.HIP_HOP);
        presetMap.put(R.id.eq_acoustic, EqPreset.ACOUSTIC);
        presetMap.put(R.id.eq_heavy_metal, EqPreset.HEAVY_METAL);
        presetMap.put(R.id.eq_head_phones, EqPreset.HEADPHONES);
        presetMap.put(R.id.eq_folk, EqPreset.FOLK);
        presetMap.put(R.id.eq_loud, EqPreset.LOUD);
        presetMap.put(R.id.eq_piano, EqPreset.PIANO);
        presetMap.put(R.id.eq_r_and_b, EqPreset.R_AND_B);
        presetMap.put(R.id.eq_lounge, EqPreset.LOUNGE);
        presetMap.put(R.id.eq_classical, EqPreset.CLASSICAL);
        presetMap.put(R.id.eq_jazz, EqPreset.JAZZ);
        presetMap.put(R.id.eq_deep, EqPreset.DEEP);
        presetMap.put(R.id.eq_latin, EqPreset.LATIN);
        presetMap.put(R.id.eq_electronic, EqPreset.ELECTRONIC);
        presetMap.put(R.id.eq_straightness, EqPreset.STRAIGHTNESS);
        presetMap.put(R.id.eq_vocal_boost, EqPreset.VOCAL_BOOST);
        presetMap.put(R.id.eq_treble_boost, EqPreset.TREBLE_BOOST);
        presetMap.put(R.id.eq_bazz_boost, EqPreset.BASS_BOOST);
    }

// =========================
// DSP application
// =========================

    private void applyUiToHardware() {
        if (audioEngine == null) return;

        // Convert UI levels to parametric bands and apply
        List<ParametricEQBand> currentBands = equalizerViewModel.getEqBands().getValue();
        if (currentBands != null) {
            // Apply the first UI_BANDS bands to hardware
            int bandsToApply = Math.min(currentBands.size(), UI_BANDS);
            for (int i = 0; i < bandsToApply; i++) {
                ParametricEQBand band = currentBands.get(i);
                // Update all three parameters: frequency, gain, and Q factor
                equalizerViewModel.setEqBand(i,
                        freqValues.get(i), // frequency from UI
                        // Convert UI level to gain Db
                        (uiLevels.get(i) / (float) Math.max(1, maxLevel - minLevel)) * 30f - 15f,
                        qValues.get(i)); // Q factor from UI
            }
        }

        // Parametric bands are processed by SoundEngine through the ViewModel.
        // Do not also apply Android's fixed-band EqualizerFX approximation here.
    }

    private void scheduleApply() {
        if (applyRunnable != null) handler.removeCallbacks(applyRunnable);
        applyRunnable = this::applyUiToHardware;
        // 16ms delay to ensure UI updates first and DSP updates at ~60fps max
        handler.postDelayed(applyRunnable, 16);
    }

// =========================
// Switch logic
// =========================

    private void setupListeners(FragmentHome fragment) {

        closeButton.setOnClickListener(v -> {
            isVisible.set(false);
            fragment.hideMediaDetailsPanel();
        });

        eqReset.setOnClickListener(v -> {
            // Reset all bands to zero gain
            equalizerViewModel.resetAllBands();
            // Reset frequency labels to show default values
            updateFrequencyLabels();
            // Notify user of reset (optional)
            Toast.makeText(context, "Equalizer reset to flat", Toast.LENGTH_SHORT).show();
        });

        // Undo/Redo listeners
        undoButton.setOnClickListener(v -> undo());
        redoButton.setOnClickListener(v -> redo());

        // Master Switch: Controls everything else
        eqSwitch.setOnCheckedChangeListener((b, on) -> {
            if (audioEngine != null) {
                audioEngine.enableEqualizer(on);
            }
            // Ensure UI state reflects the master toggle
            setEnabledTotal(sessionId != -1);
        });

        // Sub-Switches: Control their respective knobs/effects
        bassSwitch.setOnCheckedChangeListener((b, on) -> {
            if (on) {
                bassKnob.setValue(bassValue);
            } else {
                bassValue = bassKnob.getValue();
                bassKnob.setValue(0f);
            }

            if (audioEngine != null && eqSwitch.isChecked()) {
                audioEngine.enableBass(on);
                audioEngine.setBassBoost((short) (on ? Math.min(bassValue, 1000) : 0));
            }
            setEnabledWithAlpha(bassKnob, on && eqSwitch.isChecked());
        });

        virtualizerSwitch.setOnCheckedChangeListener((b, on) -> {
            if (on) {
                virtualizerKnob.setValue(virtualizerValue);
            } else {
                virtualizerValue = virtualizerKnob.getValue();
                virtualizerKnob.setValue(0f);
            }

            if (audioEngine != null && eqSwitch.isChecked()) {
                audioEngine.enableVirtualizer(on);
                audioEngine.setVirtualizer((short) (on ? virtualizerValue : 0));
            }
            setEnabledWithAlpha(virtualizerKnob, on && eqSwitch.isChecked());
        });

        tempoSwitch.setOnCheckedChangeListener((b, on) -> {
            if (on) {
                tempoKnob.setValue(tempoValue);
            } else {
                tempoValue = tempoKnob.getValue();
                tempoKnob.setValue(1.0f);
            }

            if (eqSwitch.isChecked()) {
                equalizerViewModel.setTempo(on ? tempoValue : 1.0f);
            }
            setEnabledWithAlpha(tempoKnob, on && eqSwitch.isChecked());
        });

        pitchSwitch.setOnCheckedChangeListener((b, on) -> {
            if (on) {
                pitchKnob.setValue(pitchValue);
            } else {
                pitchValue = pitchKnob.getValue();
                pitchKnob.setValue(0f);
            }

            if (eqSwitch.isChecked()) {
                equalizerViewModel.setPitch(progressToPitch(on ? pitchValue : 0f));
            }
            setEnabledWithAlpha(pitchKnob, on && eqSwitch.isChecked());
        });

        loudnessSwitch.setOnCheckedChangeListener((b, on) -> {
            if (audioEngine != null && eqSwitch.isChecked()) {
                audioEngine.enableLoudness(on);
                audioEngine.setLoudnessGain(on ? 1000 : 0);
            }
            setEnabledWithAlpha(loudnessIcon, on && eqSwitch.isChecked());
        });

        // Advanced Effects Switches
        reverbSwitch.setOnCheckedChangeListener((b, on) -> {
            if (audioEngine != null && eqSwitch.isChecked()) {
                audioEngine.setReverbEnabled(on);
                if (on) {
                    audioEngine.setReverbProperties((int) reverbRoomLevelValue, (int) reverbDecayTimeValue);
                }
            }
            setEnabledWithAlpha(reverbKnobContainer, on && eqSwitch.isChecked());
        });

        stereoWideningSwitch.setOnCheckedChangeListener((b, on) -> {
            if (eqSwitch.isChecked()) {
                equalizerViewModel.setStereoWidening(on, stereoWideningWidthValue);
            }
            setEnabledWithAlpha(stereoWideningKnobContainer, on && eqSwitch.isChecked());
        });

        exciterSwitch.setOnCheckedChangeListener((b, on) -> {
            if (eqSwitch.isChecked()) {
                equalizerViewModel.setExciter(on, exciterAmountValue, exciterFrequencyValue);
            }
            setEnabledWithAlpha(exciterKnobContainer, on && eqSwitch.isChecked());
        });

        compressorSwitch.setOnCheckedChangeListener((b, on) -> {
            if (eqSwitch.isChecked()) {
                equalizerViewModel.setCompressor(on, compressorThresholdValue,
                        compressorRatioValue, compressorAttackValue, compressorReleaseValue);
            }
            setEnabledWithAlpha(compressorKnobContainer, on && eqSwitch.isChecked());
        });

        limiterSwitch.setOnCheckedChangeListener((b, on) -> {
            if (eqSwitch.isChecked()) {
                equalizerViewModel.setLimiter(on, limiterThresholdValue);
            }
            setEnabledWithAlpha(limiterKnobContainer, on && eqSwitch.isChecked());
        });

        noiseGateSwitch.setOnCheckedChangeListener((b, on) -> {
            if (eqSwitch.isChecked()) {
                equalizerViewModel.setNoiseGate(on, noiseGateThresholdValue);
            }
            setEnabledWithAlpha(noiseGateKnobContainer, on && eqSwitch.isChecked());
        });

        deEsserSwitch.setOnCheckedChangeListener((b, on) -> {
            if (eqSwitch.isChecked()) {
                equalizerViewModel.setDeEsser(on, deEsserThresholdValue, deEsserFrequencyValue);
            }
            setEnabledWithAlpha(deEsserKnobContainer, on && eqSwitch.isChecked());
        });

        // Knobs: Update values only if enabled
        bassKnob.setOnValueChangedListener(v -> {
            if (audioEngine != null && bassSwitch.isChecked() && eqSwitch.isChecked()) {
                audioEngine.setBassBoost((short) Math.min(v, 1000));
            }
        });

        virtualizerKnob.setOnValueChangedListener(v -> {
            if (audioEngine != null && virtualizerSwitch.isChecked() && eqSwitch.isChecked()) {
                audioEngine.setVirtualizer((short) v);
            }
        });

        stereoWidthSlider.addOnChangeListener((slider, value, fromUser) -> {
            if (fromUser && eqSwitch.isChecked()) {
                stereoWideningWidthValue = value / 100f;
                equalizerViewModel.setStereoWidening(stereoWideningSwitch.isChecked(),
                        stereoWideningWidthValue);
            }
        });
        exciterAmountSlider.addOnChangeListener((slider, value, fromUser) -> {
            if (fromUser && eqSwitch.isChecked()) {
                exciterAmountValue = value / 10f;
                equalizerViewModel.setExciter(exciterSwitch.isChecked(), exciterAmountValue,
                        exciterFrequencyValue);
            }
        });
        compressorThresholdSlider.addOnChangeListener((slider, value, fromUser) -> {
            if (fromUser && eqSwitch.isChecked()) {
                compressorThresholdValue = value;
                equalizerViewModel.setCompressor(compressorSwitch.isChecked(),
                        compressorThresholdValue, compressorRatioValue,
                        compressorAttackValue, compressorReleaseValue);
            }
        });
        limiterCeilingSlider.addOnChangeListener((slider, value, fromUser) -> {
            if (fromUser && eqSwitch.isChecked()) {
                limiterThresholdValue = value;
                equalizerViewModel.setLimiter(limiterSwitch.isChecked(), value);
            }
        });
        noiseGateThresholdSlider.addOnChangeListener((slider, value, fromUser) -> {
            if (fromUser && eqSwitch.isChecked()) {
                noiseGateThresholdValue = value;
                equalizerViewModel.setNoiseGate(noiseGateSwitch.isChecked(), value);
            }
        });
        deEsserThresholdSlider.addOnChangeListener((slider, value, fromUser) -> {
            if (fromUser && eqSwitch.isChecked()) {
                deEsserThresholdValue = value;
                equalizerViewModel.setDeEsser(deEsserSwitch.isChecked(), value,
                        deEsserFrequencyValue);
            }
        });

        // Advanced Effects Knobs
        // Reverb Room Level (-1000 to 0 mB)
        reverbKnobContainer.addView(createSeekBar(-1000, 0, (int) reverbRoomLevelValue, value -> {
            reverbRoomLevelValue = value;
            if (audioEngine != null && reverbSwitch.isChecked() && eqSwitch.isChecked()) {
                audioEngine.setReverbProperties((int) reverbRoomLevelValue, (int) reverbDecayTimeValue);
            }
        }, "Reverb Room"));

        // Reverb Decay Time (0 to 5000 ms)
        reverbKnobContainer.addView(createSeekBar(0, 5000, (int) reverbDecayTimeValue, value -> {
            reverbDecayTimeValue = value;
            if (audioEngine != null && reverbSwitch.isChecked() && eqSwitch.isChecked()) {
                audioEngine.setReverbProperties((int) reverbRoomLevelValue, (int) reverbDecayTimeValue);
            }
        }, "Reverb Decay"));

        // Stereo Widening Width (0 to 100, representing 0.0 to 1.0)
        stereoWideningKnobContainer.addView(createSeekBar(0, 100, (int) (stereoWideningWidthValue * 100), value -> {
            stereoWideningWidthValue = value / 100f;
            if (stereoWideningSwitch.isChecked() && eqSwitch.isChecked()) {
                equalizerViewModel.setStereoWidening(true, stereoWideningWidthValue);
            }
        }, "Stereo Width"));

        // Exciter Amount (0 to 100, representing 0.0 to 1.0)
        exciterKnobContainer.addView(createSeekBar(0, 100, (int) (exciterAmountValue * 100), value -> {
            exciterAmountValue = value / 100f;
            if (exciterSwitch.isChecked() && eqSwitch.isChecked()) {
                equalizerViewModel.setExciter(true, exciterAmountValue, exciterFrequencyValue);
            }
        }, "Exciter Amount"));

        // Exciter Frequency (20 to 20000 Hz)
        exciterKnobContainer.addView(createSeekBar(20, 20000, (int) exciterFrequencyValue, value -> {
            exciterFrequencyValue = value;
            if (exciterSwitch.isChecked() && eqSwitch.isChecked()) {
                equalizerViewModel.setExciter(true, exciterAmountValue, exciterFrequencyValue);
            }
        }, "Exciter Freq"));

        // Compressor Threshold (-60 to 0 dB)
        compressorKnobContainer.addView(createSeekBar(-60, 0, (int) compressorThresholdValue, value -> {
            compressorThresholdValue = value;
            if (compressorSwitch.isChecked() && eqSwitch.isChecked()) {
                equalizerViewModel.setCompressor(true, compressorThresholdValue,
                        compressorRatioValue, compressorAttackValue, compressorReleaseValue);
            }
        }, "Comp Threshold"));

        // Compressor Ratio (100 to 500, representing 1.0 to 5.0, with higher ratios approximated)
        compressorKnobContainer.addView(createSeekBar(100, 500, (int) (compressorRatioValue * 100), value -> {
            compressorRatioValue = value / 100f;
            if (compressorSwitch.isChecked() && eqSwitch.isChecked()) {
                equalizerViewModel.setCompressor(true, compressorThresholdValue,
                        compressorRatioValue, compressorAttackValue, compressorReleaseValue);
            }
        }, "Comp Ratio"));

        // Compressor Attack (0 to 200 ms)
        compressorKnobContainer.addView(createSeekBar(0, 200, (int) compressorAttackValue, value -> {
            compressorAttackValue = value;
            if (compressorSwitch.isChecked() && eqSwitch.isChecked()) {
                equalizerViewModel.setCompressor(true, compressorThresholdValue,
                        compressorRatioValue, compressorAttackValue, compressorReleaseValue);
            }
        }, "Comp Attack"));

        // Compressor Release (0 to 500 ms)
        compressorKnobContainer.addView(createSeekBar(0, 500, (int) compressorReleaseValue, value -> {
            compressorReleaseValue = value;
            if (compressorSwitch.isChecked() && eqSwitch.isChecked()) {
                equalizerViewModel.setCompressor(true, compressorThresholdValue,
                        compressorRatioValue, compressorAttackValue, compressorReleaseValue);
            }
        }, "Comp Release"));

        // Limiter Threshold (-20 to 0 dB)
        limiterKnobContainer.addView(createSeekBar(-20, 0, (int) limiterThresholdValue, value -> {
            limiterThresholdValue = value;
            if (limiterSwitch.isChecked() && eqSwitch.isChecked()) {
                equalizerViewModel.setLimiter(true, limiterThresholdValue);
            }
        }, "Limit Threshold"));

        // Noise Gate Threshold (-80 to -20 dB)
        noiseGateKnobContainer.addView(createSeekBar(-80, -20, (int) noiseGateThresholdValue, value -> {
            noiseGateThresholdValue = value;
            if (noiseGateSwitch.isChecked() && eqSwitch.isChecked()) {
                equalizerViewModel.setNoiseGate(true, noiseGateThresholdValue);
            }
        }, "Noise Gate"));

        // De-esser Threshold (-40 to 0 dB)
        deEsserKnobContainer.addView(createSeekBar(-40, 0, (int) deEsserThresholdValue, value -> {
            deEsserThresholdValue = value;
            if (deEsserSwitch.isChecked() && eqSwitch.isChecked()) {
                equalizerViewModel.setDeEsser(true, deEsserThresholdValue, deEsserFrequencyValue);
            }
        }, "DeEsser Thresh"));

        // De-esser Frequency (2000 to 20000 Hz)
        deEsserKnobContainer.addView(createSeekBar(2000, 20000, (int) deEsserFrequencyValue, value -> {
            deEsserFrequencyValue = value;
            if (deEsserSwitch.isChecked() && eqSwitch.isChecked()) {
                equalizerViewModel.setDeEsser(true, deEsserThresholdValue, deEsserFrequencyValue);
            }
        }, "DeEsser Freq"));

        tempoKnob.setOnValueChangedListener(value -> {
            if (tempoSwitch.isChecked() && eqSwitch.isChecked()) {
                equalizerViewModel.setTempo(value);
            }
        });

        pitchKnob.setOnValueChangedListener(value -> {
            if (pitchSwitch.isChecked() && eqSwitch.isChecked()) {
                equalizerViewModel.setPitch(progressToPitch(value));
            }
        });
    }

    private void setEnabledTotal(boolean sessionActive) {
        // Master switch is ONLY enabled if we have a valid session
        eqSwitch.setEnabled(sessionActive);

        // Everything else depends on BOTH the session being active AND the master switch being ON
        boolean masterOn = sessionActive && eqSwitch.isChecked();

        // Master UI components
        setEnabledWithAlpha(chipsContainer, masterOn);
        setEnabledWithAlpha(eqCard, masterOn);

        // Sub-switches: Always interactive if session is active
        bassSwitch.setEnabled(sessionActive);
        virtualizerSwitch.setEnabled(sessionActive);
        tempoSwitch.setEnabled(sessionActive);
        pitchSwitch.setEnabled(sessionActive);
        loudnessSwitch.setEnabled(sessionActive);

        // Advanced Effects Switches: Always interactive if session is active
        reverbSwitch.setEnabled(sessionActive);
        stereoWideningSwitch.setEnabled(sessionActive);
        exciterSwitch.setEnabled(sessionActive);
        compressorSwitch.setEnabled(sessionActive);
        limiterSwitch.setEnabled(sessionActive);
        noiseGateSwitch.setEnabled(sessionActive);
        deEsserSwitch.setEnabled(sessionActive);

        // Bands follow master switch state
        for (int i = 0; i < UI_BANDS; i++) {
            setEnabledWithAlpha(getSeekBar(i), masterOn);
        }

        // Nested controls (knobs, icons) state
        updateNestedControlsState();
    }

    private void updateNestedControlsState() {
        boolean master = eqSwitch.isEnabled() && eqSwitch.isChecked();
        setEnabledWithAlpha(bassKnob, master && bassSwitch.isChecked());
        setEnabledWithAlpha(virtualizerKnob, master && virtualizerSwitch.isChecked());
        setEnabledWithAlpha(tempoKnob, master && tempoSwitch.isChecked());
        setEnabledWithAlpha(pitchKnob, master && pitchSwitch.isChecked());
        setEnabledWithAlpha(loudnessIcon, master && loudnessSwitch.isChecked());

        // Advanced Effects Controls
        setEnabledWithAlpha(reverbKnobContainer, master && reverbSwitch.isChecked());
        setEnabledWithAlpha(stereoWideningKnobContainer, master && stereoWideningSwitch.isChecked());
        setEnabledWithAlpha(exciterKnobContainer, master && exciterSwitch.isChecked());
        setEnabledWithAlpha(compressorKnobContainer, master && compressorSwitch.isChecked());
        setEnabledWithAlpha(limiterKnobContainer, master && limiterSwitch.isChecked());
        setEnabledWithAlpha(noiseGateKnobContainer, master && noiseGateSwitch.isChecked());
        setEnabledWithAlpha(deEsserKnobContainer, master && deEsserSwitch.isChecked());
    }

    private void setEnabledWithAlpha(View v, boolean enabled) {
        v.setEnabled(enabled);
        v.setAlpha(enabled ? 1f : 0.5f);
    }

    /**
     * Creates a simple vertical seekbar for advanced effect parameters
     */
    private View createSeekBar(int min, int max, int initialValue, Consumer<Integer> onValueChanged, String label) {
        LinearLayout container = new LinearLayout(context);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(8, 8, 8, 8);

        TextView labelView = new TextView(context);
        labelView.setText(label);
        labelView.setTextSize(12);
        labelView.setTextColor(ContextCompat.getColor(context, android.R.color.white));
        labelView.setGravity(Gravity.CENTER);
        container.addView(labelView);

        VerticalSeekBar seekBar = new VerticalSeekBar(context);
        seekBar.setMin(min);
        seekBar.setMax(max);
        seekBar.setProgress(initialValue);
        seekBar.setPadding(0, 8, 0, 8);
        seekBar.setOnSeekBarChangeListener(new VerticalSeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(VerticalSeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    onValueChanged.accept(progress);
                }
            }

            @Override
            public void onStartTrackingTouch(VerticalSeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(VerticalSeekBar seekBar) {
            }
        });
        container.addView(seekBar);

        return container;
    }

    private VerticalSeekBar getSeekBar(int index) {
        return freqSeekBars[index];
    }

    private void updateFrequencyLabels() {
        int min = audioEngine.getEqualizerCenterFreq((short) 0);
        int max = audioEngine.getEqualizerCenterFreq((short) (bandCount - 1));

        for (int i = 0; i < UI_BANDS; i++) {
            double r = i / (double) (UI_BANDS - 1);
            double hzDouble = min * Math.pow((double) max / min, r);
            int hz = (int) hzDouble;

            TextView tv = bandViews[i].findViewById(R.id.eqTextView);
            // Show one decimal place for fractional frequencies (like 31.5 Hz)
            if (hzDouble < 100) {
                // For frequencies below 100 Hz, show one decimal place if needed
                if (hzDouble - hz >= 0.01) {
                    tv.setText(String.format(Locale.getDefault(), "%.1f Hz", hzDouble));
                } else {
                    tv.setText(hz + " Hz");
                }
            } else if (hzDouble < 1000) {
                // For frequencies below 1000 Hz, show as integer Hz
                tv.setText(hz + " Hz");
            } else {
                // For frequencies 1000 Hz and above, show in kHz with two decimal places
                tv.setText(String.format(Locale.getDefault(), "%.2f kHz", hzDouble / 1000f));
            }
        }
    }

    /**
     * Update frequency display for a specific band
     */
    private void updateFrequencyDisplay(int bandIndex) {
        float freq = freqValues.get(bandIndex);
        // Show appropriate decimal places based on frequency value
        if (freq < 100) {
            // For frequencies below 100 Hz, show one decimal place
            freqValueViews[bandIndex].setText(String.format(Locale.getDefault(), "%.1f Hz", freq));
        } else if (freq < 1000) {
            // For frequencies below 1000 Hz, show as integer Hz
            freqValueViews[bandIndex].setText(String.format(Locale.getDefault(), "%.0f Hz", freq));
        } else {
            // For frequencies 1000 Hz and above, show in kHz with two decimal places
            freqValueViews[bandIndex].setText(String.format(Locale.getDefault(), "%.2f kHz", freq / 1000f));
        }
    }

// =========================
// Undo/Redo Functionality
// =========================

    /**
     * Save current state to undo history
     */
    private void saveStateToHistory() {
        // Only save if we have a valid state and not updating internally
        if (internalUpdate || uiLevels == null || uiLevels.isEmpty()) {
            return;
        }

        // Create snapshot of current state
        StateSnapshot currentState = new StateSnapshot(uiLevels, freqValues, qValues);

        // Add to undo stack
        undoStack.push(currentState);

        // Limit history size
        if (undoStack.size() > MAX_HISTORY_SIZE) {
            undoStack.remove(0);
        }

        // Clear redo stack when a new action is performed
        redoStack.clear();

        // Update button states
        updateUndoRedoButtons();
    }

    /**
     * Undo the last action
     */
    private void undo() {
        if (undoStack.isEmpty()) {
            return;
        }

        // Save current state to redo stack
        if (!internalUpdate && uiLevels != null && !uiLevels.isEmpty()) {
            // Save current state (levels, frequencies, Q factors)
            List<Integer> levelsState = new ArrayList<>(uiLevels);
            List<Float> freqState = new ArrayList<>(freqValues);
            List<Float> qState = new ArrayList<>(qValues);
            redoStack.push(new StateSnapshot(levelsState, freqState, qState));
        }

        // Get previous state from undo stack
        StateSnapshot previousState = undoStack.pop();

        // Apply previous state
        internalUpdate = true;
        uiLevels.clear();
        uiLevels.addAll(previousState.levels);
        freqValues.clear();
        freqValues.addAll(previousState.frequencies);
        qValues.clear();
        qValues.addAll(previousState.qFactors);

        // Update UI
        for (int i = 0; i < Math.min(previousState.levels.size(), UI_BANDS); i++) {
            int progress = previousState.levels.get(i);
            float freq = previousState.frequencies.get(i);
            float qFactor = previousState.qFactors.get(i);

            VerticalSeekBar bar = getSeekBar(i);
            bar.setProgress(progress);

            // Update frequency UI
            freqValues.set(i, freq);
            updateFrequencyDisplay(i);
            // Update button states
            updateUndoRedoButtons();

            // Show feedback
            Toast.makeText(context, "Undo", Toast.LENGTH_SHORT).show();
        }

    }

    /**
     * Redo the last undone action
     */
    private void redo() {
        if (redoStack.isEmpty()) {
            return;
        }

        // Save current state to undo stack
        if (!internalUpdate && uiLevels != null && !uiLevels.isEmpty()) {
            // Save current state (levels, frequencies, Q factors)
            List<Integer> levelsState = new ArrayList<>(uiLevels);
            List<Float> freqState = new ArrayList<>(freqValues);
            List<Float> qState = new ArrayList<>(qValues);
            undoStack.push(new StateSnapshot(levelsState, freqState, qState));
        }

        // Get next state from redo stack
        StateSnapshot nextState = redoStack.pop();

        // Apply next state
        internalUpdate = true;
        uiLevels.clear();
        uiLevels.addAll(nextState.levels);
        freqValues.clear();
        freqValues.addAll(nextState.frequencies);
        qValues.clear();
        qValues.addAll(nextState.qFactors);

        // Update UI
        for (int i = 0; i < Math.min(nextState.levels.size(), UI_BANDS); i++) {
            int progress = nextState.levels.get(i);
            float freq = nextState.frequencies.get(i);
            float qFactor = nextState.qFactors.get(i);

            VerticalSeekBar bar = getSeekBar(i);
            bar.setProgress(progress);

            // Update frequency UI
            freqValues.set(i, freq);
            updateFrequencyDisplay(i);
            float freqProgress = (float) ((Math.log10(freq) - Math.log10(20)) /
                                (Math.log10(20000) - Math.log10(20)) * 100);

            // Update dB value display
            float gainDb = ((progress / 100f) * 30f) - 15f;
            dbValueViews[i].setText(String.format(Locale.getDefault(), "%.1f dB", gainDb));

            // Update Q factor UI
            qValues.set(i, qFactor);
            String qText = String.format(Locale.getDefault(), "Q: %.1f", qFactor);
            qValueViews[i].setText(qText);
            float qProgress = (float) ((Math.log10(qFactor) - Math.log10(0.1f)) /
                                (Math.log10(10.0f) - Math.log10(0.1f)) * 100);
            qSeekBars[i].setProgress(Math.round(qProgress));

            // Update parametric band in ViewModel
            if (equalizerViewModel.getEqBands().getValue() != null &&
                    i < equalizerViewModel.getEqBands().getValue().size()) {
                equalizerViewModel.setEqBand(i,
                        freq,
                        gainDb,
                        qFactor);
            }
        }
        internalUpdate = false;

        // Apply to hardware
        applyUiToHardware();

        // Update spectrum
        lastChangeTime = System.currentTimeMillis();
        updateSpectrumEqCurve(equalizerViewModel.getEqBands().getValue());

        // Update button states
        updateUndoRedoButtons();

        // Show feedback
        Toast.makeText(context, "Redo", Toast.LENGTH_SHORT).show();
    }

    /**
     * Update the enabled state of undo/redo buttons
     */
    private void updateUndoRedoButtons() {
        undoButton.setEnabled(!undoStack.isEmpty());
        redoButton.setEnabled(!redoStack.isEmpty());

        // Also update alpha for visual feedback
        undoButton.setAlpha(undoStack.isEmpty() ? 0.5f : 1f);
        redoButton.setAlpha(redoStack.isEmpty() ? 0.5f : 1f);
    }

    public static float progressToPitch(float semitones) {
        // ExoPlayer default pitch = 1.0f (0 semitones)
        return (float) Math.pow(2.0, semitones / 12.0);
    }


    // =========================
    // Lifecycle
    // =========================

    public void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        if (audioEngine != null) audioEngine.release();
        audioEngine = null;

        // Stop spectrum updates
        if (spectrumUpdateRunnable != null) {
            handler.removeCallbacks(spectrumUpdateRunnable);
            spectrumUpdateRunnable = null;
        }
    }

    public View getView() {
        return rootView;
    }

    public void setIsVisible(boolean isVisible) {
        this.isVisible.set(isVisible);
    }

    public AtomicBoolean getIsVisible() {
        return isVisible;
    }


    public void setBottomPadding(int dimensionPixelSize) {
        parentView.setPadding(0, 0, 0, dimensionPixelSize);
    }

    private static class StateSnapshot {
        final List<Integer> levels;
        final List<Float> frequencies;
        final List<Float> qFactors;

        StateSnapshot(List<Integer> levels, List<Float> frequencies, List<Float> qFactors) {
            this.levels = new ArrayList<>(levels);
            this.frequencies = new ArrayList<>(frequencies);
            this.qFactors = new ArrayList<>(qFactors);
        }
    }
}
