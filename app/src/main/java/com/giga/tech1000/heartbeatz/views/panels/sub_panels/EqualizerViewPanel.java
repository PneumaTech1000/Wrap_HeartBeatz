package com.giga.tech1000.heartbeatz.views.panels.sub_panels;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LifecycleOwner;

import com.giga.tech1000.extensions.VerticalSeekBar;
import com.giga.tech1000.heartbeatz.R;
import com.giga.tech1000.heartbeatz.ui.fragments.FragmentHome;
import com.giga.tech1000.heartbeatz.utils.audio.ParametricEQBand;
import com.giga.tech1000.heartbeatz.view_models.extended_models.EqualizerViewModel;
import com.giga.tech1000.heartbeatz.views.SpectrumView;
import com.giga.tech1000.heartbeatz.views.knobs.KnobView;
import com.giga.tech1000.media_player.engine.AudioEngine;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.slider.Slider;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Production equalizer UI — drives native {@link com.giga.tech1000.soundengine.SoundEngine}
 * through {@link AudioEngine} / {@link EqualizerViewModel}.
 */
public final class EqualizerViewPanel {

    private static final int BAND_COUNT = 10;
    private static final float GAIN_MIN_DB = -12f;
    private static final float GAIN_MAX_DB = 12f;
    private static final int SEEK_MAX = 240; // 0.1 dB steps over 24 dB range
    private static final float[] ISO_FREQ_HZ = {
            31.5f, 63f, 125f, 250f, 500f, 1000f, 2000f, 4000f, 8000f, 16000f
    };
    private static final String[] ISO_LABELS = {
            "32Hz", "64Hz", "125Hz", "250Hz", "500Hz", "1kHz", "2kHz", "4kHz", "8kHz", "16kHz"
    };

    private final Context context;
    private final View rootView;
    private final EqualizerViewModel vm;
    private final Handler main = new Handler(Looper.getMainLooper());

    // Toolbar
    private ImageButton closeButton;
    private ImageButton undoButton;
    private ImageButton redoButton;
    private ImageButton resetButton;
    private MaterialSwitch masterSwitch;

    // Presets
    private ChipGroup presetGroup;
    private MaterialButton slotA;
    private MaterialButton slotB;

    // Spectrum
    private SpectrumView spectrumView;
    private View spectrumOffline;
    private TextView peakDbText;
    private TextView activeBandsText;

    // Faders
    private ViewGroup fadersContainer;
    private final VerticalSeekBar[] gainBars = new VerticalSeekBar[BAND_COUNT];
    private final TextView[] gainLabels = new TextView[BAND_COUNT];
    private final TextView[] freqLabels = new TextView[BAND_COUNT];
    private final TextView[] qBadges = new TextView[BAND_COUNT];
    private final View[] bandColumns = new View[BAND_COUNT];

    // Inspector
    private TextView inspectorBandLabel;
    private Slider inspectorQSlider;
    private TextView inspectorQVal;
    private int selectedBand = 5;

    // Macro knobs
    private KnobView bassKnob;
    private KnobView virtualizerKnob;
    private KnobView tempoKnob;
    private KnobView pitchKnob;
    private MaterialSwitch bassSwitch;
    private MaterialSwitch virtualizerSwitch;
    private MaterialSwitch tempoSwitch;
    private MaterialSwitch pitchSwitch;

    // FX racks
    private MaterialSwitch stereoSwitch;
    private MaterialSwitch exciterSwitch;
    private MaterialSwitch compressorSwitch;
    private MaterialSwitch limiterSwitch;
    private MaterialSwitch gateSwitch;
    private MaterialSwitch deEsserSwitch;

    private Slider stereoWidthSlider;
    private Slider exciterAmountSlider;
    private Slider exciterFreqSlider;
    private Slider compThresholdSlider;
    private Slider compRatioSlider;
    private Slider compAttackSlider;
    private Slider compReleaseSlider;
    private Slider limiterCeilingSlider;
    private Slider limiterReleaseSlider;
    private Slider gateThresholdSlider;
    private Slider gateHysteresisSlider;
    private Slider gateAttackSlider;
    private Slider gateHoldSlider;
    private Slider gateReleaseSlider;
    private Slider deEsserThresholdSlider;
    private Slider deEsserFreqSlider;

    private TextView valStereoWidth;
    private TextView valExciterAmount;
    private TextView valExciterFreq;
    private TextView valCompThreshold;
    private TextView valCompRatio;
    private TextView valCompAttack;
    private TextView valCompRelease;
    private TextView valLimiterCeiling;
    private TextView valLimiterRelease;
    private TextView valGateThreshold;
    private TextView valGateHysteresis;
    private TextView valGateAttack;
    private TextView valGateHold;
    private TextView valGateRelease;
    private TextView valDeEsserThreshold;
    private TextView valDeEsserFreq;

    private MaterialButton flattenButton;
    private MaterialButton savePresetButton;
    private MaterialButton applyButton;

