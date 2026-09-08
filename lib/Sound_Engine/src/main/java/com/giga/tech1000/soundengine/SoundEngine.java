package com.giga.tech1000.soundengine;

import android.content.Context;

/**
 * Main interface for the Sound Engine using DSPark for DSP processing.
 * Provides methods to control audio effects and process audio buffers.
 */
public class SoundEngine {
    static {
        System.loadLibrary("soundengine");
    }

    private final Context context;

    public SoundEngine(Context context) {
        this.context = context.getApplicationContext();
        initialize();
    }

    /**
     * Initializes the sound engine and underlying DSPark components.
     * @param sampleRate Audio sample rate in Hz
     * @param maxBlockSize Maximum block size for processing
     * @param numChannels Number of audio channels (1 for mono, 2 for stereo)
     * @return true if initialization successful
     */
    public native boolean initialize(float sampleRate, int maxBlockSize, int numChannels);

    /**
     * Releases resources used by the sound engine.
     */
    public native void release();

    /**
     * Sets parameters for a specific EQ band.
     * @param bandIndex Index of the EQ band (0-9)
     * @param frequency Center frequency in Hz
     * @param gain Gain in dB
     * @param q Quality factor
     * @param enabled Whether the band is enabled
     */
    public native void setEqBand(int bandIndex, float frequency, float gain, float q, boolean enabled);

    /**
     * Sets the frequency for a specific EQ band.
     * @param bandIndex Index of the EQ band (0-9)
     * @param frequency Center frequency in Hz
     */
    public native void setEqBandFrequency(int bandIndex, float frequency);

    /**
     * Sets the gain for a specific EQ band.
     * @param bandIndex Index of the EQ band (0-9)
     * @param gain Gain in dB
     */
    public native void setEqBandGain(int bandIndex, float gain);

    /**
     * Sets the Q factor for a specific EQ band.
     * @param bandIndex Index of the EQ band (0-9)
     * @param q Quality factor
     */
    public native void setEqBandQ(int bandIndex, float q);

    /**
     * Enables or disables a specific EQ band.
     * @param bandIndex Index of the EQ band (0-9)
     * @param enabled Whether the band should be enabled
     */
    public native void setEqBandEnabled(int bandIndex, boolean enabled);

    /**
     * Gets the frequency for a specific EQ band.
     * @param bandIndex Index of the EQ band (0-9)
     * @return Center frequency in Hz
     */
    public native float getEqBandFrequency(int bandIndex);

    /**
     * Gets the gain for a specific EQ band.
     * @param bandIndex Index of the EQ band (0-9)
     * @return Gain in dB
     */
    public native float getEqBandGain(int bandIndex);

    /**
     * Gets the Q factor for a specific EQ band.
     * @param bandIndex Index of the EQ band (0-9)
     * @return Quality factor
     */
    public native float getEqBandQ(int bandIndex);

    /**
     * Gets whether a specific EQ band is enabled.
     * @param bandIndex Index of the EQ band (0-9)
     * @return true if the band is enabled
     */
    public native boolean getEqBandEnabled(int bandIndex);

    /**
     * Enables or disables stereo widening.
     * @param enabled Whether stereo widening should be enabled
     */
    public native void setStereoWidthEnabled(boolean enabled);

    /**
     * Sets the stereo width amount.
     * @param width Stereo width (0.0 = mono, 1.0 = normal, >1.0 = widened)
     */
    public native void setStereoWidth(float width);

    /**
     * Gets the current stereo width amount.
     * @return Stereo width value
     */
    public native float getStereoWidth();

    /**
     * Gets whether stereo widening is enabled.
     * @return true if stereo widening is enabled
     */
    public native boolean getStereoWidthEnabled();

    /**
     * Processes an audio buffer through the DSP effects chain.
     *
     * @param inputBuffer  Input audio samples (float array, range -1.0 to 1.0)
     * @param outputBuffer Output audio samples (float array, range -1.0 to 1.0)
     * @param bufferSize   Number of samples to process
     */
    public native void processAudio(float[] inputBuffer, float[] outputBuffer, int bufferSize);

    /**
     * Gets the version of the sound engine.
     */
    public native String getVersion();

    // Exciter control methods
    /**
     * Enables or disables the exciter effect.
     * @param enabled Whether the exciter should be enabled
     */
    public native void setExciterEnabled(boolean enabled);

