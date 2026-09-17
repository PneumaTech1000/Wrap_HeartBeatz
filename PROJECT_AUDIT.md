# HeartBeatz Project Audit

**Date:** 2026-09-16 (updated 2026-09-17)  
**Scope:** Full codebase review (architecture, modules, DSP, Party Mode, Auth, build config, incomplete code, UI panel layout, window insets)  
**Status:** Issues listed below will be addressed one by one.

---

## Project Overview (What is being built)

HeartBeatz is a modern Android local music player with social features:

- Local music library (Songs / Albums / Artists / Genres / Folders / Playlists)
- Background playback (Media3 / ExoPlayer path)
- Advanced parametric EQ + DSP effects (native `Sound_Engine` using DSPark)
- Audio visualizer
- **Party Mode** (synchronized listening) via Firebase Realtime Database + WebRTC
- Firebase Auth + Credential Manager / Google Sign-In
- Theme support (Light/Dark/System)
- Modular architecture (`app` + several `lib/*` modules)

The stated direction is to make the foundation **buildable, coherent, and testable** first, then finish DSP and Party Mode properly.

---

## 1. Critical Architecture / Dual Playback Paths

There are still **two playback systems** living side-by-side:

| Path | Status |
|------|--------|
| Legacy `MediaPlayerThread` + `UIThread` | Still heavily referenced (~69 usages) |
| New `PlaybackManager` + `MediaPlayerService` + Media3 | Partially adopted |

### Specific problems
- `EqualizerViewModel` still depends on the legacy path:
  ```java
  MediaPlayerThread playerThread = UIThread.getInstance().getMediaPlayerThread();
  this.playbackState = new PlaybackStateManager(playerThread);
  ```
- Roadmap Phase 2 explicitly says Media3 should become the **sole** authority. This migration is incomplete.

### Risk
Race conditions, inconsistent state, equalizer/DSP not always attached to the real player, hard-to-debug behavior.

**Priority:** High  
**Status:** [x] Largely done (2026-09-17)

### Resolution notes (2026-09-17)
- Confirmed there is **no second audio engine**: `MediaPlayerThread` → `CorePlayer` → `MediaPlayerController` → `MediaPlayerService` → `PlaybackManager` (Media3/ExoPlayer).
- Introduced **one shared** `PlaybackStateRepository` owned by `UIThread` (`getPlaybackStateRepository()`).
- ViewModels (`EqualizerViewModel`, `PartyViewModel`, `PlaybackCacheViewModel`) now use the shared repo instead of `new PlaybackStateManager(...)`.
- ViewModels no longer call `release()` on the shared repository.
- Removed legacy `androidx.media:media` from `libs.versions.toml` and all module `build.gradle` files.
- Replaced `android.support.v4.media.session.PlaybackStateCompat` with `androidx.media3.session.legacy.PlaybackStateCompat`.
- Removed unused `MediaDescriptionCompat` / `MediaMetadataCompat` helpers from `LibraryScanner`.
- `MediaPlayerThread` remains as the thin Media3 bridge (rename optional later).

---

## 2. Build / Toolchain Inconsistencies

| Module | Java Version | Notes |
|--------|--------------|-------|
| `app` | 17 | |
| `lib/Media_Player`, `Party_Mode`, `Utils`, `Extensions`, `Icons_Pack` | **21** | |
| `lib/Sound_Engine` | source 11 / target 17 | |
| `Visualizer_Android`, sliding panel, readableBottomBar, etc. | 17 | |

### Specific problems
- `compileSdk` / `targetSdk` set to **37** (very aggressive; current stable is lower).
- `Sound_Engine` uses a different Java level than the module that consumes it (`Media_Player` is on 21).
- Mixed Java versions make the project fragile and harder to build consistently.

**Priority:** High  
**Status:** [x] Completed by user on local workspace (compileSdk 36 + Java 21 across modules)

---

## 3. DSP / Equalizer Integration Gaps

- `Sound_Engine` (native DSPark) exists and is wired through `DspAudioProcessor` → Media3.
- `AudioEngine` still maintains a large amount of **Android `audiofx.*`** objects (Equalizer, BassBoost, Virtualizer, LoudnessEnhancer, EnvironmentalReverb) **and** forwards advanced effects to the native engine.
- Dual control of EQ state is messy. Parametric bands are supposed to go through SoundEngine, but legacy fixed-band `Equalizer` objects are still created.
- Hard-coded sample rate / block size in several places (`48000`, `4096`) instead of always taking the actual format from the processor.
- `EqualizerViewPanel` still has many `qSeekBars[i] = null;` assignments — looks like leftover / incomplete UI wiring for Q-factor controls.

**Priority:** High  
**Status:** [x] Done (2026-09-17)

