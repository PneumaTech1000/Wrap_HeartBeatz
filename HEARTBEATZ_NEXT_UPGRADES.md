# HeartBeatz — Next Upgrades (Party + Product Edges)

**Status:** Design / implementation guide (pre-coding checklist)  
**Last updated:** 2026-11-22  
**Related:** `SERVER_EPOCH_TIMELINE.md`, `TIME_ENGINE_ARCHITECTURE.md`, `CLOUD_MEDIA.md`, `PARTY_FLOW.md`

This document captures the recommended upgrades discussed for **multi-device party sync** and **non-party product edges**. Emphasis is on **programming shape**, **libraries**, and **core modules** so implementations stay robust and professional.

---

## Design principles

1. **Two party transports, one product surface** — users pick a mode; code shares party id, members, chat, queue via Firebase.
2. **Honest sync** — never market “zero lag worldwide.” Expose **sync quality** (tight / soft) from real metrics.
3. **Host is DJ** — host owns timeline and queue; guests suggest; controls locked for guests when appropriate.
4. **Free tier hygiene** — purge party objects on party end; cap Live Lock concurrency; prefer local files as legal core library.
5. **No dual playback stacks** — one `PlaybackStateRepository` / Media3 path; party modes adapt inputs (URL vs WebRTC track), not a second player architecture.
6. **Lightweight** — party loops must not overheat devices (sparse correction, free-run, debounced play/seek).

---

## Part A — Party mode upgrades

### A1. Standard Party (default) — cloud track + SET

**Goal:** Many devices over the internet; soft lock; $0-friendly.

| Layer | Implementation |
|--------|----------------|
| **Timeline** | Server Epoch Timeline (SET): `ideal = epochMediaMs + (serverNow − epochServerMs)` while playing; freeze on pause. See `SERVER_EPOCH_TIMELINE.md`. |
| **Clock** | `PartyServerClock` via Firebase `.info/serverTimeOffset`; host writes **concrete** `epochServerMs` at sample time (not delayed `ServerValue.TIMESTAMP` for the epoch pair). `updatedAt` may still use `ServerValue.TIMESTAMP`. |
| **Media** | `PartyMediaStore` (Supabase now → R2 later); `PartyTrackUploader`; `PartyMediaLifecycle` purge on party stop. |
| **Control plane** | Firebase RTDB: `parties/{id}/sync`, members, presence, queue, chat metadata. |
| **Playback** | Media3 (`ExoPlayer` / `MediaController`) via existing `PlaybackStateRepository` + `playPartyStream(url, pos, playWhenReady)`. |
| **Guest engine** | `TimeEngine` phases: IDLE → LOADING → BUFFERING → ARMED → LOCKED; `TimeEnginePlayerBridge` free-run after RELEASE. |
| **Bias** | Signed `lagCompensationMs` + `maybeLearnBias()` on stable LOCKED samples; reset on schedule/track change. |
| **Logging** | Single tag `PartyFlow` / `SET` lines: `hostPub`, `guestRx`, `drift`, `biasLearn`. |

**Core classes (existing):**  
`PartyLiveBridge`, `PartyPlaybackSync`, `PartyPlaybackSyncRepository`, `TimeEngine`, `TimeEnginePlayerBridge`, `TimeAnchor`, `SupabasePartyMediaStore`, `PartyMediaConfig`.

**Hardening checklist**

- [x] Host **also** plays from the same public object URL after upload (same decoder path as guests).
- [x] Force new epoch on: track change, seek, play/pause, and **periodic re-anchor** (~25s while playing).
- [x] Heartbeats omit null `mediaUrl` (sticky URL); omit epoch keys so epoch stays sticky.
- [x] Idempotent host/guest bridge start; no double `session started` spam.
- [x] Concrete MIME on upload (`audio/mpeg`, `audio/mp4`, …) — never `audio/*`.
- [x] Soft seek only when `|delta|` exceeds threshold; never seek-to-frozen ideal; bias learn.
- [x] Guest `PartySyncQuality` LiveData (TIGHT / SOFT / DRIFTING) for UI chip.

---

### A2. Host plays same cloud URL

**Goal:** Remove local-file vs stream timing skew.

**Implementation**

- After `PartyTrackUploader` SUCCESS, host path: `playback.playPartyStream(mediaUrl, currentPos, true)` (or seek-aligned handoff).
- Keep local file only for **upload source**, not for ongoing host monitoring during party (optional: dual-read position from Media3 only).
- Ensure DSP/session id still attaches to the active Media3 audio session.

**Touch points:** `PartyLiveBridge` upload success callback; `PlaybackStateRepository` / `CorePlayer`.

---

### A3. Periodic force-epoch

**Goal:** Long tracks stay anchored without guest chase.

**Implementation**

- In host publish loop: if `now − lastForceEpochMono > REANCHOR_MS` (e.g. 25_000) and playing → `publishFullSync(playing, forceNewSchedule=true)`.
- Bump `scheduleId` only when required for re-arm policy (product choice: soft re-anchor **without** full re-arm if `|delta|` small; or force schedule bump only on track/seek/pause).

