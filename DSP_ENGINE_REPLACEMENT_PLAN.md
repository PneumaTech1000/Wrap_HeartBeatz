
# DSP Engine Replacement Plan

## Context
The HeartBeatz app currently implements a parametric equalizer interface on top of Android's fixed-band EqualizerFX, with advanced effects (stereo widening, exciter, compressor, limiter, noise gate, de-esser) implemented as placeholders that delegate to a DspEngine. The previous discussion identified that Superpowered Free Tier cannot be used commercially due to licensing restrictions, necessitating an open-source NDK-based DSP solution.

The goal is to replace the placeholder DspEngine with a real, production-quality DSP engine using open-source libraries (specifically DSPark) and NDK, creating a new Sound_Engine module that provides actual audio signal processing rather than just storing parameters.

## Implementation Plan

### Phase 1: Module Setup
1. **Create Sound_Engine Module**
   - Create new Android library module `:lib:Sound_Engine`
   - Configure CMake for native code compilation
   - Add DSPark as a dependency (via prebuilt library or source integration)
   - Set up proper JNI interface between Java and C++

2. **DSPark Integration**
   - Download/build DSPark library for Android ABIs (armeabi-v7a, arm64-v8a, x86, x86_64)
   - Alternatively, integrate DSPark source directly into the module
   - Configure CMakeLists.txt to link against DSPark

### Phase 2: Native DSP Implementation
1. **Core DSP Engine (C++)**
   - Implement parametric equalizer with adjustable frequency, gain, Q-factor
   - Implement stereo widening (mid/side processing)
   - Implement exciter (harmonic generation)
   - Implement compressor/limiter (envelope-follower based)
   - Implement noise gate (with hysteresis)
   - Implement de-esser (frequency-dependent compression)
   - Include FFT support (using KissFFT or similar) for spectrum analysis
   - Design for real-time audio processing with low latency

2. **JNI Interface**
   - Create Java-native interface for DSP engine control
   - Methods for enabling/disabling effects
   - Methods for setting parameters (frequency, gain, Q, thresholds, etc.)
   - Audio processing callback for real-time audio buffer processing
   - Proper thread safety and resource management

### Phase 3: Integration with Existing Architecture
1. **Update AudioEngine.java**
   - Replace DspEngineHolder with SoundEngineHolder (new)
   - Modify setter methods to forward calls to native DSP engine
   - Maintain local state mirroring for UI synchronization
   - Ensure proper initialization and release of native resources

2. **Threading Model**
   - Implement audio processing on AudioTrack's callback thread or ExoPlayer audio processor
   - Ensure UI updates happen on main thread via handlers
   - Use appropriate synchronization between audio and UI threads

3. **Audio Processing Pipeline**
   - Integrate with Media3/ExoPlayer via AudioProcessor or custom audio pipeline
   - Process audio buffers in real-time with DSP effects chain
   - Maintain proper audio format handling (sample rate, channel count, bit depth)

### Phase 4: Testing and Validation
1. **Unit Testing**
   - Test native DSP algorithms with known test signals
   - Validate frequency response of parametric EQ
   - Test effect parameters ranges and behaviors

2. **Integration Testing**
   - Verify UI controls properly affect audio output
   - Test preset loading and application
   - Validate spectrum visualization accuracy
   - Test undo/redo functionality with new DSP engine

3. **Performance Testing**
   - Measure CPU usage on target devices
   - Verify real-time processing capability (no audio glitches)
   - Test battery impact compared to previous implementation

### Verification Approach
1. **Functional Verification**
   - Compare before/after audio output with known test signals
   - Verify each effect works independently and in combination
   - Test edge cases (parameter limits, extreme values)

2. **Compatibility Verification**
   - Test on multiple Android API levels (min SDK 28+)
   - Test on different device architectures (ARM, x86)
   - Verify proper resource cleanup to prevent leaks

3. **Quality Assurance**
   - Listen test with various music genres
   - Verify no distortion or artifacts at reasonable settings
   - Test A/B comparison functionality with presets

## Files to be Modified/Created

### New Files:
- `lib/Sound_Engine/src/main/cpp/DspEngine.cpp` - Core DSP implementation
- `lib/Sound_Engine/src/main/cpp/DspEngine.h` - Header file
- `lib/Sound_Engine/src/main/java/com/giga/tech1000/soundengine/SoundEngine.java` - Java interface
- `lib/Sound_Engine/src/main/java/com/giga/tech1000/soundengine/SoundEngineHolder.java` - Singleton holder
- `lib/Sound_Engine/CMakeLists.txt` - Native build configuration
- `lib/Sound_Engine/build.gradle` - Module configuration