### Resolution notes (2026-09-17)
- Rewrote `AudioEngine` to use **only** native `SoundEngine` / `SoundEngineHolder` (DSPark via Media3 `DspAudioProcessor`).
- Removed all `android.media.audiofx` usage (`Equalizer`, `BassBoost`, `Virtualizer`, `LoudnessEnhancer`, `EnvironmentalReverb`).
- Fixed-band EQ API mapped to 10 parametric bands (31 Hz–16 kHz).
- Bass → low bands; Virtualizer → stereo widening; Loudness → mild presence EQ lift.
- Reverb: UI/preset state retained; no native reverb module yet (no audiofx fallback).
- Public `AudioEngine` API kept so `EqualizerViewModel` call sites remain valid.

---

## 4. Incomplete / Stubbed Features (TODOs & Placeholders)

### Auth & User identity
- `EnhancedFirebasePartyHostRepository.getCurrentUserId()` returns the literal string `"current_user_id"` with a TODO to use real Firebase Auth.
- Login / SignUp have TODOs for:
  - Forgot password flow
  - UI updates based on user state
- Party host repository has TODOs to notify UI when sign-in is required.

### Party Mode / WebRTC
- `WebRtcPartyManager` has TODO for proper handling of sync data in `PartyManagerListener`.
- MEMORY.md and roadmap still list several Party Mode items as incomplete (TURN servers, recovery, bandwidth adaptation, etc.).

### Other
- Visualizer has a TODO about dynamic density changes possibly causing crashes.
- Several “notify UI” paths are commented as TODO.

**Priority:** High (especially Auth identity)  
**Status:** [x] Done (2026-09-17)

### Resolution notes (2026-09-17)
- **Auth uid:** Removed broken `getCurrentUserId()` / `isAuthenticated()` overrides in `EnhancedFirebasePartyHostRepository` that returned `"current_user_id"`; uses real `FirebaseAuth` via `FirebaseRepository`.
- **Sign-in prompts:** `FirebasePartyHostRepository` posts `partyErrorLiveData` ("Please sign in…") on create/join when unauthenticated; `getPartyError()` exposed for UI.
- **Login:** Firebase `sendPasswordResetEmail` dialog flow; loading/disabled UI; clearer errors; skip login if already signed in.
- **SignUp:** Profile display name via `UserProfileChangeRequest`; loading UI; errors; skip if already signed in.
- **WebRTC sync:** `WebRtcPartyManager.onSyncDataReceived` forwards to `PartyManagerListener` and `WebRtcPartyManagerListener` with null-checks and error isolation.
- **Visualizer:** `setDensity` clamps, skips no-ops, synchronized re-init with try/catch, invalidates safely.
- No remaining `TODO`/`FIXME` in app / Party_Mode / Visualizer_Android sources.

---

## 5. Package / Naming Mismatches

- Module namespace: `com.giga.tech1000.sound_engine`
- Java package: `com.giga.tech1000.soundengine` (no underscore)

This works today but is inconsistent and easy to break with future refactors.

**Priority:** Medium  
**Status:** [x] Done (2026-09-17)

### Resolution notes (2026-09-17)
- Aligned `Sound_Engine` Android `namespace` to `com.giga.tech1000.soundengine` (matches Java package + JNI).
- Moved unit/instrumented tests under `.../soundengine` package.
- Module folder name `Sound_Engine` kept (Gradle path); language package is consistent.

---

## 6. Documentation & Project Hygiene

- `README.md` is essentially empty / corrupted.
- Multiple overlapping summary files (`IMPLEMENTATION_SUMMARY.txt`, `implementation_summary.md`, `MEMORY.md`, `PROJECT_OVERVIEW.md`, `PROJECT_ROADMAP.md`) that are partially out of date relative to the latest code.
- `CLAUDE.md` is empty.

**Priority:** Medium  
**Status:** [x] Done (2026-09-17)

### Resolution notes (2026-09-17)
- Replaced empty/corrupted `README.md` with a professional project README.
- Removed obsolete clutter: `CLAUDE.md`, `MEMORY.md`, `IMPLEMENTATION_SUMMARY.txt`, `implementation_summary.md`, `DSP_ENGINE_REPLACEMENT_PLAN.md`.
- Kept and annotated: `PROJECT_AUDIT.md` (source of truth), `PROJECT_ROADMAP.md`, `PROJECT_OVERVIEW.md`.

---

## 7. Other Notable Issues

- `Sound_Engine` is correctly included via `Media_Player`, but the overall dependency graph and Java version matrix is fragile.
- Some effect parameters exist in the ViewModel / Panel but the full UI controls (especially Q, attack/release, etc.) appear only partially wired.
- Legacy Android `audiofx` objects are still being created even when the native DSP path is the intended future.

