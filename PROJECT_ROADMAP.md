# HeartBeatz Project Roadmap

This roadmap converts the project audit into an implementation sequence. Work should proceed in small, verifiable updates. A roadmap item is complete only when its acceptance criteria are met and the relevant tests or build checks pass.

## Product Direction

HeartBeatz is an Android local music player with:

- Local music library discovery and organization
- Background playback
- Visualizer and audio effects
- Playlists and playback state persistence
- Social listening through Party Mode
- Firebase-backed authentication and party discovery

The immediate priority is to make the existing foundation buildable, coherent, and testable before adding more user-facing features.

## Status Legend

- [ ] Not started
- [~] In progress or partially implemented
- [x] Verified complete

## Phase 1: Establish A Buildable Baseline

### 1.1 Configure a repeatable development environment

- [ ] Document the required JDK, Android SDK, NDK, Gradle, and CMake versions.
- [ ] Add a short build section to the README.
- [ ] Ensure `JAVA_HOME` and Android SDK configuration are clear for Windows development.
- [ ] Confirm a clean checkout can configure Gradle without machine-specific assumptions.

**Acceptance criteria:** A clean checkout can run the debug build from the documented commands.

### 1.2 Build every included module

- [ ] Run `clean assembleDebug` from the repository root.
- [ ] Compile the app and every library module.
- [ ] Fix Java, resource, dependency, JNI, CMake, and manifest errors.
- [ ] Verify the native Sound_Engine build for supported ABIs.
- [ ] Generate a debug APK successfully.

**Acceptance criteria:** `gradlew.bat clean assembleDebug` succeeds without compilation or native build errors.

### 1.3 Add continuous integration

- [ ] Add GitHub Actions for debug builds.
- [ ] Run unit tests in CI.
- [ ] Run lint and static checks in CI.
- [ ] Upload the debug APK as a workflow artifact.
- [ ] Add a separate release-build validation job when signing configuration is available.

**Acceptance criteria:** Pull requests fail when the project cannot compile or tests do not pass.

## Phase 2: Select One Playback Architecture

### 2.1 Make Media3 the playback authority

- [ ] Define `MediaPlayerService` and `PlaybackManager` as the sole playback owner.
- [ ] Route UI commands through `MediaController`.
- [ ] Expose playback state through one observable state source.
- [ ] Remove new dependencies on the legacy `MediaPlayerThread` path.
- [ ] Migrate existing screens incrementally.

**Acceptance criteria:** Only one player controls audio, queue state, audio focus, and playback position.

### 2.2 Stabilize playback lifecycle

- [ ] Test service creation, restart, and destruction.
- [ ] Restore queue and position after process recreation.
- [ ] Handle audio focus loss and noisy-audio events.
- [ ] Handle missing, deleted, or inaccessible media gracefully.
- [ ] Release players, controllers, observers, and executors deterministically.

**Acceptance criteria:** Playback remains consistent across backgrounding, service restart, configuration changes, and audio-focus transitions.

### 2.3 Add playback tests

- [ ] Test queue insertion, removal, and reordering.
- [ ] Test next, previous, repeat, and shuffle behavior.
- [ ] Test playback-state restoration.
- [ ] Test MediaController-to-service commands.
- [ ] Test missing-media error states.

**Acceptance criteria:** Core playback behavior is covered by automated tests without requiring a physical device.

## Phase 3: Implement DSP Safely

### 3.1 Define the audio contract

- [ ] Document PCM encoding and sample format.
- [ ] Document sample rate and channel-count handling.
- [ ] Define interleaved versus planar buffer layout.
- [ ] Define block-size and latency expectations.
- [ ] Define ownership and lifetime of input/output buffers.
- [ ] Define behavior when a format is unsupported.

**Acceptance criteria:** Java, JNI, C++, and Media3 all implement the same documented audio contract.

### 3.2 Correct Sound_Engine integration

- [ ] Fix Java/native method signature mismatches.
- [ ] Fix native mutex and lifecycle errors.
- [ ] Correct stereo buffer handling.
- [ ] Implement a real Media3 `AudioProcessor`.
- [ ] Connect the processor to the Media3 playback chain.
- [ ] Ensure native resources are released on player and service shutdown.

**Acceptance criteria:** A known audio signal passes through the native DSP path during real playback without crashes, corruption, or audible dropouts.

### 3.3 Implement and verify effects incrementally

- [ ] Parametric equalizer
- [ ] Stereo widening
- [ ] Compressor
- [ ] Limiter
- [ ] Noise gate
- [ ] De-esser
- [ ] Exciter
- [ ] Spectrum analysis