    // History
    private final Deque<float[]> undoStack = new ArrayDeque<>();
    private final Deque<float[]> redoStack = new ArrayDeque<>();
    private boolean suppressHistory;
    private final AtomicBoolean isVisible = new AtomicBoolean(false);
    private int sessionId = -1;


    // Spectrum loop
    private final float[] spectrumBuf = new float[256];
    private final float[] eqCurveBuf = new float[256];
    private boolean spectrumRunning;
    private final Runnable spectrumTick = new Runnable() {
        @Override
        public void run() {
            if (!spectrumRunning) return;
            tickSpectrum();
            main.postDelayed(this, 33);
        }
    };

    public EqualizerViewPanel(
            @NonNull FragmentHome fragment,
            @NonNull ViewGroup parent,
            @NonNull EqualizerViewModel equalizerViewModel) {
        this.context = fragment.requireContext();
        this.vm = equalizerViewModel;
        this.rootView = LayoutInflater.from(context)
                .inflate(R.layout.media_equalizer_view, parent, false);

        bindViews(rootView);
        ensureTenIsoBands();
        wireToolbar(fragment);
        wirePresets();
        wireFaders();
        wireInspector();
        wireMacroKnobs();
        wireFxRacks();
        wireFooter();
        wireRackToggles();
        observeVm(fragment.getViewLifecycleOwner());
        refreshAllFromVm();
        startSpectrum();
    }

    @NonNull
    public View getView() {
        return rootView;
    }

    public void onShow() {
        startSpectrum();
        refreshAllFromVm();
    }

    public void onHide() {
        stopSpectrum();
    }

    // ── Bind ──────────────────────────────────────────────────────────────

    private void bindViews(View v) {
        closeButton = v.findViewById(R.id.equalizer_view_close);
        undoButton = v.findViewById(R.id.eq_undo_button);
        redoButton = v.findViewById(R.id.eq_redo_button);
        resetButton = v.findViewById(R.id.eq_reset);
        masterSwitch = v.findViewById(R.id.eq_enable_disable);

        presetGroup = v.findViewById(R.id.sound_profile_chip_group);
        slotA = v.findViewById(R.id.btn_ab_a);
        slotB = v.findViewById(R.id.btn_ab_b);

        spectrumView = v.findViewById(R.id.spectrum_canvas);
        spectrumOffline = v.findViewById(R.id.spectrum_offline_overlay);
        peakDbText = v.findViewById(R.id.val_peak_db);
        activeBandsText = v.findViewById(R.id.val_active_q);

        fadersContainer = v.findViewById(R.id.faders_container);
        for (int i = 0; i < BAND_COUNT && i < fadersContainer.getChildCount(); i++) {
            View col = fadersContainer.getChildAt(i);
            bandColumns[i] = col;
            gainBars[i] = col.findViewById(R.id.eqFreqSeekBar);
            gainLabels[i] = col.findViewById(R.id.eqDbValue);
            freqLabels[i] = col.findViewById(R.id.eqTextView);
            qBadges[i] = col.findViewById(R.id.eqQValue);
            if (freqLabels[i] != null) freqLabels[i].setText(ISO_LABELS[i]);
            if (gainBars[i] != null) {
                gainBars[i].setMax(SEEK_MAX);
            }
        }

        inspectorBandLabel = v.findViewById(R.id.inspector_band_label);
        inspectorQSlider = v.findViewById(R.id.inspector_q_slider);
        inspectorQVal = v.findViewById(R.id.inspector_q_val);

        bassKnob = v.findViewById(R.id.knob_bass_boost);
        virtualizerKnob = v.findViewById(R.id.knob_virtualizer);
        tempoKnob = v.findViewById(R.id.knob_tempo_speed);
        pitchKnob = v.findViewById(R.id.knob_pitch_shift);
        bassSwitch = v.findViewById(R.id.bass_enable_disable);
        virtualizerSwitch = v.findViewById(R.id.virtualizer_enable_disable);
        tempoSwitch = v.findViewById(R.id.tempo_enable_disable);
        pitchSwitch = v.findViewById(R.id.pitch_enable_disable);

        stereoSwitch = v.findViewById(R.id.switch_stereo_enabled);
        exciterSwitch = v.findViewById(R.id.switch_exciter_enabled);
        compressorSwitch = v.findViewById(R.id.switch_compressor_enabled);
        limiterSwitch = v.findViewById(R.id.switch_limiter_enabled);
        gateSwitch = v.findViewById(R.id.switch_gate_enabled);
        deEsserSwitch = v.findViewById(R.id.switch_deesser_enabled);

        // Only IDs present in media_equalizer_view.xml
        stereoWidthSlider = v.findViewById(R.id.slider_stereo_width);
        exciterAmountSlider = v.findViewById(R.id.slider_exciter_amount);
        exciterFreqSlider = null;
        compThresholdSlider = v.findViewById(R.id.slider_comp_threshold);
        compRatioSlider = null;
        compAttackSlider = null;
        compReleaseSlider = null;
        limiterCeilingSlider = v.findViewById(R.id.slider_limiter_ceiling);
        limiterReleaseSlider = null;
        gateThresholdSlider = v.findViewById(R.id.slider_gate_threshold);
        gateHysteresisSlider = null;
        gateAttackSlider = null;
        gateHoldSlider = null;
        gateReleaseSlider = null;
        deEsserThresholdSlider = v.findViewById(R.id.slider_deesser_threshold);
        deEsserFreqSlider = null;

        valStereoWidth = v.findViewById(R.id.val_stereo_width);
        valExciterAmount = v.findViewById(R.id.val_exciter_amount);
        valExciterFreq = null;
        valCompThreshold = null;
        valCompRatio = null;
        valCompAttack = null;
        valCompRelease = null;
        valLimiterCeiling = null;
        valLimiterRelease = null;
        valGateThreshold = null;
        valGateHysteresis = null;
        valGateAttack = null;
        valGateHold = null;
        valGateRelease = null;
        valDeEsserThreshold = null;
        valDeEsserFreq = null;

        flattenButton = v.findViewById(R.id.btn_eq_flatten);
        savePresetButton = v.findViewById(R.id.btn_save_preset);
        applyButton = v.findViewById(R.id.btn_apply_playback);
    }