**Priority:** Medium–Low  
**Status:** [x] Done (2026-09-17)

### Resolution notes (2026-09-17)
- DSP dual-control / audiofx issues resolved in §3 (`AudioEngine` is Sound_Engine-only).
- Equalizer Q seek bars: null-safe when layout does not provide Q controls (prevents NPE on preset apply).
- Dependency graph remains modular; Java 21 + compileSdk 36 expected across modules (user applied toolchain on local; `Sound_Engine` aligned in this workspace).

---

## 8. UI: Mini Player Visible Behind Bottom Navigation (Panel Hide Bug)

**Observed on device:** Custom bottom navigation (Home / Party) is not flush to the bottom of the screen. The mini player bar (supposed to be hidden when idle) shows behind/under the bottom navigation.

### Panel stack

| Order | Panel | Peak height | Intended start state |
|-------|--------|-------------|----------------------|
| 1 | `RootMediaPlayerPanel` (mini player) | 64dp (`media_player_bar_height`) | Hidden |
| 2 | `RootNavigationBarPanel` (Home / Party) | 64dp (`navigation_bar_height`) | Collapsed |

### Root cause

In `RootMediaPlayerPanel.onCreateView()`:

```java
this.setPanelState(MultiSlidingUpPanelLayout.HIDDEN);
```

`setPanelState(HIDDEN)` only changes the state flag. It does **not** set the library’s internal `isHidden = true`.

The multi-sliding panel library reserves space for panels above using:

```java
maxHeight += (panel.isUserHidden()) ? 0 : panel.getPeakHeight();
```

Because `isUserHidden()` stays `false`, the navigation bar still reserves the mini player’s **64dp**. Result:

- Bottom nav is pushed up by ~64dp
- Mini player area remains visible underneath (matches the screenshot)

The correct API is:

```java
hidePanel();  // calls parent hidePanel + sets isHidden = true
```

### Related show/hide inconsistency

When a song is set, code does:

```java
collapsePanel();  // shows mini player
```

There is no reliable matching path that calls `hidePanel()` when playback is idle / no song is active. Once the mini player has been shown, it can stay in the stack even when it should be fully hidden.

### Dimens note

- `media_player_bar_height` = 64dp  
- `navigation_bar_height` = 64dp  
- `bar_and_navigation_height` = **138dp** (not 128) — unexplained extra 10dp used for content bottom padding

**Priority:** High (user-visible UI bug)  
**Status:** [x] Done (2026-09-17)

### Resolution notes (2026-09-17)
- Library `hidePanel()` / `collapsePanel()` / `expandPanel()` now set `isHidden` correctly and `requestLayout()`.
- `RootMediaPlayerPanel` uses `setUserHiddenMode(true)` + `hidePanel()` on create (not only `setPanelState(HIDDEN)`).
- Added `showMiniPlayerCollapsed()` / `hideMiniPlayer()`; song null or idle clears the stack.
- `UIThread` notifies nav padding when song/playback visibility changes.
- `bar_and_navigation_height` set to **128dp** (64+64).

---

## 9. Window Insets / Edge-to-Edge / fitsSystemWindows Audit

### Overview

| Area | Status |
|------|--------|
| `android:fitsSystemWindows` in main app | **Not used** (good). Only appears in the sample `multiSlidingUpPanel` demo layout |
| `EdgeToEdge.enable()` | **Only in `MainActivity`** |
| `ViewCompat.setOnApplyWindowInsetsListener` | **Only in `MainActivity` + `FragmentHome`** |
| Login / SignUp / Scanner | **No edge-to-edge, no inset handling** |
| `FragmentParty` | **No system-bar inset handling** (only manual bottom padding for player bar) |

### 9.1 MainActivity — primary inset handling

```java
androidx.activity.EdgeToEdge.enable(this);
// ...
ViewCompat.setOnApplyWindowInsetsListener(findViewById(android.R.id.content), (v, insets) -> {
    Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
    v.setPadding(systemBars.left, 0, systemBars.right, systemBars.bottom);
    return insets;  // not consumed
});
```

**What it does**
- Enables edge-to-edge for the activity
- Pads left / right / bottom of the content root with system bars
- Does **not** pad the top (status bar) — intentional so toolbars can draw under the status bar

**Problems**
1. Top inset is left entirely to children — only `FragmentHome` handles status-bar top consistently.
2. Insets are returned **unconsumed** → children can apply the same insets again → risk of double padding.
3. Bottom padding on the content root places the entire `MultiSlidingUpPanel` above the system nav/gesture bar (correct for system UI), but combined with the panel hide bug it makes the floating bottom nav more obvious.
4. No IME (keyboard) inset handling — keyboard can cover inputs on screens that need it.