For each effect:

- [ ] Define parameter ranges and defaults.
- [ ] Add enable/disable behavior.
- [ ] Add offline signal tests.
- [ ] Test bypass behavior.
- [ ] Test extreme and invalid values.
- [ ] Measure CPU cost and latency.

**Acceptance criteria:** Each enabled effect has a measurable, tested effect on audio and a safe bypass path.

### 3.4 Make the audio thread real-time safe

- [ ] Avoid allocations in the audio callback.
- [ ] Avoid blocking locks in the audio callback.
- [ ] Avoid Java calls from the audio callback.
- [ ] Use lock-free or double-buffered parameter updates where appropriate.
- [ ] Add underrun and processing-time instrumentation.
- [ ] Test on lower-end supported devices.

**Acceptance criteria:** Processing stays within the available buffer deadline under the supported effect chain.

## Phase 4: Rebuild Party Mode Around One Protocol

### 4.1 Decide the product model

Choose and document one primary model:

- **Synchronized local playback:** each participant plays a locally available copy and receives signed playback state.
- **Actual audio streaming:** the host audio is encoded and transmitted through a proper WebRTC media pipeline.

**Acceptance criteria:** The product behavior, technical protocol, and UI wording describe the same model.

### 4.2 Secure and unify party identity

- [ ] Use Firebase Auth UIDs consistently.
- [ ] Remove placeholder owner and client IDs.
- [ ] Align signaling client paths with Firebase rules.
- [ ] Add room membership authorization.
- [ ] Add party expiration and cleanup.
- [ ] Add invite revocation.
- [ ] Add replay protection for QR/invite tokens.
- [ ] Add TURN server configuration for difficult networks.

**Acceptance criteria:** Unauthorized users cannot read, write, impersonate, or remain in a party through stale credentials.

### 4.3 Complete the selected transport

For synchronized playback:

- [ ] Define stable track identity.
- [ ] Exchange play/pause, position, queue, and timestamp state.
- [ ] Apply received state to the local Media3 player.
- [ ] Handle missing local tracks.
- [ ] Add clock drift correction.

For audio streaming:

- [ ] Use a real WebRTC audio track and supported codec.
- [ ] Implement remote audio routing.
- [ ] Implement jitter buffering and backpressure.
- [ ] Handle reconnection and peer removal.
- [ ] Add host handover or clearly restrict the feature to host-only sessions.

**Acceptance criteria:** Two test devices can join a party and demonstrate the advertised listening behavior end to end.

### 4.4 Add party tests

- [ ] Firebase Emulator Suite rules tests.
- [ ] Signaling message serialization tests.
- [ ] Join/leave/reconnect tests.
- [ ] Host and guest lifecycle tests.
- [ ] Synchronization drift tests.
- [ ] Network interruption tests.
- [ ] QR token expiry and invalid-token tests.

**Acceptance criteria:** Party behavior is tested without relying only on manual happy-path testing.

## Phase 5: Harden Product Behavior

### 5.1 Improve authentication

- [ ] Implement password reset.
- [ ] Store and update the user display name.
- [ ] Add explicit unauthenticated routing.
- [ ] Add loading, error, and retry states.
- [ ] Handle expired sessions.
- [ ] Add account deletion or documented account-management behavior.

### 5.2 Request permissions by feature

- [ ] Request audio permission immediately before library scanning.
- [ ] Request camera permission only when scanning.
- [ ] Request microphone permission only for an implemented microphone feature.
- [ ] Request nearby-device/location permissions only for an implemented feature requiring them.
- [ ] Explain denied-permission recovery in the UI.

**Acceptance criteria:** Basic browsing and playback do not require unrelated permissions.

### 5.3 Stabilize persistence and data safety

- [ ] Add Room migration strategy.
- [ ] Test upgrades from prior database versions.
- [ ] Define backup and data-extraction policy.
- [ ] Avoid persisting stale MediaStore paths.
- [ ] Reconcile duplicate repository instances.
- [ ] Close database observers and executors correctly.

### 5.4 Add production diagnostics

- [ ] Add crash reporting.
- [ ] Add structured logging with release log suppression.
- [ ] Add meaningful user-facing error states.
- [ ] Add diagnostics for playback failures, underruns, and party connection failures.
- [ ] Avoid logging tokens, personal data, or audio content.

### 5.5 Validate release readiness

