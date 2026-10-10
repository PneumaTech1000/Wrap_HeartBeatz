# Server Epoch Timeline (SET)

Production design for HeartBeatz party audio sync over Firebase + cloud media URLs.

**Status:** Implementation in progress (replaces continuous position-chase as the ideal-position source of truth).

**Related:** `TIME_ENGINE_ARCHITECTURE.md`, `TIME_ENGINE_ROADMAP.md`, `CLOUD_MEDIA.md`

---

## 1. Goal

- One **shared logical timeline** owned by Firebase server time + host-written epoch.
- Guests **compute** media position; they do not chase every host MediaController sample.
- **Independent of device wall-clock settings** (GMT/timezone). Local `elapsedRealtime` is only used to interpolate between packets.
- Clean audio: free-run at 1.0× after one RELEASE; rare seeks only.

This is **not** Auracast / WebRTC sample lock. It is the scalable file+CDN model for many concurrent devices.

---

## 2. Core formula

Host writes (on play / pause / seek / track change):

| Field | Meaning |
|-------|---------|
| `epochMediaMs` | Media position (ms) at the epoch instant |
| `epochServerMs` | Firebase server time (ms) of that instant (`ServerValue.TIMESTAMP`) |
| `isPlaying` | Whether the timeline advances |
| `scheduleId` | Bumps on track change or hard seek (guest re-arms only then) |
| `mediaUrl` / metadata | Sticky track identity |

**Ideal position at any guest:**

```text
if (!isPlaying):
    ideal = epochMediaMs
else:
    ideal = epochMediaMs + max(0, serverNowMs - epochServerMs)

ideal = clamp(ideal, 0, durationMs)
```

`serverNowMs` = Firebase `.info/serverTimeOffset` + device `currentTimeMillis` (offset only; not “user clock as truth”).

---

## 3. Firebase node

Path: `parties/{partyId}/sync` (existing `PartyFirebasePaths.SYNC`)

### 3.1 Epoch fields (authoritative for ideal)

| Key | Type | When written |
|-----|------|----------------|
| `epochMediaMs` | long | Force events only (play, pause, seek, new track) |
| `epochServerMs` | long (server timestamp) | Same force events (`ServerValue.TIMESTAMP`) |
| `scheduleId` | long | New id on track change / hard seek; **stable** on heartbeats |
| `isPlaying` | boolean | Every meaningful transport change |
| `mediaUrl`, `objectKey`, `trackId`, title… | string | When known; **omit null** (sticky URL) |

### 3.2 Diagnostic / legacy (non-authoritative for ideal once epoch present)

| Key | Role |
|-----|------|
| `positionMs` | Host sample at publish (debug, UI) |
| `targetPositionMs` / `lookaheadMs` | Arm window only (buffer before RELEASE), not continuous ideal chase |
| `hostMonoMs` / `targetHostMonoMs` | Optional; not cross-device truth |
| `updatedAt` | Server write time of this packet |

### 3.3 Heartbeat rules (lightweight)

While same track + same play state:

- Keep **same** `scheduleId`.
- **Do not** overwrite `epochMediaMs` / `epochServerMs` (omit keys in `updateChildren`).
- May refresh `updatedAt`, `positionMs`, `isPlaying`.
- Min gap ~2s to limit RTDB and device heat.

Force publish (new epoch) when:

- Track / `mediaUrl` changes  
- Play ↔ pause  
- Hard seek / scrub  
- First URL ready after upload  

---

## 4. Roles

### Host

1. Plays local (or same URL) independently — **does not** run guest STALE loops.
2. After upload: sticky `mediaUrl` on sync node.
3. On force events: write new epoch + metadata.
4. Heartbeats: presence of timeline only; epoch stays fixed until next force.

### Guest

1. Observe `sync`.
2. Sticky URL if host omits `mediaUrl` on a packet.
3. `TimeEngine.idealTrackPositionMs()` from **epoch** (fallback: `positionMs + elapsed` if epoch missing).
4. New `scheduleId` or new URL → load → seek once to ideal → buffer → RELEASE once.
5. **LOCKED:** free-run at 1.0×; seek only if `|local − ideal| > ~1.2s` and cooldown; **no** rate-bend; **no** mute on short packet gaps.
6. Host `isPlaying=false` → pause; `true` → single debounced `play()`.

---

## 5. TimeEngine phases (unchanged intent)

```text
IDLE → LOADING → BUFFERING → ARMED → LOCKED
                              ↑        │
                              └── soft recover (no mute storm)
```

- Lookahead / `msUntilRelease`: **arm timing only**.
- Ideal: **epoch formula**.
- STALE: soft free-run preferred; full re-arm only on major desync / new schedule.

---

## 6. Server clock

`PartyServerClock`:

- Listens `.info/serverTimeOffset`
- `serverNowMs() = currentTimeMillis + offset`
- Started with party session

Packet-level EMA offset remains a backup if `.info` is not ready.

---

## 7. Implementation map

| Piece | Change |
|-------|--------|
| `PartyPlaybackSync` | Add `epochMediaMs`, `epochServerMs` |
| `PartyPlaybackSyncRepository.publishHostSync` | Write epoch on force; omit epoch keys on heartbeat |
| `PartyLiveBridge` | Force vs heartbeat; remember last epoch |
| `TimeAnchor` | Carry epoch fields from sync |
| `TimeEngine.idealTrackPositionMs` | Prefer epoch formula |
| `PartySyncTimeline` | Prefer epoch formula |
| Guest bridge | Unchanged free-run policy (already tightened) |

---

## 8. Explicit non-goals (this phase)

- WebRTC SFU / TURN live sample lock  
- Pitch / rate sync  
- 100% sample-accurate multi-device lock over HTTP  
- Host depending on Firebase packets to keep itself playing  

---

## 9. Success criteria

- Guest log: one `beginBuffer` → one `RELEASE ok` per track; rare seeks.  
- No `LOCKED resume play` spam; no STALE mute loops on normal heartbeats.  
- Pause/play and track change follow host within one packet + buffer.  
- Median `|local − ideal|` after lock stays in a comfortable band without continuous correction.

---

## 10. Later (optional)

- Stronger server tick via Cloud Function (debug / audit).  
- Optional SFU “live lock” mode for small N.  
- Host seamless switch local → same cloud URL after upload.