### 9.2 FragmentHome — status bar only

```java
ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
    Insets statusBars = insets.getInsets(WindowInsetsCompat.Type.statusBars());
    // pads toolbarWrapper, drawer header, equalizer header with statusBars.top
    return insets; // unconsumed
});
```

**Problems**
1. Only status bars — navigation/gesture insets rely entirely on `MainActivity`.
2. Equalizer header inset uses a fragile parent walk:
   ```java
   equalizerViewPanel.getView()
       .findViewById(R.id.equalizer_view_close)
       .getParent().getParent();
   ```
   Easy to NPE or hit the wrong view if hierarchy changes.
3. Insets not consumed → possible double application with parent.
4. Status-bar icon appearance toggled in MotionLayout transitions without solid theme awareness.

### 9.3 Manual bottom padding (player + nav) — separate from system insets

```java
// FragmentHome / FragmentParty
paddingHeight = isDisplaying
    ? R.dimen.bar_and_navigation_height   // 138dp
    : R.dimen.navigation_bar_height;      // 64dp
pagerWrapper.setPadding(0, 0, 0, paddingHeight);
```

**Problems**
1. **138 ≠ 64 + 64** — extra 10dp is unexplained; can cause slight misalignment.
2. This padding does **not** include system navigation-bar inset (that is applied on the activity content root). Split ownership is easy to get wrong when panels show/hide.
3. When the mini player should be hidden but still occupies peak height in the panel stack, content padding and panel layout disagree → gap / overlap (links to §8).

### 9.4 Other activities — inconsistent

| Activity | Edge-to-edge | Inset listener | Notes |
|----------|--------------|----------------|-------|
| `MainActivity` | Yes | Yes (content root) | Partial (no top on root) |
| `LoginActivity` | No | No | Can draw under status/nav bars |
| `SignUpActivity` | No | No | Same |
| `CustomScannerActivity` | No | No | Camera UI may ignore cutouts / system bars |

Theme is `Theme.Material3.DayNight.NoActionBar` with no explicit status/navigation bar overrides found — without EdgeToEdge + listeners, behavior depends on OEM defaults.

### 9.5 fitsSystemWindows

- Main app layouts: **not used** (correct modern approach).
- Only in library sample app layout under `lib/multiSlidingUpPanel` — not part of shipped HeartBeatz UI.

### 9.6 How insets interact with the screenshot bug

Two systems affect the bottom of the screen:

1. **System insets** — `MainActivity` pads content bottom by `systemBars.bottom`.
2. **Panel stack** — mini player peak height still reserved because `hidePanel()` / `isUserHidden` is not used correctly (§8).

Fixing only insets will not fully fix the floating nav; fixing only the panel hide will help a lot, but insets should still be cleaned up for consistency across screens.

**Priority:** High (affects all screens + compounds §8)  
**Status:** [x] Done (2026-09-17)

### Resolution notes (2026-09-17)
- `MainActivity`: pads left/right/bottom (incl. IME); consumes those insets; leaves status bar for children.
- `FragmentHome`: applies status top to toolbar/drawer; safer equalizer header handling; consumes status insets.
- `LoginActivity` / `SignUpActivity`: `EdgeToEdge` + system bars + IME padding.
- Content bottom margin uses **128dp** when mini player + nav are both visible.

---

## Summary of Current State

Solid product vision and substantial work already in place (native DSP, WebRTC Party Mode start). Main problem areas:

1. **Incomplete migration** away from the old `MediaPlayerThread` / `UIThread` path.
2. **Inconsistent build configuration** (Java versions, SDK levels).
3. **Stubbed identity and Party Mode pieces** (hard-coded user ID, missing UI notifications, incomplete sync handling).
4. **EQ UI / DSP wiring** that is partially dual and partially unfinished.
5. **Documentation drift**.
6. **Mini player not properly hidden** → bottom nav floats above a visible mini player strip (§8).
7. **Inconsistent / incomplete window insets** across activities and fragments (§9).

---

## Recommended Treatment Order

Suggested sequence (can be reordered as needed):

1. **§8 Mini player / bottom nav panel hide** (user-visible UI fix)
2. **§9 Window insets / edge-to-edge consistency** (pairs with §8)
3. Build / Toolchain consistency (§2)
4. Auth identity fix (§4)
5. Dual playback architecture cleanup (§1)
6. DSP / Equalizer ownership cleanup (§3)
7. Party Mode incomplete pieces (§4)
8. Package naming consistency (§5)
9. Documentation cleanup (§6)

---

## Next Step

Treat **§8 (panel hide)** and **§9 (insets)** first, then continue down the list.

---

*This file is the single source of truth for the current audit. Update the Status checkboxes as each item is resolved.*
