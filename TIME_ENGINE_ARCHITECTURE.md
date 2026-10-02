# TimeEngine Architecture — Schedule-Based Party Sync

**Status:** Implemented (core + guest bridge) — 2026-10-02  
**Scope:** Online party mode — same media URL + control schedules over Firebase  
**Non-goal:** Bit-identical sample lock on arbitrary phones (not achievable over the internet)

**Code:** `architecture/timeengine/*`, wired via `PartyLiveBridge`.

---

## 1. Goal

All party devices hear the **same media position at the same shared schedule time**.

If a device is late, underbuffered, or drifted beyond a hard limit:

> **Stay silent (ignore audio output) until it can rejoin on a valid schedule window.**

Never play “something close enough” out of phase.

---

## 2. Principles

| # | Principle |
|---|-----------|
| 1 | **Media file is dumb** — upload as-is; do not bake Firebase time into the audio bytes. |
| 2 | **Timeline is smart** — sync lives in **schedules**, not in the file. |
| 3 | **Host is authority** for play, pause, seek, next/prev, and schedule ids. |
| 4 | **Firebase carries control only** — small JSON; never PCM or full files. |
| 5 | **Three clocks** — media time, schedule time, device mono time (see §3). |
| 6 | **Buffer → arm → release** — sound only starts at a scheduled instant. |
| 7 | **Lag policy** — not ready or too far off → **MUTE / STALE**, then re-arm. |
| 8 | **Small drift** → gentle rate correction; **large drift** → mute + seek + re-arm (no thrash). |

---

## 3. Three clocks

```
┌─────────────────┐     mapping      ┌──────────────────┐
│  Schedule time  │ ───────────────► │  Device mono     │
│  (shared party  │   (offset +      │  elapsedRealtime │
│   timeline)     │    skew estimate)│  (local only)    │
└────────┬────────┘                  └────────┬─────────┘
         │                                    │
         │  ideal media position              │ player position
         ▼                                    ▼
┌─────────────────┐                  ┌──────────────────┐
│  Media time     │ ◄── compare ───► │  ExoPlayer /     │
│  (track ms)     │     drift        │  AudioTrack pos  │
└─────────────────┘                  └──────────────────┘
```

| Clock | Meaning | Source |
|-------|---------|--------|
| **Media time** | Position inside the track (ms) | File / player |
| **Schedule time** | When a media position should be heard | Host-authored timeline (delivered via Firebase) |
| **Device mono** | Local monotonic time for waiting & release | `SystemClock.elapsedRealtime()` |

**Ideal media position at “now”** (while playing):

\[
P_{\text{ideal}}(t) = P_{\text{anchor}} + (t_{\text{schedule}} - T_{\text{anchor}})
\]

when paused:

\[
P_{\text{ideal}}(t) = P_{\text{frozen}}
\]

Guests never “chase the host’s speaker.” They chase **\(P_{\text{ideal}}\)** on the shared schedule.

---

## 4. System context

```
┌────────────── HOST ──────────────┐
│ Library / accepted guest upload  │
│ → cloud object (mediaUrl)        │
│ ExoPlayer (local or same URL)    │
│ SchedulePublisher                │
│   on play/pause/seek/track/tick  │
└───────────────┬──────────────────┘
                │ Firebase: parties/{id}/sync
                │ (ScheduleEnvelope only)
┌───────────────▼──────────────────┐
│              GUESTS              │
│ ScheduleConsumer                 │
│ TimeEngine (phase machine)       │
│ Media loader (same mediaUrl)     │
│ Player bridge (mute/seek/rate)   │
│ Output: speaker / BT headset     │
└──────────────────────────────────┘
```

| Plane | Content |
|-------|---------|
| **Media** | Object storage URL (Supabase/R2); lifecycle purge on party end |
| **Control** | Firebase `parties/{partyId}/sync` |
| **Audio** | Local decode only |

---

## 5. Schedule packet (control plane)

One document under `parties/{partyId}/sync` (overwrite; latest wins).

### 5.1 Fields

| Field | Type | Description |
|-------|------|-------------|
| `scheduleId` | long | Monotonic; guests **ignore** older ids |
| `mediaUrl` | string | Playable URL (same for all) |
| `objectKey` | string | Storage key (cleanup / identity) |
| `trackId` | string | App track id |
| `title` / `artist` / `album` | string | UI metadata |
| `durationMs` | long | Track length |
| `isPlaying` | bool | Transport |
| `mediaPositionMs` | long | Media time at anchor |
| `lookaheadMs` | long | e.g. 3000–5000 |
| `targetMediaPositionMs` | long | Expected media time at target |
| `anchorScheduleMs` | long | Schedule time of this anchor (host timeline) |
| `targetScheduleMs` | long | Schedule time when target media applies |
| `hostMonoMs` | long | Host `elapsedRealtime` at authoring (debug / skew assist) |
| `updatedAt` | server timestamp | Firebase `ServerValue.TIMESTAMP` (coarse) |

### 5.2 Meaning

