#include "android-aligned-alloc.h"

#include <jni.h>
#include <android/log.h>

#include <algorithm>
#include <atomic>
#include <vector>

#include <DSPark.h>

namespace {

constexpr char kTag[] = "SoundEngine";
constexpr int kMaxChannels = 16;
constexpr int kSpectrumFftSize = 2048;

struct Engine {
    Engine(jint sampleRate, jint maxBlockSize, jint channelCount)
            : spec{static_cast<double>(sampleRate), maxBlockSize, channelCount},
              scratch(static_cast<size_t>(channelCount) * maxBlockSize),
              channelPointers(static_cast<size_t>(channelCount)) {
        equalizer.prepare(spec);
        compressor.prepare(spec);
        limiter.prepare(spec);
        noiseGate.prepare(spec);
        deEsser.prepare(spec);
        exciter.prepare(spec);
        stereoWidth.prepare(spec);
        spectrum.prepare(static_cast<double>(sampleRate), kSpectrumFftSize);

        for (int band = 0; band < 10; ++band) {
            equalizer.setBand(band, defaultFrequencies[band], 0.0f, 0.707f);
            equalizer.setBandEnabled(band, false);
        }
        exciter.setAlgorithm(dspark::Saturation<float>::Algorithm::Exciter);
        exciter.setDrive(0.0f);
        exciter.setMix(0.0f);
        compressor.setThreshold(-20.0f);
        compressor.setRatio(4.0f);
        compressor.setAttack(10.0f);
        compressor.setRelease(100.0f);
        limiter.setCeiling(-3.0f);
        noiseGate.setThreshold(-60.0f);
        noiseGate.setHysteresis(6.0f);
        noiseGate.setAttack(1.0f);
        noiseGate.setHold(20.0f);
        noiseGate.setRelease(100.0f);
        noiseGate.setRange(-80.0f);
        deEsser.setThreshold(-20.0f);
        deEsser.setFrequency(5000.0f);
        stereoWidth.setWidth(1.0f);
    }

    void process(float* interleaved, int sampleCount) noexcept {
        const int channels = spec.numChannels;
        const int availableFrames = sampleCount / channels;
        for (int frameOffset = 0; frameOffset < availableFrames; frameOffset += spec.maxBlockSize) {
            const int frames = std::min(spec.maxBlockSize, availableFrames - frameOffset);
            for (int channel = 0; channel < channels; ++channel) {
                float* channelData = scratch.data() + static_cast<size_t>(channel) * spec.maxBlockSize;
                channelPointers[channel] = channelData;
                for (int frame = 0; frame < frames; ++frame) {
                    channelData[frame] = interleaved[(frameOffset + frame) * channels + channel];
                }
            }

            dspark::AudioBufferView<float> block(channelPointers.data(), channels, frames);
            spectrum.pushSamples(block.getChannel(0), frames);
            if (equalizerEnabled.load(std::memory_order_relaxed)) equalizer.processBlock(block);
            if (exciterEnabled.load(std::memory_order_relaxed)) exciter.processBlock(block);
            if (compressorEnabled.load(std::memory_order_relaxed)) compressor.processBlock(block);
            if (deEsserEnabled.load(std::memory_order_relaxed)) deEsser.processBlock(block);
            if (noiseGateEnabled.load(std::memory_order_relaxed)) noiseGate.processBlock(block);
            if (limiterEnabled.load(std::memory_order_relaxed)) limiter.processBlock(block);
            if (stereoWidthEnabled.load(std::memory_order_relaxed) && channels >= 2) {
                stereoWidth.processBlock(block);
            }

            for (int channel = 0; channel < channels; ++channel) {
                const float* channelData = channelPointers[channel];
                for (int frame = 0; frame < frames; ++frame) {
                    interleaved[(frameOffset + frame) * channels + channel] = channelData[frame];
                }
            }
        }
    }

    dspark::AudioSpec spec;
    std::vector<float> scratch;
    std::vector<float*> channelPointers;
    dspark::Equalizer<float, 16> equalizer;
    dspark::Saturation<float> exciter;
    dspark::Compressor<float> compressor;
    dspark::Limiter<float> limiter;
    dspark::NoiseGate<float> noiseGate;
    dspark::DeEsser<float> deEsser;
    dspark::StereoWidth<float> stereoWidth;
    dspark::SpectrumAnalyzer<float> spectrum;
    std::atomic<bool> equalizerEnabled{false};
    std::atomic<bool> exciterEnabled{false};
    std::atomic<bool> compressorEnabled{false};
    std::atomic<bool> limiterEnabled{false};
    std::atomic<bool> noiseGateEnabled{false};
    std::atomic<bool> deEsserEnabled{false};
    std::atomic<bool> stereoWidthEnabled{false};