**Constant:** `PartyLiveBridge.REANCHOR_INTERVAL_MS`.

---

### A4. Bias learn + rare soft seek

**Goal:** Cancel steady lag (~hundreds of ms) without clicky seek loops.

**Implementation (guest)**

- `TimeEngine.setLagCompensationMs(signed)` clamped ±2s.
- `TimeEnginePlayerBridge.maybeLearnBias(delta)` after grace: N samples, mean in [200, 1800] ms → step bias.
- `applyLockedCorrection`: HARD_SEEK only if `|local − liveIdeal| >= HARD_DRIFT_MS` and seek cooldown elapsed; **no** STALE mute loop.

**Do not:** rate-bend (pitch distortion); seek every packet.

---

### A5. Venue / same-Wi‑Fi mode

**Goal:** Tighter sync, free, in-room.

**Implementation options (pick one for v1)**

| Option | Libraries / APIs | Notes |
|--------|------------------|--------|
| **A. Same Wi‑Fi, still cloud URL** | Existing SET + Media3 | Lowest effort; lower RTT helps bias only slightly |
| **B. Host local HTTP media server** | NanoHTTPD, or `com.squareup.okhttp` + embedded server; or Android `ServerSocket` + range requests | Guests play `http://192.168.x.x:port/track`; Firebase for sync only |
| **C. NSD / Nearby** | `NsdManager`, Android Nearby Connections | Discover host; still need media + timeline |

**Product flag:** `parties/{id}/venueMode: true` + host LAN IP/port in RTDB (LAN-only, not for cellular guests).

**Robustness:** Fall back to cloud URL if LAN URL unreachable within timeout.

---

### A6. Live Lock (optional WebRTC)

**Goal:** Tighter multi-device lock for **small** N; testable on free stack.

**Product rules**

- Feature flag: `liveLockEnabled`.
- **Hard cap (free/test): 8** subscribers (+1 host publisher). Raise only after measurement.
- Over cap → join Standard Party or show upgrade/limit message.
- Audio-only; guests **subscribe only** (no default publish).

**Architecture**

```text
Host Media3 / audio capture → encode (Opus) → SFU or mesh → Guests decode → AudioTrack / WebRTC sink
Firebase: party, members, liveLock room id + tokens metadata
```

**Libraries (recommended order for Android)**

| Purpose | Library | Role |
|---------|---------|------|
| WebRTC core | `org.webrtc:google-webrtc` or vendor SDK | PeerConnection, tracks |
| SFU (self-host / cloud) | **LiveKit** (`io.livekit:livekit-android`) or raw **mediasoup** client | Rooms, SFU subscribe |
| Alt managed | Daily / Agora Android SDK | Faster spike; watch free quotas |
| Signaling | Firebase RTDB/Firestore + HTTPS token API | Room name, JWT; never embed secrets in app |
| Tokens | Small backend (Cloud Functions / Nest / etc.) | `createLiveLockRoom`, `joinLiveLockRoom` |

**Core modules to add (suggested package)**

```text
architecture/party/live/
  LiveLockManager.java          // start/stop room, cap enforcement
  LiveLockSignaling.java        // Firebase + token fetch
  LiveLockPublisher.java        // host: attach audio track
  LiveLockSubscriber.java       // guest: remote audio → play
  LiveLockConfig.java           // maxPeers=8, ice/turn, feature flag
```

**Host audio publish (hardest Android piece)**

- Prefer **app-loopback / MediaProjection / AudioPlaybackCapture** (API 29+) for “what the player plays,” not only microphone.
- Fallback for spike: mic (talk / ambient only — not true track sync).
- Always hold Wi‑Fi lock + partial wake lock while Live Lock active; release on party end.

**Mesh vs SFU**

- **Mesh:** OK for spike (2–4 devices); do not ship as primary for 8+.
- **SFU:** required for professional 6–8+; free = self-host on LAN or free-tier cloud with strict caps.

**Integration with existing player**

- Live Lock **disables** SET seek/chase for media position (timeline is the RTC stream).
- UI: still show metadata from Firebase queue; progress may be approximate or from RTCP if available.
- Leaving Live Lock mid-party can fall back to Standard if URL present.

**Diagnostics**

- Log under `PartyFlow` / `LiveLock`: room, peer count, ICE state, bitrate, packet loss.

---

### A7. Sync-quality indicator

**Goal:** Trust and support debugging.

**Implementation**

- Guest: expose `LiveData<SyncQuality>` from `|delta|` EMA + phase.
  - `TIGHT` if `|delta| < 150ms` sustained  
  - `SOFT` if `< 750ms`  
  - `DRIFTING` otherwise / buffering  
- Host: optional “listeners locked” based on presence only (host does not see guest delta unless reported).

**UI:** small chip on party + full player during party.

---

### A8. Host-as-DJ (queue + permissions)

**Goal:** Professional party control.

