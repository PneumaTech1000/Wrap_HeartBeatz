#include <jni.h>
#include <string>
#include <vector>
#include <mutex>

// Include DSPark header
#include "dspark/DSPark.h"

using namespace dspark;

// Global DSP engine instances
static std::unique_ptr<Equalizer<float>> gEqualizer = nullptr;
static std::unique_ptr<StereoWidth<float>> gStereoWidth = nullptr;
static std::unique_ptr<Saturation<float>> gExciter = nullptr;
static std::unique_ptr<Compressor<float>> gCompressor = nullptr;
static std::unique_ptr<Limiter<float>> gLimiter = nullptr;
static std::unique_ptr<NoiseGate<float>> gNoiseGate = nullptr;
static std::unique_ptr<DeEsser<float>> gDeEsser = nullptr;
static std::unique_ptr<SpectrumAnalyzer<float>> gSpectrumAnalyzer = nullptr;
static std::mutex gDspMutex;

// Audio specification
static AudioSpec gAudioSpec;

// Parameters for the equalizer bands
static constexpr int MAX_EQ_BANDS = 10;
static std::vector<float> gBandFrequencies(MAX_EQ_BANDS, 0.0f);
static std::vector<float> gBandGains(MAX_EQ_BANDS, 0.0f);
static std::vector<float> gBandQs(MAX_EQ_BANDS, 0.707f);
static std::vector<bool> gBandEnabled(MAX_EQ_BANDS, false);

// Stereo width parameter
static float gStereoWidthValue = 1.0f;
static bool gStereoWidthEnabled = false;

// Exciter parameter
static float gExciterMix = 0.0f; // 0.0 = dry, 1.0 = wet
static float gExciterDrive = 0.0f; // Input drive
static float gExciterCharacter = 0.0f; // Exciter character/waveshape

// Compressor parameters
static float gCompressorThreshold = -20.0f; // dB
static float gCompressorRatio = 4.0f; // 1.0 = no compression, higher = more compression
static float gCompressorAttack = 10.0f; // ms
static float gCompressorRelease = 100.0f; // ms
static bool gCompressorEnabled = false;

// Limiter parameters
static float gLimiterThreshold = -3.0f; // dB (ceiling)
static bool gLimiterEnabled = false;

// Noise gate parameters
static float gNoiseGateThreshold = -40.0f; // dB (open threshold)
static float gNoiseGateHysteresis = 4.0f; // dB
static float gNoiseGateAttack = 0.5f; // ms
static float gNoiseGateHold = 50.0f; // ms
static float gNoiseGateRelease = 100.0f; // ms
static float gNoiseGateRange = -80.0f; // dB (max attenuation)
static bool gNoiseGateEnabled = false;
static bool gNoiseGateDuckMode = false;

// De-esser parameters
static float gDeEsserThreshold = -20.0f; // dB
static float gDeEsserFrequency = 5000.0f; // Hz
static float gDeEsserRatio = 4.0f; // Ratio
static float gDeEsserAttack = 10.0f; // ms
static float gDeEsserRelease = 100.0f; // ms
static bool gDeEsserEnabled = false;