    static constexpr float defaultFrequencies[10] = {
            31.0f, 62.0f, 125.0f, 250.0f, 500.0f,
            1000.0f, 2000.0f, 4000.0f, 8000.0f, 16000.0f};
};

constexpr float Engine::defaultFrequencies[10];

Engine* fromHandle(jlong handle) {
    return reinterpret_cast<Engine*>(handle);
}

template <typename Function>
void withEngine(jlong handle, Function&& function) {
    if (auto* engine = fromHandle(handle)) function(*engine);
}

}

extern "C" JNIEXPORT jlong JNICALL
Java_com_giga_tech1000_soundengine_SoundEngine_nativeCreate(
        JNIEnv*, jclass, jint sampleRate, jint maxBlockSize, jint channelCount) {
    if (sampleRate <= 0 || maxBlockSize <= 0 || channelCount <= 0 || channelCount > kMaxChannels) {
        return 0;
    }
    try {
        auto* engine = new Engine(sampleRate, maxBlockSize, channelCount);
        __android_log_print(ANDROID_LOG_DEBUG, kTag, "DSPark engine prepared at %d Hz", sampleRate);
        return reinterpret_cast<jlong>(engine);
    } catch (...) {
        return 0;
    }
}

extern "C" JNIEXPORT void JNICALL
Java_com_giga_tech1000_soundengine_SoundEngine_nativeDestroy(JNIEnv*, jclass, jlong handle) {
    delete fromHandle(handle);
}

extern "C" JNIEXPORT void JNICALL
Java_com_giga_tech1000_soundengine_SoundEngine_nativeProcess(
        JNIEnv* env, jclass, jlong handle, jfloatArray samples, jint sampleCount) {
    auto* engine = fromHandle(handle);
    if (engine == nullptr || samples == nullptr || sampleCount <= 0) return;
    const jint count = std::min(sampleCount, static_cast<jint>(env->GetArrayLength(samples)));
    jfloat* data = env->GetFloatArrayElements(samples, nullptr);
    if (data == nullptr) return;
    engine->process(data, count);
    env->ReleaseFloatArrayElements(samples, data, 0);
}

extern "C" JNIEXPORT void JNICALL
Java_com_giga_tech1000_soundengine_SoundEngine_nativeSetEffectEnabled(
        JNIEnv*, jclass, jlong handle, jint effect, jboolean enabled) {
    withEngine(handle, [&](Engine& engine) {
        const bool value = enabled == JNI_TRUE;
        switch (effect) {
            case 0: engine.equalizerEnabled.store(value); break;
            case 1: engine.exciterEnabled.store(value); break;
            case 2: engine.compressorEnabled.store(value); break;
            case 3: engine.limiterEnabled.store(value); break;
            case 4: engine.noiseGateEnabled.store(value); break;
            case 5: engine.deEsserEnabled.store(value); break;
            case 6: engine.stereoWidthEnabled.store(value); break;
            default: break;
        }
    });
}

extern "C" JNIEXPORT void JNICALL
Java_com_giga_tech1000_soundengine_SoundEngine_nativeSetEqBand(
        JNIEnv*, jclass, jlong handle, jint index, jfloat frequency, jfloat gain, jfloat q,
        jboolean enabled) {
    withEngine(handle, [&](Engine& engine) {
        if (index >= 0 && index < 10) {
            engine.equalizer.setBand(index, frequency, gain, q);
            engine.equalizer.setBandEnabled(index, enabled == JNI_TRUE);
        }
    });
}

#define SOUND_ENGINE_SETTER(name, processor, method) \
extern "C" JNIEXPORT void JNICALL \
Java_com_giga_tech1000_soundengine_SoundEngine_##name(JNIEnv*, jclass, jlong handle, jfloat value) { \
    withEngine(handle, [&](Engine& engine) { engine.processor.method(value); }); \
}