    private void ensureTenIsoBands() {
        List<ParametricEQBand> bands = vm.getEqBands().getValue();
        if (bands == null || bands.size() != BAND_COUNT) {
            List<ParametricEQBand> iso = new ArrayList<>(BAND_COUNT);
            for (int i = 0; i < BAND_COUNT; i++) {
                iso.add(new ParametricEQBand(i, ISO_FREQ_HZ[i], 0f, 1.4f));
            }
            // Use public API if available — otherwise setEqBand in a loop after init
            for (int i = 0; i < BAND_COUNT; i++) {
                vm.setEqBand(i, ISO_FREQ_HZ[i], 0f, 1.4f);
            }
        }
    }

    // ── Toolbar ───────────────────────────────────────────────────────────

    private void wireToolbar(FragmentHome fragment) {
        if (closeButton != null) {
            closeButton.setOnClickListener(v -> {
                stopSpectrum();
                if (fragment.getParentFragmentManager().getBackStackEntryCount() > 0) {
                    fragment.getParentFragmentManager().popBackStack();
                } else {
                    rootView.setVisibility(View.GONE);
                }
            });
        }
        if (undoButton != null) {
            undoButton.setOnClickListener(v -> undo());
        }
        if (redoButton != null) {
            redoButton.setOnClickListener(v -> redo());
        }
        if (resetButton != null) {
            resetButton.setOnClickListener(v -> {
                pushHistory();
                flattenAll();
            });
        }
        if (masterSwitch != null) {
            masterSwitch.setOnCheckedChangeListener((btn, checked) -> {
                vm.setEqualizerEnabled(checked);
                if (spectrumOffline != null) {
                    spectrumOffline.setVisibility(checked ? View.GONE : View.VISIBLE);
                }
                View workspace = rootView.findViewById(R.id.eq_workspace_root);
                if (workspace != null) {
                    workspace.setAlpha(checked ? 1f : 0.45f);
                }
                applyAllBandsToEngine();
            });
        }
    }

    // ── Presets ───────────────────────────────────────────────────────────

    private void wirePresets() {
        if (presetGroup != null) {
            for (int i = 0; i < presetGroup.getChildCount(); i++) {
                View child = presetGroup.getChildAt(i);
                if (!(child instanceof Chip)) continue;
                Chip chip = (Chip) child;
                chip.setOnClickListener(v -> {
                    pushHistory();
                    String name = chip.getText() != null ? chip.getText().toString() : "";
                    applyNamedPreset(name);
                    highlightChip(chip);
                });
            }
        }
        if (slotA != null) {
            slotA.setOnClickListener(v -> {
                vm.switchToPresetA();
                refreshAllFromVm();
                styleAb(true);
            });
        }
        if (slotB != null) {
            slotB.setOnClickListener(v -> {
                vm.switchToPresetB();
                refreshAllFromVm();
                styleAb(false);
            });
        }
    }

    private void highlightChip(@Nullable Chip active) {
        if (presetGroup == null) return;
        for (int i = 0; i < presetGroup.getChildCount(); i++) {
            View c = presetGroup.getChildAt(i);
            if (c instanceof Chip) {
                ((Chip) c).setChecked(c == active);
            }
        }
    }

    private void styleAb(boolean aActive) {
        if (slotA != null) slotA.setSelected(aActive);
        if (slotB != null) slotB.setSelected(!aActive);
    }

    private void applyNamedPreset(@NonNull String name) {
        float[] gains = presetGains(name);
        for (int i = 0; i < BAND_COUNT; i++) {
            float q = 1.4f;
            vm.setEqBand(i, ISO_FREQ_HZ[i], gains[i], q);
        }
        refreshFaderUi();
        applyAllBandsToEngine();
    }