- [ ] Test R8 and resource shrinking.
- [ ] Validate release APK installation.
- [ ] Test supported Android API levels.
- [ ] Test arm64 and any supported secondary ABIs.
- [ ] Test low-storage, no-network, revoked-permission, and missing-media scenarios.
- [ ] Perform accessibility and responsive-layout checks.
- [ ] Document privacy behavior and data collection.

## Additional Recommended Add-ons For Approval

These items are additional proposals beyond the core roadmap. They should be approved individually before implementation.

### A. Introduce a formal domain/state model

Create explicit state models for library loading, playback, authentication, and Party Mode instead of relying on scattered booleans, callbacks, and logs.

Suggested states include:

- `Loading`
- `Ready`
- `Playing`
- `Paused`
- `Error`
- `Joining`
- `Connected`
- `Reconnecting`
- `Disconnected`

**Why:** This makes incomplete, loading, and failure states visible and testable instead of silently appearing successful.

### B. Add a repository architecture decision record

Document the ownership rules for:

- Playback state
- Room data
- Firebase state
- WebRTC connection state
- Native DSP lifecycle

**Why:** The project currently contains transitional architectures. A short decision record prevents future code from reintroducing competing owners.

### C. Add dependency and license auditing

- [ ] Generate a dependency inventory.
- [ ] Review licenses for DSPark, WebRTC, Media3, Firebase, and bundled libraries.
- [ ] Add automated dependency vulnerability scanning.
- [ ] Document notices required in the application.

**Why:** The project is intended to use open-source DSP and network components, so licensing and transitive dependency changes are release risks.

### D. Add a feature-flag and safe-mode system

- [ ] Gate unfinished DSP and Party Mode features.
- [ ] Provide a playback-safe fallback when native DSP fails.
- [ ] Disable expensive effects when repeated underruns are detected.
- [ ] Allow internal test builds to expose experimental features.

**Why:** Experimental audio or networking code should not compromise ordinary local playback.

### E. Add deterministic fake data sources

Create fake MediaStore, Firebase, playback, and WebRTC interfaces for tests.

**Why:** Deterministic fakes allow ViewModels and state transitions to be tested without requiring a device, network, Firebase account, or actual audio hardware.

### F. Add observability for audio quality

Track locally and privately:

- Audio underruns
- DSP processing duration
- Buffer size
- Current sample rate
- Decoder failures
- Audio focus transitions
- Bluetooth route changes

**Why:** Audio problems are often device-specific and cannot be diagnosed from ordinary application logs.

### G. Add a compatibility matrix

Maintain a test matrix covering:

- Android API levels
- ARM architectures
- Wired and Bluetooth audio
- Headphones and speaker output
- Small and large libraries
- Offline and poor-network conditions
- Low-memory devices

**Why:** Media and native audio behavior varies substantially across devices and routes.

### H. Add secure configuration handling

- [ ] Keep private Firebase service-account material out of the repository.
- [ ] Document which Firebase client configuration is safe to publish.
- [ ] Add secret scanning to CI.
- [ ] Rotate exposed credentials immediately.
- [ ] Separate debug and release Firebase projects where practical.

**Why:** Publishing client configuration is not automatically unsafe, but credentials, rules, and environment separation must be deliberate.

### I. Add a user data and privacy review

Document:

- What account data is stored
- What party data is stored
- Whether audio is transmitted
- How long party records remain
- What happens when a user leaves or deletes an account
- Which permissions are required and why

**Why:** Party features and local-media access create trust expectations that should be reflected in both UX and documentation.

### J. Add a release checklist and definition of done

Before each release, verify:

- Clean checkout builds
- Unit and integration tests pass
- No placeholder user-facing flows remain
- No debug logging or test credentials remain
- Backup behavior is intentional
- Privacy documentation is current
- Crash-free smoke test passes on representative devices

**Why:** A written release gate prevents documentation and implementation from drifting apart again.

## Suggested Implementation Order

1. Phase 1: Buildable baseline
2. Phase 2: Single playback architecture
3. Phase 3: Safe DSP integration
4. Phase 5.2: Feature-specific permissions
5. Phase 5.1: Authentication completion
6. Phase 4: Party Mode protocol and transport
7. Phase 5.3-5.5: Persistence, diagnostics, and release hardening
8. Approved add-ons, starting with feature flags, deterministic fakes, and dependency auditing

## Working Agreement

For each roadmap item:

1. Identify the owning module and current behavior.
2. State the intended behavior and acceptance criteria.
3. Make the smallest implementation change.
4. Run a focused test or build check.
5. Update this document with status and evidence.
6. Do not mark an item complete based solely on compilation when runtime or device behavior is required.