While **playing**:

- At schedule time `anchorScheduleMs` → media ≈ `mediaPositionMs`
- At schedule time `targetScheduleMs` → media ≈ `targetMediaPositionMs`  
  (normally `mediaPositionMs + lookaheadMs`, clamped to duration)

While **paused**:

- `isPlaying = false`
- `targetMediaPositionMs = mediaPositionMs` (frozen)
- Guests hold media time; **no audible drift**

### 5.3 When host publishes

| Event | Publish? |
|-------|----------|
| Track change (new URL) | Yes — new `scheduleId`, full metadata |
| Play / resume | Yes — new schedule, optional short lookahead |
| Pause | Yes — freeze position |
| Seek | Yes — new position + new targets |
| Heartbeat while playing | Yes — every ~1.5–2 s (refresh anchors) |
| Next / prev | Yes — as track change |

---

## 6. Guest phase machine

```
                    load URL
   IDLE ──────────────────────────► LOADING
                                      │
                              buffer OK │
                                      ▼
                                   BUFFERING
                                      │
                         enough for release + margin
                                      ▼
                    ┌────────────── ARMED ──────────────┐
                    │  muted; waiting for release time    │
                    │  or next valid checkpoint           │
                    └───────────────┬────────────────────┘
                                    │ schedule time reached
                                    │ and |pos - ideal| ≤ soft
                                    ▼
                                 LOCKED
                    │  audible; small rate correction only │
                    │                                      │
          drift > hard / underrun / old scheduleId         │
                    │                                      │
                    ▼                                      │
                 STALE ◄───────────────────────────────────┘
                    │  muted; do not play out of phase
                    │  seek toward ideal; re-buffer if needed
                    └──────────► ARMED (or LOADING if URL changed)
```

### 6.1 Phase rules

| Phase | Audio output | Behavior |
|-------|--------------|----------|
| `IDLE` | Silent | No active party schedule |
| `LOADING` | Silent | Open `mediaUrl` |
| `BUFFERING` | Silent | Wait until buffered beyond ideal + margin |
| `ARMED` | **Silent** | Player may be seeked; **volume 0 / playWhenReady gated** until release |
| `LOCKED` | Audible | Track `P_ideal`; rate correct small errors |
| `STALE` | **Silent** | Lag or large error — **ignore audio until back on track** |

### 6.2 Thresholds (initial defaults — tunable)

| Name | Default | Use |
|------|---------|-----|
| `lookaheadMs` | 4000 | Host target horizon |
| `bufferMarginMs` | 1500 | Extra media buffered past ideal before ARMED→LOCKED |
| `softDriftMs` | 40 | Below this: no action or tiny rate tweak |
| `rateCorrectMs` | 40–80 | Apply playback speed ~0.995–1.005 |
| `hardDriftMs` | 100 | Above this: STALE → mute → seek → ARMED |
| `heartbeatMs` | 2000 | Host publish period while playing |
| `staleTimeoutMs` | 8000 | No usable schedule → treat as STALE/IDLE |

---

## 7. Host architecture

```
HostTransport
  │  play / pause / seek / next
  ▼
SchedulePublisher
  │  builds ScheduleEnvelope (scheduleId++)
  │  writes Firebase sync node
  ▼
Firebase RTDB
```

**Host player** may use local file path for low latency, but **must** publish the **same** `mediaUrl` guests use, and media positions must refer to that timeline (same duration/encode).

**ScheduleId:** strictly increasing per party session; never reuse.

---

## 8. Guest architecture

```
Firebase sync listener
  │  drop if scheduleId ≤ lastApplied
  ▼
ScheduleConsumer
  │  update anchors; map schedule → local mono
  ▼
TimeEngine
  │  phase + idealMediaPositionMs(now)
  ▼
PlayerBridge
  │  load / seek / mute / rate / release
  ▼
ExoPlayer → AudioTrack → device output
```

### 8.1 Mapping schedule → local mono

On first valid envelope (or after large skew reset):

```
localOffset = localMonoNow - anchorScheduleMs
```

(Optionally refine with Firebase `serverTimeOffset` as coarse assist only.)

Then:

```
scheduleNow ≈ localMonoNow - localOffset
P_ideal = f(scheduleNow, last envelope)
```

Recompute offset only on new `scheduleId` with seek/track change, or if skew estimate diverges beyond a bound — **not** every heartbeat (avoids jitter).

### 8.2 Release

When phase is `ARMED` and `scheduleNow >= releaseScheduleMs` and buffer OK:

1. Ensure player position ≈ ideal (within soft band)  
2. Unmute / set playWhenReady  
3. Enter `LOCKED`

If the release window was missed → stay `STALE`/`ARMED` until **next** checkpoint (target or next heartbeat).

---

## 9. Lag policy (explicit)