SOUND_ENGINE_SETTER(nativeSetExciterFrequency, exciter, setPreFilterHpFrequency)
SOUND_ENGINE_SETTER(nativeSetCompressorThreshold, compressor, setThreshold)
SOUND_ENGINE_SETTER(nativeSetCompressorRatio, compressor, setRatio)
SOUND_ENGINE_SETTER(nativeSetCompressorAttack, compressor, setAttack)
SOUND_ENGINE_SETTER(nativeSetCompressorRelease, compressor, setRelease)
SOUND_ENGINE_SETTER(nativeSetLimiterThreshold, limiter, setCeiling)
SOUND_ENGINE_SETTER(nativeSetLimiterRelease, limiter, setRelease)
SOUND_ENGINE_SETTER(nativeSetNoiseGateThreshold, noiseGate, setThreshold)
SOUND_ENGINE_SETTER(nativeSetNoiseGateHysteresis, noiseGate, setHysteresis)
SOUND_ENGINE_SETTER(nativeSetNoiseGateAttack, noiseGate, setAttack)
SOUND_ENGINE_SETTER(nativeSetNoiseGateHold, noiseGate, setHold)
SOUND_ENGINE_SETTER(nativeSetNoiseGateRelease, noiseGate, setRelease)
SOUND_ENGINE_SETTER(nativeSetNoiseGateRange, noiseGate, setRange)
SOUND_ENGINE_SETTER(nativeSetDeEsserThreshold, deEsser, setThreshold)
SOUND_ENGINE_SETTER(nativeSetDeEsserFrequency, deEsser, setFrequency)
SOUND_ENGINE_SETTER(nativeSetDeEsserAttack, deEsser, setAttack)
SOUND_ENGINE_SETTER(nativeSetDeEsserRelease, deEsser, setRelease)

extern "C" JNIEXPORT void JNICALL
Java_com_giga_tech1000_soundengine_SoundEngine_nativeSetExciterAmount(
        JNIEnv*, jclass, jlong handle, jfloat amount) {
    withEngine(handle, [&](Engine& engine) {
        const float value = std::clamp(amount, 0.0f, 1.0f);
        engine.exciter.setDrive(value * 24.0f);
        engine.exciter.setMix(value);
    });
}

extern "C" JNIEXPORT void JNICALL
Java_com_giga_tech1000_soundengine_SoundEngine_nativeSetStereoWidth(
        JNIEnv*, jclass, jlong handle, jfloat width) {
    withEngine(handle, [&](Engine& engine) { engine.stereoWidth.setWidth(width * 2.0f); });
}

extern "C" JNIEXPORT void JNICALL
Java_com_giga_tech1000_soundengine_SoundEngine_nativeSetNoiseGateDuckMode(
        JNIEnv*, jclass, jlong handle, jboolean enabled) {
    withEngine(handle, [&](Engine& engine) { engine.noiseGate.setDuckMode(enabled == JNI_TRUE); });
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_giga_tech1000_soundengine_SoundEngine_nativeIsSpectrumDataReady(
        JNIEnv*, jclass, jlong handle) {
    auto* engine = fromHandle(handle);
    return engine != nullptr && engine->spectrum.isNewDataReady() ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT jint JNICALL
Java_com_giga_tech1000_soundengine_SoundEngine_nativeGetSpectrumNumBins(
        JNIEnv*, jclass, jlong handle) {
    auto* engine = fromHandle(handle);
    return engine == nullptr ? 0 : engine->spectrum.getNumBins();
}

extern "C" JNIEXPORT jfloat JNICALL
Java_com_giga_tech1000_soundengine_SoundEngine_nativeGetSpectrumBinFrequency(
        JNIEnv*, jclass, jlong handle, jint bin) {
    auto* engine = fromHandle(handle);
    return engine == nullptr ? 0.0f : engine->spectrum.binToFrequency(bin);
}

extern "C" JNIEXPORT void JNICALL
Java_com_giga_tech1000_soundengine_SoundEngine_nativeCopySpectrum(
        JNIEnv* env, jclass, jlong handle, jfloatArray destination, jboolean peakHold) {
    auto* engine = fromHandle(handle);
    if (engine == nullptr || destination == nullptr) return;
    const float* source = peakHold == JNI_TRUE
            ? engine->spectrum.getPeakHoldDb()
            : engine->spectrum.getMagnitudesDb();
    const jsize count = std::min(env->GetArrayLength(destination),
                                 static_cast<jsize>(engine->spectrum.getNumBins()));
    if (count > 0) env->SetFloatArrayRegion(destination, 0, count, source);
}