    /**
     * Sets the exciter amount (wet/dry mix).
     * @param amount Exciter amount (0.0 = no effect, 1.0 = full effect)
     */
    public native void setExciterAmount(float amount);

    /**
     * Sets the exciter frequency (controls which frequencies are excited).
     * @param frequency Frequency in Hz (typically 20-20000 Hz)
     */
    public native void setExciterFrequency(float frequency);

    /**
     * Gets whether the exciter is enabled.
     * @return true if the exciter is enabled
     */
    public native boolean getExciterEnabled();

    /**
     * Gets the exciter amount (wet/dry mix).
     * @return Exciter amount (0.0 = no effect, 1.0 = full effect)
     */
    public native float getExciterAmount();

    /**
     * Gets the exciter frequency.
     * @return Frequency in Hz
     */
    public native float getExciterFrequency();

    // Compressor control methods
    /**
     * Enables or disables the compressor effect.
     * @param enabled Whether the compressor should be enabled
     */
    public native void setCompressorEnabled(boolean enabled);

    /**
     * Sets the compressor threshold in dB.
     * @param threshold Threshold in dB (e.g., -20.0)
     */
    public native void setCompressorThreshold(float threshold);

    /**
     * Sets the compressor ratio.
     * @param ratio Compression ratio (e.g., 4.0 for 4:1)
     */
    public native void setCompressorRatio(float ratio);

    /**
     * Sets the compressor attack time in milliseconds.
     * @param attack Attack time in milliseconds
     */
    public native void setCompressorAttack(float attack);

    /**
     * Sets the compressor release time in milliseconds.
     * @param release Release time in milliseconds
     */
    public native void setCompressorRelease(float release);

    /**
     * Gets whether the compressor is enabled.
     * @return true if the compressor is enabled
     */
    public native boolean getCompressorEnabled();

    /**
     * Gets the compressor threshold in dB.
     * @return Threshold in dB
     */
    public native float getCompressorThreshold();

    /**
     * Gets the compressor ratio.
     * @return Compression ratio
     */
    public native float getCompressorRatio();

    /**
     * Gets the compressor attack time in milliseconds.
     * @return Attack time in milliseconds
     */
    public native float getCompressorAttack();

    /**
     * Gets the compressor release time in milliseconds.
     * @return Release time in milliseconds
     */
    public native float getCompressorRelease();

    // Limiter control methods
    /**
     * Enables or disables the limiter effect.
     * @param enabled Whether the limiter should be enabled
     */
    public native void setLimiterEnabled(boolean enabled);

    /**
     * Sets the limiter threshold (ceiling) in dBFS.
     * @param threshold Threshold in dBFS (e.g., -3.0 for -3dBFS)
     */
    public native void setLimiterThreshold(float threshold);

    /**
     * Gets whether the limiter is enabled.
     * @return true if the limiter is enabled
     */
    public native boolean getLimiterEnabled();

    /**
     * Gets the limiter threshold (ceiling) in dBFS.
     * @return Threshold in dBFS
     */
    public native float getLimiterThreshold();

    // Noise gate control methods
    /**
     * Enables or disables the noise gate effect.
     * @param enabled Whether the noise gate should be enabled
     */
    public native void setNoiseGateEnabled(boolean enabled);

    /**
     * Sets the noise gate threshold (open threshold) in dB.
     * @param threshold Threshold in dB (e.g., -40.0)
     */
    public native void setNoiseGateThreshold(float threshold);

    /**
     * Sets the noise gate hysteresis in dB.
     * @param hysteresis Hysteresis in dB (e.g., 4.0)
     */
    public native void setNoiseGateHysteresis(float hysteresis);

    /**
     * Sets the noise gate attack time in milliseconds.
     * @param attack Attack time in milliseconds
     */
    public native void setNoiseGateAttack(float attack);

    /**
     * Sets the noise gate hold time in milliseconds.
     * @param hold Hold time in milliseconds
     */
    public native void setNoiseGateHold(float hold);

    /**
     * Sets the noise gate release time in milliseconds.
     * @param release Release time in milliseconds
     */
    public native void setNoiseGateRelease(float release);

    /**
     * Sets the noise gate range (maximum attenuation) in dB.
     * @param range Range in dB (e.g., -80.0 for -80dB)
     */
    public native void setNoiseGateRange(float range);