extern "C" JNIEXPORT jstring JNICALL
Java_com_giga_tech1000_soundengine_SoundEngine_getVersion(
        JNIEnv* env,
        jobject /* this */) {
    std::string version = "Sound Engine 1.0.0 (DSPark)";
    return env->NewStringUTF(version.c_str());
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_giga_tech1000_soundengine_SoundEngine_initialize(
        JNIEnv* env,
        jobject /* this */,
        jfloat sampleRate,
        jint maxBlockSize,
        jint numChannels) {
    std::lock_guard<std::mutex> lock(gDspMutex);

    try {
        // Set up audio spec
        gAudioSpec = { static_cast<float>(sampleRate), maxBlockSize, numChannels };

        // Create the equalizer
        gEqualizer = std::make_unique<Equalizer<float>>(MAX_EQ_BANDS);

        // Prepare the equalizer
        gEqualizer->prepare(gAudioSpec);

        // Initialize default bands (if needed)
        for (int i = 0; i < MAX_EQ_BANDS; ++i) {
            gEqualizer->setBandEnabled(i, false);
        }

        // Create and prepare stereo width
        gStereoWidth = std::make_unique<StereoWidth<float>>();
        gStereoWidth->prepare(sampleRate);
        gStereoWidth->setWidth(gStereoWidthValue);
        gStereoWidth->setBassMono(false, 100.0); // Default bass mono off

        // Create and prepare exciter
        gExciter = std::make_unique<Saturation<float>>();
        gExciter->prepare(gAudioSpec);
        // Configure exciter with default settings
        gExciter->setAlgorithm(Saturation<float>::Algorithm::Exciter);
        gExciter->setMix(0.0f); // Start with wet mix at 0 (fully dry)
        gExciter->setDrive(0.0f); // No drive initially
        gExciter->setCharacter(0.0f); // Neutral character

        // Create and prepare compressor
        gCompressor = std::make_unique<Compressor<float>>();
        gCompressor->prepare(gAudioSpec);
        // Configure compressor with default settings
        gCompressor->setThreshold(gCompressorThreshold);
        gCompressor->setRatio(gCompressorRatio);
        gCompressor->setAttack(gCompressorAttack);
        gCompressor->setRelease(gCompressorRelease);
        gCompressor->setMode(Compressor<float>::Mode::Downward);

        // Create and prepare limiter
        gLimiter = std::make_unique<Limiter<float>>();
        gLimiter->prepare(gAudioSpec.sampleRate, gAudioSpec.numChannels);
        // Configure limiter with default settings
        gLimiter->setCeiling(gLimiterThreshold);

        // Create and prepare noise gate
        gNoiseGate = std::make_unique<NoiseGate<float>>();
        gNoiseGate->prepare(gAudioSpec);
        // Configure noise gate with default settings
        gNoiseGate->setThreshold(gNoiseGateThreshold);
        gNoiseGate->setHysteresis(gNoiseGateHysteresis);
        gNoiseGate->setAttack(gNoiseGateAttack);
        gNoiseGate->setHold(gNoiseGateHold);
        gNoiseGate->setRelease(gNoiseGateRelease);
        gNoiseGate->setRange(gNoiseGateRange);
        gNoiseGate->setDuckMode(gNoiseGateDuckMode);

        // Create and prepare de-esser
        gDeEsser = std::make_unique<DeEsser<float>>();
        gDeEsser->prepare(gAudioSpec);
        // Configure de-esser with default settings
        gDeEsser->setThreshold(gDeEsserThreshold);
        gDeEsser->setFrequency(gDeEsserFrequency);
        gDeEsser->setRatio(gDeEsserRatio);
        gDeEsser->setAttack(gDeEsserAttack);
        gDeEsser->setRelease(gDeEsserRelease);

        // Create and prepare spectrum analyzer
        gSpectrumAnalyzer = std::make_unique<SpectrumAnalyzer<float>>();
        gSpectrumAnalyzer->prepare(gAudioSpec.sampleRate); // automatic FFT size based on sample rate

        return JNI_TRUE;
    } catch (...) {
        return JNI_FALSE;
    }
}

extern "C" JNIEXPORT void JNICALL
Java_com_giga_tech1000_soundengine_SoundEngine_release(
        JNIEnv* env,
        jobject /* this */) {
    std::lock_guard<std::mutex> lock(gDspMutex);
    gEqualizer.reset();
    gStereoWidth.reset();
    gExciter.reset();
    gCompressor.reset();
    gLimiter.reset();
    gNoiseGate.reset();
    gDeEsser.reset();
    gSpectrumAnalyzer.reset();
}

extern "C" JNIEXPORT void JNICALL
Java_com_giga_tech1000_soundengine_SoundEngine_setEqBand(
        JNIEnv* env,
        jobject /* this */,
        jint bandIndex,
        jfloat frequency,
        jfloat gain,
        jfloat q,
        jboolean enabled) {
    std::lock_guard<std::mutex> lock(gEqualizerMutex);

    if (gEqualizer && bandIndex >= 0 && bandIndex < MAX_EQ_BANDS) {
        // Store parameters
        gBandFrequencies[bandIndex] = frequency;
        gBandGains[bandIndex] = gain;
        gBandQs[bandIndex] = q;
        gBandEnabled[bandIndex] = (enabled == JNI_TRUE);

        // Apply to equalizer
        gEqualizer->setBand(bandIndex, frequency, gain, q);
        gEqualizer->setBandEnabled(bandIndex, enabled == JNI_TRUE);
    }
}

extern "C" JNIEXPORT void JNICALL
Java_com_giga_tech1000_soundengine_SoundEngine_setEqBandFrequency(
        JNIEnv* env,
        jobject /* this */,
        jint bandIndex,
        jfloat frequency) {
    std::lock_guard<std::mutex> lock(gEqualizerMutex);

    if (gEqualizer && bandIndex >= 0 && bandIndex < MAX_EQ_BANDS) {
        gBandFrequencies[bandIndex] = frequency;
        gEqualizer->setBand(bandIndex, frequency, gBandGains[bandIndex], gBandQs[bandIndex]);
    }
}

extern "C" JNIEXPORT void JNICALL
Java_com_giga_tech1000_soundengine_SoundEngine_setEqBandGain(
        JNIEnv* env,
        jobject /* this */,
        jint bandIndex,
        jfloat gain) {
    std::lock_guard<std::mutex> lock(gEqualizerMutex);

    if (gEqualizer && bandIndex >= 0 && bandIndex < MAX_EQ_BANDS) {
        gBandGains[bandIndex] = gain;
        gEqualizer->setBand(bandIndex, gBandFrequencies[bandIndex], gain, gBandQs[bandIndex]);
    }
}

extern "C" JNIEXPORT void JNICALL
Java_com_giga_tech1000_soundengine_SoundEngine_setEqBandQ(
        JNIEnv* env,
        jobject /* this */,
        jint bandIndex,
        jfloat q) {
    std::lock_guard<std::mutex> lock(gEqualizerMutex);

    if (gEqualizer && bandIndex >= 0 && bandIndex < MAX_EQ_BANDS) {
        gBandQs[bandIndex] = q;
        gEqualizer->setBand(bandIndex, gBandFrequencies[bandIndex], gBandGains[bandIndex], q);
    }
}

extern "C" JNIEXPORT void JNICALL
Java_com_giga_tech1000_soundengine_SoundEngine_setEqBandEnabled(
        JNIEnv* env,
        jobject /* this */,
        jint bandIndex,
        jboolean enabled) {
    std::lock_guard<std::mutex> lock(gEqualizerMutex);

    if (gEqualizer && bandIndex >= 0 && bandIndex < MAX_EQ_BANDS) {
        gBandEnabled[bandIndex] = (enabled == JNI_TRUE);
        gEqualizer->setBandEnabled(bandIndex, enabled == JNI_TRUE);
    }
}

extern "C" JNIEXPORT void JNICALL
Java_com_giga_tech1000_soundengine_SoundEngine_processAudio(
        JNIEnv* env,
        jobject /* this */,
        jfloatArray inputBuffer,
        jfloatArray outputBuffer,
        jint bufferSize) {
    std::lock_guard<std::mutex> lock(gDspMutex);

    if (!gEqualizer || !gStereoWidth || !gExciter || !gCompressor || !gLimiter || !gNoiseGate || !gDeEsser || bufferSize <= 0) {
        return;
    }

    // Get the buffer elements
    jfloat* input = env->GetFloatArrayElements(inputBuffer, nullptr);
    jfloat* output = env->GetFloatArrayElements(outputBuffer, nullptr);

    if (!input || !output) {
        if (input) env->ReleaseFloatArrayElements(inputBuffer, input, 0);
        if (output) env->ReleaseFloatArrayElements(outputBuffer, output, 0);
        return;
    }

    // Process based on number of channels
    int numChannels = gAudioSpec.numChannels;
    int samplesPerChannel = bufferSize / numChannels;

    if (numChannels == 1) {
        // Mono processing - apply equalizer, then exciter, then compressor, then limiter, then de-esser, then noise gate
        dspark::AudioBufferView<float> monoView(&input, 1, samplesPerChannel);
        gEqualizer->processBlock(monoView);
        gExciter->processBlock(monoView);
        if (gCompressorEnabled) {
            gCompressor->processBlock(monoView);
        }
        if (gLimiterEnabled) {
            gLimiter->processBlock(monoView);
        }
        if (gDeEsserEnabled) {
            gDeEsser->processBlock(monoView);
        }
        if (gNoiseGateEnabled) {
            gNoiseGate->processBlock(monoView);
        }
        // Copy result to output
        for (int i = 0; i < samplesPerChannel; ++i) {
            output[i] = input[i];
        }
    } else if (numChannels == 2) {
        // Stereo processing - apply equalizer, then exciter, then compressor, then limiter, then noise gate, then stereo width
        dspark::AudioBufferView<float> stereoView(
            reinterpret_cast<float**>(&input),
            2,
            samplesPerChannel
        );

        // Apply equalizer first
        gEqualizer->processBlock(stereoView);

        // Apply exciter
        gExciter->processBlock(stereoView);

        // Apply compressor if enabled
        if (gCompressorEnabled) {
            gCompressor->processBlock(stereoView);
        }

        // Apply limiter if enabled
        if (gLimiterEnabled) {
            gLimiter->processBlock(stereoView);
        }

        // Apply de-esser if enabled
        if (gDeEsserEnabled) {
            gDeEsser->processBlock(stereoView);
        }

        // Apply noise gate if enabled
        if (gNoiseGateEnabled) {
            gNoiseGate->processBlock(stereoView);
        }

        // Apply stereo width if enabled
        if (gStereoWidthEnabled) {
            gStereoWidth->processBlock(stereoView);
        }

        // Copy result to output
        for (int i = 0; i < bufferSize; ++i) {
            output[i] = input[i];
        }
    }

    // Release the buffer elements
    env->ReleaseFloatArrayElements(inputBuffer, input, 0);
    env->ReleaseFloatArrayElements(outputBuffer, output, 0);
}

// Additional helper methods for getting current band parameters
extern "C" JNIEXPORT jfloat JNICALL
Java_com_giga_tech1000_soundengine_SoundEngine_getEqBandFrequency(
        JNIEnv* env,
        jobject /* this */,
        jint bandIndex) {
    std::lock_guard<std::mutex> lock(gDspMutex);

    if (bandIndex >= 0 && bandIndex < MAX_EQ_BANDS) {
        return gBandFrequencies[bandIndex];
    }
    return 0.0f;
}

extern "C" JNIEXPORT jfloat JNICALL
Java_com_giga_tech1000_soundengine_SoundEngine_getEqBandGain(
        JNIEnv* env,
        jobject /* this */,
        jint bandIndex) {
    std::lock_guard<std::mutex> lock(gDspMutex);

    if (bandIndex >= 0 && bandIndex < MAX_EQ_BANDS) {
        return gBandGains[bandIndex];
    }
    return 0.0f;
}

extern "C" JNIEXPORT jfloat JNICALL
Java_com_giga_tech1000_soundengine_SoundEngine_getEqBandQ(
        JNIEnv* env,
        jobject /* this */,
        jint bandIndex) {
    std::lock_guard<std::mutex> lock(gDspMutex);

    if (bandIndex >= 0 && bandIndex < MAX_EQ_BANDS) {
        return gBandQs[bandIndex];
    }
    return 0.707f;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_giga_tech1000_soundengine_SoundEngine_getEqBandEnabled(
        JNIEnv* env,
        jobject /* this */,
        jint bandIndex) {
    std::lock_guard<std::mutex> lock(gDspMutex);

    if (bandIndex >= 0 && bandIndex < MAX_EQ_BANDS) {
        return gBandEnabled[bandIndex] ? JNI_TRUE : JNI_FALSE;
    }
    return JNI_FALSE;
}

// Stereo width control methods
extern "C" JNIEXPORT void JNICALL
Java_com_giga_tech1000_soundengine_SoundEngine_setStereoWidthEnabled(
        JNIEnv* env,
        jobject /* this */,
        jboolean enabled) {
    std::lock_guard<std::mutex> lock(gDspMutex);
    gStereoWidthEnabled = (enabled == JNI_TRUE);
}

extern "C" JNIEXPORT void JNICALL
Java_com_giga_tech1000_soundengine_SoundEngine_setStereoWidth(
        JNIEnv* env,
        jobject /* this */,
        jfloat width) {
    std::lock_guard<std::mutex> lock(gDspMutex);
    gStereoWidthValue = width;
    if (gStereoWidth) {
        gStereoWidth->setWidth(width);
    }
}

extern "C" JNIEXPORT jfloat JNICALL
Java_com_giga_tech1000_soundengine_SoundEngine_getStereoWidth(
        JNIEnv* env,
        jobject /* this */) {
    std::lock_guard<std::mutex> lock(gDspMutex);
    return gStereoWidthValue;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_giga_tech1000_soundengine_SoundEngine_getStereoWidthEnabled(
        JNIEnv* env,
        jobject /* this */) {
    std::lock_guard<std::mutex> lock(gDspMutex);
    return gStereoWidthEnabled ? JNI_TRUE : JNI_FALSE;
}

// Exciter control methods
extern "C" JNIEXPORT void JNICALL
Java_com_giga_tech1000_soundengine_SoundEngine_setExciterEnabled(
        JNIEnv* env,
        jobject /* this */,
        jboolean enabled) {
    std::lock_guard<std::mutex> lock(gDspMutex);
    // For exciter, we'll use the mix parameter to enable/disable
    // 0.0 = disabled (dry), 1.0 = enabled (full wet)
    if (gExciter) {
        gExciterMix = enabled ? 1.0f : 0.0f;
        gExciter->setMix(gExciterMix);
    }
}

extern "C" JNIEXPORT void JNICALL
Java_com_giga_tech1000_soundengine_SoundEngine_setExciterAmount(
        JNIEnv* env,
        jobject /* this */,
        jfloat amount) {
    std::lock_guard<std::mutex> lock(gDspMutex);
    // Clamp amount to [0.0, 1.0]
    gExciterMix = std::max(0.0f, std::min(1.0f, amount));
    if (gExciter) {
        gExciter->setMix(gExciterMix);
    }
}

extern "C" JNIEXPORT void JNICALL
Java_com_giga_tech1000_soundengine_SoundEngine_setExciterFrequency(
        JNIEnv* env,
        jobject /* this */,
        jfloat frequency) {
    std::lock_guard<std::mutex> lock(gDspMutex);
    // Map frequency range (20Hz - 20kHz) to character parameter [-1.0, 1.0]
    // This is a simplified mapping - in practice you might want a more sophisticated approach
    float normalized = (frequency - 20.0f) / (20000.0f - 20.0f); // 0.0 to 1.0
    gExciterCharacter = (normalized * 2.0f) - 1.0f; // -1.0 to 1.0
    if (gExciter) {
        gExciter->setCharacter(gExciterCharacter);
    }
}

extern "C" JNIEXPORT void JNICALL
Java_com_giga_tech1000_soundengine_SoundEngine_setExciterDrive(
        JNIEnv* env,
        jobject /* this */,
        jfloat drive) {
    std::lock_guard<std::mutex> lock(gDspMutex);
    // Clamp drive to reasonable range [-24.0, 48.0] dB
    gExciterDrive = std::max(-24.0f, std::min(48.0f, drive));
    if (gExciter) {
        gExciter->setDrive(gExciterDrive);
    }
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_giga_tech1000_soundengine_SoundEngine_getExciterEnabled(
        JNIEnv* env,
        jobject /* this */) {
    std::lock_guard<std::mutex> lock(gDspMutex);
    return (gExciterMix > 0.0f) ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT jfloat JNICALL
Java_com_giga_tech1000_soundengine_SoundEngine_getExciterAmount(
        JNIEnv* env,
        jobject /* this */) {
    std::lock_guard<std::mutex> lock(gDspMutex);
    return gExciterMix;
}

extern "C" JNIEXPORT jfloat JNICALL
Java_com_giga_tech1000_soundengine_SoundEngine_getExciterFrequency(
        JNIEnv* env,
        jobject /* this */) {
    std::lock_guard<std::mutex> lock(gDspMutex);
    // Map character [-1.0, 1.0] back to frequency [20Hz, 20kHz]
    float normalized = (gExciterCharacter + 1.0f) / 2.0f; // 0.0 to 1.0
    return 20.0f + normalized * (20000.0f - 20.0f);
}

extern "C" JNIEXPORT jfloat JNICALL
Java_com_giga_tech1000_soundengine_SoundEngine_getExciterDrive(
        JNIEnv* env,
        jobject /* this */) {
    std::lock_guard<std::mutex> lock(gDspMutex);
    return gExciterDrive;
}

// Compressor control methods
extern "C" JNIEXPORT void JNICALL
Java_com_giga_tech1000_soundengine_SoundEngine_setCompressorEnabled(
        JNIEnv* env,
        jobject /* this */,
        jboolean enabled) {
    std::lock_guard<std::mutex> lock(gDspMutex);
    gCompressorEnabled = (enabled == JNI_TRUE);
}

extern "C" JNIEXPORT void JNICALL
Java_com_giga_tech1000_soundengine_SoundEngine_setCompressorThreshold(
        JNIEnv* env,
        jobject /* this */,
        jfloat threshold) {
    std::lock_guard<std::mutex> lock(gDspMutex);
    gCompressorThreshold = threshold;
    if (gCompressor) {
        gCompressor->setThreshold(threshold);
    }
}

extern "C" JNIEXPORT void JNICALL
Java_com_giga_tech1000_soundengine_SoundEngine_setCompressorRatio(
        JNIEnv* env,
        jobject /* this */,
        jfloat ratio) {
    std::lock_guard<std::mutex> lock(gDspMutex);
    gCompressorRatio = ratio;
    if (gCompressor) {
        gCompressor->setRatio(ratio);
    }
}

extern "C" JNIEXPORT void JNICALL
Java_com_giga_tech1000_soundengine_SoundEngine_setCompressorAttack(
        JNIEnv* env,
        jobject /* this */,
        jfloat attack) {
    std::lock_guard<std::mutex> lock(gDspMutex);
    gCompressorAttack = attack;
    if (gCompressor) {
        gCompressor->setAttack(attack);
    }
}

extern "C" JNIEXPORT void JNICALL
Java_com_giga_tech1000_soundengine_SoundEngine_setCompressorRelease(
        JNIEnv* env,
        jobject /* this */,
        jfloat release) {
    std::lock_guard<std::mutex> lock(gDspMutex);
    gCompressorRelease = release;
    if (gCompressor) {
        gCompressor->setRelease(release);
    }
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_giga_tech1000_soundengine_SoundEngine_getCompressorEnabled(
        JNIEnv* env,
        jobject /* this */) {
    std::lock_guard<std::mutex> lock(gDspMutex);
    return gCompressorEnabled ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT jfloat JNICALL
Java_com_giga_tech1000_soundengine_SoundEngine_getCompressorThreshold(
        JNIEnv* env,
        jobject /* this */) {
    std::lock_guard<std::mutex> lock(gDspMutex);
    return gCompressorThreshold;
}

extern "C" JNIEXPORT jfloat JNICALL
Java_com_giga_tech1000_soundengine_SoundEngine_getCompressorRatio(
        JNIEnv* env,
        jobject /* this */) {
    std::lock_guard<std::mutex> lock(gDspMutex);
    return gCompressorRatio;
}

extern "C" JNIEXPORT jfloat JNICALL
Java_com_giga_tech1000_soundengine_SoundEngine_getCompressorAttack(
        JNIEnv* env,
        jobject /* this */) {
    std::lock_guard<std::mutex> lock(gDspMutex);
    return gCompressorAttack;
}

extern "C" JNIEXPORT jfloat JNICALL
Java_com_giga_tech1000_soundengine_SoundEngine_getCompressorRelease(
        JNIEnv* env,
        jobject /* this */) {
    std::lock_guard<std::mutex> lock(gDspMutex);
    return gCompressorRelease;
}

// Limiter control methods
extern "C" JNIEXPORT void JNICALL
Java_com_giga_tech1000_soundengine_SoundEngine_setLimiterEnabled(
        JNIEnv* env,
        jobject /* this */,
        jboolean enabled) {
    std::lock_guard<std::mutex> lock(gDspMutex);
    gLimiterEnabled = (enabled == JNI_TRUE);
}

extern "C" JNIEXPORT void JNICALL
Java_com_giga_tech1000_soundengine_SoundEngine_setLimiterThreshold(
        JNIEnv* env,
        jobject /* this */,
        jfloat threshold) {
    std::lock_guard<std::mutex> lock(gDspMutex);
    gLimiterThreshold = threshold;
    if (gLimiter) {
        gLimiter->setCeiling(threshold);
    }
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_giga_tech1000_soundengine_SoundEngine_getLimiterEnabled(
        JNIEnv* env,
        jobject /* this */) {
    std::lock_guard<std::mutex> lock(gDspMutex);
    return gLimiterEnabled ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT jfloat JNICALL
Java_com_giga_tech1000_soundengine_SoundEngine_getLimiterThreshold(
        JNIEnv* env,
        jobject /* this */) {
    std::lock_guard<std::mutex> lock(gDspMutex);
    return gLimiterThreshold;
}

// Noise gate control methods
extern "C" JNIEXPORT void JNICALL
Java_com_giga_tech1000_soundengine_SoundEngine_setNoiseGateEnabled(
        JNIEnv* env,
        jobject /* this */,
        jboolean enabled) {
    std::lock_guard<std::mutex> lock(gDspMutex);
    gNoiseGateEnabled = (enabled == JNI_TRUE);
}

// De-esser control methods
extern "C" JNIEXPORT void JNICALL
Java_com_giga_tech1000_soundengine_SoundEngine_setDeEsserEnabled(
        JNIEnv* env,
        jobject /* this */,
        jboolean enabled) {
    std::lock_guard<std::mutex> lock(gDspMutex);
    gDeEsserEnabled = (enabled == JNI_TRUE);
    if (gDeEsser) {
        // DeEsser doesn't have an explicit enable/disable method, we'll handle it in processAudio
    }
}

extern "C" JNIEXPORT void JNICALL
Java_com_giga_tech1000_soundengine_SoundEngine_setDeEsserThreshold(
        JNIEnv* env,
        jobject /* this */,
        jfloat threshold) {
    std::lock_guard<std::mutex> lock(gDspMutex);
    gDeEsserThreshold = threshold;
    if (gDeEsser) {
        gDeEsser->setThreshold(threshold);
    }
}

extern "C" JNIEXPORT void JNICALL
Java_com_giga_tech1000_soundengine_SoundEngine_setDeEsserFrequency(
        JNIEnv* env,
        jobject /* this */,
        jfloat frequency) {
    std::lock_guard<std::mutex> lock(gDspMutex);
    gDeEsserFrequency = frequency;
    if (gDeEsser) {
        gDeEsser->setFrequency(frequency);
    }
}

extern "C" JNIEXPORT void JNICALL
Java_com_giga_tech1000_soundengine_SoundEngine_setDeEsserRatio(
        JNIEnv* env,
        jobject /* this */,
        jfloat ratio) {
    std::lock_guard<std::mutex> lock(gDspMutex);
    gDeEsserRatio = ratio;
    if (gDeEsser) {
        gDeEsser->setRatio(ratio);
    }
}

extern "C" JNIEXPORT void JNICALL
Java_com_giga_tech1000_soundengine_SoundEngine_setDeEsserAttack(
        JNIEnv* env,
        jobject /* this */,
        jfloat attack) {
    std::lock_guard<std::mutex> lock(gDspMutex);
    gDeEsserAttack = attack;
    if (gDeEsser) {
        gDeEsser->setAttack(attack);
    }
}

extern "C" JNIEXPORT void JNICALL
Java_com_giga_tech1000_soundengine_SoundEngine_setDeEsserRelease(
        JNIEnv* env,
        jobject /* this */,
        jfloat release) {
    std::lock_guard<std::mutex> lock(gDspMutex);
    gDeEsserRelease = release;
    if (gDeEsser) {
        gDeEsser->setRelease(release);
    }
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_giga_tech1000_soundengine_SoundEngine_getDeEsserEnabled(
        JNIEnv* env,
        jobject /* this */) {
    std::lock_guard<std::mutex> lock(gDspMutex);
    return gDeEsserEnabled ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT jfloat JNICALL
Java_com_giga_tech1000_soundengine_SoundEngine_getDeEsserThreshold(
        JNIEnv* env,
        jobject /* this */) {
    std::lock_guard<std::mutex> lock(gDspMutex);
    return gDeEsserThreshold;
}

extern "C" JNIEXPORT jfloat JNICALL
Java_com_giga_tech1000_soundengine_SoundEngine_getDeEsserFrequency(
        JNIEnv* env,
        jobject /* this */) {
    std::lock_guard<std::mutex> lock(gDspMutex);
    return gDeEsserFrequency;
}

extern "C" JNIEXPORT jfloat JNICALL
Java_com_giga_tech1000_soundengine_SoundEngine_getDeEsserRatio(
        JNIEnv* env,
        jobject /* this */) {
    std::lock_guard<std::mutex> lock(gDspMutex);
    return gDeEsserRatio;
}

extern "C" JNIEXPORT jfloat JNICALL
Java_com_giga_tech1000_soundengine_SoundEngine_getDeEsserAttack(
        JNIEnv* env,
        jobject /* this */) {
    std::lock_guard<std::mutex> lock(gDspMutex);
    return gDeEsserAttack;
}

extern "C" JNIEXPORT jfloat JNICALL
Java_com_giga_tech1000_soundengine_SoundEngine_getDeEsserRelease(
        JNIEnv* env,
        jobject /* this */) {
    std::lock_guard<std::mutex> lock(gDspMutex);
    return gDeEsserRelease;
}

extern "C" JNIEXPORT void JNICALL
Java_com_giga_tech1000_soundengine_SoundEngine_setNoiseGateThreshold(
        JNIEnv* env,
        jobject /* this */,
        jfloat threshold) {
    std::lock_guard<std::mutex> lock(gDspMutex);
    gNoiseGateThreshold = threshold;
    if (gNoiseGate) {
        gNoiseGate->setThreshold(threshold);
    }
}

extern "C" JNIEXPORT void JNICALL
Java_com_giga_tech1000_soundengine_SoundEngine_setNoiseGateHysteresis(
        JNIEnv* env,
        jobject /* this */,
        jfloat hysteresis) {
    std::lock_guard<std::mutex> lock(gDspMutex);
    gNoiseGateHysteresis = hysteresis;
    if (gNoiseGate) {
        gNoiseGate->setHysteresis(hysteresis);
    }
}

extern "C" JNIEXPORT void JNICALL
Java_com_giga_tech1000_soundengine_SoundEngine_setNoiseGateAttack(
        JNIEnv* env,
        jobject /* this */,
        jfloat attack) {
    std::lock_guard<std::mutex> lock(gDspMutex);
    gNoiseGateAttack = attack;
    if (gNoiseGate) {
        gNoiseGate->setAttack(attack);
    }
}

extern "C" JNIEXPORT void JNICALL
Java_com_giga_tech1000_soundengine_SoundEngine_setNoiseGateHold(
        JNIEnv* env,
        jobject /* this */,
        jfloat hold) {
    std::lock_guard<std::mutex> lock(gDspMutex);
    gNoiseGateHold = hold;
    if (gNoiseGate) {
        gNoiseGate->setHold(hold);
    }
}

extern "C" JNIEXPORT void JNICALL
Java_com_giga_tech1000_soundengine_SoundEngine_setNoiseGateRelease(
        JNIEnv* env,
        jobject /* this */,
        jfloat release) {
    std::lock_guard<std::mutex> lock(gDspMutex);
    gNoiseGateRelease = release;
    if (gNoiseGate) {
        gNoiseGate->setRelease(release);
    }
}

extern "C" JNIEXPORT void JNICALL
Java_com_giga_tech1000_soundengine_SoundEngine_setNoiseGateRange(
        JNIEnv* env,
        jobject /* this */,
        jfloat range) {
    std::lock_guard<std::mutex> lock(gDspMutex);
    gNoiseGateRange = range;
    if (gNoiseGate) {
        gNoiseGate->setRange(range);
    }
}

extern "C" JNIEXPORT void JNICALL
Java_com_giga_tech1000_soundengine_SoundEngine_setNoiseGateDuckMode(
        JNIEnv* env,
        jobject /* this */,
        jboolean duckMode) {
    std::lock_guard<std::mutex> lock(gDspMutex);
    gNoiseGateDuckMode = (duckMode == JNI_TRUE);
    if (gNoiseGate) {
        gNoiseGate->setDuckMode(duckMode);
    }
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_giga_tech1000_soundengine_SoundEngine_getNoiseGateEnabled(
        JNIEnv* env,
        jobject /* this */) {
    std::lock_guard<std::mutex> lock(gDspMutex);
    return gNoiseGateEnabled ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT jfloat JNICALL
Java_com_giga_tech1000_soundengine_SoundEngine_getNoiseGateThreshold(
        JNIEnv* env,
        jobject /* this */) {
    std::lock_guard<std::mutex> lock(gDspMutex);
    return gNoiseGateThreshold;
}

extern "C" JNIEXPORT jfloat JNICALL
Java_com_giga_tech1000_soundengine_SoundEngine_getNoiseGateHysteresis(
        JNIEnv* env,
        jobject /* this */) {
    std::lock_guard<std::mutex> lock(gDspMutex);
    return gNoiseGateHysteresis;
}

extern "C" JNIEXPORT jfloat JNICALL
Java_com_giga_tech1000_soundengine_SoundEngine_getNoiseGateAttack(
        JNIEnv* env,
        jobject /* this */) {
    std::lock_guard<std::mutex> lock(gDspMutex);
    return gNoiseGateAttack;
}

extern "C" JNIEXPORT jfloat JNICALL
Java_com_giga_tech1000_soundengine_SoundEngine_getNoiseGateHold(
        JNIEnv* env,
        jobject /* this */) {
    std::lock_guard<std::mutex> lock(gDspMutex);
    return gNoiseGateHold;
}

extern "C" JNIEXPORT jfloat JNICALL
Java_com_giga_tech1000_soundengine_SoundEngine_getNoiseGateRelease(
        JNIEnv* env,
        jobject /* this */) {
    std::lock_guard<std::mutex> lock(gDspMutex);
    return gNoiseGateRelease;
}

extern "C" JNIEXPORT jfloat JNICALL
Java_com_giga_tech1000_soundengine_SoundEngine_getNoiseGateRange(
        JNIEnv* env,
        jobject /* this */) {
    std::lock_guard<std::mutex> lock(gDspMutex);
    return gNoiseGateRange;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_giga_tech1000_soundengine_SoundEngine_getNoiseGateDuckMode(
        JNIEnv* env,
        jobject /* this */) {
    std::lock_guard<std::mutex> lock(gDspMutex);
    return gNoiseGateDuckMode ? JNI_TRUE : JNI_FALSE;
}

// FFT/Spectrum Analysis methods
extern "C" JNIEXPORT jboolean JNICALL
Java_com_giga_tech1000_soundengine_SoundEngine_isSpectrumDataReady(
        JNIEnv* env,
        jobject /* this */) {
    std::lock_guard<std::mutex> lock(gDspMutex);
    if (gSpectrumAnalyzer) {
        return gSpectrumAnalyzer->isNewDataReady() ? JNI_TRUE : JNI_FALSE;
    }
    return JNI_FALSE;
}

extern "C" JNIEXPORT jint JNICALL
Java_com_giga_tech1000_soundengine_SoundEngine_getSpectrumNumBins(
        JNIEnv* env,
        jobject /* this */) {
    std::lock_guard<std::mutex> lock(gDspMutex);
    if (gSpectrumAnalyzer) {
        return gSpectrumAnalyzer->getNumBins();
    }
    return 0;
}

extern "C" JNIEXPORT jfloat JNICALL
Java_com_giga_tech1000_soundengine_SoundEngine_getSpectrumBinFrequency(
        JNIEnv* env,
        jobject /* this */,
        jint binIndex) {
    std::lock_guard<std::mutex> lock(gDspMutex);
    if (gSpectrumAnalyzer) {
        return gSpectrumAnalyzer->binToFrequency(binIndex);
    }
    return 0.0f;
}

extern "C" JNIEXPORT void JNICALL
Java_com_giga_tech1000_soundengine_SoundEngine_getSpectrumMagnitudes(
        JNIEnv* env,
        jobject /* this */,
        jfloatArray magnitudeArray) {
    std::lock_guard<std::mutex> lock(gDspMutex);
    if (!gSpectrumAnalyzer || !magnitudeArray) {
        return;
    }

    jsize arraySize = env->GetArrayLength(magnitudeArray);
    if (arraySize <= 0) {
        return;
    }

    int numBins = gSpectrumAnalyzer->getNumBins();
    if (numBins <= 0) {
        return;
    }

    // Only copy as many bins as fit in the array
    int binsToCopy = std::min(numBins, static_cast<int>(arraySize));
    if (binsToCopy <= 0) {
        return;
    }

    const float* magnitudes = gSpectrumAnalyzer->getMagnitudesDb();
    if (!magnitudes) {
        return;
    }

    // Copy the magnitude data to the Java array
    env->SetFloatArrayRegion(magnitudeArray, 0, binsToCopy, magnitudes);
}

extern "C" JNIEXPORT void JNICALL
Java_com_giga_tech1000_soundengine_SoundEngine_getSpectrumPeakHold(
        JNIEnv* env,
        jobject /* this */,
        jfloatArray peakHoldArray) {
    std::lock_guard<std::mutex> lock(gDspMutex);
    if (!gSpectrumAnalyzer || !peakHoldArray) {
        return;
    }

    jsize arraySize = env->GetArrayLength(peakHoldArray);
    if (arraySize <= 0) {
        return;
    }

    int numBins = gSpectrumAnalyzer->getNumBins();
    if (numBins <= 0) {
        return;
    }

    // Only copy as many bins as fit in the array
    int binsToCopy = std::min(numBins, static_cast<int>(arraySize));
    if (binsToCopy <= 0) {
        return;
    }

    const float* peakHold = gSpectrumAnalyzer->getPeakHoldDb();
    if (!peakHold) {
        return;
    }

    // Copy the peak hold data to the Java array
    env->SetFloatArrayRegion(peakHoldArray, 0, binsToCopy, peakHold);
}