### Modified Files:
- `lib/Media_Player/src/main/java/com/giga/tech1000/media_player/AudioEngine.java` - Update to use SoundEngine
- `lib/Media_Player/src/main/java/com/giga/tech1000/media_player/DspEngineHolder.java` - Replace with SoundEngineHolder
- `lib/Media_Player/build.gradle` - Add dependency on `:lib:Sound_Engine`
- `settings.gradle` - Include `:lib:Sound_Engine` module

## Dependencies and External Libraries
- DSPark (https://github.com/CristianMoresi/DSPark) for core DSP algorithms
- KissFFT or similar for FFT operations (if not included in DSPark)
- OpenSL ES or Android Audio API for audio I/O (if implementing standalone processor)
- Media3/ExoPlayer audio processing extensions for integration

## Timeline and Milestones
1. Week 1: Module setup, DSPark integration, basic JNI interface
2. Week 2: Implement core parametric EQ and stereo widening
3. Week 3: Implement exciter, compressor/limiter
4. Week 4: Implement noise gate, de-esser, spectrum analysis
5. Week 5: Integration with audio pipeline, testing, optimization
6. Week 6: Final validation, performance tuning, documentation

## Risks and Mitigation
- **Performance**: DSP algorithms may be CPU-intensive -> Mitigation: Optimize critical paths, use fixed-point where possible, profile regularly
- **Audio Glitches**: Buffer underruns -> Mitigation: Proper buffer sizing, priority tuning, fallback to simpler processing
- **Compatibility**: NDK issues across devices -> Mitigation: Test on multiple ABIs and API levels, use Android NDK r25+
- **Integration Complexity**: Audio pipeline integration -> Mitigation: Start with simple AudioTrack test, then integrate with ExoPlayer

## DSPark-Specific Risk Mitigation
Based on professional assessment with DSPark:

### 1. Performance
**Original risk is still valid**, but DSPark reduces the danger significantly. It is header-only, modern C++20, and already written with real-time constraints in mind.  

**Correction / recommendation**:
- Prefer DSPark’s built-in processors over hand-rolled ones.
- Stick to float32 (DSPark is designed for it). Fixed-point is usually unnecessary.
- Process in reasonable block sizes (128-256 samples).
- Profile the full chain (parametric EQ + compressor + limiter + de-esser + widening) on mid-range devices. DSPark should stay comfortable if you don’t enable every effect at maximum quality at once.

### 2. Audio Glitches / Buffer Underruns
Still the most dangerous risk. DSPark itself won’t cause underruns — bad buffer sizing or a heavy callback will.  

**Correction**:
- Keep the Oboe (or AAudio) callback extremely light: just call DSPark’s process methods.
- No allocations, no locks, no Java/JNI inside the audio thread.
- Add a simple “safe mode” that temporarily disables the heaviest DSPark modules (exciter, high-order filters, multi-band) if XRuns appear.

### 3. Compatibility (NDK / devices)
DSPark helps here because it is pure header-only C++ with no external dependencies.  

**Correction**:
- Still test on arm64-v8a (primary) and a couple of older 32-bit devices if you support them.
- Watch for denormals — enable flush-to-zero on the audio thread. DSPark’s filters can produce them under heavy processing.
- Use a recent NDK (r25+ or newer) as you planned.

### 4. Integration Complexity
This remains the biggest practical risk, but DSPark makes the DSP side cleaner.  

**Correction**:
- Treat DSPark as a pure processing black box.
- First verify correctness with offline/file-based tests (sine sweeps, music files).
- Only then plug it into Oboe → later into ExoPlayer/Media3 AudioProcessor.
- Keep a thin C++ wrapper around DSPark so the rest of your `Sound_Engine` module stays simple.

### Additional points worth keeping
- **Latency**: DSPark itself adds almost no algorithmic latency on most effects. The real latency will come from your buffer size + Oboe configuration.
- **Battery & thermal**: Monitor sustained CPU. DSPark is efficient, but a full professional chain + spectrum analysis can still heat mid-range phones. Consider reducing spectrum update rate or disabling heavy effects when the screen is off.

**Bottom line with DSPark**  
The original risk list is still correct, but the severity of the *Performance* and *Compatibility* risks drops, while *Glitches* and *Integration* stay high-priority. Focus your mitigation effort on a clean, allocation-free audio callback and staged integration (offline test → Oboe → player).