    /**
     * Sets the noise gate duck mode.
     * @param duckMode True for duck mode (attenuate when above threshold)
     */
    public native void setNoiseGateDuckMode(boolean duckMode);

    /**
     * Gets whether the noise gate is enabled.
     * @return true if the noise gate is enabled
     */
    public native boolean getNoiseGateEnabled();

    /**
     * Gets the noise gate threshold (open threshold) in dB.
     * @return Threshold in dB
     */
    public native float getNoiseGateThreshold();

    /**
     * Gets the noise gate hysteresis in dB.
     * @return Hysteresis in dB
     */
    public native float getNoiseGateHysteresis();

    /**
     * Gets the noise gate attack time in milliseconds.
     * @return Attack time in milliseconds
     */
    public native float getNoiseGateAttack();

    /**
     * Gets the noise gate hold time in milliseconds.
     * @return Hold time in milliseconds
     */
    public native float getNoiseGateHold();

    /**
     * Gets the noise gate release time in milliseconds.
     * @return Release time in milliseconds
     */
    public native float getNoiseGateRelease();

    /**
     * Gets the noise gate range (maximum attenuation) in dB.
     * @return Range in dB
     */
    public native float getNoiseGateRange();

    /**
     * Gets the noise gate duck mode.
     * @return True if duck mode is enabled
     */
    public native boolean getNoiseGateDuckMode();

    // De-esser control methods
    /**
     * Enables or disables the de-esser effect.
     * @param enabled Whether the de-esser should be enabled
     */
    public native void setDeEsserEnabled(boolean enabled);

    /**
     * Sets the de-esser threshold in dB.
     * @param threshold Threshold in dB (e.g., -20.0)
     */
    public native void setDeEsserThreshold(float threshold);

    /**
     * Sets the de-esser frequency in Hz.
     * @param frequency Frequency in Hz (typically 2000-8000 Hz for sibilance)
     */
    public native void setDeEsserFrequency(float frequency);

    /**
     * Sets the de-esser ratio.
     * @param ratio Compression ratio (e.g., 4.0 for 4:1)
     */
    public native void setDeEsserRatio(float ratio);

    /**
     * Sets the de-esser attack time in milliseconds.
     * @param attack Attack time in milliseconds
     */
    public native void setDeEsserAttack(float attack);

    /**
     * Sets the de-esser release time in milliseconds.
     * @param release Release time in milliseconds
     */
    public native void setDeEsserRelease(float release);

    /**
     * Gets whether the de-esser is enabled.
     * @return true if the de-esser is enabled
     */
    public native boolean getDeEsserEnabled();

    /**
     * Gets the de-esser threshold in dB.
     * @return Threshold in dB
     */
    public native float getDeEsserThreshold();

    /**
     * Gets the de-esser frequency in Hz.
     * @return Frequency in Hz
     */
    public native float getDeEsserFrequency();

    /**
     * Gets the de-esser ratio.
     * @return Compression ratio
     */
    public native float getDeEsserRatio();

    /**
     * Gets the de-esser attack time in milliseconds.
     * @return Attack time in milliseconds
     */
    public native float getDeEsserAttack();

    /**
     * Gets the de-esser release time in milliseconds.
     * @return Release time in milliseconds
     */
    public native float getDeEsserRelease();

    // FFT/Spectrum Analysis methods
    /**
     * Checks if new spectrum data is ready for reading.
     * @return true if new spectrum data is available
     */
    public native boolean isSpectrumDataReady();

    /**
     * Gets the number of frequency bins in the spectrum analysis.
     * @return Number of spectrum bins
     */
    public native int getSpectrumNumBins();

    /**
     * Gets the center frequency of a specific spectrum bin in Hz.
     * @param binIndex Index of the spectrum bin
     * @return Center frequency in Hz
     */
    public native float getSpectrumBinFrequency(int binIndex);

    /**
     * Gets the current magnitude spectrum in decibels.
     * @param magnitudeArray Array to fill with magnitude data (in dB)
     */
    public native void getSpectrumMagnitudes(float[] magnitudeArray);

    /**
     * Gets the peak hold spectrum in decibels.
     * @param peakHoldArray Array to fill with peak hold data (in dB)
     */
    public native void getSpectrumPeakHold(float[] peakHoldArray);
}
}