```
if !buffered_for(ideal + margin):
    mute
    phase = BUFFERING or STALE
    do not output audio

if abs(playerPos - ideal) > hardDriftMs:
    mute
    phase = STALE
    seek to ideal (or next safe keyframe)
    re-enter ARMED
    do not unmute until release condition holds

if abs(playerPos - ideal) in (soft, hard]:
    optional playbackSpeed in [0.995, 1.005]
    stay LOCKED

else:
    playbackSpeed = 1.0
    stay LOCKED
```

**Silent ignore** means: no audible frames while wrong. UI may still show “syncing…” / progress from `P_ideal`.

---

## 10. Track change & controls

| Host action | Guest behavior |
|-------------|----------------|
| **New track** | New URL + new `scheduleId` → LOADING → … → ARMED → LOCKED |
| **Pause** | Envelope `isPlaying=false` → freeze ideal; mute or pause player; stay aligned |
| **Resume** | New envelope with future `targetScheduleMs` → ARMED → release |
| **Seek** | New positions + new `scheduleId` → STALE/ARMED path (mute until locked) |
| **Next/Prev** | Same as new track |

No re-encoding of audio for control. Same object key until track changes or host removes it (lifecycle delete).

---

## 11. Module layout (proposed)

```
architecture/timeengine/
  ScheduleEnvelope.java      // packet model (immutable)
  ScheduleClock.java         // scheduleNow, idealMediaPosition
  TimeEnginePhase.java       // IDLE…STALE
  TimeEngine.java            // phase + thresholds + API
  TimeEngineHostPublisher.java
  TimeEngineGuestController.java
  TimeEnginePlayerBridge.java  // mute/seek/rate/release vs ExoPlayer/MediaController

architecture/party/
  PartyLiveBridge.java       // wires host/guest to Firebase + uploader (existing)
```

**Public API (sketch):**

```text
TimeEngine
  applySchedule(ScheduleEnvelope)
  onLocalMonoTick(localMonoMs)       // or driven by player callbacks
  phase(): TimeEnginePhase
  idealMediaPositionMs(): long
  driftMs(playerPositionMs): long
  shouldOutputAudio(): boolean       // false ⇒ force mute

TimeEnginePlayerBridge
  bind(player)
  sync(TimeEngine)                   // apply mute/seek/rate
```

---

## 12. Sequence (happy path)

```
Host                         Firebase                      Guest
 │  play track                  │                           │
 │  upload if needed            │                           │
 │  publish scheduleId=1        │──────────────────────────►│
 │  (lookahead 4s)              │                           │ LOADING → BUFFERING
 │                              │                           │ ARMED (muted)
 │  … 4s later …                │                           │ release → LOCKED (audible)
 │  heartbeat scheduleId=2      │──────────────────────────►│ soft correct if needed
 │  pause                       │──────────────────────────►│ freeze, silent/paused
 │  resume scheduleId=3         │──────────────────────────►│ ARMED → LOCKED
 │  stopHosting                 │  purge media + clear sync │ IDLE
```

---

## 13. Sequence (lagging guest)

```
Guest late / weak network
  → BUFFERING (muted)
  → misses first release
  → STALE (muted) — does not play old position out loud
  → receives heartbeat with new target
  → seeks to new ideal, buffers
  → ARMED → LOCKED on next valid window
```

---

## 14. What we intentionally do **not** do

- Bake Firebase/global time into the uploaded audio file  
- Stream PCM through Firebase  
- Use only `position + isPlaying` without target/schedule fields  
- Play while underbuffered to “catch up”  
- Large speed changes (chipmunk/slow-mo) as primary sync  
- Require Auracast / same LAN for this mode (optional later)

---

## 15. Success metrics (device logs)

Log tag e.g. `TimeEngine`:

| Metric | Target when LOCKED |
|--------|---------------------|
| `driftMs = playerPos - idealPos` | \|drift\| &lt; 40 ms most samples |
| Time in STALE per track | Low after first lock |
| Audible start count per track | 1 (not every packet) |
| scheduleId monotonic | Always increasing on host |

Cross-device: two phones, same room — no obvious echo when both LOCKED.

---

## 16. Implementation order (after you approve)

1. **ScheduleEnvelope** + Firebase read/write shape (compatible with current `sync` node where possible)  
2. **TimeEngine** phase machine + ideal position (no player yet)  
3. **PlayerBridge** mute / seek / rate / release  
4. **Host publisher** on transport + heartbeat  
5. **Guest consumer** + wire `PartyLiveBridge`  
6. Tune thresholds on real devices  
7. UI: guest “syncing” while not `shouldOutputAudio()`

---

## 17. Open choices (decide before coding)

| Choice | Options | Suggestion |
|--------|---------|------------|
| Lookahead | 3 s / 4 s / 5 s | **4 s** default |
| Host plays local file vs URL | Local for host, URL for guests | OK if positions match same encode |
| Pause = pause player vs mute at position | Either | Pause player + freeze ideal |
| Rate correction | On / off | On, narrow band only |
| Firebase path | Keep `parties/{id}/sync` | Yes — extend fields, don’t fork |

---

**End of architecture.**  

Review this document; once you confirm (and any open choices), implementation can follow §16 without redesigning the model.