    @NonNull
    private static float[] presetGains(@NonNull String name) {
        String n = name.toLowerCase(Locale.US);
        if (n.contains("flat") || n.contains("0db")) {
            return zeros();
        }
        if (n.contains("rock")) {
            return new float[]{4.5f, 3f, 1.5f, -0.5f, -1.5f, 0.5f, 2f, 3.5f, 4f, 4.5f};
        }
        if (n.contains("pop")) {
            return new float[]{1.5f, 2.5f, 3f, 1f, 0f, 0.5f, 1.5f, 2.5f, 3f, 3.5f};
        }
        if (n.contains("dance") || n.contains("edm") || n.contains("hip")) {
            return new float[]{5.5f, 4.5f, 2f, 0f, 0f, 2f, 3.5f, 4f, 4.5f, 3f};
        }
        if (n.contains("acoustic") || n.contains("folk")) {
            return new float[]{3f, 2.5f, 1f, 0.5f, 1f, 1.5f, 2f, 3f, 3.5f, 3f};
        }
        if (n.contains("jazz")) {
            return new float[]{3.5f, 2.5f, 1f, 1.5f, -0.5f, -0.5f, 1f, 2f, 3f, 3.5f};
        }
        if (n.contains("vocal")) {
            return new float[]{-1f, -0.5f, 0f, 1.5f, 3.5f, 4f, 3f, 2f, 0.5f, 0f};
        }
        if (n.contains("bass") || n.contains("deep")) {
            return new float[]{7f, 6f, 4.5f, 2f, 0.5f, -0.5f, 0f, 0f, 0f, 0f};
        }
        if (n.contains("treble") || n.contains("crisp")) {
            return new float[]{0f, 0f, 0f, 0f, 0f, 1f, 2.5f, 4.5f, 6f, 7.5f};
        }
        if (n.contains("classical") || n.contains("piano")) {
            return new float[]{3f, 2f, 1f, 0f, 0f, 0f, 1f, 2f, 3f, 3.5f};
        }
        if (n.contains("metal") || n.contains("loud")) {
            return new float[]{5f, 3f, 0f, -1f, -2f, 1f, 3f, 4f, 5f, 5f};
        }
        return zeros();
    }

    @NonNull
    private static float[] zeros() {
        float[] z = new float[BAND_COUNT];
        Arrays.fill(z, 0f);
        return z;
    }

    // ── Faders ────────────────────────────────────────────────────────────