| Feature | Data | Code |
|---------|------|------|
| Shared queue | `parties/{id}/queue/{itemId}` | `PartyQueueRepository`, host reorder/remove |
| Guest suggest | queue item `status=pending` | Host approve → upload if needed → `ready` |
| Guest lock | `PartyLiveBridge` / player UI | Disable seek/skip on guest when `isGuestPlayerLocked` |
| Metadata | title, artist, art URL | Bind mini + full player from sync packet |

Reuse `FragmentBottomSheetQueue` as party queue when live; keep chat separate.

---

## Part B — Non-party product edges

### B1. Serious local DSP (signature edge)

**Goal:** Alone, HeartBeatz sounds different from a stock player.

| Piece | Implementation |
|--------|----------------|
| Engine | Existing **Sound_Engine** / DSP module — **no** `android.media.audiofx` as primary |
| Session | Bind to Media3 audio session id when ready (`onAudioSessionId`) |
| UI | `EqualizerViewPanel` + spectrum; presets Bass / Night / Voice / Flat |
| Safety | True-peak / limiter stage in DSP graph |
| Persistence | `DataStore` or Room: preset id + band values |

**Libraries:** keep current native/DSP pipeline; Media3 for decode/play only.

---

### B2. Per-output EQ memory

**Goal:** Bluetooth vs speaker vs car each remember curve.

**Implementation**

- Key profile by: `AudioDeviceInfo` type + address (when available) or “speaker” / “bt” / “other”.
- On `AudioManager.AudioDeviceCallback` or Media3 device change → load preset → apply to DSP.
- Store in DataStore: `eq_profiles/{deviceKey}`.

---

### B3. Gapless + crossfade (local)

**Goal:** Album / DJ-style continuity.

**Implementation**

- Media3 `ConcatenatingMediaSource` / playlist API with gapless where codec allows.
- Crossfade: Media3 transition or dual-player fade (heavier); start with gapless + optional short fade.
- Party mode: host-only responsibility; guests follow URL/stream boundaries via epoch/track change.

---

### B4. Smarter local library

**Goal:** Essential music-player baseline.

- MediaStore scan + cache (Room).
- Album artist, disc number, artwork (Glide — already used).
- “Play folder,” “play album,” queue inject.
- Rescan action in Settings (existing UI direction).

---

### B5. Mini player everywhere

**Goal:** Playback never feels stranded.

- Root chrome: mini + bottom nav coordination (existing MultiSlidingUpPanel / sheet work).
- Settings and secondary screens: same mini elevation; expand to full player without losing party state.
- Single owner: `RootMediaPlayerPanel` / `MediaNavigationManager` — no second player instance.

---

### B6. Curated legal free shelf (optional)

**Goal:** Online tracks without illegal catalog scraping.

- Sources: user-owned files (primary), CC0/CC-BY with attribution, artist free drops, PD where **recording** is clear.
- Do **not** scrape Spotify/YouTube/etc.
- Implementation: optional remote JSON index of allowed URLs + license field; play via Media3 HTTP; show attribution UI.

---

### B7. Product signature (positioning)

| Context | Promise |
|---------|---------|
| Solo | DSP that you can hear; profiles per device |
| Together | Host-controlled shared listening (Standard or Live Lock) |

---

## Suggested implementation order

| Phase | Work | Depends on |
|-------|------|------------|
| **P0** | Standard Party harden: host same URL, periodic epoch, bias/seek policy, purge, MIME | Existing SET |
| **P1** | Sync-quality chip + guest lock polish **(done)** | P0 metrics |
| **P2** | Host-as-DJ queue approve flow | Firebase queue rules |
| **P3** | DSP presets + per-output memory | Sound_Engine stable |
| **P4** | Venue LAN flag (cloud URL on same Wi‑Fi first; local HTTP later) **(done)** | P0 |
| **P5** | Live Lock spike (1 host + 2 guests, free tier or LAN SFU, cap 8) | Token backend |
| **P6** | Gapless/crossfade + library polish | Media3 playlist |
| **P7** | Legal free shelf (optional) | Policy + index |

---

## Library map (summary)

| Area | Primary stack |
|------|----------------|
| Playback | **Media3** (ExoPlayer, Session, UI as needed) |
| Party control | **Firebase** Realtime Database + Auth |
| Party media files | **Supabase Storage** (dev) → **Cloudflare R2** (prod); OkHttp |
| Timeline | Custom **TimeEngine** / SET (no extra lib) |
| Live Lock | **LiveKit Android** or **google-webrtc** + self-hosted SFU; token API |
| Images | **Glide** |
| DSP | **Sound_Engine** (project module) |
| DI / lifecycle | Existing app patterns (`HeartBeatzApp`, ViewModels) |

---

## Explicit non-goals

- Nested phone-hotspot mesh as primary scale path  
- Host phone as SFU for large N  
- Guaranteeing 0 ms sync over cellular internet with file streaming  
- Free unlimited public SFU for 50+ devices  
- Unlicensed commercial music streaming catalog  

---

## Next step

Implement **P0** (Standard Party harden) unless Live Lock spike is prioritized for experiments. Each phase should land with `PartyFlow` diagnostics and a short test script: **1 host + 2 guests**, 60s LOCKED, pause/play, track change.