    private void wireFaders() {
        for (int i = 0; i < BAND_COUNT; i++) {
            final int index = i;
            VerticalSeekBar bar = gainBars[i];
            if (bar == null) continue;
            bar.setOnSeekBarChangeListener(new VerticalSeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(VerticalSeekBar seekBar, int progress, boolean fromUser) {
                    if (!fromUser) return;
                    float gain = progressToGain(progress);
                    updateGainLabel(index, gain);
                    ParametricEQBand band = bandAt(index);
                    float q = band != null ? band.getQFactor() : 1.4f;
                    vm.setEqBand(index, ISO_FREQ_HZ[index], gain, q);
                    updateEqCurveOverlay();
                }

                @Override
                public void onStartTrackingTouch(VerticalSeekBar seekBar) {
                    selectBand(index);
                }

                @Override
                public void onStopTrackingTouch(VerticalSeekBar seekBar) {
                    pushHistory();
                }
            });
            if (bandColumns[i] != null) {
                bandColumns[i].setOnClickListener(v -> selectBand(index));
            }
        }
    }

    private void selectBand(int index) {
        selectedBand = index;
        for (int i = 0; i < BAND_COUNT; i++) {
            if (bandColumns[i] != null) {
                bandColumns[i].setSelected(i == index);
                bandColumns[i].setAlpha(i == index ? 1f : 0.85f);
            }
        }
        ParametricEQBand band = bandAt(index);
        if (inspectorBandLabel != null) {
            inspectorBandLabel.setText(String.format(Locale.US,
                    "Band %d (%s)", index + 1, ISO_LABELS[index]));
        }
        if (inspectorQSlider != null && band != null) {
            inspectorQSlider.setValue(clamp(band.getQFactor(), 0.1f, 10f));
        }
        if (inspectorQVal != null && band != null) {
            inspectorQVal.setText(String.format(Locale.US, "%.1f", band.getQFactor()));
        }
    }

    private void wireInspector() {
        if (inspectorQSlider == null) return;
        inspectorQSlider.addOnChangeListener((slider, value, fromUser) -> {
            if (!fromUser) return;
            if (inspectorQVal != null) {
                inspectorQVal.setText(String.format(Locale.US, "%.1f", value));
            }
            ParametricEQBand band = bandAt(selectedBand);
            float gain = band != null ? band.getGainDb() : 0f;
            vm.setEqBand(selectedBand, ISO_FREQ_HZ[selectedBand], gain, value);
            if (qBadges[selectedBand] != null) {
                qBadges[selectedBand].setText(String.format(Locale.US, "Q:%.1f", value));
            }
            updateEqCurveOverlay();
        });
        inspectorQSlider.addOnSliderTouchListener(new Slider.OnSliderTouchListener() {
            @Override
            public void onStartTrackingTouch(@NonNull Slider slider) { }

            @Override
            public void onStopTrackingTouch(@NonNull Slider slider) {
                pushHistory();
            }
        });
    }

    // ── Macro knobs ───────────────────────────────────────────────────────

    private void wireMacroKnobs() {
        if (bassSwitch != null) {
            bassSwitch.setOnCheckedChangeListener((b, on) -> {
                int strength = bassKnob != null ? Math.round(bassKnob.getValue() * 100f) : 50;
                vm.setBassBoost(on, strength);
            });
        }
        if (bassKnob != null) {
            bassKnob.setOnValueChangedListener(v -> {
                boolean on = bassSwitch == null || bassSwitch.isChecked();
                vm.setBassBoost(on, Math.round(v * 100f));
            });
        }
        if (virtualizerSwitch != null) {
            virtualizerSwitch.setOnCheckedChangeListener((b, on) -> {
                int strength = virtualizerKnob != null
                        ? Math.round(virtualizerKnob.getValue() * 100f) : 50;
                vm.setVirtualizer(on, strength);
            });
        }
        if (virtualizerKnob != null) {
            virtualizerKnob.setOnValueChangedListener(v -> {
                boolean on = virtualizerSwitch == null || virtualizerSwitch.isChecked();
                vm.setVirtualizer(on, Math.round(v * 100f));
            });
        }
        if (tempoSwitch != null && tempoKnob != null) {
            tempoSwitch.setOnCheckedChangeListener((b, on) -> {
                if (!on) vm.setPlaybackSpeed(1f);
                else vm.setPlaybackSpeed(0.5f + tempoKnob.getValue() * 1.5f);
            });
            tempoKnob.setOnValueChangedListener(v -> {
                if (tempoSwitch != null && tempoSwitch.isChecked()) {
                    vm.setPlaybackSpeed(0.5f + v * 1.5f);
                }
            });
        }
        if (pitchSwitch != null && pitchKnob != null) {
            pitchSwitch.setOnCheckedChangeListener((b, on) -> {
                if (!on) vm.setPlaybackPitch(1f);
                else vm.setPlaybackPitch(0.5f + pitchKnob.getValue() * 1.5f);
            });
            pitchKnob.setOnValueChangedListener(v -> {
                if (pitchSwitch != null && pitchSwitch.isChecked()) {
                    vm.setPlaybackPitch(0.5f + v * 1.5f);
                }
            });
        }
    }

    // ── FX racks ──────────────────────────────────────────────────────────

    private void wireFxRacks() {
        bindSwitchSlider(stereoSwitch, stereoWidthSlider, valStereoWidth, "%",
                (on, val) -> vm.setStereoWidening(on, val / 100f), 0f, 100f);

        bindSwitchSlider(exciterSwitch, exciterAmountSlider, valExciterAmount, " dB",
                (on, val) -> {
                    float freq = exciterFreqSlider != null ? exciterFreqSlider.getValue() : 2500f;
                    vm.setExciter(on, val, freq);
                }, 0f, 12f);
        if (exciterFreqSlider != null) {
            exciterFreqSlider.addOnChangeListener((s, v, fromUser) -> {
                if (valExciterFreq != null) {
                    valExciterFreq.setText(String.format(Locale.US, "%.0f Hz", v));
                }
                if (fromUser) {
                    boolean on = exciterSwitch == null || exciterSwitch.isChecked();
                    float amt = exciterAmountSlider != null ? exciterAmountSlider.getValue() : 0f;
                    vm.setExciter(on, amt, v);
                }
            });
        }

        if (compressorSwitch != null) {
            compressorSwitch.setOnCheckedChangeListener((b, on) -> applyCompressor(on));
        }
        bindSliderReadout(compThresholdSlider, valCompThreshold, " dB", () ->
                applyCompressor(compressorSwitch == null || compressorSwitch.isChecked()));
        bindSliderReadout(compRatioSlider, valCompRatio, ":1", () ->
                applyCompressor(compressorSwitch == null || compressorSwitch.isChecked()));
        bindSliderReadout(compAttackSlider, valCompAttack, " ms", () ->
                applyCompressor(compressorSwitch == null || compressorSwitch.isChecked()));
        bindSliderReadout(compReleaseSlider, valCompRelease, " ms", () ->
                applyCompressor(compressorSwitch == null || compressorSwitch.isChecked()));

        if (limiterSwitch != null) {
            limiterSwitch.setOnCheckedChangeListener((b, on) -> applyLimiter(on));
        }
        bindSliderReadout(limiterCeilingSlider, valLimiterCeiling, " dB", () ->
                applyLimiter(limiterSwitch == null || limiterSwitch.isChecked()));
        bindSliderReadout(limiterReleaseSlider, valLimiterRelease, " ms", () ->
                applyLimiter(limiterSwitch == null || limiterSwitch.isChecked()));

        if (gateSwitch != null) {
            gateSwitch.setOnCheckedChangeListener((b, on) -> applyGate(on));
        }
        bindSliderReadout(gateThresholdSlider, valGateThreshold, " dB", () ->
                applyGate(gateSwitch == null || gateSwitch.isChecked()));
        bindSliderReadout(gateHysteresisSlider, valGateHysteresis, " dB", () ->
                applyGate(gateSwitch == null || gateSwitch.isChecked()));
        bindSliderReadout(gateAttackSlider, valGateAttack, " ms", () ->
                applyGate(gateSwitch == null || gateSwitch.isChecked()));
        bindSliderReadout(gateHoldSlider, valGateHold, " ms", () ->
                applyGate(gateSwitch == null || gateSwitch.isChecked()));
        bindSliderReadout(gateReleaseSlider, valGateRelease, " ms", () ->
                applyGate(gateSwitch == null || gateSwitch.isChecked()));

        if (deEsserSwitch != null) {
            deEsserSwitch.setOnCheckedChangeListener((b, on) -> applyDeEsser(on));
        }
        bindSliderReadout(deEsserThresholdSlider, valDeEsserThreshold, " dB", () ->
                applyDeEsser(deEsserSwitch == null || deEsserSwitch.isChecked()));
        bindSliderReadout(deEsserFreqSlider, valDeEsserFreq, " Hz", () ->
                applyDeEsser(deEsserSwitch == null || deEsserSwitch.isChecked()));
    }

    private interface FxBiConsumer {
        void accept(boolean on, float value);
    }

    private void bindSwitchSlider(
            @Nullable MaterialSwitch sw,
            @Nullable Slider slider,
            @Nullable TextView readout,
            @NonNull String unit,
            @NonNull FxBiConsumer apply,
            float unusedMin,
            float unusedMax) {
        if (sw != null) {
            sw.setOnCheckedChangeListener((b, on) -> {
                float v = slider != null ? slider.getValue() : 0f;
                apply.accept(on, v);
            });
        }
        if (slider != null) {
            slider.addOnChangeListener((s, value, fromUser) -> {
                if (readout != null) {
                    if (unit.contains("%")) {
                        readout.setText(String.format(Locale.US, "%.0f%s", value, unit));
                    } else if (unit.contains(":")) {
                        readout.setText(String.format(Locale.US, "%.1f%s", value, unit));
                    } else {
                        readout.setText(String.format(Locale.US, "%+.1f%s", value, unit.trim()));
                    }
                }
                if (fromUser) {
                    boolean on = sw == null || sw.isChecked();
                    apply.accept(on, value);
                }
            });
        }
    }

    private void bindSliderReadout(
            @Nullable Slider slider,
            @Nullable TextView readout,
            @NonNull String unit,
            @NonNull Runnable onChange) {
        if (slider == null) return;
        slider.addOnChangeListener((s, value, fromUser) -> {
            if (readout != null) {
                if (unit.contains("Hz") || unit.contains("ms") || unit.contains(":")) {
                    readout.setText(String.format(Locale.US, "%.0f%s", value, unit));
                } else {
                    readout.setText(String.format(Locale.US, "%+.1f%s", value, unit));
                }
            }
            if (fromUser) onChange.run();
        });
    }

    private void applyCompressor(boolean on) {
        float th = compThresholdSlider != null ? compThresholdSlider.getValue() : -20f;
        float ratio = compRatioSlider != null ? compRatioSlider.getValue() : 4f;
        float atk = compAttackSlider != null ? compAttackSlider.getValue() : 10f;
        float rel = compReleaseSlider != null ? compReleaseSlider.getValue() : 100f;
        vm.setCompressor(on, th, ratio, atk, rel);
    }

    private void applyLimiter(boolean on) {
        float ceiling = limiterCeilingSlider != null ? limiterCeilingSlider.getValue() : -1f;
        float rel = limiterReleaseSlider != null ? limiterReleaseSlider.getValue() : 50f;
        vm.setLimiter(on, ceiling, rel);
    }

    private void applyGate(boolean on) {
        float th = gateThresholdSlider != null ? gateThresholdSlider.getValue() : -40f;
        float hy = gateHysteresisSlider != null ? gateHysteresisSlider.getValue() : 6f;
        float atk = gateAttackSlider != null ? gateAttackSlider.getValue() : 5f;
        float hold = gateHoldSlider != null ? gateHoldSlider.getValue() : 50f;
        float rel = gateReleaseSlider != null ? gateReleaseSlider.getValue() : 100f;
        vm.setNoiseGate(on, th, hy, atk, hold, rel);
    }

    private void applyDeEsser(boolean on) {
        float th = deEsserThresholdSlider != null ? deEsserThresholdSlider.getValue() : -20f;
        float freq = deEsserFreqSlider != null ? deEsserFreqSlider.getValue() : 6000f;
        vm.setDeEsser(on, th, freq);
    }

    private void wireRackToggles() {
        // Optional: click header to expand/collapse content — user may customize later
        togglePair(R.id.rack_stereo_header, R.id.rack_stereo_content, R.id.rack_stereo_chevron);
        togglePair(R.id.rack_exciter_header, R.id.rack_exciter_content, R.id.rack_exciter_chevron);
        togglePair(R.id.rack_compressor_header, R.id.rack_compressor_content, R.id.rack_compressor_chevron);
        togglePair(R.id.rack_limiter_header, R.id.rack_limiter_content, R.id.rack_limiter_chevron);
        togglePair(R.id.rack_gate_header, R.id.rack_gate_content, R.id.rack_gate_chevron);
        togglePair(R.id.rack_deesser_header, R.id.rack_deesser_content, R.id.rack_deesser_chevron);
    }

    private void togglePair(int headerId, int contentId, int chevronId) {
        View header = rootView.findViewById(headerId);
        View content = rootView.findViewById(contentId);
        View chevron = rootView.findViewById(chevronId);
        if (header == null || content == null) return;
        header.setOnClickListener(v -> {
            boolean open = content.getVisibility() != View.VISIBLE;
            content.setVisibility(open ? View.VISIBLE : View.GONE);
            if (chevron != null) {
                chevron.animate().rotation(open ? 180f : 0f).setDuration(180).start();
            }
        });
    }

    private void wireFooter() {
        if (flattenButton != null) {
            flattenButton.setOnClickListener(v -> {
                pushHistory();
                flattenAll();
            });
        }
        if (savePresetButton != null) {
            savePresetButton.setOnClickListener(v -> vm.saveCurrentAsUserPreset("User Preset"));
        }
        if (applyButton != null) {
            applyButton.setOnClickListener(v -> {
                applyAllBandsToEngine();
                applyCompressor(compressorSwitch == null || compressorSwitch.isChecked());
                applyLimiter(limiterSwitch == null || limiterSwitch.isChecked());
                applyGate(gateSwitch == null || gateSwitch.isChecked());
                applyDeEsser(deEsserSwitch == null || deEsserSwitch.isChecked());
                applyButton.setText("Applied");
                applyButton.postDelayed(() -> applyButton.setText("Apply Engine"), 1200);
            });
        }
    }

    // ── VM observe / refresh ──────────────────────────────────────────────

    private void observeVm(@Nullable LifecycleOwner owner) {
        if (owner == null) return;
        vm.getEqBands().observe(owner, bands -> {
            if (bands == null || suppressHistory) return;
            refreshFaderUi();
        });
        vm.isEqualizerEnabled().observe(owner, on -> {
            if (masterSwitch != null && on != null && masterSwitch.isChecked() != on) {
                masterSwitch.setChecked(on);
            }
        });
    }

    private void refreshAllFromVm() {
        suppressHistory = true;
        try {
            refreshFaderUi();
            Boolean on = vm.isEqualizerEnabled().getValue();
            if (masterSwitch != null && on != null) masterSwitch.setChecked(on);
            selectBand(selectedBand);
        } finally {
            suppressHistory = false;
        }
    }

    private void refreshFaderUi() {
        List<ParametricEQBand> bands = vm.getEqBands().getValue();
        for (int i = 0; i < BAND_COUNT; i++) {
            float gain = 0f;
            float q = 1.4f;
            if (bands != null && i < bands.size()) {
                gain = bands.get(i).getGainDb();
                q = bands.get(i).getQFactor();
            }
            if (gainBars[i] != null) {
                gainBars[i].setProgress(gainToProgress(gain));
            }
            updateGainLabel(i, gain);
            if (qBadges[i] != null) {
                qBadges[i].setText(String.format(Locale.US, "Q:%.1f", q));
            }
        }
        updateEqCurveOverlay();
        if (activeBandsText != null) {
            activeBandsText.setText(BAND_COUNT + " BANDS");
        }
    }

    // ── Engine apply ──────────────────────────────────────────────────────

    private void applyAllBandsToEngine() {
        List<ParametricEQBand> bands = vm.getEqBands().getValue();
        boolean on = Boolean.TRUE.equals(vm.isEqualizerEnabled().getValue());
        AudioEngine engine = vm.getAudioEngineOrNull();
        if (engine == null) {
            // Fall back through ViewModel setters
            for (int i = 0; i < BAND_COUNT; i++) {
                float gain = bands != null && i < bands.size() ? bands.get(i).getGainDb() : 0f;
                float q = bands != null && i < bands.size() ? bands.get(i).getQFactor() : 1.4f;
                vm.setEqBand(i, ISO_FREQ_HZ[i], gain, q);
            }
            return;
        }
        engine.enableEqualizer(on);
        for (int i = 0; i < BAND_COUNT; i++) {
            float gain = bands != null && i < bands.size() ? bands.get(i).getGainDb() : 0f;
            float q = bands != null && i < bands.size() ? bands.get(i).getQFactor() : 1.4f;
            engine.setParametricEqualizerBand(i, ISO_FREQ_HZ[i], gain, q, on);
        }
    }

    private void flattenAll() {
        for (int i = 0; i < BAND_COUNT; i++) {
            vm.setEqBand(i, ISO_FREQ_HZ[i], 0f, 1.4f);
        }
        refreshFaderUi();
        applyAllBandsToEngine();
    }

    // ── History ───────────────────────────────────────────────────────────

    private void pushHistory() {
        if (suppressHistory) return;
        float[] snap = snapshotGains();
        undoStack.push(snap);
        while (undoStack.size() > 40) undoStack.removeLast();
        redoStack.clear();
    }

    private void undo() {
        if (undoStack.isEmpty()) return;
        redoStack.push(snapshotGains());
        applySnapshot(undoStack.pop());
    }

    private void redo() {
        if (redoStack.isEmpty()) return;
        undoStack.push(snapshotGains());
        applySnapshot(redoStack.pop());
    }

    @NonNull
    private float[] snapshotGains() {
        float[] g = new float[BAND_COUNT];
        List<ParametricEQBand> bands = vm.getEqBands().getValue();
        for (int i = 0; i < BAND_COUNT; i++) {
            g[i] = bands != null && i < bands.size() ? bands.get(i).getGainDb() : 0f;
        }
        return g;
    }

    private void applySnapshot(@NonNull float[] gains) {
        suppressHistory = true;
        try {
            for (int i = 0; i < BAND_COUNT; i++) {
                ParametricEQBand b = bandAt(i);
                float q = b != null ? b.getQFactor() : 1.4f;
                vm.setEqBand(i, ISO_FREQ_HZ[i], gains[i], q);
            }
            refreshFaderUi();
            applyAllBandsToEngine();
        } finally {
            suppressHistory = false;
        }
    }

    // ── Spectrum ──────────────────────────────────────────────────────────

    private void startSpectrum() {
        spectrumRunning = true;
        main.removeCallbacks(spectrumTick);
        main.post(spectrumTick);
    }

    private void stopSpectrum() {
        spectrumRunning = false;
        main.removeCallbacks(spectrumTick);
    }

    private void tickSpectrum() {
        AudioEngine engine = vm.getAudioEngineOrNull();
        if (spectrumView == null) return;
        if (engine != null && engine.isSpectrumDataReady()) {
            if (spectrumBuf.length != Math.max(16, engine.getSpectrumNumBins())) {
                // keep buffer size
            }
            engine.getSpectrumMagnitudes(spectrumBuf);
            spectrumView.setFftData(spectrumBuf);
            if (spectrumOffline != null) spectrumOffline.setVisibility(View.GONE);
            float peak = 0f;
            for (float m : spectrumBuf) if (m > peak) peak = m;
            if (peakDbText != null) {
                float db = (float) (20.0 * Math.log10(Math.max(1e-6, peak)));
                peakDbText.setText(String.format(Locale.US, "%+.1f dB", db));
            }
        }
        updateEqCurveOverlay();
    }

    private void updateEqCurveOverlay() {
        if (spectrumView == null) return;
        List<ParametricEQBand> bands = vm.getEqBands().getValue();
        int n = eqCurveBuf.length;
        for (int i = 0; i < n; i++) {
            float t = i / (float) (n - 1);
            // Map to log-ish band index
            int bi = Math.min(BAND_COUNT - 1, Math.round(t * (BAND_COUNT - 1)));
            float gain = bands != null && bi < bands.size() ? bands.get(bi).getGainDb() : 0f;
            // Normalize -12..+12 → 0..1 for view
            eqCurveBuf[i] = (gain - GAIN_MIN_DB) / (GAIN_MAX_DB - GAIN_MIN_DB);
        }
        spectrumView.setEqCurveData(eqCurveBuf);
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    @Nullable
    private ParametricEQBand bandAt(int index) {
        List<ParametricEQBand> bands = vm.getEqBands().getValue();
        if (bands == null || index < 0 || index >= bands.size()) return null;
        return bands.get(index);
    }

    private static float progressToGain(int progress) {
        float t = progress / (float) SEEK_MAX;
        return GAIN_MIN_DB + t * (GAIN_MAX_DB - GAIN_MIN_DB);
    }

    private static int gainToProgress(float gainDb) {
        float t = (gainDb - GAIN_MIN_DB) / (GAIN_MAX_DB - GAIN_MIN_DB);
        return Math.round(clamp(t, 0f, 1f) * SEEK_MAX);
    }

    private void updateGainLabel(int index, float gain) {
        if (gainLabels[index] == null) return;
        gainLabels[index].setText(String.format(Locale.US, "%+.1f", gain));
    }

    private static float clamp(float v, float lo, float hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    // ── FragmentHome contract ─────────────────────────────────────────────

    public void setSessionId(int id) {
        this.sessionId = id;
    }

    public int getSessionId() {
        return sessionId;
    }

    @NonNull
    public AtomicBoolean getIsVisible() {
        return isVisible;
    }

    public void setIsVisible(boolean visible) {
        isVisible.set(visible);
        rootView.setVisibility(visible ? View.VISIBLE : View.GONE);
        if (visible) {
            onShow();
        } else {
            onHide();
        }
    }

    public void setBottomPadding(int paddingPx) {
        if (rootView != null) {
            rootView.setPadding(
                    rootView.getPaddingLeft(),
                    rootView.getPaddingTop(),
                    rootView.getPaddingRight(),
                    Math.max(0, paddingPx));
        }
    }

    public void onDestroy() {
        stopSpectrum();
        undoStack.clear();
        redoStack.clear();
    }
}
